package com.ie643.cxr

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ie643.cxr.core.Level
import com.ie643.cxr.ui.AboutScreen
import com.ie643.cxr.ui.AppTheme
import com.ie643.cxr.ui.BlockColor
import com.ie643.cxr.ui.FindingCard
import com.ie643.cxr.ui.HistoryScreen
import com.ie643.cxr.ui.HomeScreen
import com.ie643.cxr.ui.MetricsScreen
import com.ie643.cxr.ui.PrepareScreen
import com.ie643.cxr.ui.ResultScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AppTheme { AppRoot() } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(vm: AppViewModel = viewModel()) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) vm.startPrepare(uri, "upload")
    }
    var camUri by rememberSaveable { mutableStateOf<String?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok: Boolean ->
        val u = camUri
        if (ok && u != null) vm.startPrepare(Uri.parse(u), "camera")
    }
    var pendingSave by remember { mutableStateOf<Pair<Analysis, Boolean>?>(null) }
    val savePdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri: Uri? ->
        val p = pendingSave
        if (uri != null && p != null) scope.launch { writeCard(ctx, vm, p.first, true, uri) }
    }
    val savePng = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri: Uri? ->
        val p = pendingSave
        if (uri != null && p != null) scope.launch { writeCard(ctx, vm, p.first, false, uri) }
    }

    LaunchedEffect(vm.message) {
        val m = vm.message
        if (m != null) { snack.showSnackbar(m); vm.message = null }
    }
    BackHandler(enabled = vm.sub != Sub.NONE) { vm.back() }

    fun takePhoto() {
        val dir = File(ctx.cacheDir, "camera").also { it.mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val f = File(dir, "photo_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
        camUri = uri.toString()
        try { camera.launch(uri) } catch (e: Exception) { vm.message = "No camera app is available." }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            if (vm.sub == Sub.NONE) {
                NavigationBar {
                    for (t in Tab.values()) {
                        NavigationBarItem(
                            selected = vm.tab == t, onClick = { vm.tab = t },
                            icon = { Text(when (t) { Tab.ANALYZE -> "◉"; Tab.HISTORY -> "☰"; Tab.METRICS -> "▤"; Tab.ABOUT -> "ⓘ" }) },
                            label = { Text(when (t) { Tab.ANALYZE -> "Analyse"; Tab.HISTORY -> "History"; Tab.METRICS -> "Metrics"; Tab.ABOUT -> "About" }) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (vm.sub) {
                Sub.PREPARE -> PrepareScreen(vm)
                Sub.RESULT -> ResultScreen(
                    vm,
                    onShare = { a, pdf -> scope.launch { shareCard(ctx, vm, a, pdf) } },
                    onSave = { a, pdf ->
                        pendingSave = Pair(a, pdf)
                        if (pdf) savePdf.launch(vm.suggestedName(a, true)) else savePng.launch(vm.suggestedName(a, false))
                    },
                )
                Sub.NONE -> when (vm.tab) {
                    Tab.ANALYZE -> HomeScreen(vm, onPick = { pick.launch("image/*") }, onCamera = { takePhoto() })
                    Tab.HISTORY -> HistoryScreen(vm)
                    Tab.METRICS -> MetricsScreen(vm)
                    Tab.ABOUT -> AboutScreen(vm)
                }
            }
            if (vm.busy) {
                Box(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)).pointerInput(Unit) { detectTapGestures { } },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator()
                        Text(vm.busyMsg, color = Color.White)
                    }
                }
            }
        }
    }

    val blocks = vm.pendingBlock
    if (blocks != null) {
        AlertDialog(
            onDismissRequest = { vm.dismissBlock() },
            title = { Text("This image may not be suitable", color = BlockColor) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (f in blocks.filter { it.level != Level.HINT }) FindingCard(f.level.name, f.title, f.detail)
                    Text("Report generation has not run. If you continue, the result is shown greyed out and marked NOT RELIABLE.")
                }
            },
            confirmButton = { TextButton(onClick = { vm.confirmBlock() }) { Text("Analyse anyway") } },
            dismissButton = { TextButton(onClick = { vm.dismissBlock() }) { Text("Cancel") } },
        )
    }
}

private suspend fun shareCard(ctx: android.content.Context, vm: AppViewModel, a: Analysis, pdf: Boolean) {
    try {
        val f = withContext(Dispatchers.Default) { vm.cardFile(a, pdf) }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = if (pdf) "application/pdf" else "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "IE643 chest X-ray report (research prototype, not for clinical use)")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(send, "Share result"))
    } catch (e: Exception) {
        vm.message = "Could not create the file: ${e.message}"
    }
}

private suspend fun writeCard(ctx: android.content.Context, vm: AppViewModel, a: Analysis, pdf: Boolean, dest: Uri) {
    try {
        withContext(Dispatchers.IO) {
            val f = vm.cardFile(a, pdf)
            ctx.contentResolver.openOutputStream(dest)?.use { out -> f.inputStream().use { it.copyTo(out) } }
        }
        vm.message = "Saved."
    } catch (e: Exception) {
        vm.message = "Could not save the file: ${e.message}"
    }
}

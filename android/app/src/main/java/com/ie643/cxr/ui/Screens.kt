@file:OptIn(ExperimentalMaterial3Api::class)

package com.ie643.cxr.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ie643.cxr.Analysis
import com.ie643.cxr.AppViewModel
import com.ie643.cxr.ImageIO
import com.ie643.cxr.Sample
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun fmtTime(ts: Long): String = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(ts))

// ============================================================ HOME
@Composable
fun HomeScreen(vm: AppViewModel, onPick: () -> Unit, onCamera: () -> Unit) {
    val ready = vm.engineReady
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Chest X-ray report assistant", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Research prototype, not for clinical use.", style = MaterialTheme.typography.bodyMedium, color = BlockColor, fontWeight = FontWeight.SemiBold)
            }
        }
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f))) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (vm.engineError != null) {
                        Text("Could not load the model", fontWeight = FontWeight.SemiBold, color = BlockColor)
                        Text(vm.engineError ?: "", style = MaterialTheme.typography.bodySmall)
                    } else {
                        Text(vm.engineStatus, style = MaterialTheme.typography.bodyMedium)
                        if (!ready) LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PrimaryButton("Upload image", enabled = ready, modifier = Modifier.weight(1f), onClick = onPick)
                PrimaryButton("Take photo", enabled = ready, modifier = Modifier.weight(1f), onClick = onCamera)
            }
        }
        item {
            Text(
                "Photos of screens or films add glare and distortion and are less reliable. A properly exported X-ray image works best.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        item { Text("Sample X-rays from the test set", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
        item {
            Text("Tap one to run the model on the phone and compare with the ground-truth report.", style = MaterialTheme.typography.bodySmall)
        }
        items(vm.samples.chunked(3)) { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (s in rowItems) SampleThumb(vm, s, ready, Modifier.weight(1f))
                repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun SampleThumb(vm: AppViewModel, s: Sample, enabled: Boolean, modifier: Modifier) {
    val bmp by produceState<ImageBitmap?>(null, s.file) {
        value = withContext(Dispatchers.IO) { vm.sampleThumb(s.file).asImageBitmap() }
    }
    Box(
        modifier.aspectRatio(1f).clip(RoundedCornerShape(10.dp)).background(androidx.compose.ui.graphics.Color.Black)
            .clickable(enabled = enabled) { vm.analyzeSample(s) },
        contentAlignment = Alignment.Center,
    ) {
        val b = bmp
        if (b != null) Image(b, "Sample ${s.idx}", Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = if (enabled) 1f else 0.4f)
        Text("#${s.idx}", Modifier.align(Alignment.BottomStart).padding(6.dp), color = androidx.compose.ui.graphics.Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ============================================================ PREPARE
@Composable
fun PrepareScreen(vm: AppViewModel) {
    val p = vm.prep ?: return
    val ready = vm.engineReady
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Prepare the image") },
            windowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
            navigationIcon = { IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        )
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Drag the frame so it covers only the chest. The model needs an upright, frontal X-ray in normal polarity. DO NOT mirror the image.", style = MaterialTheme.typography.bodySmall)
            CropBox(p.rotated, p.crop, onCrop = { vm.setCrop(it) }, onDone = { vm.commitCrop() })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { vm.rotatePrep(-1) }, modifier = Modifier.weight(1f)) { Text("Rotate left") }
                OutlinedButton(onClick = { vm.rotatePrep(1) }, modifier = Modifier.weight(1f)) { Text("Rotate right") }
                OutlinedButton(onClick = { vm.resetCrop() }, modifier = Modifier.weight(1f)) { Text("Reset crop") }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Invert (bones dark, background bright)", fontWeight = FontWeight.Medium)
                    Text("Use if the image is a negative.", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = p.invert, onCheckedChange = { vm.setInvert(it) })
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Match training contrast", fontWeight = FontWeight.Medium)
                    Text("The training images were histogram-equalised, so uploads are too. Leave on unless the image is already a processed dataset image.", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = p.equalize, onCheckedChange = { vm.setEqualize(it) })
            }
            val prev = p.preview
            if (prev != null) {
                SectionCard("What the model will see (512 x 512)") {
                    Image(
                        prev.asImageBitmap(), "Model input preview", Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Fit, filterQuality = FilterQuality.Medium,
                    )
                    Text("Check the L/R marker and the orientation here. A mirrored image gives a wrong-sided report with no warning.", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (p.findings.isEmpty()) {
                Text("No input problems detected by the heuristic checks.", style = MaterialTheme.typography.bodyMedium)
            } else {
                Text("Input checks", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                for (f in p.findings) {
                    val act = when (f.action) { "invert" -> "Invert now"; "rotate" -> "Rotate right"; else -> null }
                    FindingCard(
                        f.level.name, f.title, f.detail, act,
                        onAction = when (f.action) { "invert" -> ({ vm.setInvert(!p.invert) }); "rotate" -> ({ vm.rotatePrep(1) }); else -> null },
                    )
                }
                Text("These checks are heuristics. They were tuned on the sample X-rays and altered copies of them, not on real camera photos.", style = MaterialTheme.typography.bodySmall)
            }
        }
        val hasBlock = p.findings.any { it.level == com.ie643.cxr.core.Level.BLOCK }
        Column(Modifier.padding(16.dp)) {
            PrimaryButton(if (hasBlock) "Analyse… (blocking issue)" else "Analyse", enabled = ready, modifier = Modifier.fillMaxWidth()) { vm.analyzePrepared() }
        }
    }
}

// ============================================================ RESULT
@Composable
fun ResultScreen(vm: AppViewModel, onShare: (Analysis, Boolean) -> Unit, onSave: (Analysis, Boolean) -> Unit) {
    val a = vm.current ?: return
    val cfg = vm.config ?: return
    var showExport by remember { mutableStateOf(false) }
    val img = remember(a.id, a.canvas) { a.canvas?.let { ImageIO.grayToBitmap(it, 512, 512).asImageBitmap() } }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Result") },
            windowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
            navigationIcon = { IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            actions = { IconButton(onClick = { showExport = true }) { Icon(Icons.Filled.Share, "Share or save") } },
        )
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val src = a.sample?.let { "Test-set sample #${it.idx}" } ?: if (a.source == "camera") "Your image (camera photo)" else "Your image (uploaded)"
            Text("$src  |  ${fmtTime(a.ts)}", style = MaterialTheme.typography.bodySmall)
            if (!a.reliable) {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = BlockColor.copy(alpha = 0.15f))) {
                    Text("NOT RELIABLE: this image was analysed after overriding a blocking warning. Do not read anything into the result.", Modifier.padding(12.dp), color = BlockColor, fontWeight = FontWeight.Bold)
                }
            }
            val notes = a.findings.filter { it.id != "padded" && it.id != "trim" }
            for (f in notes.sortedBy { when (it.level) { "BLOCK" -> 0; "WARN" -> 1; else -> 2 } }) FindingCard(f.level, f.title, f.detail)

            SectionCard("Draft report (generated on this phone)") {
                Text(a.report.ifBlank { "(empty report)" }, fontSize = 18.sp, lineHeight = 26.sp, modifier = Modifier.alpha(if (a.reliable) 1f else 0.45f))
                Text("A draft for a clinician to review, not a diagnosis. Produced by a 95M-parameter student distilled from a 3B teacher model.", style = MaterialTheme.typography.bodySmall)
            }

            SectionCard("Screening flags") {
                val fired = cfg.flags.filter { it.label in a.fired }
                Text(if (fired.isEmpty()) "No flag fired." else "${fired.size} of ${cfg.flags.size} flags fired:", fontWeight = FontWeight.Medium)
                for (f in cfg.flags) {
                    val on = f.label in a.fired
                    Column(Modifier.alpha(if (on) 1f else 0.5f)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text((if (on) "● " else "○ ") + f.label, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
                            Text(if (on) "FLAG" else "clear", color = if (on) WarnColor else HintColor, fontWeight = FontWeight.SemiBold)
                        }
                        if (on) {
                            Text(
                                "TEST sensitivity ${"%.2f".format(f.sens)}, PPV ${"%.2f".format(f.ppv)}, AUC ${"%.2f".format(f.auc)} (${f.positives} positives)" +
                                    if (f.label in cfg.textNeverNames) ". The report text never names this finding." else "",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
                HorizontalDivider()
                Text("Flags come from a separate screening layer on the image features, tuned for about ${(cfg.flagTarget * 100).toInt()}% sensitivity, so they raise many false alarms and can disagree with the report text (the report is not wrong or right because of a flag).", style = MaterialTheme.typography.bodySmall)
                Text("Not assessed: ${cfg.notAssessed.joinToString(", ")}.", style = MaterialTheme.typography.bodySmall)
                Text(cfg.limits, style = MaterialTheme.typography.bodySmall)
            }

            a.sample?.let { s ->
                SectionCard("Ground truth for this test image") {
                    Text(s.gt, fontSize = 16.sp)
                    Text("Positive findings (CheXbert on the reference): ${if (s.gtPositive.isEmpty()) "none" else s.gtPositive.joinToString(", ")}", style = MaterialTheme.typography.bodySmall)
                    val m = a.matchesSaved
                    if (m != null) Text(if (m) "On-device result is identical to the report and flags saved at export time." else "Differs from the report saved at export time (saved: ${s.model}).", style = MaterialTheme.typography.bodySmall, color = if (m) HintColor else BlockColor)
                }
            }
            if (img != null) {
                SectionCard("Image as seen by the model") {
                    Image(img, "Model input", Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Fit)
                }
            }
            a.timing?.let { t ->
                SectionCard("On-device timing") {
                    Kv("Image preparation", "${t.prepMs} ms")
                    Kv("Image encoder", "${t.encoderMs} ms")
                    Kv("Report generation", "${t.generateMs} ms (${t.steps} steps, ${"%.1f".format(t.msPerStep)} ms/step)")
                    Kv("Total", "${t.totalMs} ms")
                    Kv("Peak memory (PSS)", "${t.peakPssMb} MB (${t.basePssMb} MB before)")
                    Kv("CPU threads", "${t.threads}")
                    Kv("Model load (app start)", "${t.loadMs} ms")
                    if (a.d2 != null) Kv("Plausibility distance", "${"%.0f".format(a.d2)} (warn above 324)")
                    Text(vm.deviceLine + ", ONNX Runtime ${AppViewModel.ORT_VERSION}, ${cfg.decoderPrecision} decoder.", style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { showExport = true }, modifier = Modifier.weight(1f)) { Text("Share or save") }
                Button(onClick = { vm.closeResult() }, modifier = Modifier.weight(1f)) { Text("Done") }
            }
        }
    }
    if (showExport) {
        AlertDialog(
            onDismissRequest = { showExport = false },
            title = { Text("Share or save this result") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("The card contains the report, flags, input warnings and limits, and a \"$WATERMARK_TEXT\" watermark." +
                        if (a.sample != null) " The sample X-ray itself is left out (CheXpert Plus images cannot be redistributed)." else " Your image is included.")
                    TextButton(onClick = { showExport = false; onShare(a, true) }) { Text("Share as PDF") }
                    TextButton(onClick = { showExport = false; onShare(a, false) }) { Text("Share as image") }
                    TextButton(onClick = { showExport = false; onSave(a, true) }) { Text("Save PDF to a folder…") }
                    TextButton(onClick = { showExport = false; onSave(a, false) }) { Text("Save image to a folder…") }
                }
            },
            confirmButton = { TextButton(onClick = { showExport = false }) { Text("Cancel") } },
        )
    }
}

const val WATERMARK_TEXT = "Research prototype, not for clinical use"

// ============================================================ HISTORY
@Composable
fun HistoryScreen(vm: AppViewModel) {
    var confirm by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("History", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Keep a history on this phone", fontWeight = FontWeight.Medium)
                    Text("Turning it off does not delete what is already saved.", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = vm.historyOn, onCheckedChange = { vm.updateHistoryOn(it) })
            }
        }
        if (vm.history.isNotEmpty()) {
            item { OutlinedButton(onClick = { confirm = true }) { Text("Clear all") } }
        }
        if (vm.history.isEmpty()) item { Text("No saved analyses yet.", style = MaterialTheme.typography.bodyMedium) }
        items(vm.history, key = { it.id }) { a ->
            Card(Modifier.fillMaxWidth().clickable { vm.openHistory(a) }) {
                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    val thumb by produceState<ImageBitmap?>(null, a.id) {
                        value = withContext(Dispatchers.IO) {
                            if (a.sample != null) vm.sampleThumb(a.sample.file).asImageBitmap() else vm.historyThumb(a)?.asImageBitmap()
                        }
                    }
                    Box(Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                        val t = thumb
                        if (t != null) Image(t, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) else Text("—")
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(a.sample?.let { "Sample #${it.idx}" } ?: if (a.source == "camera") "Camera photo" else "Uploaded image", fontWeight = FontWeight.SemiBold)
                        Text(fmtTime(a.ts) + if (a.reliable) "" else "  |  NOT RELIABLE", style = MaterialTheme.typography.bodySmall, color = if (a.reliable) HintColor else BlockColor)
                        Text(a.report, maxLines = 2, style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = { vm.deleteHistory(a) }) { Icon(Icons.Filled.Delete, "Delete") }
                }
            }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Clear the history?") },
            text = { Text("All saved analyses and thumbnails on this phone will be deleted.") },
            confirmButton = { TextButton(onClick = { confirm = false; vm.clearHistory() }) { Text("Delete all") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
}

// ============================================================ METRICS
@Composable
fun MetricsScreen(vm: AppViewModel) {
    val r = vm.results
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Performance metrics", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        if (r == null) {
            item { Text("results.json is missing from the app.", style = MaterialTheme.typography.bodyMedium) }
        } else {
            items(r.sections) { s ->
                SectionCard(s.title) {
                    if (s.note.isNotEmpty()) Text(s.note, style = MaterialTheme.typography.bodySmall)
                    for (row in s.rows) {
                        Column {
                            Kv(row.label, row.value)
                            if (row.note.isNotEmpty()) Text(row.note, style = MaterialTheme.typography.bodySmall, color = HintColor)
                        }
                    }
                }
            }
        }
    }
}

// ============================================================ ABOUT
@Composable
fun AboutScreen(vm: AppViewModel) {
    val cfg = vm.config
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("About this prototype", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(WATERMARK_TEXT + ".", color = BlockColor, fontWeight = FontWeight.SemiBold)
        SectionCard("The app") {
            Text("A chest X-ray goes in; a short draft impression comes out, together with screening flags. It runs on this phone with ONNX Runtime on the CPU.", style = MaterialTheme.typography.bodyMedium)
            Text("1. Crop, grayscale, plain borders trimmed, longer side to 512 px, histogram equalisation, centred on a black 512 x 512 square (the same preparation as the training images).", style = MaterialTheme.typography.bodySmall)
            Text("2. A ViT-Tiny encoder reads the image at 384 px and produces 32 visual tokens and a 384-number feature vector.", style = MaterialTheme.typography.bodySmall)
            Text("3. A DistilGPT2-sized decoder (vocabulary pruned to 3,877 tokens) writes the impression one token at a time (greedy decoding).", style = MaterialTheme.typography.bodySmall)
            Text("4. Linear probes on the encoder features raise the screening flags. A distance to the training features drives the plausibility check.", style = MaterialTheme.typography.bodySmall)
        }
        if (cfg != null) {
            SectionCard("Model") {
                Kv("Student run", cfg.run)
                Kv("Probe", cfg.probe)
                Kv("Export version", cfg.version)
                Kv("Decoder weights", "${cfg.decoderPrecision} storage (identical reports to fp32)")
                Kv("ONNX Runtime", AppViewModel.ORT_VERSION)
                Text("Teacher (training only, not in the app): CheXagent-2-3b, verified by the TA.", style = MaterialTheme.typography.bodySmall)
            }
            SectionCard("Screening flags (TEST set)") {
                for (f in cfg.flags) {
                    Column {
                        Text(f.label, fontWeight = FontWeight.Medium)
                        Text("sensitivity ${f.sensCi}, PPV ${"%.2f".format(f.ppv)}, specificity ${"%.2f".format(f.spec)}, AUC ${"%.2f".format(f.auc)}, ${f.positives} positives" +
                            if (f.label in cfg.textNeverNames) ", report text never names it" else "", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Text("Not assessed (probe too weak): ${cfg.notAssessed.joinToString(", ")}.", style = MaterialTheme.typography.bodySmall)
            }
            SectionCard("Limits") {
                Text(cfg.limits, style = MaterialTheme.typography.bodyMedium)
                Text("The report text never names ${cfg.textNeverNames.size} of the ${cfg.flags.size} flagged findings, because they are rare in the training data. The encoder does carry them (linear probe AUC, e.g. Cardiomegaly 0.83, Pneumothorax 0.79), so the flags cover what the decoder leaves out.", style = MaterialTheme.typography.bodySmall)
                Text("Input checks are heuristics tuned on the sample X-rays and altered copies, not on real camera photos. A mirrored image or a lateral view may pass unnoticed.", style = MaterialTheme.typography.bodySmall)
            }
        }
        SectionCard("Data and sharing") {
            Text("The weights and the sample images derive from CheXpert Plus (Stanford Research Use Agreement, non-commercial). The APK is private and must not be published. Exported cards leave out the sample X-ray images.", style = MaterialTheme.typography.bodySmall)
        }
        SectionCard("This device") { Text(vm.deviceLine, style = MaterialTheme.typography.bodySmall) }
    }
}

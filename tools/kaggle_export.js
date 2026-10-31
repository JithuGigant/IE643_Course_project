// Run on https://www.kaggle.com (any page) in Chrome while signed in as jithendraampatt.
// DevTools (F12) -> Console -> paste -> Enter. It downloads ONE zip: kaggle_ie643_versions.zip
// Each version is saved as source only (outputs stripped) plus a manifest with run end-times.
(async () => {
  const LIST = {
    eda: {1:357876473, 2:357911004, 3:357915449, 4:357923231},
    cleaning: {1:359697222, 2:359704724, 3:360186854},
    cleaning_step7: {1:360207717},
    teacher_cache: {1:360264449, 2:360269863, 3:360374007, 4:360858511},
    tokencheck_baselines: {1:360231606, 2:360242277, 3:360423706},
    token_kd_diagnostic: {1:360916631},
    student_architecture_pilot: {1:360860677, 2:360865736},
    distillation: {1:360883709, 2:360888113, 3:360894425, 4:360928395, 5:361113611, 6:361114542, 7:361180984, 8:361181487, 9:361224216, 10:361233324, 11:361255594, 12:361258171, 13:361382996, 14:361429826, 15:361436370, 16:361453684, 17:361468177, 18:361471600, 19:361499281, 20:361558478, 21:361561022, 22:361576576, 23:361595331},
    distill_v10: {1:361778009, 2:361780508, 3:361813937},
    evaluation: {2:361830694, 3:361911978, 4:361925652, 5:361928280},
    probe_flags: {1:361776675, 2:361870669},
    deployment: {1:361942417}
  };
  const files = {}, manifest = {};
  const jobs = [];
  for (const [slug, vs] of Object.entries(LIST))
    for (const [n, id] of Object.entries(vs)) jobs.push([slug, +n, id]);
  let done = 0;
  const run = async ([slug, n, id]) => {
    const r = await fetch('/kernels/scriptcontent/' + id + '/download', { credentials: 'include' });
    if (!r.ok) { manifest[slug + '/v' + n] = { id, error: r.status }; return; }
    const nb = JSON.parse(await r.text());
    const cells = nb.cells.map(c => {
      const o = { cell_type: c.cell_type, metadata: {}, source: c.source };
      if (c.cell_type === 'code') { o.execution_count = null; o.outputs = []; }
      return o;
    });
    const ends = nb.cells.map(c => c.metadata && c.metadata.papermill && c.metadata.papermill.end_time).filter(Boolean).sort();
    const clean = { cells, metadata: nb.metadata && nb.metadata.kernelspec ? { kernelspec: nb.metadata.kernelspec, language_info: nb.metadata.language_info } : {}, nbformat: 4, nbformat_minor: 4 };
    files[slug + '/v' + String(n).padStart(2, '0') + '.ipynb'] = JSON.stringify(clean, null, 1);
    manifest[slug + '/v' + n] = { id, end: ends[ends.length - 1] || null, cells: cells.length };
    done++;
  };
  // 4 at a time
  for (let i = 0; i < jobs.length; i += 4) await Promise.all(jobs.slice(i, i + 4).map(run));
  files['manifest.json'] = JSON.stringify(manifest, null, 1);
  // store-only zip
  const enc = new TextEncoder();
  const T = new Uint32Array(256).map((_, n) => { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xEDB88320 ^ (c >>> 1) : c >>> 1; return c >>> 0; });
  const crc = b => { let c = 0xFFFFFFFF; for (let i = 0; i < b.length; i++) c = T[(c ^ b[i]) & 255] ^ (c >>> 8); return (c ^ 0xFFFFFFFF) >>> 0; };
  const parts = [], cen = []; let off = 0;
  for (const [name, txt] of Object.entries(files)) {
    const nb = enc.encode(name), d = enc.encode(txt), cr = crc(d);
    const lh = new DataView(new ArrayBuffer(30));
    lh.setUint32(0, 0x04034b50, true); lh.setUint16(4, 20, true); lh.setUint16(6, 0x0800, true); lh.setUint16(12, 0x21, true);
    lh.setUint32(14, cr, true); lh.setUint32(18, d.length, true); lh.setUint32(22, d.length, true); lh.setUint16(26, nb.length, true);
    parts.push(new Uint8Array(lh.buffer), nb, d);
    const ch = new DataView(new ArrayBuffer(46));
    ch.setUint32(0, 0x02014b50, true); ch.setUint16(4, 20, true); ch.setUint16(6, 20, true); ch.setUint16(8, 0x0800, true); ch.setUint16(14, 0x21, true);
    ch.setUint32(16, cr, true); ch.setUint32(20, d.length, true); ch.setUint32(24, d.length, true); ch.setUint16(28, nb.length, true); ch.setUint32(42, off, true);
    cen.push(new Uint8Array(ch.buffer), nb); off += 30 + nb.length + d.length;
  }
  const cs = cen.reduce((a, b) => a + b.length, 0), N = Object.keys(files).length;
  const e = new DataView(new ArrayBuffer(22));
  e.setUint32(0, 0x06054b50, true); e.setUint16(8, N, true); e.setUint16(10, N, true); e.setUint32(12, cs, true); e.setUint32(16, off, true);
  const blob = new Blob([...parts, ...cen, new Uint8Array(e.buffer)], { type: 'application/zip' });
  const a = document.createElement('a'); a.href = URL.createObjectURL(blob); a.download = 'kaggle_ie643_versions.zip';
  document.body.appendChild(a); a.click();
  console.log('fetched ' + done + '/' + jobs.length + ' versions, ' + N + ' files, ' + Math.round(blob.size / 1024) + ' kB');
})();

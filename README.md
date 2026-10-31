# IE643 course project: chest X-ray report generation by vision-language distillation

Automated radiology report generation from chest X-rays. A small student model (ViT-Tiny image encoder, a 32-query resampler that feeds
a prefix to DistilGPT2, about 95M parameters) is trained from a frozen teacher (CheXagent-2-3b) on a stratified 10k subset of CheXpert Plus,
evaluated clinically (CheXbert label agreement, RadGraph, safety checks) and exported to ONNX for an on-device Android app.

Course: IE643 (2026), IIT Bombay. Author: Jithendra J Ampatt.

## Repository layout

| Path | What it is |
|---|---|
| `notebooks/00_eda.ipynb` | EDA of the 10k CheXpert Plus subset |
| `notebooks/01_cleaning.ipynb` | Report/label cleaning and 512 px images (steps 1-6) |
| `notebooks/02_cleaning_step7.ipynb` | Label-conflict fix, text rules, leakage audit (step 7) |
| `notebooks/03_teacher_cache.ipynb` | Teacher caching: smoke, probe, cache (T4 x2), merge |
| `notebooks/04_tokencheck_baselines.ipynb` | Tokenizer identity check and trivial/retrieval baselines (224 and 384 px) |
| `notebooks/05_student_architecture_pilot.ipynb` | Connector pilot: prefix vs cross-attention |
| `notebooks/06_token_kd_diagnostic.ipynb` | Token-level KD diagnostic |
| `notebooks/07_distillation.ipynb` | Student training: ablations, sequence-level KD, SCST, KL-SCST (23 Kaggle versions) |
| `notebooks/08_distill_v10.ipynb` | Final distillation runs (v10a, v10b, F2 reward) |
| `notebooks/09_evaluation.ipynb` | Final evaluation of all students (CPU) |
| `notebooks/10_probe_flags.ipynb` | Safety layer: linear probe flags on the deployed encoder |
| `notebooks/11_deployment.ipynb` | Export of the deployed student to ONNX (encoder + one-step decoder) |
| `docs/VERSION_MAP.md` | Kaggle version -> date, status, notebook stamp, run set, for every notebook |
| `android/` | Android app (Kotlin, Jetpack Compose, ONNX Runtime) that runs the exported student on the phone |
| `tools/kaggle_export.js` | Console snippet that exported the notebook versions from Kaggle |

Run order is the numbering. Each notebook reads the outputs of earlier ones as Kaggle inputs
(mounted under `/kaggle/input/notebooks/jithendraampatt/<notebook>/`), so they are written for Kaggle, not for local execution.
Training uses GPU T4 x2; evaluation, probe flags and cleaning run on CPU.

## How the history is organised

The notebooks were developed on Kaggle, where every "Save & Run All" creates a numbered version. This repository records that history:

* each commit is one saved Kaggle version of one notebook; the commit date is the end time of that run (UTC) and the message says which
  version it is, its status, and what changed;
* the history was reconstructed on 2026-10-30 from Kaggle's version history, so commit dates are run times, not editing times;
* notebooks are stored without cell outputs (source only);
* tags: `<notebook>-kaggle-v<N>` for every version, and the notebooks' own stamps (`distill-v2` ... `distill-v9.1`, `distill-v10a`, `distill-v10b`,
  `eval-final-v2`, `eval-final-v4.1`, `probe-v1`, `probe-v2`, `export-v1.1`) on the first Kaggle version that carries them.

Examples:

```bash
git log --oneline --follow notebooks/07_distillation.ipynb   # all 23 versions of the training notebook
git checkout distill-v7                                       # the sequence-level KD version
git diff distill-v8 distill-v8.1 -- notebooks/07_distillation.ipynb
```

`docs/VERSION_MAP.md` lists which Kaggle version produced which runs. Earlier run sets generally stay selectable through `RUN_SET` in later
versions (see the comment next to it), and the exact earlier code is always available through the tags.

## Android app

`android/` is the Gradle project as developed in Android Studio (package `com.ie643.cxr`, minSdk 24). It runs the exported encoder and one-step decoder
with ONNX Runtime, applies the same preprocessing as the cleaning notebook, shows input-quality checks and probe flags, and keeps a history.
To build it, run `notebooks/11_deployment.ipynb` on Kaggle to produce the `app_bundle` folder (`encoder.onnx`, a decoder `.onnx`, `vocab.json`,
`app_config.json`, `samples/samples.json`), point `bundleDir` in `android/gradle.properties` at it, put the plausibility statistics file next to
`results.json` in `app/src/main/assets/` and open `android/` in Android Studio. The model files, sample X-rays and the plausibility file are not
in the repository.

## Data and weights

Not in the repository: CheXpert Plus images and reports (Stanford research-use agreement), the cached teacher outputs, model weights, the ONNX
export and the Android build. `.gitignore` excludes them.

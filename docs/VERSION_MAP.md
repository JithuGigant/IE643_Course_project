# Kaggle version map

Every notebook in `notebooks/` is the *latest* Kaggle version; earlier versions are earlier commits of the same file (`git log --follow notebooks/07_distillation.ipynb`). Each commit is one saved Kaggle version, dated by that run's end time (UTC). `*` = the version was cancelled or failed before any cell finished, so it has no run time; it is placed one minute after the previous version. Tags: `<notebook>-kaggle-v<N>` for every version, plus the notebook's own version stamp (e.g. `distill-v6.1`, `eval-final-v4.1`, `probe-v2`) on the first Kaggle version that carries it.


## `notebooks/00_eda.ipynb` - Kaggle "IE643 EDA" (`ie643-eda`)

| Kaggle v | Run end (UTC) | Status | Stamp | Mode | Run set | Runs | Lines changed | Tag |
|---|---|---|---|---|---|---|---|---|
| 1 | 2026-10-13 05:08 | ran 3m41s |  |  |  |  | first |  |
| 2 | 2026-10-13 07:34 | ran 4m11s |  |  |  |  | +189 -22 |  |
| 3 | 2026-10-13 07:35* | cancelled after 0s |  |  |  |  | +0 -0 |  |
| 4 | 2026-10-13 08:19 | ran 3m35s |  |  |  |  | +0 -0 |  |

## `notebooks/01_cleaning.ipynb` - Kaggle "IE643 Cleaning" (`ie643-cleaning`)

| Kaggle v | Run end (UTC) | Status | Stamp | Mode | Run set | Runs | Lines changed | Tag |
|---|---|---|---|---|---|---|---|---|
| 1 | 2026-10-18 12:36 | ran 7m52s |  |  |  |  | first |  |
| 2 | 2026-10-18 13:05 | ran 8m48s |  |  |  |  | +15 -3 |  |
| 3 | 2026-10-19 20:46 | cancelled after 1h44m |  |  |  |  | +204 -0 |  |

## `notebooks/02_cleaning_step7.ipynb` - Kaggle "IE643 Cleaning Step 7" (`ie643-cleaning-step-7`)

| Kaggle v | Run end (UTC) | Status | Stamp | Mode | Run set | Runs | Lines changed | Tag |
|---|---|---|---|---|---|---|---|---|
| 1 | 2026-10-19 22:49 | ran 1m36s |  |  |  |  | first |  |

## `notebooks/03_teacher_cache.ipynb` - Kaggle "IE643 Teacher Cache_GPU_probe_and_commit" (`ie643-teacher-cache-gpu-probe-and-commit`)

| Kaggle v | Run end (UTC) | Status | Stamp | Mode | Run set | Runs | Lines changed | Tag |
|---|---|---|---|---|---|---|---|---|
| 1 | 2026-10-20 03:40 | ran 14m51s | v3-mplbackend-fix | smoke |  |  | first |  |
| 2 | 2026-10-20 04:07 | ran 19m15s | v3-mplbackend-fix | probe |  |  | +2 -2 |  |
| 3 | 2026-10-20 16:16 | ran 6h19m | v3-mplbackend-fix | cache |  |  | +2 -2 |  |
| 4 | 2026-10-21 17:02 | ran 1m7s | v3-mplbackend-fix | merge |  |  | +1 -1 |  |

## `notebooks/04_tokencheck_baselines.ipynb` - Kaggle "IE643 tokencheck baselines_224and384" (`ie643-tokencheck-baselines-224and384`)

| Kaggle v | Run end (UTC) | Status | Stamp | Mode | Run set | Runs | Lines changed | Tag |
|---|---|---|---|---|---|---|---|---|
| 1 | 2026-10-20 01:03 | ran 10m42s |  |  |  |  | first |  |
| 2 | 2026-10-20 02:00 | ran 8m28s |  |  |  |  | +6 -6 |  |
| 3 | 2026-10-20 13:19 | ran 28m50s |  |  |  |  | +86 -62 |  |

## `notebooks/05_student_architecture_pilot.ipynb` - Kaggle "IE643 Student architecture_pilot" (`ie643-student-architecture-pilot`)

| Kaggle v | Run end (UTC) | Status | Stamp | Mode | Run set | Runs | Lines changed | Tag |
|---|---|---|---|---|---|---|---|---|
| 1 | 2026-10-21 17:11 | ran 4m5s |  | smoke |  |  | first |  |
| 2 | 2026-10-21 17:37 | ran 14m14s |  | pilot |  |  | +1 -1 |  |

## `notebooks/06_token_kd_diagnostic.ipynb` - Kaggle "IE643 Token KD Diagnostic" (`ie643-token-kd-diagnostic`)

| Kaggle v | Run end (UTC) | Status | Stamp | Mode | Run set | Runs | Lines changed | Tag |
|---|---|---|---|---|---|---|---|---|
| 1 | 2026-10-21 21:13 | ran 1m50s |  |  |  |  | first |  |

## `notebooks/07_distillation.ipynb` - Kaggle "IE643 Distillation" (`ie643-distillation`)

| Kaggle v | Run end (UTC) | Status | Stamp | Mode | Run set | Runs | Lines changed | Tag |
|---|---|---|---|---|---|---|---|---|
| 1 | 2026-10-21 18:38 | ran 7m29s | distill-v2 | smoke | ablation1 |  | first | `distill-v2` |
| 2 | 2026-10-21 18:52 | cancelled after 10m8s | distill-v2 | train | ablation1 |  | +1 -1 |  |
| 3 | 2026-10-21 20:42 | ran 1h20m | distill-v3 | train | ablation1 |  | +48 -12 | `distill-v3` |
| 4 | 2026-10-21 23:11 | ran 54m4s | distill-v4.1 | train | ablation3 |  | +109 -13 | `distill-v4.1` |
| 5 | 2026-10-21 23:12* | cancelled after 0s | distill-v5 | train | ablation2 | CE-only seeds + 224 + xattn | +51 -7 | `distill-v5` |
| 6 | 2026-10-21 23:13* | cancelled after 0s | distill-v5 | train | ablation2 | CE-only seeds + 224 + xattn | +0 -0 |  |
| 7 | 2026-10-21 23:14* | cancelled after 0s | distill-v5 | train | custom |  | +6 -0 |  |
| 8 | 2026-10-22 16:30 | ran 32m3s | distill-v5 | train | custom | A0s1_ce | +2 -8 |  |
| 9 | 2026-10-22 19:02 | ran 32m56s | distill-v5 | train | custom | A4_ce_xattn, A0s2_ce | +1 -1 |  |
| 10 | 2026-10-22 19:54 | ran 39m44s | distill-v5 | train | custom | A5_ce_224 | +1 -1 |  |
| 11 | 2026-10-22 21:16 | ran 7m38s | distill-v6 | smoke | prompt | A10, A10s1: diagnosis prompts | +140 -19 | `distill-v6` |
| 12 | 2026-10-22 22:44 | failed after 33m29s | distill-v6 | train | prompt | A10, A10s1: diagnosis prompts | +1 -1 |  |
| 13 | 2026-10-23 08:12 | ran 39m23s | distill-v6.1 | train | prompt | A10, A10s1: diagnosis prompts | +13 -4 | `distill-v6.1` |
| 14 | 2026-10-23 10:48 | ran 15m26s | distill-v7 | smoke | seqkd1 | S1, S1s1: teacher text -> GT | +105 -11 | `distill-v7` |
| 15 | 2026-10-23 11:21 | ran 21m31s | distill-v7 | train | seqkd1 | S1, S1s1: teacher text -> GT | +1 -1 |  |
| 16 | 2026-10-23 12:32 | ran 22m9s | distill-v7 | train | seqkd2 | S2, S2s1: mixed | +1 -1 |  |
| 17 | 2026-10-23 13:14 | ran 9m21s | distill-v8 | smoke | scst | R0, R1: SCST from A0_ce / A0s1_ce | +216 -13 | `distill-v8` |
| 18 | 2026-10-23 14:09 | ran 50m38s | distill-v8 | train | scst | R0, R1: SCST from A0_ce / A0s1_ce | +1 -1 |  |
| 19 | 2026-10-23 15:51 | ran 45m51s | distill-v8.1 | train | scst2 | R0r, R1r: SCST + repeated-trigram penalty | +17 -9 | `distill-v8.1` |
| 20 | 2026-10-23 19:15 | ran 8m1s | distill-v9 | smoke | klscst1 | K7a, K7b: KL-SCST from A7, beta 0.02 / 0.1 | +218 -26 | `distill-v9` |
| 21 | 2026-10-23 19:56 | failed after 37m3s | distill-v9 | train | klscst1 | K7a, K7b: KL-SCST from A7, beta 0.02 / 0.1 | +1 -1 |  |
| 22 | 2026-10-23 21:12 | ran 53m47s | distill-v9.1 | train | klscst1 | K7a, K7b: KL-SCST from A7, beta 0.02 / 0.1 | +15 -3 | `distill-v9.1` |
| 23 | 2026-10-23 22:48 | ran 1h18m | distill-v9.1 | train | klscst2 | A7s1 -> K7s1, P0_clipcap | +1 -1 |  |

## `notebooks/08_distill_v10.ipynb` - Kaggle "IE643 Distill v10 (K0, K0s1, K7a, K7s1, F2 reward)" (`ie643-distill-v10-k0-k0s1-k7a-k7s1-f2-reward`)

| Kaggle v | Run end (UTC) | Status | Stamp | Mode | Run set | Runs | Lines changed | Tag |
|---|---|---|---|---|---|---|---|---|
| 1 | 2026-10-24 11:24 | ran 6m41s | distill-v10 | smoke | v10a |  | first |  |
| 2 | 2026-10-24 12:52 | ran 1h24m | distill-v10 | train | v10a |  | +1 -1 | `distill-v10a` |
| 3 | 2026-10-24 14:27 | ran 52m50s | distill-v10 | train | v10b |  | +1 -1 | `distill-v10b` |

## `notebooks/09_evaluation.ipynb` - Kaggle "IE643 Evaluation" (`ie643-evaluation`)

| Kaggle v | Run end (UTC) | Status | Stamp | Mode | Run set | Runs | Lines changed | Tag |
|---|---|---|---|---|---|---|---|---|
| 2 | 2026-10-24 15:16 | ran 40m50s | eval-final-v2 |  |  |  | first | `eval-final-v2` |
| 3 | 2026-10-24 15:17* | failed after 0s | eval-final-v4.1 |  |  |  | +300 -8 | `eval-final-v4.1` |
| 4 | 2026-10-24 15:18* | failed after 0s | eval-final-v4.1 |  |  |  | +0 -0 |  |
| 5 | 2026-10-24 21:50 | ran 54m1s | eval-final-v4.1 |  |  |  | +0 -0 |  |

## `notebooks/10_probe_flags.ipynb` - Kaggle "IE643 Probe flags for app" (`ie643-probe-flags-for-app`)

| Kaggle v | Run end (UTC) | Status | Stamp | Mode | Run set | Runs | Lines changed | Tag |
|---|---|---|---|---|---|---|---|---|
| 1 | 2026-10-24 12:32 | ran 1h20m | probe-v1 |  |  |  | first | `probe-v1` |
| 2 | 2026-10-24 18:27 | ran 1h24m | probe-v2 |  |  |  | +2 -2 | `probe-v2` |

## `notebooks/11_deployment.ipynb` - Kaggle "IE643 Deployment" (`ie643-deployment`)

| Kaggle v | Run end (UTC) | Status | Stamp | Mode | Run set | Runs | Lines changed | Tag |
|---|---|---|---|---|---|---|---|---|
| 1 | 2026-10-24 23:41 | ran 1h28m | export-v1.1 |  |  |  | first | `export-v1.1` |

## Which Kaggle version produced which runs (from the project notes, checked against Kaggle on 2026-10-25)

| Notebook | Kaggle version | Runs |
|---|---|---|
| `07_distillation` | 13 | ablations through A10s1 |
| `07_distillation` | 15-16 | v7 sequence-level KD (v16 holds S1, S1s1, S2, S2s1) |
| `07_distillation` | 18 | v8 SCST (R0, R1) |
| `07_distillation` | 19 | v8.1 (R0r, R1r) |
| `07_distillation` | 22 | klscst1 (K7a, K7b) |
| `07_distillation` | 23 | klscst2 (A7s1, K7s1, P0) |
| `08_distill_v10` | 2 / 3 | v10a (K0, K0s1, A7s2, K7s2) / v10b (K10a_fb2, K10s1_fb2) |
| `09_evaluation` | 5 | eval-final-v4.1 (all 35 students, 38 systems) |
| `10_probe_flags` | 2 | probe-v2 on the deployed student (K10s1_fb2) |


# ml/: custom notification model (Phase 2)

Data tooling and training code for a custom, multilingual, app-aware notification classifier. It is tracked in issue #24 and is **not** part of the Android build.

## Ground rules

- **Purely synthetic data.** No real notifications, contacts, phone numbers, accounts or phone data are ever used. `ml/data/privacy_guard.py` (added in stage S1) fails the build on real-looking personal data.
- **Nothing trains on unreviewed data.** Training and evaluation refuse to run unless `testdata/golden_v2/SIGNOFF.json` matches the data on disk (stages S4 and S4b).
- **Local GPU first, Colab as the fallback.** The same scripts run in both places.
- Keep published files free of personal names, emails and hardware model names (say "a GPU").

## Stages

S0 kickoff and toolchain spike, S1 rubrics and app catalogue, S2 authoring, S3 relabel and QA, S4 your sample review, S4b coverage gate, S5 freeze, S6 train, S7 evaluate, S8 export to TFLite, S9 selection report. The full plan and checklist are in issue #24.

## Layout

```
ml/data/      app catalogue, schema, validators, leak and privacy checks, augmentation, review packs
ml/train/     tokenizer, Track A (fine-tune), Track B (from scratch), training CLI
ml/export/    TFLite export, quantization, parity checks, tokenizer fixtures
ml/eval/      baselines, evaluation, reports
ml/notebooks/ Colab fallback notebook
testdata/golden_v2/  the dataset (JSONL, source of truth), dataset card, sign-off record
```

Checkpoints, TFLite candidates, review packs and the derived SQLite copy are git-ignored (`ml/.gitignore`).

## Local setup (planned, finalized in S0)

```
py -3.12 -m venv ml/.venv
ml\.venv\Scripts\activate
pip install -r ml/requirements.txt
pytest ml/
```

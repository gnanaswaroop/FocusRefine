# How the Track B model was trained

This folder holds everything that turns synthetic notifications into the on-device model the app ships. It is **not** part of the Android build. The app only uses the exported files in `app/src/main/assets/`.

The work ran in stages. Each stage has one job and writes files the next stage reads. Each round of data went through the same stages, and a round was only kept if it improved the held-out scores.

## The pipeline at a glance

```
 1. Rules and catalogue ──► 2. Write items ──► 3. Second opinion ──► 4. Review and coverage ──► 5. Next round
                                                                                                   │
 8. Export for the app ◄── 7. Evaluate ◄── 6. Train ◄──────────────────────── (repeat until good)◄──┘
```

Stage 1 sets the rules. Stages 2 to 5 build the data, and rounds repeat them. Stages 6 to 8 train, check and export the model.

## Ground rules

- **Synthetic data only.** Every notification is invented. Names are fictional, phone numbers and links use reserved ranges, and no real notification was used. `ml/data/privacy_guard.py` rejects anything that looks like personal data.
- **Labels come from the rules, not the author.** An author picks a *use case*. The mode labels follow from `ml/data/rubric.json`, so a label cannot drift from the rules.
- **A held-out set that never moves.** Training and scoring use different items, and the held-out items are fixed. Adding data never changes which items are held out.
- **No tuning on the held-out set.** Thresholds and choices are made on training data only.

## Stage 1: Rules and app catalogue

| File | What it is |
| :--- | :--- |
| `ml/data/rubric.json` | The 5 modes (Commute, Deep Work / Focus, Do Not Disturb, Sleeping / Rest, Workout / Gym) and 67 use cases. Each use case says which modes it is urgent in. |
| `ml/data/apps.tsv` | About 255 Android apps (package, name, category, home regions), checked against public store listings by `verify_apps.py`. |
| `ml/data/schema.py` | The item format, and the check that a label matches its use case. |
| `ml/data/privacy_guard.py`, `ml/data/leakcheck.py` | Privacy checks, and checks that the same text or template does not appear in two splits. |

## Stage 2: Write the items

- An author picks a use case, one of its apps and a sender type, then writes the notification in that language.
- For scale, Antigravity (`agy`) writes batches from the same slots (`ml/data/agy_scale.py`, `ml/data/safety_round.py`).
- `ml/data/assemble.py` fills in the id, app name, category and label, and rejects anything that breaks a rule.
- Items come in three difficulties: `easy` (a clear example), `hard_negative` (looks urgent but is routine, such as a storm sale), and `near_miss` (close to an urgent case but missing the key cue).

## Stage 3: A second opinion

- `ml/data/relabel.py prepare` gives a second labeller the text without the author's label.
- `relabel.py compare` measures agreement. Items that agree are kept. Items that disagree are **contested**.
- Contested items go to a decision: `agy_adjudicate.py` or the project owner. Where a decision reveals a rule, the rule is written into `rubric.json`.

## Stage 4: Review and coverage

- `ml/data/review_packs.py` builds offline HTML pages per language, with English glosses for reading.
- `ml/data/coverage.py` reports what each language is missing, such as safety cases, calls, or counter-examples for apps that only ever carry one label. The report is in `testdata/golden_v2/COVERAGE_REPORT.md`.
- `ml/data/plan_round2.py` and `plan_round3.py` turn the gaps into exact quotas for the next round.

## Stage 5: Rounds of data

Each round adds items, then goes through stages 2 to 4 again.

| Round | What it added | Result |
| :--- | :--- | :--- |
| Pilot | First items for all 18 languages | 1,079 agreed items |
| Round 2 | Gaps from the coverage report | 1,949 agreed items |
| Round 3 | Coverage gaps, with quotas | 712 agreed items (86 rejected by the validator) |
| Round 4 | Antigravity-written dev and test items, per language | Added to the agreed sets |
| Round 5 | Safety notices (weather, alarms, health, school, emergencies), 6 languages, plus routine look-alikes | 960 items for training, and a 312-item **safety holdout** that is never trained on |
| Round 6 | More safety items, training split only | 1,918 items added to training |
| Round 7 | General items for the weakest languages | **Rejected**: it scored lower, so it is not in the model |

## Stage 6: Train

The trainer is `ml/train/train_b.py`.

- **Holdout:** 20% of the template families from the first four rounds are held out, 1,600 items in total. The holdout is chosen once, from those original rounds, so adding rounds never changes it.
- **Model:** a small transformer. 4 layers, 192 dimensions, 4 attention heads, inputs up to 96 tokens.
- **Input text:** `category | app name | title | text`. The app context comes from the package name.
- **Tokenizer:** SentencePiece unigram, 8,000 pieces, trained on the training text.
- **Output:** five scores, one per mode. A mode shows the notification when its score is 0 or more.
- **Training settings:** AdamW, learning rate 3e-4, batch 64, 12 epochs, seed 1. The loss weights rare positives more.
- **Outputs:** `ml/artifacts/track_b/metrics.json` and `run.json`, with the data hashes, so each run can be checked.

Run it with `python -m ml.train.train_b` from the repo root, using the training environment (`ml/requirements.txt`, plus PyTorch and SentencePiece).

## Stage 7: Evaluate

| Run | Golden holdout, macro F1 | Safety holdout, macro F1 | Decision |
| :--- | :--- | :--- | :--- |
| Round 4 | 0.716 | not measured | baseline |
| Round 5 | 0.739 | 0.869 | kept |
| Round 6 | **0.748** | **0.939** | **shipped** |
| Round 7 | 0.743 | 0.931 | rejected |

A round is kept only if it improves the holdout without making the safety results worse. The training log also reports the 44 safety notices from the device test, shown per mode.

Results and reasoning are in `docs/track_b_decisions.md`. The comparison with the earlier engine is in `docs/track_b_vs_old_engine.md`.

## Stage 8: Export for the app

The export runs in four steps. Steps 2 and 3 use different Python environments, because the TensorFlow conversion does not run in the training environment.

1. `python -m ml.export.dump_track_b`: writes the weights and PyTorch reference outputs.
2. `python ml/export/export_tflite.py` (TensorFlow environment): rebuilds the network in TensorFlow, checks it against PyTorch, and writes `track_b.tflite`. For round 6 the largest difference was 9.7e-6 in logits, and all 1,600 held-out decisions matched.
3. `python -m ml.export.export_app_assets`: writes the vocabulary, app catalogue, output head and the centring vector.
4. `python -m ml.export.make_device_fixture`: writes the held-out items for the on-device test.

The files are copied into `app/src/main/assets/` as `track_b.tflite`, `track_b_vocab.json`, `track_b_apps.json`, `track_b_head.json` and `track_b_centre.json`. The device tests (`TrackBEngineTest`, `TrackBTokenizerTest`) check the copy on the phone.

Details of each export step are in `ml/export/README.md`.

## Data files

| Folder | What it holds |
| :--- | :--- |
| `testdata/golden_v2/pilot_agreed/`, `r2_agreed/`, `round3/agreed/`, `round4/agreed/` | Agreed items used for training and the holdout |
| `testdata/golden_v2/round5/`, `round6/` | Safety items. `agreed/` is for training; `safety_holdout/` is held out |
| `testdata/golden_v2/*/assembled/`, `*/rejected/`, `contested_*.jsonl` | Items before agreement, rejected items, and contested items (reported, not scored) |
| `testdata/golden_v2/gloss/` | English glosses of the non-English items, for review |
| `testdata/safety/safety_items.json` | The 44 notices measured on the device |

## Running the checks

```bash
python -m unittest discover -s ml/tests -t .       # unit tests for the data tools
python -m ml.data.validate testdata/golden_v2/pilot_agreed   # schema, privacy and leak checks
python -m ml.data.stats testdata/golden_v2/pilot_agreed      # counts per split, language and label
```

## Tools that were used and are not in this folder

- Antigravity (`agy`) wrote and checked many of the items. The scripts that call it are in `ml/data/`, but it must be installed separately.
- Track A (a fine-tuned model) was planned in an earlier version of this plan and was not built. Track B is the model in the app.

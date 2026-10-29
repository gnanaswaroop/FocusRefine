# golden_v2: synthetic multilingual, app-grounded notification dataset

Status: planned (issue #24). Files are added stage by stage; this card describes what they will contain.

## What it is

Labeled notification examples for training and validating a custom classifier. Each item says which of the five built-in modes (Commute, Deep Work / Focus, Do Not Disturb (DND), Sleeping / Rest, Workout / Gym) it should be **Shown** in, and is Muted in all others.

## Purely synthetic

All message text is invented. Names are fictional, phone numbers use reserved fictional ranges, email addresses and links use reserved example domains, and card or account numbers are random and fail real checksums. Real application ids (Android package names) appear only as public identifiers of where a notification claims to come from. No real notifications, contacts or phone data were used.

## Item fields

`id, split, lang, region, package, app_name, app_category, usecase, sender_type, title, text, urgentIn, difficulty, template_family, provenance, augmentations`

## Splits

training (authored base plus deterministic augmentation), dev (thresholds and selection), frozen test (never trained on, includes held-out apps and templates), challenge sets, contested items (labelers disagreed, reported but not scored), and the legacy English golden and holdout sets for regression.

## Storage

JSON Lines is the source of truth (one item per line, one file per split and language). A SQLite copy is built locally from it for analysis and is not committed.

## Sign-off

`SIGNOFF.json` records the SHA-256 hashes of the reviewed data and the accepted known gaps. Training and evaluation refuse to run if the data on disk does not match.

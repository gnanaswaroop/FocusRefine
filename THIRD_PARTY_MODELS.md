# Third-party models

## Track B (`app/src/main/assets/track_b.tflite`)

- **Used for:** the on-device urgency scoring in `EmbeddingClassifier` (LiteRT 2.2.0 interpreter).
- **Origin:** trained by this project on synthetic, app-grounded notifications (issue #24). No third-party model weights, data or tokenizer are used: the vocabulary (`track_b_vocab.json`) is trained on the same synthetic data.
- **License:** the project's own license.

The Universal Sentence Encoder (MediaPipe `TextEmbedder`) that this file used to describe was removed: its redistribution terms were never confirmed.

# Third-party models

## Universal Sentence Encoder (`app/src/main/assets/text_embedder.tflite`)

- **Used for:** the on-device urgency scoring in `EmbeddingClassifier` (MediaPipe `TextEmbedder`).
- **Size:** 6,120,274 bytes (float32).
- **Source:** `https://storage.googleapis.com/mediapipe-models/text_embedder/universal_sentence_encoder/float32/latest/universal_sentence_encoder.tflite`, listed on the MediaPipe Text Embedder page (`https://developers.google.com/edge/mediapipe/solutions/text/text_embedder`).
- **License: not confirmed.** That page and the download location state no model-specific license or terms. The page's CC BY 4.0 and Apache 2.0 notices cover the page text and code samples, not the model file. Resolve this before a public release: confirm the redistribution terms with the publisher, or swap in a model with a clear license (for example by replacing this file and re-tuning `embedding_anchors.json`).

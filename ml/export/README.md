# Track B export and on-device check

Diagnostic pipeline for the Track B model. Nothing here is wired into the app: the production classifier is unchanged.
All inputs and outputs are git-ignored under `ml/artifacts/`.

1. Train (training venv, `ml/.venv`): `python -m ml.train.train_b`
2. Dump weights and PyTorch reference outputs: `python -m ml.export.dump_track_b`
3. Convert to TFLite (TensorFlow venv, not the training venv; `litert-torch` has no Windows build):
   `python ml/export/export_tflite.py` checks the rebuilt network against PyTorch, then writes `model.tflite`.
4. Write the held-out device fixture: `python -m ml.export.make_device_fixture`
5. Push both files to the emulator or phone:
   ```
   adb shell mkdir -p /data/local/tmp/focusrefine/trackb
   adb push ml/artifacts/track_b/export/model.tflite ml/artifacts/track_b/device/fixture.json /data/local/tmp/focusrefine/trackb/
   ```
6. Run the on-device check: `adb shell am instrument -w -e class com.focusrefine.app.TrackBDeviceTest com.focusrefine.app.test/androidx.test.runner.AndroidJUnitRunner`
   Scores are logged to tag `TRACKB_BENCH`. Without the pushed files the test is skipped.

Result on the held-out items (1,600): host macro F1 0.716, emulator 0.716, largest logit difference 8.6e-6.

## Baseline: MediaPipe Gesture Recognizer vs pose_mlp (same HaGRID photos)

Photos: 680, hand found by our landmarker: 95.0%. Images where no hand is found count as OTHER for both. Photos are of test-split people only. Many HaGRID photos show two hands; both systems look for one hand and may pick the hand without the gesture, so gesture recall here is a lower bound.

### MediaPipe Gesture Recognizer

- samples: 680
- accuracy: **74.71%**
- accuracy on gesture classes (without OTHER): **48.75%**
- background false positives per frame (OTHER predicted as a gesture): **11.14%**

| class | recall | precision |
|---|---|---|
| OTHER | 88.9% | 76.5% |
| OPEN_PALM | 70.0% | 85.7% |
| FIST | 80.0% | 100.0% |
| POINTING | 40.0% | 50.0% |
| TWO_FINGERS | 15.0% | 85.7% |
| THUMB_UP | 75.0% | 36.6% |
| THUMB_DOWN | 80.0% | 94.1% |

Confusion matrix (rows = true, columns = predicted):

| | OTHER | OPEN_PALM | FIST | POINTING | TWO_FINGERS | THUMB_UP | THUMB_DOWN |
|---|---|---|---|---|---|---|---|
| **OTHER** | 391 | 7 | 0 | 15 | 2 | 24 | 1 |
| **OPEN_PALM** | 18 | 42 | 0 | 0 | 0 | 0 | 0 |
| **FIST** | 4 | 0 | 16 | 0 | 0 | 0 | 0 |
| **POINTING** | 24 | 0 | 0 | 16 | 0 | 0 | 0 |
| **TWO_FINGERS** | 65 | 0 | 0 | 1 | 12 | 2 | 0 |
| **THUMB_UP** | 5 | 0 | 0 | 0 | 0 | 15 | 0 |
| **THUMB_DOWN** | 4 | 0 | 0 | 0 | 0 | 0 | 16 |

### pose_mlp (quantized TFLite, thresholds)

- samples: 680
- accuracy: **92.65%**
- accuracy on gesture classes (without OTHER): **81.67%**
- background false positives per frame (OTHER predicted as a gesture): **1.36%**

| class | recall | precision |
|---|---|---|
| OTHER | 98.6% | 90.8% |
| OPEN_PALM | 96.7% | 96.7% |
| FIST | 80.0% | 94.1% |
| POINTING | 75.0% | 100.0% |
| TWO_FINGERS | 77.5% | 98.4% |
| THUMB_UP | 70.0% | 87.5% |
| THUMB_DOWN | 80.0% | 100.0% |

Confusion matrix (rows = true, columns = predicted):

| | OTHER | OPEN_PALM | FIST | POINTING | TWO_FINGERS | THUMB_UP | THUMB_DOWN |
|---|---|---|---|---|---|---|---|
| **OTHER** | 434 | 2 | 1 | 0 | 1 | 2 | 0 |
| **OPEN_PALM** | 2 | 58 | 0 | 0 | 0 | 0 | 0 |
| **FIST** | 4 | 0 | 16 | 0 | 0 | 0 | 0 |
| **POINTING** | 10 | 0 | 0 | 30 | 0 | 0 | 0 |
| **TWO_FINGERS** | 18 | 0 | 0 | 0 | 62 | 0 | 0 |
| **THUMB_UP** | 6 | 0 | 0 | 0 | 0 | 14 | 0 |
| **THUMB_DOWN** | 4 | 0 | 0 | 0 | 0 | 0 | 16 |

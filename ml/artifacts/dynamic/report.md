## Dynamic model (IPN Hand, test = official validation subjects)

### argmax

- samples: 7485
- accuracy: **82.06%**
- accuracy on gesture classes (without OTHER): **53.87%**
- background false positives per frame (OTHER predicted as a gesture): **12.63%**

| class | recall | precision |
|---|---|---|
| NO_GESTURE | 87.4% | 95.8% |
| SWIPE_LEFT | 50.6% | 37.0% |
| SWIPE_RIGHT | 29.3% | 26.3% |
| SWIPE_UP | 47.6% | 42.0% |
| SWIPE_DOWN | 64.0% | 42.3% |
| FIST_OPEN | 78.2% | 34.7% |

Confusion matrix (rows = true, columns = predicted):

| | NO_GESTURE | SWIPE_LEFT | SWIPE_RIGHT | SWIPE_UP | SWIPE_DOWN | FIST_OPEN |
|---|---|---|---|---|---|---|
| **NO_GESTURE** | 5502 | 158 | 125 | 113 | 151 | 248 |
| **SWIPE_LEFT** | 80 | 129 | 24 | 10 | 3 | 9 |
| **SWIPE_RIGHT** | 67 | 21 | 65 | 20 | 11 | 38 |
| **SWIPE_UP** | 34 | 25 | 25 | 119 | 23 | 24 |
| **SWIPE_DOWN** | 45 | 16 | 6 | 6 | 151 | 12 |
| **FIST_OPEN** | 14 | 0 | 2 | 15 | 18 | 176 |

### confidence ≥ 0.8

- samples: 7485
- accuracy: **82.06%**
- accuracy on gesture classes (without OTHER): **53.87%**
- background false positives per frame (OTHER predicted as a gesture, confidence ≥ 0.80): **7.21%**

| class | recall | precision |
|---|---|---|
| NO_GESTURE | 87.4% | 95.8% |
| SWIPE_LEFT | 50.6% | 37.0% |
| SWIPE_RIGHT | 29.3% | 26.3% |
| SWIPE_UP | 47.6% | 42.0% |
| SWIPE_DOWN | 64.0% | 42.3% |
| FIST_OPEN | 78.2% | 34.7% |

Confusion matrix (rows = true, columns = predicted):

| | NO_GESTURE | SWIPE_LEFT | SWIPE_RIGHT | SWIPE_UP | SWIPE_DOWN | FIST_OPEN |
|---|---|---|---|---|---|---|
| **NO_GESTURE** | 5502 | 158 | 125 | 113 | 151 | 248 |
| **SWIPE_LEFT** | 80 | 129 | 24 | 10 | 3 | 9 |
| **SWIPE_RIGHT** | 67 | 21 | 65 | 20 | 11 | 38 |
| **SWIPE_UP** | 34 | 25 | 25 | 119 | 23 | 24 |
| **SWIPE_DOWN** | 45 | 16 | 6 | 6 | 151 | 12 |
| **FIST_OPEN** | 14 | 0 | 2 | 15 | 18 | 176 |

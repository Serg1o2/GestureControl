## Dynamic model (IPN Hand, test = official validation subjects)

### argmax

- samples: 29299
- accuracy: **85.55%**
- accuracy on gesture classes (without OTHER): **71.81%**
- background false positives per frame (OTHER predicted as a gesture): **10.75%**

| class | recall | precision |
|---|---|---|
| NO_GESTURE | 89.2% | 97.1% |
| SWIPE_LEFT | 67.2% | 50.2% |
| SWIPE_RIGHT | 68.4% | 51.4% |
| SWIPE_UP | 72.9% | 54.1% |
| SWIPE_DOWN | 73.7% | 69.2% |
| FIST_OPEN | 76.3% | 53.9% |

Confusion matrix (rows = true, columns = predicted):

| | NO_GESTURE | SWIPE_LEFT | SWIPE_RIGHT | SWIPE_UP | SWIPE_DOWN | FIST_OPEN |
|---|---|---|---|---|---|---|
| **NO_GESTURE** | 20605 | 615 | 525 | 421 | 400 | 522 |
| **SWIPE_LEFT** | 151 | 842 | 144 | 114 | 0 | 2 |
| **SWIPE_RIGHT** | 137 | 138 | 790 | 23 | 3 | 64 |
| **SWIPE_UP** | 62 | 53 | 6 | 890 | 0 | 210 |
| **SWIPE_DOWN** | 179 | 2 | 8 | 61 | 917 | 77 |
| **FIST_OPEN** | 86 | 26 | 63 | 136 | 6 | 1021 |

### confidence ≥ 0.8

- samples: 29299
- accuracy: **85.55%**
- accuracy on gesture classes (without OTHER): **71.81%**
- background false positives per frame (OTHER predicted as a gesture, confidence ≥ 0.80): **4.53%**

| class | recall | precision |
|---|---|---|
| NO_GESTURE | 89.2% | 97.1% |
| SWIPE_LEFT | 67.2% | 50.2% |
| SWIPE_RIGHT | 68.4% | 51.4% |
| SWIPE_UP | 72.9% | 54.1% |
| SWIPE_DOWN | 73.7% | 69.2% |
| FIST_OPEN | 76.3% | 53.9% |

Confusion matrix (rows = true, columns = predicted):

| | NO_GESTURE | SWIPE_LEFT | SWIPE_RIGHT | SWIPE_UP | SWIPE_DOWN | FIST_OPEN |
|---|---|---|---|---|---|---|
| **NO_GESTURE** | 20605 | 615 | 525 | 421 | 400 | 522 |
| **SWIPE_LEFT** | 151 | 842 | 144 | 114 | 0 | 2 |
| **SWIPE_RIGHT** | 137 | 138 | 790 | 23 | 3 | 64 |
| **SWIPE_UP** | 62 | 53 | 6 | 890 | 0 | 210 |
| **SWIPE_DOWN** | 179 | 2 | 8 | 61 | 917 | 77 |
| **FIST_OPEN** | 86 | 26 | 63 | 136 | 6 | 1021 |

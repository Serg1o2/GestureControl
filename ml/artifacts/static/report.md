## Static pose model (Keras, test split by person)

### all test, argmax

- samples: 234378
- accuracy: **98.73%**
- accuracy on gesture classes (without OTHER): **99.08%**
- background false positives per frame (OTHER predicted as a gesture): **1.40%**

| class | recall | precision |
|---|---|---|
| OTHER | 98.6% | 99.7% |
| OPEN_PALM | 99.8% | 96.1% |
| FIST | 99.4% | 96.9% |
| POINTING | 97.3% | 89.2% |
| TWO_FINGERS | 99.2% | 99.3% |
| THUMB_UP | 99.3% | 96.2% |
| THUMB_DOWN | 99.1% | 94.9% |

Confusion matrix (rows = true, columns = predicted):

| | OTHER | OPEN_PALM | FIST | POINTING | TWO_FINGERS | THUMB_UP | THUMB_DOWN |
|---|---|---|---|---|---|---|---|
| **OTHER** | 167837 | 593 | 132 | 1062 | 154 | 183 | 254 |
| **OPEN_PALM** | 25 | 14946 | 0 | 0 | 0 | 0 | 1 |
| **FIST** | 19 | 2 | 4873 | 6 | 0 | 1 | 2 |
| **POINTING** | 217 | 1 | 17 | 9303 | 13 | 7 | 7 |
| **TWO_FINGERS** | 136 | 10 | 5 | 41 | 24613 | 0 | 1 |
| **THUMB_UP** | 25 | 0 | 1 | 8 | 1 | 4929 | 0 |
| **THUMB_DOWN** | 29 | 0 | 0 | 13 | 0 | 2 | 4909 |

### all test, with per-class thresholds [0.0, 0.89, 0.5, 0.92, 0.5, 0.5, 0.5]

- samples: 234378
- accuracy: **99.08%**
- accuracy on gesture classes (without OTHER): **98.50%**
- background false positives per frame (OTHER predicted as a gesture): **0.71%**

| class | recall | precision |
|---|---|---|
| OTHER | 99.3% | 99.5% |
| OPEN_PALM | 99.4% | 98.2% |
| FIST | 99.3% | 97.1% |
| POINTING | 94.1% | 96.9% |
| TWO_FINGERS | 99.2% | 99.3% |
| THUMB_UP | 99.3% | 96.5% |
| THUMB_DOWN | 99.1% | 95.3% |

Confusion matrix (rows = true, columns = predicted):

| | OTHER | OPEN_PALM | FIST | POINTING | TWO_FINGERS | THUMB_UP | THUMB_DOWN |
|---|---|---|---|---|---|---|---|
| **OTHER** | 169014 | 263 | 124 | 256 | 152 | 172 | 234 |
| **OPEN_PALM** | 89 | 14882 | 0 | 0 | 0 | 0 | 1 |
| **FIST** | 31 | 0 | 4868 | 2 | 0 | 1 | 1 |
| **POINTING** | 523 | 1 | 16 | 9001 | 12 | 5 | 7 |
| **TWO_FINGERS** | 159 | 3 | 4 | 27 | 24612 | 0 | 1 |
| **THUMB_UP** | 32 | 0 | 1 | 1 | 1 | 4929 | 0 |
| **THUMB_DOWN** | 39 | 0 | 0 | 3 | 0 | 2 | 4909 |

### HaGRID annotations, argmax

- samples: 233315
- accuracy: **98.74%**
- accuracy on gesture classes (without OTHER): **99.09%**
- background false positives per frame (OTHER predicted as a gesture): **1.39%**

| class | recall | precision |
|---|---|---|
| OTHER | 98.6% | 99.7% |
| OPEN_PALM | 99.8% | 96.1% |
| FIST | 99.4% | 96.9% |
| POINTING | 97.3% | 89.2% |
| TWO_FINGERS | 99.2% | 99.3% |
| THUMB_UP | 99.3% | 96.3% |
| THUMB_DOWN | 99.1% | 94.9% |

Confusion matrix (rows = true, columns = predicted):

| | OTHER | OPEN_PALM | FIST | POINTING | TWO_FINGERS | THUMB_UP | THUMB_DOWN |
|---|---|---|---|---|---|---|---|
| **OTHER** | 167076 | 586 | 132 | 1052 | 152 | 180 | 252 |
| **OPEN_PALM** | 25 | 14885 | 0 | 0 | 0 | 0 | 1 |
| **FIST** | 19 | 1 | 4848 | 6 | 0 | 1 | 2 |
| **POINTING** | 215 | 1 | 17 | 9266 | 13 | 7 | 7 |
| **TWO_FINGERS** | 135 | 10 | 5 | 41 | 24507 | 0 | 1 |
| **THUMB_UP** | 25 | 0 | 1 | 7 | 1 | 4906 | 0 |
| **THUMB_DOWN** | 28 | 0 | 0 | 13 | 0 | 2 | 4889 |

### HaGRID annotations, with per-class thresholds [0.0, 0.89, 0.5, 0.92, 0.5, 0.5, 0.5]

- samples: 233315
- accuracy: **99.08%**
- accuracy on gesture classes (without OTHER): **98.51%**
- background false positives per frame (OTHER predicted as a gesture): **0.70%**

| class | recall | precision |
|---|---|---|
| OTHER | 99.3% | 99.5% |
| OPEN_PALM | 99.4% | 98.3% |
| FIST | 99.3% | 97.1% |
| POINTING | 94.1% | 96.9% |
| TWO_FINGERS | 99.2% | 99.3% |
| THUMB_UP | 99.3% | 96.5% |
| THUMB_DOWN | 99.1% | 95.3% |

Confusion matrix (rows = true, columns = predicted):

| | OTHER | OPEN_PALM | FIST | POINTING | TWO_FINGERS | THUMB_UP | THUMB_DOWN |
|---|---|---|---|---|---|---|---|
| **OTHER** | 168240 | 259 | 124 | 255 | 150 | 169 | 233 |
| **OPEN_PALM** | 89 | 14821 | 0 | 0 | 0 | 0 | 1 |
| **FIST** | 30 | 0 | 4843 | 2 | 0 | 1 | 1 |
| **POINTING** | 520 | 1 | 16 | 8965 | 12 | 5 | 7 |
| **TWO_FINGERS** | 158 | 3 | 4 | 27 | 24506 | 0 | 1 |
| **THUMB_UP** | 31 | 0 | 1 | 1 | 1 | 4906 | 0 |
| **THUMB_DOWN** | 38 | 0 | 0 | 3 | 0 | 2 | 4889 |

### HaGRID re-extracted (our MediaPipe), argmax

- samples: 1063
- accuracy: **97.18%**
- accuracy on gesture classes (without OTHER): **97.84%**
- background false positives per frame (OTHER predicted as a gesture): **3.06%**

| class | recall | precision |
|---|---|---|
| OTHER | 96.9% | 99.5% |
| OPEN_PALM | 100.0% | 88.4% |
| FIST | 96.2% | 100.0% |
| POINTING | 94.9% | 77.1% |
| TWO_FINGERS | 99.1% | 98.1% |
| THUMB_UP | 95.8% | 88.5% |
| THUMB_DOWN | 95.2% | 90.9% |

Confusion matrix (rows = true, columns = predicted):

| | OTHER | OPEN_PALM | FIST | POINTING | TWO_FINGERS | THUMB_UP | THUMB_DOWN |
|---|---|---|---|---|---|---|---|
| **OTHER** | 761 | 7 | 0 | 10 | 2 | 3 | 2 |
| **OPEN_PALM** | 0 | 61 | 0 | 0 | 0 | 0 | 0 |
| **FIST** | 0 | 1 | 25 | 0 | 0 | 0 | 0 |
| **POINTING** | 2 | 0 | 0 | 37 | 0 | 0 | 0 |
| **TWO_FINGERS** | 1 | 0 | 0 | 0 | 106 | 0 | 0 |
| **THUMB_UP** | 0 | 0 | 0 | 1 | 0 | 23 | 0 |
| **THUMB_DOWN** | 1 | 0 | 0 | 0 | 0 | 0 | 20 |

### HaGRID re-extracted (our MediaPipe), with per-class thresholds [0.0, 0.89, 0.5, 0.92, 0.5, 0.5, 0.5]

- samples: 1063
- accuracy: **98.31%**
- accuracy on gesture classes (without OTHER): **97.48%**
- background false positives per frame (OTHER predicted as a gesture): **1.40%**

| class | recall | precision |
|---|---|---|
| OTHER | 98.6% | 99.1% |
| OPEN_PALM | 100.0% | 93.8% |
| FIST | 96.2% | 100.0% |
| POINTING | 92.3% | 97.3% |
| TWO_FINGERS | 99.1% | 98.1% |
| THUMB_UP | 95.8% | 88.5% |
| THUMB_DOWN | 95.2% | 95.2% |

Confusion matrix (rows = true, columns = predicted):

| | OTHER | OPEN_PALM | FIST | POINTING | TWO_FINGERS | THUMB_UP | THUMB_DOWN |
|---|---|---|---|---|---|---|---|
| **OTHER** | 774 | 4 | 0 | 1 | 2 | 3 | 1 |
| **OPEN_PALM** | 0 | 61 | 0 | 0 | 0 | 0 | 0 |
| **FIST** | 1 | 0 | 25 | 0 | 0 | 0 | 0 |
| **POINTING** | 3 | 0 | 0 | 36 | 0 | 0 | 0 |
| **TWO_FINGERS** | 1 | 0 | 0 | 0 | 106 | 0 | 0 |
| **THUMB_UP** | 1 | 0 | 0 | 0 | 0 | 23 | 0 |
| **THUMB_DOWN** | 1 | 0 | 0 | 0 | 0 | 0 | 20 |

### Keras vs TFLite parity (2000 test samples)

- float TFLite max |Δp|: 4.17e-07
- quantized TFLite max |Δp|: 0.0607, argmax agreement 100.00%
- model size: float 58988 B, quantized 20592 B

### quantized TFLite, all test, argmax

- samples: 234378
- accuracy: **98.72%**
- accuracy on gesture classes (without OTHER): **99.09%**
- background false positives per frame (OTHER predicted as a gesture): **1.42%**

| class | recall | precision |
|---|---|---|
| OTHER | 98.6% | 99.7% |
| OPEN_PALM | 99.8% | 96.1% |
| FIST | 99.4% | 96.8% |
| POINTING | 97.3% | 88.9% |
| TWO_FINGERS | 99.2% | 99.3% |
| THUMB_UP | 99.3% | 96.3% |
| THUMB_DOWN | 99.1% | 94.9% |

Confusion matrix (rows = true, columns = predicted):

| | OTHER | OPEN_PALM | FIST | POINTING | TWO_FINGERS | THUMB_UP | THUMB_DOWN |
|---|---|---|---|---|---|---|---|
| **OTHER** | 167794 | 598 | 136 | 1097 | 156 | 181 | 253 |
| **OPEN_PALM** | 25 | 14946 | 0 | 0 | 0 | 0 | 1 |
| **FIST** | 19 | 2 | 4873 | 6 | 0 | 1 | 2 |
| **POINTING** | 214 | 1 | 17 | 9305 | 14 | 7 | 7 |
| **TWO_FINGERS** | 134 | 10 | 5 | 41 | 24615 | 0 | 1 |
| **THUMB_UP** | 24 | 0 | 1 | 9 | 1 | 4929 | 0 |
| **THUMB_DOWN** | 29 | 0 | 0 | 13 | 0 | 2 | 4909 |

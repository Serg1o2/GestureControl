## Static pose model (Keras, test split by person)

### all test, argmax

- samples: 248344
- accuracy: **98.47%**
- accuracy on gesture classes (without OTHER): **98.66%**
- background false positives per frame (OTHER predicted as a gesture): **1.62%**

| class | recall | precision |
|---|---|---|
| OTHER | 98.4% | 99.7% |
| OPEN_PALM | 99.8% | 95.9% |
| FIST | 98.6% | 96.4% |
| POINTING | 97.3% | 89.6% |
| TWO_FINGERS | 98.6% | 98.7% |
| THUMB_UP | 99.3% | 96.8% |
| THUMB_DOWN | 99.0% | 96.2% |

Confusion matrix (rows = true, columns = predicted):

| | OTHER | OPEN_PALM | FIST | POINTING | TWO_FINGERS | THUMB_UP | THUMB_DOWN |
|---|---|---|---|---|---|---|---|
| **OTHER** | 171478 | 639 | 175 | 1383 | 282 | 156 | 186 |
| **OPEN_PALM** | 26 | 15569 | 0 | 2 | 5 | 0 | 0 |
| **FIST** | 19 | 2 | 5639 | 56 | 1 | 0 | 0 |
| **POINTING** | 279 | 3 | 29 | 14179 | 66 | 7 | 6 |
| **TWO_FINGERS** | 199 | 25 | 8 | 171 | 27826 | 0 | 3 |
| **THUMB_UP** | 26 | 0 | 0 | 9 | 1 | 4931 | 0 |
| **THUMB_DOWN** | 29 | 1 | 0 | 17 | 0 | 1 | 4910 |

### all test, with per-class thresholds [0.0, 0.87, 0.5, 0.92, 0.5, 0.5, 0.5]

- samples: 248344
- accuracy: **98.88%**
- accuracy on gesture classes (without OTHER): **97.86%**
- background false positives per frame (OTHER predicted as a gesture): **0.68%**

| class | recall | precision |
|---|---|---|
| OTHER | 99.3% | 99.2% |
| OPEN_PALM | 99.5% | 98.1% |
| FIST | 98.5% | 97.2% |
| POINTING | 93.8% | 97.7% |
| TWO_FINGERS | 98.5% | 98.9% |
| THUMB_UP | 99.3% | 97.0% |
| THUMB_DOWN | 99.0% | 97.0% |

Confusion matrix (rows = true, columns = predicted):

| | OTHER | OPEN_PALM | FIST | POINTING | TWO_FINGERS | THUMB_UP | THUMB_DOWN |
|---|---|---|---|---|---|---|---|
| **OTHER** | 173106 | 288 | 131 | 238 | 244 | 148 | 144 |
| **OPEN_PALM** | 75 | 15523 | 0 | 0 | 4 | 0 | 0 |
| **FIST** | 73 | 2 | 5632 | 9 | 1 | 0 | 0 |
| **POINTING** | 805 | 3 | 26 | 13666 | 59 | 6 | 4 |
| **TWO_FINGERS** | 334 | 13 | 6 | 72 | 27805 | 0 | 2 |
| **THUMB_UP** | 33 | 0 | 0 | 2 | 1 | 4931 | 0 |
| **THUMB_DOWN** | 47 | 0 | 0 | 4 | 0 | 1 | 4906 |

### HaGRID annotations, argmax

- samples: 233315
- accuracy: **98.68%**
- accuracy on gesture classes (without OTHER): **99.08%**
- background false positives per frame (OTHER predicted as a gesture): **1.47%**

| class | recall | precision |
|---|---|---|
| OTHER | 98.5% | 99.7% |
| OPEN_PALM | 99.8% | 96.2% |
| FIST | 99.3% | 96.8% |
| POINTING | 97.2% | 87.7% |
| TWO_FINGERS | 99.3% | 99.0% |
| THUMB_UP | 99.3% | 96.8% |
| THUMB_DOWN | 99.1% | 96.2% |

Confusion matrix (rows = true, columns = predicted):

| | OTHER | OPEN_PALM | FIST | POINTING | TWO_FINGERS | THUMB_UP | THUMB_DOWN |
|---|---|---|---|---|---|---|---|
| **OTHER** | 166943 | 580 | 133 | 1203 | 233 | 153 | 185 |
| **OPEN_PALM** | 20 | 14888 | 0 | 1 | 2 | 0 | 0 |
| **FIST** | 13 | 0 | 4843 | 21 | 0 | 0 | 0 |
| **POINTING** | 218 | 1 | 19 | 9261 | 15 | 7 | 5 |
| **TWO_FINGERS** | 121 | 8 | 6 | 49 | 24514 | 0 | 1 |
| **THUMB_UP** | 24 | 0 | 0 | 9 | 1 | 4906 | 0 |
| **THUMB_DOWN** | 29 | 1 | 0 | 15 | 0 | 1 | 4886 |

### HaGRID annotations, with per-class thresholds [0.0, 0.87, 0.5, 0.92, 0.5, 0.5, 0.5]

- samples: 233315
- accuracy: **99.14%**
- accuracy on gesture classes (without OTHER): **98.53%**
- background false positives per frame (OTHER predicted as a gesture): **0.64%**

| class | recall | precision |
|---|---|---|
| OTHER | 99.4% | 99.5% |
| OPEN_PALM | 99.7% | 98.2% |
| FIST | 99.2% | 97.4% |
| POINTING | 93.8% | 97.2% |
| TWO_FINGERS | 99.2% | 99.1% |
| THUMB_UP | 99.3% | 97.0% |
| THUMB_DOWN | 99.0% | 97.1% |

Confusion matrix (rows = true, columns = predicted):

| | OTHER | OPEN_PALM | FIST | POINTING | TWO_FINGERS | THUMB_UP | THUMB_DOWN |
|---|---|---|---|---|---|---|---|
| **OTHER** | 168354 | 270 | 103 | 218 | 197 | 145 | 143 |
| **OPEN_PALM** | 41 | 14868 | 0 | 0 | 2 | 0 | 0 |
| **FIST** | 34 | 0 | 4840 | 3 | 0 | 0 | 0 |
| **POINTING** | 542 | 1 | 19 | 8940 | 14 | 6 | 4 |
| **TWO_FINGERS** | 153 | 2 | 5 | 28 | 24510 | 0 | 1 |
| **THUMB_UP** | 31 | 0 | 0 | 2 | 1 | 4906 | 0 |
| **THUMB_DOWN** | 44 | 0 | 0 | 4 | 0 | 1 | 4883 |

### HaGRID re-extracted (our MediaPipe), argmax

- samples: 1063
- accuracy: **97.37%**
- accuracy on gesture classes (without OTHER): **97.84%**
- background false positives per frame (OTHER predicted as a gesture): **2.80%**

| class | recall | precision |
|---|---|---|
| OTHER | 97.2% | 99.5% |
| OPEN_PALM | 100.0% | 89.7% |
| FIST | 96.2% | 100.0% |
| POINTING | 94.9% | 74.0% |
| TWO_FINGERS | 99.1% | 98.1% |
| THUMB_UP | 95.8% | 92.0% |
| THUMB_DOWN | 95.2% | 100.0% |

Confusion matrix (rows = true, columns = predicted):

| | OTHER | OPEN_PALM | FIST | POINTING | TWO_FINGERS | THUMB_UP | THUMB_DOWN |
|---|---|---|---|---|---|---|---|
| **OTHER** | 763 | 7 | 0 | 11 | 2 | 2 | 0 |
| **OPEN_PALM** | 0 | 61 | 0 | 0 | 0 | 0 | 0 |
| **FIST** | 0 | 0 | 25 | 1 | 0 | 0 | 0 |
| **POINTING** | 2 | 0 | 0 | 37 | 0 | 0 | 0 |
| **TWO_FINGERS** | 1 | 0 | 0 | 0 | 106 | 0 | 0 |
| **THUMB_UP** | 1 | 0 | 0 | 0 | 0 | 23 | 0 |
| **THUMB_DOWN** | 0 | 0 | 0 | 1 | 0 | 0 | 20 |

### HaGRID re-extracted (our MediaPipe), with per-class thresholds [0.0, 0.87, 0.5, 0.92, 0.5, 0.5, 0.5]

- samples: 1063
- accuracy: **98.68%**
- accuracy on gesture classes (without OTHER): **97.48%**
- background false positives per frame (OTHER predicted as a gesture): **0.89%**

| class | recall | precision |
|---|---|---|
| OTHER | 99.1% | 99.1% |
| OPEN_PALM | 100.0% | 95.3% |
| FIST | 96.2% | 100.0% |
| POINTING | 92.3% | 100.0% |
| TWO_FINGERS | 99.1% | 98.1% |
| THUMB_UP | 95.8% | 92.0% |
| THUMB_DOWN | 95.2% | 100.0% |

Confusion matrix (rows = true, columns = predicted):

| | OTHER | OPEN_PALM | FIST | POINTING | TWO_FINGERS | THUMB_UP | THUMB_DOWN |
|---|---|---|---|---|---|---|---|
| **OTHER** | 778 | 3 | 0 | 0 | 2 | 2 | 0 |
| **OPEN_PALM** | 0 | 61 | 0 | 0 | 0 | 0 | 0 |
| **FIST** | 1 | 0 | 25 | 0 | 0 | 0 | 0 |
| **POINTING** | 3 | 0 | 0 | 36 | 0 | 0 | 0 |
| **TWO_FINGERS** | 1 | 0 | 0 | 0 | 106 | 0 | 0 |
| **THUMB_UP** | 1 | 0 | 0 | 0 | 0 | 23 | 0 |
| **THUMB_DOWN** | 1 | 0 | 0 | 0 | 0 | 0 | 20 |

### IPN low light (teacher labels from clean frames), argmax

- samples: 13966
- accuracy: **94.92%**
- accuracy on gesture classes (without OTHER): **95.97%**
- background false positives per frame (OTHER predicted as a gesture): **7.64%**

| class | recall | precision |
|---|---|---|
| OTHER | 92.4% | 96.2% |
| OPEN_PALM | 98.4% | 89.5% |
| FIST | 94.7% | 93.5% |
| POINTING | 97.5% | 93.7% |
| TWO_FINGERS | 93.6% | 96.9% |
| THUMB_UP | 66.7% | 66.7% |
| THUMB_DOWN | 80.0% | 50.0% |

Confusion matrix (rows = true, columns = predicted):

| | OTHER | OPEN_PALM | FIST | POINTING | TWO_FINGERS | THUMB_UP | THUMB_DOWN |
|---|---|---|---|---|---|---|---|
| **OTHER** | 3772 | 52 | 42 | 169 | 47 | 1 | 1 |
| **OPEN_PALM** | 6 | 620 | 0 | 1 | 3 | 0 | 0 |
| **FIST** | 6 | 2 | 771 | 34 | 1 | 0 | 0 |
| **POINTING** | 59 | 2 | 10 | 4881 | 51 | 0 | 1 |
| **TWO_FINGERS** | 77 | 17 | 2 | 122 | 3206 | 0 | 2 |
| **THUMB_UP** | 1 | 0 | 0 | 0 | 0 | 2 | 0 |
| **THUMB_DOWN** | 0 | 0 | 0 | 1 | 0 | 0 | 4 |

### IPN low light (teacher labels from clean frames), with per-class thresholds [0.0, 0.87, 0.5, 0.92, 0.5, 0.5, 0.5]

- samples: 13966
- accuracy: **94.65%**
- accuracy on gesture classes (without OTHER): **93.55%**
- background false positives per frame (OTHER predicted as a gesture): **2.69%**

| class | recall | precision |
|---|---|---|
| OTHER | 97.3% | 88.5% |
| OPEN_PALM | 94.3% | 95.2% |
| FIST | 94.2% | 95.5% |
| POINTING | 93.7% | 98.5% |
| TWO_FINGERS | 93.1% | 97.2% |
| THUMB_UP | 66.7% | 66.7% |
| THUMB_DOWN | 60.0% | 60.0% |

Confusion matrix (rows = true, columns = predicted):

| | OTHER | OPEN_PALM | FIST | POINTING | TWO_FINGERS | THUMB_UP | THUMB_DOWN |
|---|---|---|---|---|---|---|---|
| **OTHER** | 3974 | 15 | 28 | 20 | 45 | 1 | 1 |
| **OPEN_PALM** | 34 | 594 | 0 | 0 | 2 | 0 | 0 |
| **FIST** | 38 | 2 | 767 | 6 | 1 | 0 | 0 |
| **POINTING** | 260 | 2 | 7 | 4690 | 45 | 0 | 0 |
| **TWO_FINGERS** | 180 | 11 | 1 | 44 | 3189 | 0 | 1 |
| **THUMB_UP** | 1 | 0 | 0 | 0 | 0 | 2 | 0 |
| **THUMB_DOWN** | 2 | 0 | 0 | 0 | 0 | 0 | 3 |

### Keras vs TFLite parity (2000 test samples)

- float TFLite max |Δp|: 4.47e-07
- quantized TFLite max |Δp|: 0.0527, argmax agreement 100.00%
- model size: float 58988 B, quantized 20592 B

### quantized TFLite, all test, argmax

- samples: 248344
- accuracy: **98.46%**
- accuracy on gesture classes (without OTHER): **98.66%**
- background false positives per frame (OTHER predicted as a gesture): **1.63%**

| class | recall | precision |
|---|---|---|
| OTHER | 98.4% | 99.7% |
| OPEN_PALM | 99.8% | 95.8% |
| FIST | 98.6% | 96.4% |
| POINTING | 97.3% | 89.6% |
| TWO_FINGERS | 98.5% | 98.7% |
| THUMB_UP | 99.3% | 96.8% |
| THUMB_DOWN | 99.0% | 96.2% |

Confusion matrix (rows = true, columns = predicted):

| | OTHER | OPEN_PALM | FIST | POINTING | TWO_FINGERS | THUMB_UP | THUMB_DOWN |
|---|---|---|---|---|---|---|---|
| **OTHER** | 171462 | 647 | 172 | 1396 | 282 | 155 | 185 |
| **OPEN_PALM** | 25 | 15569 | 0 | 3 | 5 | 0 | 0 |
| **FIST** | 19 | 2 | 5638 | 57 | 1 | 0 | 0 |
| **POINTING** | 277 | 3 | 29 | 14182 | 65 | 7 | 6 |
| **TWO_FINGERS** | 201 | 27 | 8 | 172 | 27821 | 0 | 3 |
| **THUMB_UP** | 26 | 0 | 0 | 9 | 1 | 4931 | 0 |
| **THUMB_DOWN** | 29 | 1 | 0 | 17 | 0 | 1 | 4910 |

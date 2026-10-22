# Точность моделей

_Сгенерировано `python -m gc_ml.accuracy_doc` 22.10.2026 14:17. Модель позы: `pose_mlp` версии `20261022-1415`, нормализация `norm-v1`._

## Итог по целям этапа

| Цель | Значение | Статус |
|---|---|---|
| Static poses, accuracy ≥ 95%: HaGRID test (annotation landmarks) | 99.14% | ✅ |
| Background false positives ≤ 2% per frame: HaGRID test (annotation landmarks) | 0.64% | ✅ |
| Static poses, accuracy ≥ 95%: HaGRID test (our MediaPipe, true aspect) | 98.68% | ✅ |
| Background false positives ≤ 2% per frame: HaGRID test (our MediaPipe, true aspect) | 0.89% | ✅ |
| My phone recordings ≥ 90% after fine-tuning | no recordings yet | ⏳ |
| Inference ≤ 5 ms on the phone | model 20592 B; measured by connectedDebugAndroidTest | ⏳ |

Все метрики — на тестовых данных людей, которых не было в обучении (HaGRID: разбиение по `user_id`; IPN: официальный список val).
Ложные срабатывания считаются **на кадр**; в приложении поверх модели работают пороги и голосование по 5 кадрам, а жест требует ещё и правильной динамики (удержание, траектория), поэтому ложные срабатывания жестов ниже.

## Данные

- HaGRIDv2, точки из разметки: 1372287 рук (train/val/test [983991, 154981, 233315]), соотношение сторон принято 0.75.
- HaGRIDv2, выборка фото через наш MediaPipe 0.10.20: 6314 рук; рука не найдена в 11.4% размеченных случаев.
- IPN Hand, окна по 24 кадра: 102678 (по классам [79980, 4437, 4378, 4306, 4330, 5247]); доля кадров без руки внутри жестов: 19.5%.

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


_Динамическая модель обучена на всех 200 видео IPN. В приложение **не входит**: ложных срабатываний на фоне ~11% кадров — больше, чем у правил с защитами от фантомов (docs/phantoms.md). Свайпы, взмахи пальцем и разжатие кулака распознаются правилами поверх позы от `pose_mlp`._

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


Проверка зеркальности IPN (`python -m gc_ml.ipn check-mirror`): после отражения кадра «throw left» (G05) смещает ладонь в среднем на −0,36 размера руки (влево от пользователя), «throw right» (G06) — на +0,63. Направления согласованы с координатами приложения.

## До и после дообучения на моих записях

_Записей с телефона пока нет._ Порядок: логгер обучения (экран отладки → метка) → `adb pull` в `ml/data/raw/mine/` → `python -m gc_ml.mine features` → `python run_all.py --force train-static`. В отчёте модели появится блок «my recordings»; сюда переносятся значения до (текущая модель на этих записях) и после дообучения.

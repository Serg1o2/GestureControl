# Точность моделей

_Сгенерировано `python -m gc_ml.accuracy_doc` 22.10.2026 01:01. Модель позы: `pose_mlp` версии `20261022-0057`, нормализация `norm-v1`._

## Итог по целям этапа

| Цель | Значение | Статус |
|---|---|---|
| Static poses, accuracy ≥ 95%: HaGRID test (annotation landmarks) | 99.08% | ✅ |
| Background false positives ≤ 2% per frame: HaGRID test (annotation landmarks) | 0.70% | ✅ |
| Static poses, accuracy ≥ 95%: HaGRID test (our MediaPipe, true aspect) | 98.31% | ✅ |
| Background false positives ≤ 2% per frame: HaGRID test (our MediaPipe, true aspect) | 1.40% | ✅ |
| My phone recordings ≥ 90% after fine-tuning | no recordings yet | ⏳ |
| Inference ≤ 5 ms on the phone | model 20592 B; measured by connectedDebugAndroidTest | ⏳ |

Все метрики — на тестовых данных людей, которых не было в обучении (HaGRID: разбиение по `user_id`; IPN: официальный список val).
Ложные срабатывания считаются **на кадр**; в приложении поверх модели работают пороги и голосование по 5 кадрам, а жест требует ещё и правильной динамики (удержание, траектория), поэтому ложные срабатывания жестов ниже.

## Данные

- HaGRIDv2, точки из разметки: 1372287 рук (train/val/test [983991, 154981, 233315]), соотношение сторон принято 0.75.
- HaGRIDv2, выборка фото через наш MediaPipe 0.10.20: 6314 рук; рука не найдена в 11.4% размеченных случаев.
- IPN Hand: ещё не обработан (видео загружаются).

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
- accuracy: **92.35%**
- accuracy on gesture classes (without OTHER): **81.67%**
- background false positives per frame (OTHER predicted as a gesture): **1.82%**

| class | recall | precision |
|---|---|---|
| OTHER | 98.2% | 90.8% |
| OPEN_PALM | 96.7% | 95.1% |
| FIST | 80.0% | 94.1% |
| POINTING | 75.0% | 96.8% |
| TWO_FINGERS | 77.5% | 98.4% |
| THUMB_UP | 70.0% | 87.5% |
| THUMB_DOWN | 80.0% | 100.0% |

Confusion matrix (rows = true, columns = predicted):

| | OTHER | OPEN_PALM | FIST | POINTING | TWO_FINGERS | THUMB_UP | THUMB_DOWN |
|---|---|---|---|---|---|---|---|
| **OTHER** | 432 | 3 | 1 | 1 | 1 | 2 | 0 |
| **OPEN_PALM** | 2 | 58 | 0 | 0 | 0 | 0 | 0 |
| **FIST** | 4 | 0 | 16 | 0 | 0 | 0 | 0 |
| **POINTING** | 10 | 0 | 0 | 30 | 0 | 0 | 0 |
| **TWO_FINGERS** | 18 | 0 | 0 | 0 | 62 | 0 | 0 |
| **THUMB_UP** | 6 | 0 | 0 | 0 | 0 | 14 | 0 |
| **THUMB_DOWN** | 4 | 0 | 0 | 0 | 0 | 0 | 16 |


_not available: ml\artifacts\dynamic\report.md was not produced in this run_


## До и после дообучения на моих записях

_Записей с телефона пока нет._ Порядок: логгер обучения (экран отладки → метка) → `adb pull` в `ml/data/raw/mine/` → `python -m gc_ml.mine features` → `python run_all.py --force train-static`. В отчёте модели появится блок «my recordings»; сюда переносятся значения до (текущая модель на этих записях) и после дообучения.

# GestureControl ML pipeline

Python-пайплайн для обучения моделей распознавания жестов. В APK не входит: в приложение попадают только `app/src/main/assets/models/pose_mlp.{tflite,json}`.

## Окружение

Python 3.11 (на машине разработчика — `C:\Users\vladk\dev\python311`). Два виртуальных окружения, потому что MediaPipe и TensorFlow требуют разных версий protobuf:

```bash
cd ml
/c/Users/vladk/dev/python311/python.exe -m venv .venv-extract
/c/Users/vladk/dev/python311/python.exe -m venv .venv-train
.venv-extract/Scripts/python.exe -m pip install -r requirements-extract.txt
.venv-train/Scripts/python.exe -m pip install -r requirements-train.txt
```

- `.venv-extract`: MediaPipe **0.10.20** — та же версия, что `com.google.mediapipe:tasks-vision` в приложении; модель `hand_landmarker.task` берётся из `app/src/main/assets`, настройки как в `HandLandmarkerEngine` (1 рука, пороги 0.5, режим VIDEO для видео).
- `.venv-train`: TensorFlow 2.19 (CPU). Модели крошечные, GPU не нужен. Для GPU: WSL2 + `tensorflow[and-cuda]` или Colab, команды те же.

## Одна команда

```bash
.venv-extract/Scripts/python.exe run_all.py --pilot   # подмножество: проверка пайплайна, ~20–40 мин
.venv-extract/Scripts/python.exe run_all.py           # полный прогон; готовые шаги пропускаются
.venv-extract/Scripts/python.exe run_all.py --force train-static   # переобучить один шаг
.venv-extract/Scripts/python.exe run_all.py --skip-dynamic         # без IPN
```

После прогона модель копируется в assets приложения; затем `./gradlew assembleDebug`.

## Шаги

| Шаг | Команда | Выход |
|---|---|---|
| Разметка HaGRIDv2 с точками (719 МБ) | `python -m gc_ml.download hagrid` | `data/raw/hagrid/annotations.zip`, хэш в `hashes.json` |
| Статические признаки из разметки | `python -m gc_ml.hagrid annotations --aspect 0.75` | `data/features/static_hagrid_ann.npz` |
| Выборка фото 512px → наш MediaPipe | `python -m gc_ml.hagrid reextract --per-class 150` | `data/features/hagrid_mp_chunks/` (продолжается после прерывания) |
| Признаки из выборки | `python -m gc_ml.hagrid reextract-features` | `data/features/static_hagrid_mp.npz` + доля потерь |
| Обучение позы (MLP) | `python -m gc_ml.train_static --features ...` | `artifacts/static/pose_mlp.tflite`, `.json`, `report.md` |
| IPN: загрузка / распаковка | `python -m gc_ml.ipn download`, `unpack` | `data/raw/ipn/` |
| IPN: точки из видео | `python -m gc_ml.ipn extract --workers 8` | `data/features/ipn_landmarks/*.npz` (продолжается) |
| IPN: окна | `python -m gc_ml.ipn windows` | `data/features/dynamic_ipn.npz` |
| Динамическая модель (1D-CNN) | `python -m gc_ml.train_dynamic` | `artifacts/dynamic/` |
| Векторы совпадения Kotlin ↔ Python | `python -m gc_ml.export_vectors --what norm|model` | `gestures/src/test/resources/ml/`, `app/src/androidTest/assets/` |

## Правила

- **Воспроизводимость:** seed 42 везде, версии в `requirements-*.txt`, хэши сырых файлов в `hashes.json`.
- **Разбиение по людям:** HaGRID — по папкам train/val/test (`user_id`), IPN — официальные списки (val-список = тест, 15% обучающих видео = валидация).
- **Нормализация `norm-v1`** (`gc_ml/normalize.py`) продублирована в Kotlin (`LandmarkNormalizer`). Любое изменение → новая версия + новые векторы; приложение отказывается грузить модель с другой версией нормализации.
- **Данные не коммитятся** (`ml/.gitignore`).

## Оценка времени и места (i5-12400, 32 ГБ, канал ~0,3–1 МБ/с)

| Этап | Время | Диск |
|---|---|---|
| Разметка HaGRID | 20–40 мин загрузка, ~5 мин признаки | 0,7 ГБ + 0,2 ГБ |
| Выборка фото (150/класс ≈ 5 тыс. фото) | ~30–60 мин (сеть) | ~0,6 ГБ трафика, на диск только точки |
| Обучение позы (25 эпох, ~1 млн примеров, CPU) | 15–30 мин | < 50 МБ |
| IPN: загрузка 4,6 ГБ | 1,5–4 ч (сеть) | 4,6 ГБ + 4,6 ГБ распаковка |
| IPN: точки (800 тыс. кадров, 8 процессов) | ~1 ч | ~0,2 ГБ |
| Динамическая модель (30 эпох, CPU) | 10–20 мин | < 10 МБ |

## Дообучение на своих записях

1. В приложении включить «Логирование для обучения» и записать жесты с метками.
2. `adb pull /sdcard/Android/data/com.gesturecontrol/files/training/ data/raw/mine/`
3. `python -m gc_ml.mine features` → `data/features/static_mine.npz` (split по сессиям).
4. `python run_all.py --force train-static` — свои примеры добавляются к обучению, в отчёте появляется блок «my recordings».

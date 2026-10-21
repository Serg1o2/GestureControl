# Датасеты для этапа 3

_Проверено 21.10.2026. Приложение личное и некоммерческое; лицензии записаны для информации. Условия доступа (регистрация, формы) выполняет владелец проекта вручную; скрипты ждут файлы в `ml/data/raw/<dataset>/`._

Статус доступности: ✅ ссылка проверена (HTTP 200 / страница открылась), ⚠️ процедура описана по документации, но не проверена, ❌ не удалось проверить.

## Сводка

| Датасет | Тип | Нужен для | Объём | Получение | Приоритет |
|---|---|---|---|---|---|
| HaGRIDv2 — разметка с точками | статичные, точки MediaPipe (2D) | быстрый пилот, доп. обучение статики | 719 МБ | прямая ссылка ✅ | **1** |
| HaGRIDv2 512px | статичные, фото | основная статика (точки нашей версией MediaPipe) | 128 ГБ целиком; поднабор по range-запросам ~15–30 ГБ | прямая ссылка ✅ | **1** |
| IPN Hand | динамические, видео | свайпы руки, «разжатие» (Open twice), фон, указание | 4,6 ГБ (mp4) | Google Drive, без регистрации ✅ | **1** |
| Jester (Qualcomm) | динамические, видео | свайпы, слайды двумя пальцами, разжатие, «нет жеста» | 22,8 ГБ | Qualcomm OneID, вручную ⚠️ | **2** |
| MediaPipe Gesture Recognizer | готовая модель | базовая линия для сравнения | ~8 МБ | прямая ссылка ✅ | **1** |
| FreiHAND | фото рук, 3D-поза | негативы «рука в кадре без жеста» | ~4 ГБ (не проверено) | сайт недоступен при проверке ❌ | 3 |
| EgoGesture | динамические, эгоцентрическая камера | — (ракурс «от головы» не совпадает с фронтальной камерой) | ~ (не проверено) | форма у авторов (NLPR CASIA) ❌ | не берём |
| NVGesture | динамические, салон автомобиля, RGB-D | — (1 532 примера, другой ракурс) | ~30 ГБ | соглашение NVIDIA ⚠️ | не берём |
| SHREC'17 / DHG-14/28 | скелет 22 точки (RealSense) | — (другая топология точек, не MediaPipe) | ~ | сайт Telecom Lille ⚠️ | не берём |
| InterHand2.6M | 3D-поза, 2 руки | — (нет меток жестов, сотни ГБ) | >100 ГБ | — | не берём |
| ChaLearn IsoGD/ConGD | 249 классов, RGB-D, в основном жестовый язык | — (не наши жесты) | десятки ГБ | регистрация ⚠️ | не берём |

## HaGRIDv2

- **Источник:** https://github.com/hukenovs/hagrid (SberDevices). Статья: arXiv 2412.01508.
- **Содержимое:** 1 086 158 FullHD-фото, 65 977 человек, 33 жеста + `no_gesture`; разбиение по `user_id`: 821 458 / 99 200 / 165 500.
- **Классы:** call, peace, grabbing, dislike, peace_inverted, grip, fist, rock, hand_heart, four, stop, hand_heart2, like, stop_inverted, holy, mute, three, little_finger, ok, three2, middle_finger, one, two_up, point, palm, two_up_inverted, take_picture, three3, three_gun, thumb_index, thumb_index2, timeout, xsign, no_gesture.
- **Разметка:** JSON по классам; `bboxes`, `labels`, `user_id`, `hand_landmarks` (точки MediaPipe, **только x, y**), `meta`.
- **Ссылки (проверены):**
  - разметка с точками: `https://rndml-team-cv.obs.ru-moscow-1.hc.sbercloud.ru/datasets/hagrid_v2/annotations_with_landmarks/annotations.zip` — 719 041 314 байт;
  - 512px: `https://rndml-team-cv.obs.ru-moscow-1.hc.sbercloud.ru/datasets/hagrid_v2/hagridv2_512.zip` — 128 252 037 404 байт.
- **Как получаем:** скрипт качает разметку целиком. Изображения 512px читаются **выборочно** HTTP range-запросами из ZIP (центральный каталог в конце архива), поэтому весь архив на 128 ГБ не нужен; при желании можно скачать целиком.
- **Лицензия:** вариант CC BY-SA 4.0 (`license/en_us.pdf` в репозитории).
- **Замечание:** готовые точки HaGRID сделаны другой версией MediaPipe и без z — для финальной модели прогоняем фото через нашу версию (0.10.20).

## IPN Hand

- **Источник:** https://gibranbenitez.github.io/IPN_Hand/ , код https://github.com/GibranBenitez/IPN-hand. Статья: arXiv 2005.02134.
- **Содержимое:** 200 видео, 50 человек, 640×480, 30 fps, ~800 тыс. кадров; 4 218 жестов + 1 431 «не-жест».
- **Классы:** D0X не-жест; B0A указание одним пальцем; B0B указание двумя пальцами; G01 клик одним пальцем; G02 клик двумя; G03 бросок вверх; G04 бросок вниз; G05 бросок влево; G06 бросок вправо; G07 раскрыть дважды; G08 двойной клик одним пальцем; G09 двойной клик двумя; G10 zoom in; G11 zoom out.
- **Файлы (Google Drive, публичная папка):** видео mp4 4,6 ГБ (5 × .tgz); кадры 320×240 8,37 ГБ; оптический поток 11,4 ГБ; маски 982 МБ; аннотации 364 КБ (6 .txt + 1 .csv). Берём **mp4 + аннотации**.
- **Как получить:** открыть страницу проекта → «Download Link» → скачать `videos` (5 файлов) и `annotations` в `ml/data/raw/ipn/`. Скрипт может скачать публичные файлы через `gdown`, если Google не ограничит квоту; иначе — вручную.
- **Лицензия:** данные CC BY 4.0, код MIT.

## Jester

- **Источник:** https://www.qualcomm.com/developer/software/jester-dataset (ранее 20BN). Статья: ICCVW 2019.
- **Содержимое:** ~148 тыс. клипов, 1 376 человек, кадры JPG **высотой 100 px**, 12 fps; 27 классов, разбиение 8:1:1.
- **Классы:** Doing other things, Drumming Fingers, No gesture, Pulling Hand In, Pulling Two Fingers In, Pushing Hand Away, Pushing Two Fingers Away, Rolling Hand Backward, Rolling Hand Forward, Shaking Hand, Sliding Two Fingers Down/Left/Right/Up, Stop Sign, Swiping Down/Left/Right/Up, Thumb Down, Thumb Up, Turning Hand Clockwise/Counterclockwise, Zooming In/Out With Full Hand, Zooming In/Out With Two Fingers.
- **Как получить (вручную):**
  1. Зайти на страницу датасета, нажать **Register**, создать Qualcomm OneID, войти.
  2. На странице Downloads скачать **все** части архива `20bn-jester-v1-??`, файлы с метками (`jester-v1-labels.csv`, `-train.csv`, `-validation.csv`, `-test.csv`) и PDF с инструкцией.
  3. Положить всё в `ml/data/raw/jester/`. Скрипт сам склеит части и распакует (аналог `cat 20bn-jester-v1-?? | tar zx`).
- **Риск:** кадры 100 px в высоту — MediaPipe может часто не находить руку. Сначала пилот на 2 000 клипах с замером доли потерь; если потери > 40%, Jester используем только для классов, где рука крупная, или не используем.
- **Лицензия:** для исследований (условия Qualcomm).

## MediaPipe Gesture Recognizer (базовая линия)

- **Модель:** `https://storage.googleapis.com/mediapipe-models/gesture_recognizer/gesture_recognizer/float16/latest/gesture_recognizer.task`.
- **Классы:** None, Closed_Fist, Open_Palm, Pointing_Up, Thumb_Down, Thumb_Up, Victory, ILoveYou.
- **Использование:** прогон на тестовом разбиении HaGRID и на моих записях; сравнение с нашей MLP на общих классах.

## FreiHAND (опционально)

- **Источник:** https://lmb.informatik.uni-freiburg.de/projects/freihand/ — при проверке сайт с данными не отвечал.
- **Зачем:** ~130 тыс. фото рук в произвольных позах, часто с предметами — негативы «рука есть, жеста нет» для статики. Подключаем, только если ложные срабатывания на фоне выше 2%.

## Не используем (и почему)

- **EgoGesture** — камера на голове (вид «от первого лица»), с фронтальной камерой телефона не совпадает; доступ по форме, процедуру проверить не удалось.
- **NVGesture** — водитель в салоне, 1 532 примера, ракурс сверху-сбоку.
- **SHREC'17 / DHG-14/28** — скелет 22 точки с RealSense, не переводится в 21 точку MediaPipe без потерь.
- **InterHand2.6M, ChaLearn IsoGD** — нет нужных меток жестов или жесты из жестового языка; объём не оправдан.

## Собственные данные (главный источник для дообучения)

- Режим «логирование для обучения» в приложении: точки руки + метка жеста, без изображений, CSV в `Android/data/com.gesturecontrol/files/training/`.
- Перенос: `adb pull` в `ml/data/raw/mine/`.
- Ориентир: 10 сессий по 5 минут в разное время и при разном свете; ≥ 30 повторов на жест + ≥ 10 минут «просто держу телефон / жестикулирую без цели» для фона.

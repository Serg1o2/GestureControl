"""Assembles docs/accuracy.md from the reports of the last pipeline run."""

from __future__ import annotations

import json
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REPO = ROOT.parent
ART = ROOT / "artifacts"
FEAT = ROOT / "data" / "features"


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8") if path.exists() else f"_not available: {path.relative_to(REPO)} was not produced in this run_\n"


def stats(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8")) if path.exists() else {}


def dynamic_note() -> str:
    videos = len(list((FEAT / "ipn_landmarks").glob("*.npz")))
    if videos >= 200:
        return ""
    return (
        f"_Динамическая модель — **предварительная**: обработано {videos} из 200 видео IPN (остальные ещё загружаются). "
        "В приложение не входит: свайпы, взмахи пальцем и разжатие кулака распознаются правилами поверх позы от `pose_mlp`. "
        "После полной загрузки: `python run_all.py --force ipn-windows --force train-dynamic`._\n"
    )


def main() -> None:
    sys.stdout.reconfigure(encoding="utf-8")
    meta = stats(ART / "static" / "pose_mlp.json")
    ann = stats(FEAT / "static_hagrid_ann.json")
    mp = stats(FEAT / "static_hagrid_mp.json")
    dyn = stats(FEAT / "dynamic_ipn.json")
    summary = stats(ART / "static" / "summary.json")
    lines = [
        "# Точность моделей",
        "",
        f"_Сгенерировано `python -m gc_ml.accuracy_doc` {time.strftime('%d.%m.%Y %H:%M')}. Модель позы: `{meta.get('name', '?')}` версии `{meta.get('version', '?')}`, нормализация `{meta.get('norm', '?')}`._",
        "",
        "## Итог по целям этапа",
        "",
        "| Цель | Значение | Статус |",
        "|---|---|---|",
    ]
    for row in summary.get("goals", []):
        lines.append(f"| {row['goal']} | {row['value']} | {row['status']} |")
    lines += [
        "",
        "Все метрики — на тестовых данных людей, которых не было в обучении (HaGRID: разбиение по `user_id`; IPN: официальный список val).",
        "Ложные срабатывания считаются **на кадр**; в приложении поверх модели работают пороги и голосование по 5 кадрам, а жест требует ещё и правильной динамики (удержание, траектория), поэтому ложные срабатывания жестов ниже.",
        "",
        "## Данные",
        "",
        f"- HaGRIDv2, точки из разметки: {ann.get('kept', '?')} рук (train/val/test {ann.get('per_split', '?')}), соотношение сторон принято {ann.get('aspect', '?')}.",
        f"- HaGRIDv2, выборка фото через наш MediaPipe 0.10.20: {mp.get('hands', '?')} рук; рука не найдена в {mp.get('loss_rate', 0) * 100:.1f}% размеченных случаев.",
        (f"- IPN Hand, окна по 24 кадра: {dyn['windows']} (по классам {dyn['per_class']}); доля кадров без руки внутри жестов: {dyn['hand_missing_rate_in_segments'] * 100:.1f}%." if dyn else "- IPN Hand: ещё не обработан (видео загружаются)."),
        "",
        read(ART / "static" / "report.md"),
        "",
        read(ART / "baseline" / "report.md"),
        "",
        dynamic_note(),
        read(ART / "dynamic" / "report.md"),
        "",
        "Проверка зеркальности IPN (`python -m gc_ml.ipn check-mirror`): после отражения кадра «throw left» (G05) смещает ладонь в среднем на −0,36 размера руки (влево от пользователя), «throw right» (G06) — на +0,63. Направления согласованы с координатами приложения.",
        "",
        "## До и после дообучения на моих записях",
        "",
        "_Записей с телефона пока нет._ Порядок: логгер обучения (экран отладки → метка) → `adb pull` в `ml/data/raw/mine/` → "
        "`python -m gc_ml.mine features` → `python run_all.py --force train-static`. В отчёте модели появится блок «my recordings»; "
        "сюда переносятся значения до (текущая модель на этих записях) и после дообучения.",
        "",
    ]
    out = REPO / "docs" / "accuracy.md"
    out.write_text("\n".join(lines), encoding="utf-8")
    print(f"written {out}")


if __name__ == "__main__":
    main()

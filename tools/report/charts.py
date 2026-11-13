#!/usr/bin/env python3
"""
Графіки для звіту з JSON, які зберігає demo.py (папка report-data).

    python3 charts.py                       # report-data → report-charts
    python3 charts.py --data D --out O      # інші папки

Будується лише те, для чого є дані:
  mmr.json             → mmr_tradeoff.png, mmr_variety.png
  ndcg.json            → ndcg_methods.png, ndcg_ablation.png, ndcg_metrics.png
  echo-validation.json → echo_validation.png
  echo-history.json    → echo_history.png
  echo-scenario.json   → echo_scenario.png
  trajectory.json      → trajectory.png

Оформлення під друк на A4: білий фон, сіра рамка (щоб графік не зливався з папером),
ширина 16 см, 200 dpi; крім кольору, серії розрізняються маркерами й типом лінії —
читаються й на чорно-білому друці.
"""

import argparse
import json
from datetime import datetime
from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt  # noqa: E402
from matplotlib.ticker import MaxNLocator  # noqa: E402

# Перевірена категоріальна палітра (порядок фіксований, кольори не циклічні)
BLUE, ORANGE, AQUA, YELLOW = "#2a78d6", "#eb6834", "#1baf7a", "#eda100"
SERIES = [BLUE, ORANGE, AQUA, YELLOW]
MARKERS = ["o", "s", "^", "D"]
LINESTYLES = ["-", "--", "-.", ":"]
INK, INK_2, GRID, FRAME, MUTED = "#0b0b0b", "#52514e", "#e4e4e0", "#8A96A3", "#b5b4ae"

WIDTH_IN = 6.3      # ≈ 16 см — ширина тексту на A4 з полями
DPI = 200

plt.rcParams.update({
    "font.family": "DejaVu Sans",          # кирилиця
    "font.size": 9,
    "axes.titlesize": 11,
    "axes.titleweight": "bold",
    "axes.titlelocation": "left",
    "axes.labelcolor": INK_2,
    "axes.edgecolor": MUTED,
    "axes.linewidth": 0.8,
    "axes.grid": True,
    "axes.axisbelow": True,
    "grid.color": GRID,
    "grid.linewidth": 0.8,
    "xtick.color": INK_2,
    "ytick.color": INK_2,
    "legend.frameon": False,
    "lines.linewidth": 2,
    "lines.markersize": 6,
    "figure.facecolor": "white",
    "savefig.facecolor": "white",
})


def new_figure(height=3.4):
    fig, ax = plt.subplots(figsize=(WIDTH_IN, height))
    for side in ("top", "right"):
        ax.spines[side].set_visible(False)
    return fig, ax


def save(fig, out_dir, name, note=None):
    if note:
        fig.text(0.01, 0.01, note, fontsize=7, color=INK_2, ha="left", va="bottom")
    # сіра рамка навколо всього рисунка — як у скриншотів інтерфейсу
    fig.patch.set_edgecolor(FRAME)
    fig.patch.set_linewidth(1.2)
    fig.tight_layout(rect=(0, 0.04 if note else 0, 1, 1))
    path = out_dir / name
    fig.savefig(path, dpi=DPI, edgecolor=fig.get_edgecolor())
    plt.close(fig)
    print(f"  ✔ {path}")


def load(data_dir, name):
    path = data_dir / name
    if not path.exists():
        print(f"  – {name}: немає даних, пропускаю")
        return None
    return json.loads(path.read_text(encoding="utf-8"))


def label_end(ax, x, y, text, color, dy=0.0):
    """Підпис серії біля її останньої точки (замість пошуку кольору в легенді)."""
    ax.annotate(text, (x, y), xytext=(6, dy), textcoords="offset points",
                va="center", fontsize=8, color=INK, fontweight="bold")


# ===== MMR =====

def mmr_charts(data, out):
    rows = sorted(data["rows"], key=lambda r: -r["lambda"])
    lambdas = [r["lambda"] for r in rows]

    fig, ax = new_figure()
    series = [("Різноманітність (ILD)", "intraListDiversity"), ("Збережена релевантність", "relevanceRetained")]
    for i, (title, key) in enumerate(series):
        ys = [r[key] for r in rows]
        ax.plot(lambdas, ys, color=SERIES[i], marker=MARKERS[i], linestyle=LINESTYLES[i], label=title)
        label_end(ax, lambdas[-1], ys[-1], f"{ys[-1]:.2f}", SERIES[i])
    ax.invert_xaxis()   # зліва λ = 1 (чиста релевантність), праворуч — більше різноманіття
    ax.set_xticks(lambdas)
    ax.set_ylim(0, 1.05)
    ax.set_xlabel("λ (1 — лише релевантність)")
    ax.set_ylabel("значення метрики")
    ax.set_title("MMR: компроміс між релевантністю та різноманітністю")
    ax.legend(loc="lower left")
    save(fig, out, "mmr_tradeoff.png", f"Користувач #{data['userId']}, стрічка з 10 постів.")

    fig, ax = new_figure(3.0)
    width = 0.36
    xs = range(len(rows))
    for i, (title, key) in enumerate([("Різних тегів", "distinctTags"), ("Різних авторів", "distinctAuthors")]):
        vals = [r[key] for r in rows]
        pos = [x + (i - 0.5) * (width + 0.02) for x in xs]
        bars = ax.bar(pos, vals, width, color=SERIES[i], label=title,
                      hatch=None if i == 0 else "///", edgecolor="white", linewidth=0.8)
        ax.bar_label(bars, fontsize=7, color=INK_2, padding=1)
    ax.set_xticks(list(xs), [f"{l:g}" for l in lambdas])
    ax.yaxis.set_major_locator(MaxNLocator(integer=True))
    ax.set_xlabel("λ")
    ax.set_ylabel("кількість у стрічці")
    ax.set_title("MMR: різноманіття тем і авторів у топ-10")
    ax.legend(loc="upper left", ncols=2)
    save(fig, out, "mmr_variety.png")


# ===== NDCG =====

METHOD_NAMES = {
    "random": "Випадковий порядок",
    "popularityOnly": "Лише популярність",
    "itemCfOnly": "Лише item-CF",
    "graphOnly": "Лише граф (node2vec)",
    "semanticOnly": "Лише семантика",
    "recencyOnly": "Лише свіжість",
    "equalWeights": "Рівні ваги",
    "default": "Ваги за замовчуванням",
    "tuned": "Підібрані ваги",
    "expertDefault": "Експертні ваги",
    "candidate": "Кандидат",
}
COMPONENT_NAMES = {"popularity": "популярність", "itemCf": "item-CF", "graphSim": "граф",
                   "semantic": "семантика", "recency": "свіжість"}


def ndcg_charts(report, out):
    k = report.get("config", {}).get("k", 10)
    methods = [m for m in report["methods"] if not m["name"].startswith("default_without_")]
    methods.sort(key=lambda m: m["validation"]["ndcg"])

    # 1. Методи: NDCG на підборі та на валідації
    fig, ax = new_figure(0.42 * len(methods) + 1.2)
    ys = range(len(methods))
    h = 0.38
    for i, (title, part) in enumerate([("Підбір ваг", "tuning"), ("Валідація", "validation")]):
        vals = [m[part]["ndcg"] for m in methods]
        pos = [y + (i - 0.5) * (h + 0.02) for y in ys]
        bars = ax.barh(pos, vals, h, color=SERIES[i], label=title,
                       hatch=None if i == 1 else "///", edgecolor="white", linewidth=0.8)
        if part == "validation":
            ax.bar_label(bars, fmt="%.3f", fontsize=7, color=INK, padding=2)
    ax.set_yticks(list(ys), [METHOD_NAMES.get(m["name"], m["name"]) for m in methods])
    ax.set_xlabel(f"NDCG@{k}")
    ax.grid(axis="y", visible=False)
    ax.set_xlim(0, max(m["tuning"]["ndcg"] for m in methods) * 1.18)
    ax.set_title(f"Якість ранжування: NDCG@{k} за методами")
    ax.legend(loc="lower right")
    ds = report.get("dataset", {})
    save(fig, out, "ndcg_methods.png",
         f"Синтетичні дані: {ds.get('tuningUsers', '?')} користувачів для підбору, "
         f"{ds.get('validationUsers', '?')} для валідації.")

    # 2. Абляція: що втрачаємо без кожної складової
    by_name = {m["name"]: m for m in report["methods"]}
    if "default" in by_name:
        base = by_name["default"]["validation"]["ndcg"]
        rows = [(COMPONENT_NAMES.get(n[len("default_without_"):], n[len("default_without_"):]),
                 by_name[n]["validation"]["ndcg"] - base)
                for n in by_name if n.startswith("default_without_")]
        if rows:
            rows.sort(key=lambda r: r[1])
            fig, ax = new_figure(0.45 * len(rows) + 1.2)
            bars = ax.barh(range(len(rows)), [r[1] for r in rows],
                           color=[ORANGE if r[1] < 0 else BLUE for r in rows], height=0.55)
            ax.bar_label(bars, labels=[f"{r[1]:+.3f}" for r in rows], fontsize=7, color=INK, padding=2)
            ax.set_yticks(range(len(rows)), [f"без: {r[0]}" for r in rows])
            ax.axvline(0, color=INK_2, linewidth=0.8)
            ax.grid(axis="y", visible=False)
            lim = max(abs(r[1]) for r in rows) * 1.35 or 0.01
            ax.set_xlim(-lim, lim)
            ax.set_xlabel(f"зміна NDCG@{k} відносно повної моделі ({base:.3f})")
            ax.set_title("Внесок складових гібридної оцінки (абляція)")
            save(fig, out, "ndcg_ablation.png", "Від'ємне значення — без складової якість падає.")

    # 3. Усі метрики для ключових методів
    keys = [n for n in ("random", "popularityOnly", "default", "tuned") if n in by_name]
    metrics = [("precision", "Precision"), ("recall", "Recall"), ("hitRate", "Hit rate"),
               ("ndcg", "NDCG"), ("mrr", "MRR")]
    fig, ax = new_figure(3.2)
    width = 0.8 / len(keys)
    for i, name in enumerate(keys):
        vals = [by_name[name]["validation"][key] for key, _ in metrics]
        pos = [x + (i - (len(keys) - 1) / 2) * width for x in range(len(metrics))]
        ax.bar(pos, vals, width * 0.92, color=SERIES[i], label=METHOD_NAMES.get(name, name),
               hatch=[None, "///", "...", "xx"][i], edgecolor="white", linewidth=0.6)
    ax.set_xticks(range(len(metrics)), [f"{t}@{k}" for _, t in metrics])
    ax.set_ylim(0, 1)
    ax.set_ylabel("значення на валідації")
    ax.set_title("Метрики ранжування для ключових методів")
    ax.legend(loc="upper left", ncols=2)
    save(fig, out, "ndcg_metrics.png")


# ===== Ехо-камери =====

def echo_validation_chart(report, out):
    rows = report["byLevel"]
    levels = [r["level"] for r in rows]
    series = [("Тематична однорідність", "topic"), ("Семантична згуртованість", "semantic"),
              ("Однорідність тональності", "sentimentHomogeneity"), ("Echo-score (інтегральний)", "score")]
    fig, ax = new_figure(3.6)
    for i, (title, key) in enumerate(series):
        means = [r.get(key + "Mean") for r in rows]
        sds = [r.get(key + "Sd") or 0 for r in rows]
        pts = [(l, m, s) for l, m, s in zip(levels, means, sds) if m is not None]
        if not pts:
            continue
        xs, ms, ss = zip(*pts)
        ax.errorbar(xs, ms, yerr=ss, color=SERIES[i], marker=MARKERS[i], linestyle=LINESTYLES[i],
                    capsize=3, elinewidth=1, label=title,
                    linewidth=2.6 if key == "score" else 1.6)
    ax.set_xticks(levels)
    ax.set_ylim(0, 1.05)
    ax.set_xlabel("закладений рівень однорідності спільноти")
    ax.set_ylabel("середнє ± ст. відхилення")
    ax.set_title("Валідація метрики ехо-камер на синтетичних спільнотах")
    ax.legend(loc="upper left", fontsize=8)
    rho = report.get("spearmanVsLevel", {}).get("composite")
    cfg = report.get("config", {})
    note = (f"Spearman ρ (рівень ↔ echo-score) = {rho:.3f}; " if isinstance(rho, (int, float)) else "") \
           + f"{cfg.get('repeats', '?')} повторів на рівень, {cfg.get('usersPerCommunity', '?')} користувачів у спільноті."
    save(fig, out, "echo_validation.png", note)


def parse_time(value):
    return datetime.fromisoformat(str(value).replace("Z", "")[:19])


def echo_history_chart(history, out):
    if not history:
        return
    history = sorted(history, key=lambda s: s["createdAt"])
    xs = list(range(1, len(history) + 1))
    fig, (top, bottom) = plt.subplots(2, 1, figsize=(WIDTH_IN, 4.6), sharex=True,
                                      gridspec_kw={"height_ratios": [2.2, 1]})
    for ax in (top, bottom):
        for side in ("top", "right"):
            ax.spines[side].set_visible(False)
    for i, (title, key) in enumerate([("Зважений echo-score мережі", "weightedEchoScore"),
                                      ("Максимальний echo-score спільноти", "maxEchoScore")]):
        ys = [s[key] for s in history]
        top.plot(xs, ys, color=SERIES[i], marker=MARKERS[i], linestyle=LINESTYLES[i], label=title)
        label_end(top, xs[-1], ys[-1], f"{ys[-1]:.2f}", SERIES[i])
    top.set_ylim(0, 1.05)
    top.set_ylabel("echo-score")
    top.set_title("Історія ехо-камер: знімки мережі")
    top.legend(loc="lower left", fontsize=8)

    counts = [s["communityCount"] for s in history]
    bars = bottom.bar(xs, counts, color=AQUA, width=0.6)
    bottom.bar_label(bars, fontsize=7, color=INK_2, padding=1)
    bottom.set_ylabel("спільнот")
    bottom.yaxis.set_major_locator(MaxNLocator(nbins=3, integer=True))
    bottom.set_ylim(0, max(counts) * 1.3 + 1)
    bottom.set_xticks(xs, [f"#{s['snapshotId']}\n{parse_time(s['createdAt']):%d.%m %H:%M}" for s in history],
                      fontsize=7)
    bottom.set_xlabel("знімок")
    save(fig, out, "echo_history.png")


def echo_scenario_chart(steps, out):
    if not steps:
        return
    labels = [f"Крок {s['step']}\n{s['title']}" for s in steps]
    series = [("Група A", lambda s: (s.get("groupA") or {}).get("echoChamberScore")),
              ("Група B", lambda s: (s.get("groupB") or {}).get("echoChamberScore")),
              ("Мережа (зважено)", lambda s: (s.get("snapshot") or {}).get("weightedEchoScore"))]
    fig, ax = new_figure(3.4)
    width = 0.26
    for i, (title, get) in enumerate(series):
        vals = [get(s) or 0 for s in steps]
        pos = [x + (i - 1) * (width + 0.02) for x in range(len(steps))]
        bars = ax.bar(pos, vals, width, color=SERIES[i], label=title,
                      hatch=[None, "///", "..."][i], edgecolor="white", linewidth=0.8)
        ax.bar_label(bars, fmt="%.2f", fontsize=7, color=INK, padding=1)
    ax.set_xticks(range(len(steps)), labels, fontsize=8)
    ax.set_ylim(0, 1.1)
    ax.set_ylabel("echo-score")
    ax.set_title("Сценарій: формування ехо-камери та «міст» між групами")
    ax.legend(loc="upper left", ncols=3, fontsize=8)
    save(fig, out, "echo_scenario.png")


def trajectory_chart(data, out):
    points = sorted(data.get("points") or [], key=lambda p: p["createdAt"])
    if not points:
        print("  – trajectory.json: користувача ще немає в жодному знімку")
        return
    xs = list(range(1, len(points) + 1))
    fig, ax = new_figure(3.2)
    for i, (title, key) in enumerate([("Спільнота користувача", "echoChamberScore"),
                                      ("Мережа в цілому", "networkEchoScore")]):
        ys = [p[key] for p in points]
        ax.plot(xs, ys, color=SERIES[i], marker=MARKERS[i], linestyle=LINESTYLES[i], label=title)
        label_end(ax, xs[-1], ys[-1], f"{ys[-1]:.2f}", SERIES[i])
    ax.set_xticks(xs, [f"{parse_time(p['createdAt']):%d.%m}\nспільн. {p['community']}" for p in points], fontsize=7)
    ax.set_ylim(0, 1.05)
    ax.set_ylabel("echo-score")
    ax.set_title(f"Траєкторія користувача #{data['userId']} у ехо-камерах")
    ax.legend(loc="lower left", fontsize=8)
    save(fig, out, "trajectory.png")


def main():
    parser = argparse.ArgumentParser(description="Графіки для звіту з даних demo.py")
    parser.add_argument("--data", default="report-data")
    parser.add_argument("--out", default="report-charts")
    args = parser.parse_args()
    data_dir, out = Path(args.data), Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    print(f"Дані: {data_dir.resolve()}  →  графіки: {out.resolve()}")

    if (d := load(data_dir, "mmr.json")) is not None:
        mmr_charts(d, out)
    if (d := load(data_dir, "ndcg.json")) is not None:
        ndcg_charts(d, out)
    if (d := load(data_dir, "echo-validation.json")) is not None:
        echo_validation_chart(d, out)
    if (d := load(data_dir, "echo-history.json")) is not None:
        echo_history_chart(d, out)
    if (d := load(data_dir, "echo-scenario.json")) is not None:
        echo_scenario_chart(d, out)
    if (d := load(data_dir, "trajectory.json")) is not None:
        trajectory_chart(d, out)


if __name__ == "__main__":
    main()
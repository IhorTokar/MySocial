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
  trajectory-scenario.json → trajectory_scenario.png (учасник сценарію, demo.py --scenario)
  evolution.json       → evolution_personal.png, evolution_echo.png, evolution_lifecycle.png,
                         evolution_example.png (seed.py --epochs N)
  community-experiment.json → community_experiment.png (Louvain проти Leiden за гомофілією)
  community-quality.json    → community_quality.png (NMI, ARI, чистота на основній мережі)
  community-structure.json  → community_structure.png, community_matrix.png

Оформлення під друк на A4: білий фон, сіра рамка (щоб графік не зливався з папером),
ширина 16 см, 200 dpi; крім кольору, серії розрізняються маркерами й типом лінії —
читаються й на чорно-білому друці.
"""

import argparse
import json
import textwrap
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
    # посередині графіка порожньо, а внизу легенда лягала б на лінію ILD
    ax.legend(loc="center left")
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
    # запас зверху під легенду, інакше вона накриває найвищі стовпчики
    ax.set_ylim(0, max(max(r["distinctTags"], r["distinctAuthors"]) for r in rows) * 1.3)
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
    # межа — за більшим із двох наборів, інакше найдовша смуга валідації обрізається без підпису
    ax.set_xlim(0, max(max(m["tuning"]["ndcg"], m["validation"]["ndcg"]) for m in methods) * 1.18)
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

    # дата — лише під першим знімком кожного дня, інакше підписи налізають один на одний
    # (якщо дві дати поруч — друга опускається на рядок нижче)
    labels, previous_day, last_dated, lifted = [], None, -2, False
    for i, s in enumerate(history):
        day = f"{parse_time(s['createdAt']):%d.%m}"
        if day == previous_day:
            labels.append(f"#{s['snapshotId']}")
        else:
            lifted = i - last_dated == 1 and not lifted
            labels.append(f"#{s['snapshotId']}\n" + ("\n" if lifted else "") + day)
            last_dated = i
        previous_day = day
    bottom.set_xticks(xs, labels, fontsize=7)
    bottom.set_xlabel("знімок (номер і дата)")

    # різке зростання кількості користувачів — наповнення бази, а не природна зміна мережі
    for i in range(1, len(history)):
        before, after = history[i - 1]["userCount"], history[i]["userCount"]
        if before and after >= before * 1.5:
            x = xs[i] - 0.5
            for ax in (top, bottom):
                ax.axvline(x, color=MUTED, linestyle=":", linewidth=1.2, zorder=0)
            top.annotate(f"наповнення бази\n{before} → {after} корист.", xy=(x, 0.97), xytext=(-6, 0),
                         textcoords="offset points", ha="right", va="top", fontsize=7, color=INK_2)
    save(fig, out, "echo_history.png")


def echo_scenario_chart(steps, out):
    if not steps:
        return
    # довгі назви кроків — у два рядки, інакше сусідні підписи злипаються
    labels = [f"Крок {s['step']}\n" + textwrap.fill(s["title"], 16, break_on_hyphens=False) for s in steps]
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


def trajectory_chart(data, out, name="trajectory.png"):
    """
    Траєкторія одного користувача по знімках.
    Якщо в даних є "steps" (trajectory-scenario.json) — під точками кроки сценарію,
    інакше — номер знімка, дата й номер спільноти.
    """
    points = sorted(data.get("points") or [], key=lambda p: p["createdAt"])
    if not points:
        print(f"  – {name}: користувача ще немає в жодному знімку")
        return
    steps = {s["snapshotId"]: s for s in data.get("steps") or []}
    xs = list(range(1, len(points) + 1))
    fig, ax = new_figure(3.4 if steps else 3.2)
    for i, (title, key) in enumerate([("Спільнота користувача", "echoChamberScore"),
                                      ("Мережа в цілому", "networkEchoScore")]):
        ys = [p[key] for p in points]
        ax.plot(xs, ys, color=SERIES[i], marker=MARKERS[i], linestyle=LINESTYLES[i], label=title)
        if steps and i == 0:
            # точок мало — підписуємо кожну точку спільноти: над нею, якщо вона вища за лінію мережі,
            # і під нею, якщо нижча, — щоб підпис не лягав на іншу лінію
            for p, x, y in zip(points, xs, ys):
                above = y >= p["networkEchoScore"]
                ax.annotate(f"{y:.2f}", (x, y), xytext=(0, 9 if above else -10), textcoords="offset points",
                            ha="center", va="bottom" if above else "top",
                            fontsize=8, color=INK, fontweight="bold")
        elif not steps:
            label_end(ax, xs[-1], ys[-1], f"{ys[-1]:.2f}", SERIES[i], dy=6 if i == 0 else -6)

    if steps:
        labels = []
        for p in points:
            s = steps.get(p["snapshotId"])
            if data.get("role"):
                head = s["title"].replace("Базова мережа", "База").replace("Тиждень ", "Т") if s else f"#{p['snapshotId']}"
                labels.append(f"{head}\n{p['memberCount']} ос.")
            else:
                head = f"Крок {s['step']}\n{s['title']}" if s else f"#{p['snapshotId']}"
                labels.append(f"{head}\nспільнота: {p['memberCount']} осіб")
        ax.set_xticks(xs, labels, fontsize=7)
        who = data.get("username") or f"#{data['userId']}"
        if data.get("role"):
            ax.set_xlabel("тиждень симуляції (розмір спільноти користувача)")
            ax.set_title(f"Траєкторія користувача з роллю «{data['role']}»")
        else:
            ax.set_xlabel("крок сценарію (після кожного — знімок мережі)")
            ax.set_title(f"Траєкторія учасника групи A ({who}) у сценарії")
    else:
        # номер знімка під кожною точкою, дата — лише коли змінюється;
        # номер спільноти — лише коли змінюється (інакше підписи злипаються)
        # (якщо дві дати поруч — друга опускається на рядок нижче)
        labels, previous_day, previous_community, last_dated, lifted = [], None, None, -2, False
        for i, p in enumerate(points):
            day = f"{parse_time(p['createdAt']):%d.%m}"
            parts = [f"#{p['snapshotId']}"]
            if day != previous_day:
                lifted = i - last_dated == 1 and not lifted
                parts.append(("\n" if lifted else "") + day)
                last_dated = i
            if p["community"] != previous_community:
                parts.append(f"сп. {p['community']}")
            labels.append("\n".join(parts))
            previous_day, previous_community = day, p["community"]
        ax.set_xticks(xs, labels, fontsize=7)
        ax.set_xlabel("знімок (номер, дата, спільнота — коли змінюються)")
        ax.set_title(f"Траєкторія користувача #{data['userId']} у ехо-камерах")
    ax.set_xlim(0.5, len(xs) + 0.5)
    ax.set_ylim(0, 1.05)
    ax.set_ylabel("echo-score")
    ax.legend(loc="lower left", fontsize=8)
    save(fig, out, name, f"Користувач @{data['username']} (id {data['userId']})." if data.get("role") else None)


# ===== Еволюція мережі (seed.py --epochs) =====

EVOLUTION_ROLES = [("regular", "Звичайні"), ("drifter", "Затягування"), ("escaper", "Вихід"), ("migrant", "Міграція")]


def nan(values):
    return [float("nan") if v is None else v for v in values]


def epoch_labels(data):
    return ["База" if e["epoch"] == 0 else f"Т{e['epoch']}" for e in data["epochs"]]


def evolution_lines(data, out, key, title, ylabel, name, note=None, roles=EVOLUTION_ROLES, ylim=(0, 1.05),
                    skip_base=False):
    personas = data["personas"]
    labels = epoch_labels(data)
    first = 1 if skip_base else 0
    labels = labels[first:]
    xs = list(range(len(labels)))
    fig, ax = new_figure(3.4)
    drawn = 0
    ends = []   # (x, y, підпис) — розносяться нижче, щоб не налазили один на одного
    for i, (role, label) in enumerate(roles):
        values = ((personas.get(role) or {}).get(key) or [])[first:]
        if not values or all(v is None for v in values):
            continue
        ys = nan(values)
        ax.plot(xs, ys, color=SERIES[i], marker=MARKERS[i], linestyle=LINESTYLES[i],
                label=f"{label} ({personas[role]['count']})")
        last = max((j for j, v in enumerate(values) if v is not None), default=None)
        if last is not None:
            # підпис з назвою ролі: після рознесення підписи можуть відійти від своїх ліній
            ends.append([xs[last], values[last], f"{label} {values[last]:.2f}"])
        drawn += 1
    if ends:
        lo, hi = ylim if ylim else (0, max(e[1] for e in ends) * 1.1 or 1)
        gap = (hi - lo) * 0.06
        ends.sort(key=lambda e: e[1])
        for prev, cur in zip(ends, ends[1:]):
            cur[1] = max(cur[1], prev[1] + gap)
        for x, y, text in ends:
            label_end(ax, x, y, text, INK)
    if not drawn:
        plt.close(fig)
        print(f"  – {name}: немає даних (знімки не робились?)")
        return
    ax.set_xticks(xs, labels)
    ax.set_xlim(-0.3, len(xs) + 0.6)   # праворуч — місце під підписи з назвами ролей
    if ylim:
        ax.set_ylim(*ylim)
    ax.set_xlabel("тиждень симуляції")
    ax.set_ylabel(ylabel)
    ax.set_title(title)
    ax.legend(loc="lower left", fontsize=8, ncols=2)
    save(fig, out, name, note)


def evolution_charts(data, out):
    p = data.get("params", {})
    net = p.get("network", {})
    base_note = (f"Симуляція: {net.get('users', '?')} користувачів, база {net.get('days', '?')} дн. + "
                 f"{p.get('epochs', '?')} тижнів; у дужках — кількість користувачів з роллю.")
    evolution_lines(data, out, "personalHomogeneity",
                    "Особиста тематична однорідність за ролями",
                    "1 − H(тем) / log₂(тем)", "evolution_personal.png", base_note)

    outcome_parts = []
    for role, label in (("drifter", "затягування {} у цільовій"),
                        ("migrant", "міграція {} у цільовій"),
                        ("escaper", "вихід {} поза своєю")):
        o = (data["personas"].get(role) or {}).get("outcome") or {}
        value = o.get("reachedTargetCommunity", o.get("leftOwnCommunity"))
        if value is not None:
            outcome_parts.append(label.format(f"{value * 100:.0f}%"))
    note = ("Спільнота Louvain наприкінці: " + "; ".join(outcome_parts) + ".") if outcome_parts else None
    evolution_lines(data, out, "communityEcho",
                    "Echo-score спільноти, у якій перебуває користувач",
                    "середній echo-score", "evolution_echo.png", note)

    evolution_lines(data, out, "actionsPerUser",
                    "Активність: пости й вподобання на користувача за тиждень",
                    "дій на користувача", "evolution_lifecycle.png",
                    "Новачки з'являються посеред симуляції; активність тих, хто йде, згасає.",
                    roles=[("regular", "Звичайні"), ("newcomer", "Новачки"), ("leaver", "Ті, хто йде")],
                    ylim=None, skip_base=True)

    example = (data["personas"].get("drifter") or {}).get("example")
    if example and example.get("points"):
        titles = {e["epoch"]: ("Базова мережа" if e["epoch"] == 0 else f"Тиждень {e['epoch']}") for e in data["epochs"]}
        points = [{"snapshotId": pt["snapshotId"], "createdAt": f"2000-01-01T00:00:{pt['epoch']:02d}",
                   "community": pt["community"], "memberCount": pt["memberCount"],
                   "echoChamberScore": pt["echoScore"], "networkEchoScore": pt["networkEchoScore"]}
                  for pt in example["points"]]
        steps = [{"snapshotId": pt["snapshotId"], "step": pt["epoch"], "title": titles[pt["epoch"]]}
                 for pt in example["points"]]
        trajectory_chart({"userId": example["userId"], "username": example["username"], "points": points,
                          "steps": steps, "role": "затягування"}, out, "evolution_example.png")


# ===== Аналіз спільнот =====

ALGORITHMS = [("louvain", "Louvain", BLUE), ("leiden", "Leiden", ORANGE)]


def community_experiment_chart(report, out):
    rows = report["summary"]
    fig, (top, bottom) = plt.subplots(2, 1, figsize=(WIDTH_IN, 5.0), sharex=True,
                                      gridspec_kw={"height_ratios": [2.2, 1]})
    for ax in (top, bottom):
        for side in ("top", "right"):
            ax.spines[side].set_visible(False)
    for i, (key, name, color) in enumerate(ALGORITHMS):
        group = sorted((r for r in rows if r["algorithm"] == key), key=lambda r: r["homophily"])
        if not group:
            continue
        xs = [r["homophily"] for r in group]
        # Louvain — товстіша лінія знизу, Leiden — тонша з порожніми маркерами зверху:
        # коли результати збігаються, видно обидва
        width = 3.4 if key == "louvain" else 1.5
        face = color if key == "louvain" else "white"
        for metric, style, marker in (("nmi", "-", "o"), ("ari", "--", "s")):
            top.errorbar(xs, [r[f"{metric}Mean"] for r in group], yerr=[r[f"{metric}Sd"] for r in group],
                         color=color, linestyle=style, marker=marker, markerfacecolor=face, linewidth=width,
                         markersize=8 if key == "louvain" else 6, capsize=3, elinewidth=1,
                         label=f"{name}: {metric.upper()}", zorder=2 if key == "louvain" else 3)
        bottom.errorbar(xs, [r["foundCommunitiesMean"] for r in group],
                        yerr=[r["foundCommunitiesSd"] for r in group], color=color, marker=MARKERS[i],
                        markerfacecolor=face, linewidth=width, linestyle=LINESTYLES[i], capsize=3, elinewidth=1,
                        label=name, zorder=2 if key == "louvain" else 3)
    cfg = report.get("config", {})
    if cfg.get("communities"):
        bottom.axhline(cfg["communities"], color=INK_2, linestyle=":", linewidth=1.2)
        # підпис — праворуч над лінією, де розкид найменший і він не лягає на смуги похибок
        bottom.annotate(f"закладено {cfg['communities']}", xy=(max(cfg.get("homophilies", [1])), cfg["communities"]),
                        xytext=(0, 9), textcoords="offset points", ha="right", fontsize=7, color=INK_2)
    top.set_ylim(0, 1.05)
    top.set_ylabel("збіг зі справжніми")
    top.set_title("Якість виявлення спільнот: Louvain проти Leiden")
    top.legend(loc="lower right", fontsize=8, ncols=2)
    bottom.set_ylabel("знайдено")
    found_max = max([r["foundCommunitiesMean"] + r["foundCommunitiesSd"] for r in rows] + [cfg.get("communities") or 1])
    bottom.set_ylim(0, found_max * 1.25)
    bottom.yaxis.set_major_locator(MaxNLocator(nbins=4, integer=True))
    bottom.set_xlabel("гомофілія (частка підписок усередині своєї спільноти)")
    bottom.set_xticks(cfg.get("homophilies") or sorted({r["homophily"] for r in rows}))
    disconnected = sum(r.get("disconnectedCommunitiesMean", 0) for r in rows if r["algorithm"] == "louvain")
    note = (f"{cfg.get('users', '?')} користувачів, {cfg.get('communities', '?')} спільнот, "
            f"{cfg.get('repeats', '?')} повтори на точку; середнє ± ст. відхилення.")
    if disconnected:
        note += " Louvain інколи дає незв'язні спільноти, Leiden — ні."
    save(fig, out, "community_experiment.png", note)


def community_quality_chart(report, out):
    algorithms = [a for a in report["algorithms"] if "error" not in a]
    if not algorithms:
        print("  – community-quality.json: спільноти не визначені")
        return
    metrics = [("nmi", "NMI"), ("ari", "ARI"), ("purity", "Чистота")]
    fig, ax = new_figure(3.0)
    width = 0.36
    for i, a in enumerate(algorithms):
        name = dict((k, n) for k, n, _ in ALGORITHMS).get(a["algorithm"], a["algorithm"])
        pos = [x + (i - 0.5) * (width + 0.02) for x in range(len(metrics))]
        bars = ax.bar(pos, [a[k] for k, _ in metrics], width, color=SERIES[i],
                      hatch=None if i == 0 else "///", edgecolor="white", linewidth=0.8,
                      label=f"{name}: знайдено {a['foundCommunities']}")
        ax.bar_label(bars, fmt="%.3f", fontsize=7, color=INK, padding=1)
    ax.set_xticks(range(len(metrics)), [t for _, t in metrics])
    ax.set_ylim(0, 1.35)   # місце під легенду над стовпчиками
    ax.set_yticks([0, 0.2, 0.4, 0.6, 0.8, 1.0])
    ax.set_ylabel("збіг зі справжніми спільнотами")
    ax.set_title("Виявлення спільнот на згенерованій мережі")
    ax.legend(loc="upper center", fontsize=8, ncols=2)
    save(fig, out, "community_quality.png",
         f"{report['users']} згенерованих користувачів, закладено {report['trueCommunities']} спільнот "
         f"(ролі з переходами між спільнотами не враховано).")


def community_label(c):
    tag = f" · #{c['topTags'][0]}" if c.get("topTags") else ""
    return f"сп. {c['community']}{tag} ({c['size']})"


def community_structure_chart(report, out):
    details = report["details"]
    if not details:
        return
    labels = [community_label(c) for c in details][::-1]
    fig, ax = new_figure(0.42 * len(details) + 1.3)
    h = 0.38
    ys = range(len(details))
    for i, (key, title) in enumerate([("density", "Щільність"), ("conductance", "Провідність")]):
        vals = [c[key] for c in details][::-1]
        pos = [y + (0.5 - i) * (h + 0.02) for y in ys]
        bars = ax.barh(pos, vals, h, color=SERIES[i], label=title,
                       hatch=None if i == 0 else "///", edgecolor="white", linewidth=0.8)
        ax.bar_label(bars, fmt="%.2f", fontsize=7, color=INK, padding=2)
    ax.set_yticks(list(ys), labels, fontsize=8)
    ax.set_xlim(0, max(max(c["density"], c["conductance"]) for c in details) * 1.2)
    ax.grid(axis="y", visible=False)
    ax.set_title(f"Структура спільнот ({report['algorithm'].capitalize()})")
    # легенда — під віссю, щоб не лягала на стовпчики й заголовок
    ax.legend(loc="upper right", bbox_to_anchor=(1.0, -0.07), fontsize=8, ncols=2)
    save(fig, out, "community_structure.png",
         f"Щільність — частка можливих підписок усередині; провідність — частка зв'язків назовні. "
         f"Модульність {report['modularity']:.3f}.")


def community_matrix_chart(report, out):
    m = report.get("matrix") or {}
    follows = m.get("follows")
    if not follows:
        return
    by_label = {str(c["community"]): c for c in report["details"]}
    names = [community_label(by_label[l]) if l in by_label else l for l in m["labels"]]
    shares = []
    for row in follows:
        total = sum(row) or 1
        shares.append([v / total for v in row])
    n = len(names)
    size = min(WIDTH_IN, 1.0 + 0.55 * n)
    fig, ax = plt.subplots(figsize=(WIDTH_IN, size))
    from matplotlib.colors import LinearSegmentedColormap
    cmap = LinearSegmentedColormap.from_list("blues", ["#ffffff", "#cde1f7", "#6fa6e8", BLUE, "#123f78"])
    image = ax.imshow(shares, cmap=cmap, vmin=0, vmax=1, aspect="auto")
    for i in range(n):
        for j in range(n):
            value = shares[i][j]
            if value >= 0.005:
                ax.text(j, i, f"{value * 100:.0f}", ha="center", va="center", fontsize=7,
                        color="white" if value > 0.55 else INK)
    ax.set_xticks(range(n), [l.split(" (")[0] for l in names], rotation=40, ha="right", fontsize=7)
    ax.set_yticks(range(n), names, fontsize=7)
    ax.set_xlabel("на кого підписуються (спільнота)")
    ax.set_ylabel("хто підписується")
    ax.grid(False)
    ax.set_title("Підписки між спільнотами, % від підписок рядка")
    fig.colorbar(image, ax=ax, fraction=0.04, pad=0.02).ax.tick_params(labelsize=7)
    save(fig, out, "community_matrix.png", "Діагональ — підписки всередині своєї спільноти.")


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
    if (d := load(data_dir, "trajectory-scenario.json")) is not None:
        trajectory_chart(d, out, "trajectory_scenario.png")
    if (d := load(data_dir, "evolution.json")) is not None:
        evolution_charts(d, out)
    if (d := load(data_dir, "community-experiment.json")) is not None:
        community_experiment_chart(d, out)
    if (d := load(data_dir, "community-quality.json")) is not None:
        community_quality_chart(d, out)
    if (d := load(data_dir, "community-structure.json")) is not None:
        community_structure_chart(d, out)
        community_matrix_chart(d, out)


if __name__ == "__main__":
    main()
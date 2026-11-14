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
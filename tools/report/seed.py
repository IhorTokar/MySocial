#!/usr/bin/env python3
"""
Наповнення основної бази реалістичними даними для демонстрації та звіту.

Кроки:
  1. POST /api/dev/seed-realistic   — користувачі, спільноти, пости, підписки, вподобання, коментарі
  2. POST /api/dev/backfill-embeddings — семантичні вектори постів (NLP-сервіс)
  3. POST /api/dev/backfill-sentiment  — тональність постів (NLP-сервіс)
  4. POST /api/graph/embeddings        — node2vec для «Схожих інтересів»
  5. POST /api/admin/echo-history      — знімок ехо-камер (оновлює граф і спільноти)

Приклади:
  python seed.py --admin medoko                         # 200 користувачів за замовчуванням
  python seed.py --admin medoko --users 400 --days 60   # більша мережа
  python seed.py --admin medoko --cleanup               # видалити все згенероване
  python seed.py --admin medoko --cleanup --users 300   # перегенерувати з нуля
  python seed.py --admin medoko --only-nlp              # лише кроки 2–5 для наявних постів
                                                        # (напр. після зміни моделі тональності)
  python seed.py --admin medoko --reembed               # перерахувати вектори ВСІХ постів
                                                        # (вектор — із заголовка й тексту разом)
  python seed.py --admin medoko --cleanup --epochs 6    # мережа, що живе 6 тижнів (див. нижче)

Еволюція (--epochs N): базова мережа за --days днів (за замовчуванням 21), далі N тижнів активності.
Частина користувачів має ролі з відомою наперед поведінкою — затягування в ехо-камеру, вихід з неї,
міграція між спільнотами, новачки, ті, хто йде. Після бази й кожного тижня сервер сам рахує тональність
і вектори нових постів та робить знімок ехо-камер (кроки 2, 3 і 5 тоді не потрібні).
Результат — report-data/evolution.json, з нього charts.py будує графіки evolution_*.png.
Триває кілька хвилин (з NLP-сервісом — довше).

Усі згенеровані акаунти мають email @seed.nexus.local і пароль password123 —
під будь-яким із них можна увійти в клієнт.
"""

import argparse
import getpass
import json
import os
import sys
import time

from mysocial_api import Api, ApiError

LONG = 1800   # генерація й backfill на сотнях постів тривають хвилини


def step(title):
    print(f"\n=== {title} ===", flush=True)


def timed(action):
    started = time.time()
    result = action()
    return result, time.time() - started


def short(data, keys):
    return {k: data[k] for k in keys if isinstance(data, dict) and k in data}


def main():
    parser = argparse.ArgumentParser(description="Реалістичні дані для MySocial")
    parser.add_argument("--url", default="http://localhost:8080", help="адреса сервера")
    parser.add_argument("--admin", required=True, help="логін адміністратора")
    parser.add_argument("--cleanup", action="store_true", help="спершу видалити раніше згенероване")
    parser.add_argument("--only-cleanup", action="store_true", help="лише видалити згенероване й вийти")
    parser.add_argument("--skip-nlp", action="store_true", help="не рахувати вектори й тональність")
    parser.add_argument("--only-nlp", action="store_true",
                        help="не генерувати дані — лише вектори, тональність, node2vec і знімок")
    parser.add_argument("--reembed", action="store_true",
                        help="лише перерахувати вектори всіх постів (заголовок + текст) і вийти")
    parser.add_argument("--out", default="report-data", help="куди зберегти підсумок генерації")

    model = parser.add_argument_group("параметри мережі")
    model.add_argument("--users", type=int, default=200)
    model.add_argument("--communities", type=int, default=8)
    model.add_argument("--days", type=int, default=None,
                       help="за скільки днів дати постів базової мережі (45; з --epochs — 21)")
    model.add_argument("--avg-posts", type=float, default=6)
    model.add_argument("--avg-follows", type=float, default=12)
    model.add_argument("--avg-likes", type=float, default=30)
    model.add_argument("--homophily", type=float, default=0.8, help="частка підписок усередині спільноти")
    model.add_argument("--narrow-share", type=float, default=0.5, help="частка «вузьких» спільнот")
    model.add_argument("--bridge-share", type=float, default=0.1, help="частка користувачів-«мостів»")
    model.add_argument("--comment-rate", type=float, default=0.15)
    model.add_argument("--reply-rate", type=float, default=0.4)
    model.add_argument("--save-rate", type=float, default=0.05)
    model.add_argument("--seed", type=int, default=42, help="зерно випадковості (той самий seed — ті самі дані)")

    evo = parser.add_argument_group("еволюція мережі")
    evo.add_argument("--epochs", type=int, default=0, help="скільки тижнів симулювати після базової мережі (0 — без еволюції)")
    evo.add_argument("--epoch-days", type=int, default=7, help="тривалість одного «тижня» в днях")
    evo.add_argument("--drifters", type=float, default=0.05, help="частка тих, кого затягує в ехо-камеру")
    evo.add_argument("--escapers", type=float, default=0.05, help="частка тих, хто з неї виходить")
    evo.add_argument("--migrants", type=float, default=0.05, help="частка тих, хто переходить в іншу спільноту")
    evo.add_argument("--newcomers", type=float, default=0.1, help="новачків (від початкової кількості)")
    evo.add_argument("--leavers", type=float, default=0.05, help="частка тих, хто поступово йде")
    args = parser.parse_args()
    if args.days is None:
        args.days = 21 if args.epochs else 45
    if args.epochs and args.only_nlp:
        sys.exit("--epochs і --only-nlp разом не мають сенсу: еволюція сама рахує NLP для нових постів")

    password = os.environ.get("MYSOCIAL_ADMIN_PASSWORD") or getpass.getpass(f"Пароль {args.admin}: ")
    try:
        admin = Api(args.url).login(args.admin, password)
    except (ApiError, OSError) as e:
        sys.exit(f"Не вдалося увійти: {e}")
    if (admin.role or "").lower() != "admin":
        sys.exit(f"{args.admin} не адміністратор (роль {admin.role})")

    try:
        if args.reembed:
            step("Перерахунок векторів усіх постів (заголовок + текст)")
            r, sec = timed(lambda: admin.post("/api/dev/backfill-embeddings", timeout=LONG, all="true"))
            print(f"  {short(r, ['processed', 'failed', 'total'])} ({sec:.1f} с)")
            if r.get("failed"):
                print("  ! частина постів без нових векторів — чи запущено NLP-сервіс (FastAPI)?")
            return
        if args.cleanup or args.only_cleanup:
            step("Видалення згенерованих даних")
            r, sec = timed(lambda: admin.request("DELETE", "/api/dev/seed-realistic", timeout=LONG))
            print(f"видалено користувачів: {r.get('usersDeleted')} ({sec:.1f} с)")
            if args.only_cleanup:
                return

        params = summary = None
        if args.epochs:
            evolve(admin, args)
            step("node2vec (схожі інтереси)")
            try:
                r, sec = timed(lambda: admin.post("/api/graph/embeddings", timeout=LONG))
                print(f"  готово ({sec:.1f} с)")
            except ApiError as e:
                print(f"  пропущено: {e} — чи запущено Neo4j?")
            print("Далі: python charts.py")
            return
        if args.only_nlp:
            print("Генерацію пропущено (--only-nlp).")
        else:
            params, summary = generate(admin, args)

        if not args.skip_nlp:
            step("Семантичні вектори постів")
            r, sec = timed(lambda: admin.post("/api/dev/backfill-embeddings", timeout=LONG))
            print(f"  {short(r, ['processed', 'failed', 'total'])} ({sec:.1f} с)")
            if r.get("failed"):
                print("  ! частина постів без векторів — чи запущено NLP-сервіс (FastAPI)?")

            step("Тональність постів")
            r, sec = timed(lambda: admin.post("/api/dev/backfill-sentiment", timeout=LONG))
            print(f"  {short(r, ['processed', 'bySource'])} ({sec:.1f} с)")

        step("node2vec (схожі інтереси)")
        try:
            r, sec = timed(lambda: admin.post("/api/graph/embeddings", timeout=LONG))
            print(f"  готово ({sec:.1f} с)")
        except ApiError as e:
            print(f"  пропущено: {e} — чи запущено Neo4j?")

        step("Знімок ехо-камер")
        try:
            snap, sec = timed(lambda: admin.post("/api/admin/echo-history", timeout=LONG, refresh="true"))
            print(f"  {short(snap, ['snapshotId', 'communityCount', 'userCount', 'weightedEchoScore', 'maxEchoScore'])}"
                  f" ({sec:.1f} с)")
        except ApiError as e:
            print(f"  пропущено: {e}")

        if summary is not None:
            os.makedirs(args.out, exist_ok=True)
            path = os.path.join(args.out, "seed-summary.json")
            with open(path, "w", encoding="utf-8") as f:
                json.dump({"params": params, "summary": summary}, f, ensure_ascii=False, indent=2)
            print(f"\nПідсумок збережено: {path}")
        print("Далі: python demo.py --admin", args.admin, "--experiments --scenario && python charts.py")
    except ApiError as e:
        sys.exit(f"\nПомилка сервера: {e}")
    except OSError as e:
        sys.exit(f"\nНемає зв'язку із сервером: {e}")


def network_params(args):
    return {
        "users": args.users, "communities": args.communities, "days": args.days,
        "avgPosts": args.avg_posts, "avgFollows": args.avg_follows, "avgLikes": args.avg_likes,
        "homophily": args.homophily, "narrowShare": args.narrow_share, "bridgeShare": args.bridge_share,
        "commentRate": args.comment_rate, "replyRate": args.reply_rate, "saveRate": args.save_rate,
        "seed": args.seed,
    }


def fmt(value, digits=2):
    return "—" if value is None else f"{value:.{digits}f}"


def evolve(admin, args):
    """Базова мережа + args.epochs тижнів еволюції з ролями; результат — report-data/evolution.json."""
    step(f"Еволюція: {args.users} користувачів, база {args.days} днів + {args.epochs} тижнів по {args.epoch_days} дн.")
    params = network_params(args)
    params.update({
        "epochs": args.epochs, "epochDays": args.epoch_days,
        "drifterShare": args.drifters, "escaperShare": args.escapers, "migrantShare": args.migrants,
        "newcomerShare": args.newcomers, "leaverShare": args.leavers,
        "snapshots": "false" if args.skip_nlp else "true",
    })
    print("  кілька хвилин: після кожного тижня — NLP і знімок ехо-камер…", flush=True)
    result, sec = timed(lambda: admin.post("/api/dev/seed-evolution", timeout=LONG * 2, **params))

    print(f"\n  {'тиждень':16} {'користувачів':>12} {'постів':>7} {'вподоб.':>8} {'підписок':>9} {'відписок':>9} {'echo мережі':>12}")
    for e in result["epochs"]:
        a, snap = e["activity"], e.get("snapshot") or {}
        print(f"  {e['title']:16} {a.get('users', ''):>12} {a.get('posts', ''):>7} {a.get('likesCreated', ''):>8} "
              f"{a.get('follows', ''):>9} {a.get('unfollows', ''):>9} {fmt(snap.get('weightedEchoScore'), 3):>12}")

    print("\n  ролі: особиста однорідність (база → останній тиждень), echo спільноти, результат")
    for key, p in result["personas"].items():
        homog = [v for v in p["personalHomogeneity"] if v is not None]
        echo = [v for v in (p.get("communityEcho") or []) if v is not None]
        line = (f"    {p['title']:28} {p['count']:4}  однорідність {fmt(homog[0] if homog else None)} → "
                f"{fmt(homog[-1] if homog else None)}")
        if echo:
            line += f"   echo {fmt(echo[0], 3)} → {fmt(echo[-1], 3)}"
        outcome = p.get("outcome") or {}
        for name, label in (("reachedTargetCommunity", "у цільовій спільноті"),
                            ("leftOwnCommunity", "вийшли зі своєї спільноти")):
            if name in outcome:
                line += f"   {label}: {outcome[name] * 100:.0f}%"
        print(line)
    if result.get("snapshotError"):
        print(f"\n  ! знімки ехо-камер не вдалися: {result['snapshotError']} — чи запущено Neo4j?")
    print(f"  ({sec:.0f} с)")

    os.makedirs(args.out, exist_ok=True)
    path = os.path.join(args.out, "evolution.json")
    with open(path, "w", encoding="utf-8") as f:
        json.dump(result, f, ensure_ascii=False, indent=2)
    print(f"\nРезультат збережено: {path}")


def generate(admin, args):
    """Крок 1: згенерувати мережу; повертає (параметри, підсумок сервера)."""
    step(f"Генерація: {args.users} користувачів, {args.communities} спільнот, {args.days} днів")
    params = network_params(args)
    summary, sec = timed(lambda: admin.post("/api/dev/seed-realistic", timeout=LONG, **params))
    for key in ("usersCreated", "postsCreated", "followsCreated", "likesCreated",
                "commentsCreated", "repliesCreated", "savesCreated", "followsWithinCommunityShare"):
        print(f"  {key:30} {summary.get(key)}")
    print("  спільноти:")
    for c in summary.get("communities", []):
        print(f"    #{c['index']} {c['type']:8} тон {c['tone']:+.1f}  {c['members']:3} осіб  {', '.join(c['topics'])}")
    print(f"  граф: {summary.get('graphSync')}")
    print(f"  ({sec:.1f} с)")
    return params, summary


if __name__ == "__main__":
    main()
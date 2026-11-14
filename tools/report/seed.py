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
    parser.add_argument("--out", default="report-data", help="куди зберегти підсумок генерації")

    model = parser.add_argument_group("параметри мережі")
    model.add_argument("--users", type=int, default=200)
    model.add_argument("--communities", type=int, default=8)
    model.add_argument("--days", type=int, default=45, help="за скільки останніх днів дати постів")
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
    args = parser.parse_args()

    password = os.environ.get("MYSOCIAL_ADMIN_PASSWORD") or getpass.getpass(f"Пароль {args.admin}: ")
    try:
        admin = Api(args.url).login(args.admin, password)
    except (ApiError, OSError) as e:
        sys.exit(f"Не вдалося увійти: {e}")
    if (admin.role or "").lower() != "admin":
        sys.exit(f"{args.admin} не адміністратор (роль {admin.role})")

    try:
        if args.cleanup or args.only_cleanup:
            step("Видалення згенерованих даних")
            r, sec = timed(lambda: admin.request("DELETE", "/api/dev/seed-realistic", timeout=LONG))
            print(f"видалено користувачів: {r.get('usersDeleted')} ({sec:.1f} с)")
            if args.only_cleanup:
                return

        step(f"Генерація: {args.users} користувачів, {args.communities} спільнот, {args.days} днів")
        params = {
            "users": args.users, "communities": args.communities, "days": args.days,
            "avgPosts": args.avg_posts, "avgFollows": args.avg_follows, "avgLikes": args.avg_likes,
            "homophily": args.homophily, "narrowShare": args.narrow_share, "bridgeShare": args.bridge_share,
            "commentRate": args.comment_rate, "replyRate": args.reply_rate, "saveRate": args.save_rate,
            "seed": args.seed,
        }
        summary, sec = timed(lambda: admin.post("/api/dev/seed-realistic", timeout=LONG, **params))
        for key in ("usersCreated", "postsCreated", "followsCreated", "likesCreated",
                    "commentsCreated", "repliesCreated", "savesCreated", "followsWithinCommunityShare"):
            print(f"  {key:30} {summary.get(key)}")
        print("  спільноти:")
        for c in summary.get("communities", []):
            print(f"    #{c['index']} {c['type']:8} тон {c['tone']:+.1f}  {c['members']:3} осіб  {', '.join(c['topics'])}")
        print(f"  граф: {summary.get('graphSync')}")
        print(f"  ({sec:.1f} с)")

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


if __name__ == "__main__":
    main()
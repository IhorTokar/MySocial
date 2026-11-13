#!/usr/bin/env python3
"""
Демонстрація функціоналу MySocial через REST API — для захисту та звіту.

Сценарій створює трьох демо-користувачів (Олена, Тарас, Ірина), розігрує між ними
типову активність і показує, як на неї реагує система: сповіщення, стрічки «Для вас» /
«Відстежувані», гарячі теми, «Кого почитати», пошук, заявка «Забули пароль?»,
а від імені адміністратора — MMR-експеримент і знімок ехо-камер.
Результати зберігаються в JSON (папка --out), з них charts.py будує графіки для звіту.

Запуск (сервер має працювати):
    python3 demo.py --admin admin                 # пароль буде запитано
    python3 demo.py --admin admin --pause         # пауза після кожного кроку (для живого показу)
    python3 demo.py --admin admin --experiments   # + NDCG і валідація ехо-камер (кілька хвилин)
    python3 demo.py --admin admin --scenario      # + покроковий сценарій ехо-камери
    python3 demo.py --admin admin --keep          # не видаляти демо-користувачів наприкінці

Пароль адміністратора можна передати через змінну середовища MYSOCIAL_ADMIN_PASSWORD.
"""

import argparse
import getpass
import json
import os
import random
import string
import sys
import time
from pathlib import Path

from mysocial_api import Api, ApiError

DEMO_PASSWORD = "demo-pass-123"
LONG = 900  # секунд — для експериментів, що створюють і видаляють сотні тестових користувачів

# ----- вивід -----

GREEN, RED, DIM, BOLD, RESET = "\033[32m", "\033[31m", "\033[2m", "\033[1m", "\033[0m"
if not sys.stdout.isatty():
    GREEN = RED = DIM = BOLD = RESET = ""


class Demo:
    def __init__(self, args):
        self.args = args
        self.out = Path(args.out)
        self.out.mkdir(parents=True, exist_ok=True)
        self.anon = Api(args.url)
        self.admin = None
        self.users = {}          # ім'я → Api
        self.posts = {}          # ключ → postId
        self.step_no = 0
        self.failed = []

    # ----- допоміжне -----

    def step(self, title, explanation=""):
        self.step_no += 1
        print(f"\n{BOLD}{self.step_no}. {title}{RESET}")
        if explanation:
            print(f"{DIM}   {explanation}{RESET}")

    def ok(self, text):
        print(f"   {GREEN}✔{RESET} {text}")

    def info(self, text):
        print(f"     {text}")

    def fail(self, text):
        print(f"   {RED}✘ {text}{RESET}")
        self.failed.append(f"{self.step_no}: {text}")

    def pause(self):
        if self.args.pause:
            input(f"{DIM}   … Enter — далі{RESET}")

    def save(self, name, data):
        path = self.out / name
        path.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
        self.info(f"{DIM}збережено: {path}{RESET}")

    def run_step(self, func):
        try:
            func()
        except ApiError as e:
            self.fail(str(e))
        except Exception as e:  # noqa: BLE001 — демонстрація має йти далі
            self.fail(f"{type(e).__name__}: {e}")
        self.pause()

    # ----- кроки -----

    def login_admin(self):
        self.step("Вхід адміністратора")
        password = os.environ.get("MYSOCIAL_ADMIN_PASSWORD") or getpass.getpass(
            f"   Пароль для {self.args.admin}: ")
        self.admin = self.anon.login(self.args.admin, password)
        if self.admin.role != "admin":
            raise ApiError(403, f"{self.args.admin} не адміністратор (роль {self.admin.role})")
        self.ok(f"увійшли як @{self.admin.username} (id {self.admin.user_id})")

    def create_users(self):
        self.step("Реєстрація демо-користувачів",
                  "Три нові акаунти з унікальним суфіксом, щоб демонстрацію можна було повторювати.")
        suffix = "".join(random.choices(string.ascii_lowercase + string.digits, k=4))
        for name in ("olena", "taras", "iryna"):
            username = f"demo_{name}_{suffix}"
            self.anon.register(username, f"{username}@demo.local", DEMO_PASSWORD)
            self.users[name] = self.anon.login(username, DEMO_PASSWORD)
            self.ok(f"@{username} (id {self.users[name].user_id})")

    def create_posts(self):
        self.step("Публікація постів з тегами")
        plan = [
            ("olena", "trip", "Карпати восени", "Пройшла маршрут на Говерлу — туман, але вид неймовірний.", ["подорожі", "гори"]),
            ("olena", "food", "Банош на вогнищі", "Найкращий сніданок після ночівлі в наметі.", ["їжа", "подорожі"]),
            ("taras", "code", "Spring Boot + Neo4j", "Підключив графову БД для рекомендацій друзів.", ["програмування", "наука"]),
            ("taras", "bike", "Веломаршрут Київ — Ірпінь", "40 км лісом, рекомендую на вихідні.", ["спорт", "подорожі"]),
            ("iryna", "art", "Виставка сучасного мистецтва", "Інсталяції з переробленого пластику — дуже сильно.", ["мистецтво", "дизайн"]),
            ("iryna", "recipe", "Вареники з вишнями", "Мамин рецепт, тісто на кефірі.", ["їжа"]),
        ]
        for user, key, label, text, tags in plan:
            resp = self.users[user].post("/api/posts", {"label": label, "text": text, "tags": tags})
            self.posts[key] = resp["postId"]
            self.ok(f"@{self.users[user].username}: «{label}» #{' #'.join(tags)}")

    def social_activity(self):
        self.step("Підписки, вподобання, коментарі, відповідь",
                  "Олена читає Тараса, Тарас — Ірину. Ірина й Тарас реагують на пост Олени.")
        o, t, i = self.users["olena"], self.users["taras"], self.users["iryna"]
        o.post(f"/api/followers/{t.user_id}")
        t.post(f"/api/followers/{i.user_id}")
        i.post(f"/api/followers/{o.user_id}")
        self.ok("підписки: Олена → Тарас → Ірина → Олена")
        i.post(f"/api/posts/{self.posts['trip']}/like")
        t.post(f"/api/posts/{self.posts['trip']}/like")
        o.post(f"/api/posts/{self.posts['bike']}/like")
        self.ok("вподобання: Ірина і Тарас → «Карпати восени», Олена → «Веломаршрут»")
        comment = t.post(f"/api/posts/{self.posts['trip']}/comments", {"text": "Який маршрут обрали?"})
        o.post(f"/api/posts/{self.posts['trip']}/comments",
               {"text": "Через Заросляк, 4 години.", "parentCommentId": comment["commentId"]})
        self.ok("Тарас прокоментував пост Олени, Олена відповіла")

    def notifications(self):
        self.step("Сповіщення",
                  "Складаються на льоту з підписок, вподобань і коментарів; лічильник — у шапці клієнта.")
        for name in ("olena", "taras"):
            api = self.users[name]
            count = api.get("/api/notifications/unread-count")["unread"]
            items = api.get("/api/notifications", limit=10)
            self.ok(f"@{api.username}: непрочитаних {count}")
            for n in items:
                self.info(f"· {n['type']:<7} від @{n['actorUsername']}"
                          + (f" — «{n['snippet']}»" if n.get("snippet") else ""))
            api.post("/api/notifications/seen")
        self.ok("після відкриття списку лічильник = "
                f"{self.users['olena'].get('/api/notifications/unread-count')['unread']}")

    def feeds(self):
        self.step("Стрічки «Для вас» і «Відстежувані»",
                  "«Для вас» — гібридне ранжування + MMR, після рекомендацій — хронологія; "
                  "«Відстежувані» — лише підписки за часом.")
        o = self.users["olena"]
        for_you = o.get("/api/posts/feed", limit=5)
        following = o.get("/api/posts/feed/following", limit=5)
        self.ok("«Для вас» (перші 5):")
        for p in for_you:
            self.info(f"· @{p['authorUsername']}: {p.get('label') or p['text'][:50]}")
        self.ok("«Відстежувані» (перші 5):")
        for p in following:
            self.info(f"· @{p['authorUsername']}: {p.get('label') or p['text'][:50]}")
        self.save("feed-for-you.json", for_you)

    def discovery(self):
        self.step("Права колонка: гарячі теми й «Кого почитати»")
        o = self.users["olena"]
        trends = o.get("/api/discover/trends", limit=5)
        for t in trends:
            self.info(f"· #{t['tag']}: {t['posts']} постів, {t['likes']} вподобань, {t['comments']} коментарів")
        people = o.get("/api/discover/people", limit=5)
        self.ok("пропозиції для Олени:")
        for p in people:
            self.info(f"· @{p['username']} — {p['reason']}")

    def search(self):
        self.step("Пошук за тегом #подорожі")
        result = self.users["iryna"].get("/api/search", q="#подорожі")
        posts = result.get("posts", [])
        self.ok(f"знайдено постів: {len(posts)}")
        for p in posts[:5]:
            self.info(f"· @{p['authorUsername']}: {p.get('label') or p['text'][:50]}")

    def forgot_password(self):
        self.step("«Забули пароль?» → заявка адміністратору",
                  "Анонімна заявка; однакова відповідь для існуючого й вигаданого логіна.")
        login = self.users["iryna"].username
        resp = self.anon.post("/api/auth/password-reset-requests",
                              {"login": login, "contact": "+380671234567", "message": "демонстрація"})
        self.ok(resp.get("message", "заявку прийнято"))
        pending = self.admin.get("/api/admin/password-requests", status="pending")
        mine = [r for r in pending if r["username"] == login]
        if mine:
            self.ok(f"адмін бачить заявку #{mine[0]['requestId']} від @{login}; відхиляємо (демо)")
            self.admin.post(f"/api/admin/password-requests/{mine[0]['requestId']}/reject")

    def mmr(self):
        self.step("MMR-експеримент (різноманітність стрічки)",
                  "Та сама стрічка при різних λ: 1 — лише релевантність, менше — більше різноманіття.")
        # у щойно створеної Олени лише кілька кандидатів — λ нічого не змінить; беремо акаунт з історією
        user_id = self.args.mmr_user or self.admin.user_id
        rows = self.admin.get("/api/dev/mmr-experiment", userId=user_id, limit=10,
                              lambdas="1,0.9,0.8,0.7,0.6,0.5,0.4,0.3")
        for r in rows:
            self.info(f"λ={r['lambda']:.1f}: ILD={r['intraListDiversity']:.3f}, "
                      f"релевантність={r['relevanceRetained']:.3f}, тегів={r['distinctTags']}, авторів={r['distinctAuthors']}")
        self.save("mmr.json", {"userId": user_id, "rows": rows})

    def echo(self):
        self.step("Знімок ехо-камер (Louvain) та історія",
                  "Потрібні Neo4j + GDS; знімок перераховує спільноти й зберігає метрики.")
        snap = self.admin.post("/api/admin/echo-history", timeout=LONG, refresh="true")
        self.ok(f"знімок #{snap['snapshotId']}: спільнот {snap['communityCount']}, "
                f"зважений echo-score {snap['weightedEchoScore']:.3f}")
        history = self.admin.get("/api/admin/echo-history", limit=100)
        self.save("echo-history.json", history)
        user_id = self.args.trajectory_user or self.admin.user_id
        trajectory = self.admin.get(f"/api/graph/community/{user_id}/history")
        self.save("trajectory.json", {"userId": user_id, "points": trajectory})
        self.ok(f"знімків в історії: {len(history)}, точок траєкторії користувача {user_id}: {len(trajectory)}")

    def experiments(self):
        self.step("Експеримент NDCG@10 (ваги рекомендацій)",
                  "Синтетичні користувачі з прихованими вподобаннями; порівняння методів ранжування. "
                  "Триває кілька хвилин.")
        started = time.time()
        report = self.admin.post("/api/dev/validation/recommendation-weights", timeout=LONG)
        self.save("ndcg.json", report)
        for m in report["methods"]:
            if not m["name"].startswith("default_without_"):
                self.info(f"· {m['name']:<15} NDCG валідація = {m['validation']['ndcg']:.3f}")
        self.ok(f"готово за {time.time() - started:.0f} с")
        self.pause()

        self.step("Валідація метрики ехо-камер",
                  "Штучні спільноти із заданим рівнем однорідності; чи росте echo-score разом із ним.")
        started = time.time()
        report = self.admin.post("/api/dev/validation/echo-chamber", timeout=LONG)
        self.save("echo-validation.json", report)
        for row in report["byLevel"]:
            self.info(f"· рівень {row['level']:.1f}: echo-score {row['scoreMean']:.3f} ± {row['scoreSd']:.3f}")
        self.ok(f"Spearman (рівень ↔ echo-score) = {report['spearmanVsLevel']['composite']}; "
                f"готово за {time.time() - started:.0f} с")

    def scenario(self):
        self.step("Сценарій ехо-камери: 3 кроки",
                  "Дві групи → одна замикається в ехо-камеру → з'являється «міст» між групами.")
        steps = []
        for n in (1, 2, 3):
            r = self.admin.post(f"/api/dev/echo-scenario/{n}", timeout=LONG)
            steps.append(r)
            a, b = r.get("groupA") or {}, r.get("groupB") or {}
            self.info(f"· крок {n} «{r['title']}»: A={a.get('echoChamberScore', 0):.3f}, "
                      f"B={b.get('echoChamberScore', 0):.3f}, спільнота A={a.get('community')}, B={b.get('community')}")
        self.save("echo-scenario.json", steps)
        self.admin.delete("/api/dev/echo-scenario")
        self.ok("тестові групи сценарію видалено")

    def cleanup(self):
        self.step("Прибирання: видалення демо-користувачів")
        for api in self.users.values():
            api.delete("/api/users/me", {"password": DEMO_PASSWORD})
            self.ok(f"@{api.username} видалено (разом із постами, підписками, коментарями)")

    # ----- запуск -----

    def run(self):
        print(f"{BOLD}MySocial — демонстрація функціоналу{RESET}  ({self.args.url})")
        self.run_step(self.login_admin)
        if self.admin is None:
            print(f"{RED}Без адміністратора далі не йдемо.{RESET}")
            return 1
        self.run_step(self.create_users)
        if len(self.users) < 3:
            return 1
        for func in (self.create_posts, self.social_activity, self.notifications, self.feeds,
                     self.discovery, self.search, self.forgot_password, self.mmr, self.echo):
            self.run_step(func)
        if self.args.experiments:
            self.run_step(self.experiments)
        if self.args.scenario:
            self.run_step(self.scenario)
        if not self.args.keep:
            self.run_step(self.cleanup)

        print(f"\n{BOLD}Готово.{RESET} Дані для графіків: {self.out.resolve()}")
        if self.failed:
            print(f"{RED}Не вдалося ({len(self.failed)}):{RESET}")
            for f in self.failed:
                print(f"  - крок {f}")
            return 1
        return 0


def main():
    parser = argparse.ArgumentParser(description="Демонстрація функціоналу MySocial через REST API")
    parser.add_argument("--url", default="http://localhost:8080", help="адреса сервера")
    parser.add_argument("--admin", required=True, help="логін адміністратора")
    parser.add_argument("--out", default="report-data", help="куди зберігати JSON для графіків")
    parser.add_argument("--pause", action="store_true", help="пауза після кожного кроку")
    parser.add_argument("--experiments", action="store_true", help="NDCG і валідація ехо-камер (довго)")
    parser.add_argument("--scenario", action="store_true", help="покроковий сценарій ехо-камери")
    parser.add_argument("--keep", action="store_true", help="не видаляти демо-користувачів")
    parser.add_argument("--mmr-user", type=int, help="id користувача для MMR (за замовчуванням — адміністратор)")
    parser.add_argument("--trajectory-user", type=int,
                        help="id користувача для траєкторії ехо-камер (за замовчуванням — адміністратор)")
    sys.exit(Demo(parser.parse_args()).run())


if __name__ == "__main__":
    main()
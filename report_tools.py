#!/usr/bin/env python3
"""Виводить у термінал короткі таблиці з відповідей сервера MySocial для скріншотів у звіт.

  curl ... /api/posts/feed/debug?limit=5 | python3 report_tools.py feed
  python3 report_tools.py echo echo_validation.json        (або через | з curl)
  python3 report_tools.py weights w_seed43_tau0.json w_seed43_tau7.json w_seed44_tau0.json w_seed44_tau7.json
"""
import json
import statistics
import sys

W = {'P': 0.10, 'C': 0.35, 'G': 0.35, 'M': 0.10, 'R': 0.10}


def num(x, d=3):
    return f'{x:.{d}f}'.replace('.', ',')


def load(args):
    return json.load(open(args[0], encoding='utf-8')) if args else json.load(sys.stdin)


def feed(args):
    rows = load(args)
    print('Складові оцінки публікацій у стрічці (GET /api/posts/feed/debug)\n')
    print(f"{'Пост':>6} {'P':>7} {'C':>7} {'G':>7} {'M':>7} {'R':>7} {'B':>5} {'S сервер':>10} {'S за (3.1)':>11}")
    for p in rows:
        s = (W['P'] * p['popularityScore'] + W['C'] * p['itemCfScore'] + W['G'] * p['graphSimScore']
             + W['M'] * p['semanticScore'] + W['R'] * p['recencyScore'] + p['followedBonus'])
        print(f"{p['postId']:>6} {num(p['popularityScore']):>7} {num(p['itemCfScore']):>7} {num(p['graphSimScore']):>7} "
              f"{num(p['semanticScore']):>7} {num(p['recencyScore']):>7} {num(p['followedBonus'], 1):>5} "
              f"{num(p['totalScore']):>10} {num(s):>11}")


def echo(args):
    d = load(args)
    c = d['config']
    print(f"Перевірка показника ехо-камери (POST /api/dev/validation/echo-chamber, seed={c['seed']})")
    print(f"спільнот: {len(d['communities'])}, користувачів у спільноті: {c['usersPerCommunity']}, "
          f"публікацій на користувача: {c['postsPerUser']}, час: {d['durationSeconds']:.0f} с\n")
    print(f"{'h':>5} {'T':>7} {'K':>7} {'E':>7} {'Z':>7}")
    for r in d['byLevel']:
        print(f"{num(r['level'], 1):>5} {num(r['topicMean']):>7} {num(r['semanticMean']):>7} "
              f"{num(r['sentimentHomogeneityMean']):>7} {num(r['scoreMean']):>7}")
    s1, s2 = d['spearmanVsLevel'], d['spearmanVsDominantShare']
    print('\nКореляція Спірмена ρ з рівнем h / з фактичною часткою головної теми:')
    for key, name in [('topicHomogeneity', 'T'), ('semanticCohesion', 'K'),
                      ('sentimentHomogeneity', 'E'), ('composite', 'Z')]:
        print(f"  {name}: {num(s1[key]):>7} / {num(s2[key]):>7}")


def weights(args):
    runs = [json.load(open(f, encoding='utf-8')) for f in args]
    names = [('random', 'Випадковий порядок'), ('popularityOnly', 'Лише популярність'),
             ('itemCfOnly', 'Лише колаб. фільтрація'), ('graphOnly', 'Лише граф'),
             ('semanticOnly', 'Лише семантика'), ('recencyOnly', 'Лише свіжість'),
             ('expertDefault', 'Експертні ваги'), ('candidate', 'Нові ваги')]
    heads = [f"s={r['config']['seed']},τ={int(r['config']['recencyTauDays'])}" for r in runs]
    print('Середній NDCG@10 (POST /api/dev/validation/recommendation-weights)\n')
    print(f"{'Метод':<24}" + ''.join(f'{h:>12}' for h in heads))
    for key, name in names:
        print(f'{name:<24}' + ''.join(f"{num(statistics.mean(r['perUserNdcg'][key])):>12}" for r in runs))
    diff = [statistics.mean(a - b for a, b in zip(r['perUserNdcg']['candidate'], r['perUserNdcg']['expertDefault'])) for r in runs]
    print(f"{'Різниця (нові − експ.)':<24}" + ''.join(f"{('+' if x >= 0 else '') + num(x):>12}" for x in diff))
    cw = runs[0]['config'].get('candidate')
    if cw:
        print('\nНові ваги (P; C; G; M; R):', '; '.join(num(x, 2) for x in cw))


if __name__ == '__main__':
    cmd, rest = sys.argv[1], sys.argv[2:]
    {'feed': feed, 'echo': echo, 'weights': weights}[cmd](rest)

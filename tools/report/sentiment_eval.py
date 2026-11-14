#!/usr/bin/env python3
"""
Порівняння моделей тональності на розмічених українських текстах.

Розмітка: 80 сюжетів із server/src/main/resources/seed/content.json (поле mood)
+ 20 текстів сценарію ехо-камери. Для кожної моделі — точність, macro-F1,
повнота по класах, матриця помилок і час на текст.

Запуск усередині контейнера NLP-сервісу (там уже є transformers, torch і доступ до Hugging Face):
  docker cp tools/report/sentiment_eval.py social-nlp:/tmp/
  docker cp server/src/main/resources/seed/content.json social-nlp:/tmp/
  docker exec -w /tmp social-nlp python sentiment_eval.py --content content.json
  docker cp social-nlp:/tmp/sentiment-eval.json tools/report/report-data/
"""

import argparse
import json
import time

LABELS = ["negative", "neutral", "positive"]

MODELS = [
    "lxyuan/distilbert-base-multilingual-cased-sentiments-student",   # поточна
    "cardiffnlp/twitter-xlm-roberta-base-sentiment",
    "tabularisai/multilingual-sentiment-analysis",
]
EMBEDDING_MODEL = "paraphrase-multilingual-mpnet-base-v2"   # уже використовується сервісом

# Опорні фрази для методу «найближчий прототип» на векторах, які сервіс уже рахує
ANCHORS = {
    "positive": ["Це чудово, я дуже задоволений.", "Неймовірні враження, раджу всім!",
                 "Як же добре, справжня радість.", "Все вийшло ідеально, я щасливий."],
    "negative": ["Це жахливо, я дуже розчарований.", "Шкода витраченого часу, не раджу.",
                 "Дуже прикро і неприємно.", "Все зіпсовано, мене це дратує."],
    "neutral": ["Звичайний день, нічого особливого.", "Ось як це відбувалося, коротко про факти.",
                "Планую поїздку на наступний тиждень.", "Записую для себе, щоб не забути."],
}

SCENARIO = [
    ("positive", "Запуск нової ракети був неймовірним! Космос — це найкраще, що є в науці."),
    ("positive", "Сьогодні дивився трансляцію старту ракети. Неймовірно надихає, обожнюю космос!"),
    ("positive", "Космічні місії — це майбутнє людства. Кожен новий запуск — чудова новина!"),
    ("positive", "Нарешті успішна посадка ракети! Космос надихає мене щодня."),
    ("positive", "Обожнюю новини про космос: нова ракета, нові супутники, нові відкриття!"),
    ("positive", "Ще один ідеальний запуск! Космічна програма просто фантастична."),
    ("positive", "Космос — моя пристрасть. Кожна ракета, що злітає, робить мене щасливим."),
    ("positive", "Чудовий день: успішний запуск і красиві фото Землі з орбіти. Люблю космос!"),
    ("positive", "Приготував борщ за бабусиним рецептом — вийшло дуже смачно."),
    ("negative", "Знову дощ і холод, уже набридла ця сіра осінь."),
    ("negative", "Фільм виявився нудним і затягнутим, шкода витраченого часу."),
    ("positive", "Пробіг сьогодні десять кілометрів, почуваюся чудово."),
    ("neutral", "Дочитав детектив: фінал передбачуваний, але загалом непогано."),
    ("negative", "Оновлення телефону зламало половину застосунків, дуже дратує."),
    ("positive", "Слухаю новий альбом улюбленого гурту — просто шедевр."),
    ("negative", "Автобус запізнився на сорок хвилин, ледве встиг на роботу."),
    ("positive", "Повернувся з Карпат, гори восени неймовірно гарні."),
    ("neutral", "Звичайний робочий день, багато зустрічей і листів."),
    ("neutral", "Нова гра має чудову графіку, але сюжет слабкий."),
    ("neutral", "Посадив на балконі помідори, подивимось, що виросте."),
]


def load_dataset(content_path):
    rows = list(SCENARIO)
    if content_path:
        with open(content_path, encoding="utf-8") as f:
            content = json.load(f)
        for topic in content["topics"]:
            for item in topic["items"]:
                rows.append((item["mood"], item["text"]))
    return rows


def normalize(label):
    """'Very Negative', 'NEGATIVE', 'negative' → 'negative' тощо."""
    label = label.lower()
    if "neg" in label:
        return "negative"
    if "pos" in label:
        return "positive"
    return "neutral"


def classifier_predict(model_name, texts):
    from transformers import pipeline
    pipe = pipeline("sentiment-analysis", model=model_name, tokenizer=model_name)
    started = time.time()
    raw = [pipe(t[:512])[0] for t in texts]
    seconds = (time.time() - started) / len(texts)
    return [normalize(r["label"]) for r in raw], seconds


def anchors_predict(texts):
    from sentence_transformers import SentenceTransformer
    model = SentenceTransformer(EMBEDDING_MODEL)
    prototypes = {}
    for label, phrases in ANCHORS.items():
        vectors = model.encode(phrases, normalize_embeddings=True)
        mean = vectors.mean(axis=0)
        prototypes[label] = mean / (sum(x * x for x in mean) ** 0.5)
    started = time.time()
    vectors = model.encode(texts, normalize_embeddings=True)
    seconds = (time.time() - started) / len(texts)
    predictions = []
    for v in vectors:
        predictions.append(max(prototypes, key=lambda label: float(v @ prototypes[label])))
    return predictions, seconds


def metrics(gold, predicted):
    confusion = {g: {p: 0 for p in LABELS} for g in LABELS}
    for g, p in zip(gold, predicted):
        confusion[g][p] += 1
    f1s, recall = [], {}
    for label in LABELS:
        tp = confusion[label][label]
        fn = sum(confusion[label].values()) - tp
        fp = sum(confusion[g][label] for g in LABELS) - tp
        precision = tp / (tp + fp) if tp + fp else 0.0
        rec = tp / (tp + fn) if tp + fn else 0.0
        recall[label] = round(rec, 3)
        f1s.append(2 * precision * rec / (precision + rec) if precision + rec else 0.0)
    accuracy = sum(g == p for g, p in zip(gold, predicted)) / len(gold)
    return {"accuracy": round(accuracy, 3), "macroF1": round(sum(f1s) / len(f1s), 3),
            "recall": recall, "confusion": confusion}


def main():
    parser = argparse.ArgumentParser(description="Порівняння моделей тональності на українських текстах")
    parser.add_argument("--content", help="seed/content.json із розміченими сюжетами")
    parser.add_argument("--out", default="sentiment-eval.json")
    args = parser.parse_args()

    rows = load_dataset(args.content)
    gold = [g for g, _ in rows]
    texts = [t for _, t in rows]
    print(f"Текстів: {len(rows)} (позитив {gold.count('positive')}, нейтраль {gold.count('neutral')}, "
          f"негатив {gold.count('negative')})\n")

    candidates = [(name, lambda n=name: classifier_predict(n, texts)) for name in MODELS]
    candidates.append((f"anchors:{EMBEDDING_MODEL}", lambda: anchors_predict(texts)))

    results = []
    print(f"{'модель':58} {'точн.':>6} {'F1':>6} {'neg':>5} {'neu':>5} {'pos':>5} {'мс/текст':>9}")
    for name, predict in candidates:
        try:
            predicted, seconds = predict()
        except Exception as e:   # модель не завантажилась — решту все одно порівнюємо
            print(f"{name:58} помилка: {e}")
            continue
        m = metrics(gold, predicted)
        m.update(model=name, msPerText=round(seconds * 1000, 1),
                 errors=[{"text": t, "gold": g, "predicted": p}
                         for t, g, p in zip(texts, gold, predicted) if g != p])
        results.append(m)
        r = m["recall"]
        print(f"{name:58} {m['accuracy']:6.2f} {m['macroF1']:6.2f} {r['negative']:5.2f} "
              f"{r['neutral']:5.2f} {r['positive']:5.2f} {m['msPerText']:9.1f}")

    with open(args.out, "w", encoding="utf-8") as f:
        json.dump({"texts": len(rows), "results": results}, f, ensure_ascii=False, indent=2)
    print(f"\nДеталі (матриці помилок, хибні приклади): {args.out}")


if __name__ == "__main__":
    main()
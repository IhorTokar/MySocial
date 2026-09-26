import logging

from fastapi import FastAPI
from pydantic import BaseModel
from sentence_transformers import SentenceTransformer
from transformers import pipeline

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("nlp-service")

EMBEDDING_MODEL_NAME = "paraphrase-multilingual-mpnet-base-v2"
SENTIMENT_MODEL_NAME = "lxyuan/distilbert-base-multilingual-cased-sentiments-student"

app = FastAPI(title="Social Network NLP Service")

logger.info("Loading embedding model %s...", EMBEDDING_MODEL_NAME)
embedding_model = SentenceTransformer(EMBEDDING_MODEL_NAME)
logger.info("Embedding model loaded. Dimension: %d", embedding_model.get_sentence_embedding_dimension())

logger.info("Loading sentiment model %s...", SENTIMENT_MODEL_NAME)
sentiment_pipeline = pipeline("sentiment-analysis", model=SENTIMENT_MODEL_NAME)
logger.info("Sentiment model loaded.")

LABEL_TO_SCORE = {"positive": 1.0, "neutral": 0.0, "negative": -1.0}


class EmbedRequest(BaseModel):
    text: str


class EmbedResponse(BaseModel):
    vector: list[float]
    dimension: int


class SentimentResponse(BaseModel):
    label: str
    confidence: float
    sentiment: float  # нормалізований бал у [-1, 1]: label_score * confidence


@app.get("/health")
def health():
    return {
        "status": "ok",
        "embedding_model": EMBEDDING_MODEL_NAME,
        "sentiment_model": SENTIMENT_MODEL_NAME,
        "dimension": embedding_model.get_sentence_embedding_dimension(),
    }


@app.post("/embed", response_model=EmbedResponse)
def embed(request: EmbedRequest):
    vector = embedding_model.encode(request.text, normalize_embeddings=True)
    return EmbedResponse(vector=vector.tolist(), dimension=len(vector))


@app.post("/sentiment", response_model=SentimentResponse)
def sentiment(request: EmbedRequest):
    text = request.text[:512]  # обмеження довжини входу для моделі
    result = sentiment_pipeline(text)[0]
    label = result["label"].lower()
    confidence = float(result["score"])
    base_score = LABEL_TO_SCORE.get(label, 0.0)
    return SentimentResponse(label=label, confidence=confidence, sentiment=base_score * confidence)
"""SupportAI AI service - runs 100% locally, no API key needed.

Retrieval : TF-IDF + cosine similarity (scikit-learn)
Answers   : extractive by default; optional local LLM via Ollama (USE_OLLAMA=true)
Sentiment : VADER
Classifier: TF-IDF + Logistic Regression trained from tickets_seed.csv + priority rules
"""
import csv, io, json, os, re
from pathlib import Path
from typing import List

import requests
from fastapi import FastAPI, File, Form, UploadFile
from pydantic import BaseModel
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics.pairwise import cosine_similarity
from sklearn.pipeline import make_pipeline
from vaderSentiment.vaderSentiment import SentimentIntensityAnalyzer

BASE = Path(__file__).parent
DATA_FILE = Path(os.getenv("KB_FILE", BASE / "data" / "kb.json"))
MIN_SCORE = float(os.getenv("MIN_SCORE", "0.12"))          # below this -> escalate to a human
USE_OLLAMA = os.getenv("USE_OLLAMA", "false").lower() == "true"
OLLAMA_URL = os.getenv("OLLAMA_URL", "http://localhost:11434/api/generate")
OLLAMA_MODEL = os.getenv("OLLAMA_MODEL", "llama3.2:3b")

app = FastAPI(title="SupportAI AI Service")
sia = SentimentIntensityAnalyzer()

# ---------------------------------------------------------------- knowledge base
chunks: List[dict] = []
_vec = None
_mat = None
INJECTION = re.compile(
    r"(ignore (all |any )?(previous|above|prior) instructions|disregard .*instructions|reveal .*system prompt|you are now)", re.I)


def load():
    global chunks
    if DATA_FILE.exists():
        chunks = json.loads(DATA_FILE.read_text(encoding="utf-8"))
    rebuild()


def save():
    DATA_FILE.parent.mkdir(parents=True, exist_ok=True)
    DATA_FILE.write_text(json.dumps(chunks), encoding="utf-8")


def rebuild():
    global _vec, _mat
    _vec = _mat = None
    if not chunks:
        return
    try:
        _vec = TfidfVectorizer(stop_words="english", ngram_range=(1, 2), sublinear_tf=True)
        _mat = _vec.fit_transform([c["text"] for c in chunks])
    except ValueError:
        _vec = _mat = None


def sanitize(text: str) -> str:
    """Drop lines that look like prompt-injection attempts inside uploaded documents."""
    return "\n".join(l for l in text.splitlines() if not INJECTION.search(l))


def split_text(text: str, size: int = 600) -> List[str]:
    paras = [p.strip() for p in re.split(r"\n\s*\n", text) if p.strip()]
    out, cur = [], ""
    for p in paras:
        # keep a short heading together with the paragraph below it; otherwise one topic per chunk
        if cur and len(cur) < 60 and len(cur) + len(p) <= size:
            cur = (cur + "\n\n" + p).strip()
        else:
            if cur:
                out.append(cur)
            while len(p) > size:
                out.append(p[:size])
                p = p[size - 80:]
            cur = p
    if cur:
        out.append(cur)
    return out


def ingest_text(org_id: int, source: str, text: str) -> int:
    global chunks
    chunks = [c for c in chunks if not (c["org_id"] == org_id and c["source"] == source)]  # re-index
    parts = split_text(sanitize(text))
    chunks += [{"org_id": org_id, "source": source, "text": p} for p in parts]
    rebuild()
    save()
    return len(parts)


def retrieve(org_id: int, question: str, k: int = 3):
    if _vec is None:
        return []
    sims = cosine_similarity(_vec.transform([question]), _mat)[0]
    order = sims.argsort()[::-1]
    return [(float(sims[i]), chunks[i]) for i in order if chunks[i]["org_id"] == org_id][:k]


def best_sentences(question: str, text: str, n: int = 2) -> str:
    sents = [s.strip() for s in re.split(r"(?<=[.!?])\s+|\n", text) if len(s.strip()) > 15]
    if len(sents) <= n or _vec is None:
        return text[:500]
    sc = cosine_similarity(_vec.transform([question]), _vec.transform(sents))[0]
    top = sorted(sc.argsort()[::-1][:n])
    return " ".join(sents[i] for i in top)


def extract_text(filename: str, data: bytes) -> str:
    ext = filename.lower().rsplit(".", 1)[-1]
    if ext == "pdf":
        from pypdf import PdfReader
        return "\n\n".join((p.extract_text() or "") for p in PdfReader(io.BytesIO(data)).pages)
    if ext == "docx":
        import docx
        return "\n\n".join(p.text for p in docx.Document(io.BytesIO(data)).paragraphs)
    return data.decode("utf-8", errors="ignore")  # txt, md, csv


def ask_ollama(question: str, context: str = "") -> str:
    if context:
        prompt = (
            "You are a helpful customer support assistant. "
            "Use the supplied company knowledge base for company-specific facts. "
            "Do not invent company policies or claim actions were completed. "
            "If the knowledge base does not contain a company-specific answer, "
            "say that you cannot verify it and recommend contacting support. "
            "Keep the answer clear and concise.\n\n"
            f"Knowledge base:\n{context}\n\nQuestion: {question}\nAnswer:"
        )
    else:
        prompt = (
            "You are a helpful general-purpose AI assistant. "
            "Answer general questions accurately and clearly. "
            "Do not invent company-specific policies, order details, refund status, "
            "or account information. If asked about these without company data, "
            "explain that you cannot verify them and recommend contacting support.\n\n"
            f"Question: {question}\nAnswer:"
        )

    r = requests.post(
        OLLAMA_URL,
        json={"model": OLLAMA_MODEL, "prompt": prompt, "stream": False},
        timeout=120
    )
    r.raise_for_status()
    return r.json()["response"].strip()


# ---------------------------------------------------------------- sentiment
def sentiment_of(text: str):
    c = sia.polarity_scores(text)["compound"]
    label = ("Very negative" if c <= -0.6 else "Negative" if c <= -0.05
             else "Positive" if c >= 0.05 else "Neutral")
    return label, c


# ---------------------------------------------------------------- classifier
DEPARTMENTS = {"Payment": "Billing", "Refunds": "Billing", "Account": "Account Support",
               "Orders": "Logistics", "Technical": "Technical Support"}
classifier = None


def train_classifier():
    global classifier
    rows = list(csv.DictReader((BASE / "tickets_seed.csv").open(encoding="utf-8")))
    classifier = make_pipeline(TfidfVectorizer(ngram_range=(1, 2), sublinear_tf=True),
                               LogisticRegression(max_iter=1000, C=5))
    classifier.fit([r["text"] for r in rows], [r["category"] for r in rows])


CRITICAL = re.compile(r"fraud|hacked|unauthori[sz]ed|breach|stolen|emergency|urgent|without (my )?permission", re.I)
HIGH = re.compile(r"twice|double|deducted|debited|not received|failed|locked|cannot login|can't login|unable|lost|damaged", re.I)
LOW = re.compile(r"\bhow (do|to|can)\b|\bwhat is\b|information|wondering|policy", re.I)
LEVELS = ["Low", "Medium", "High", "Critical"]


def predict_priority(text: str, sentiment: str) -> str:
    if CRITICAL.search(text):
        lvl = 3
    elif HIGH.search(text):
        lvl = 2
    elif LOW.search(text):
        lvl = 0
    else:
        lvl = 1
    if sentiment == "Very negative":
        lvl = min(3, lvl + 1)
    return LEVELS[lvl]


# ---------------------------------------------------------------- API models
class IngestReq(BaseModel):
    org_id: int = 1
    source: str
    text: str


class DeleteReq(BaseModel):
    org_id: int = 1
    source: str


class ChatReq(BaseModel):
    org_id: int = 1
    question: str


class TextReq(BaseModel):
    text: str


class SummReq(BaseModel):
    messages: List[str]


class SuggestReq(BaseModel):
    org_id: int = 1
    conversation: str


@app.on_event("startup")
def startup():
    load()
    train_classifier()


@app.get("/api/ai/health")
def health():
    return {"status": "ok", "chunks": len(chunks), "llm": "ollama" if USE_OLLAMA else "extractive"}


@app.post("/api/ai/ingest")
def ingest(req: IngestReq):
    return {"chunks_indexed": ingest_text(req.org_id, req.source, req.text)}


@app.post("/api/ai/ingest-file")
async def ingest_file(org_id: int = Form(1), file: UploadFile = File(...)):
    text = extract_text(file.filename, await file.read())
    return {"source": file.filename, "chunks_indexed": ingest_text(org_id, file.filename, text)}


@app.post("/api/ai/delete-source")
def delete_source(req: DeleteReq):
    global chunks
    chunks = [c for c in chunks if not (c["org_id"] == req.org_id and c["source"] == req.source)]
    rebuild()
    save()
    return {"ok": True}


HANDOFF = re.compile(r"\b(human|agent|representative|real person|talk to someone|speak to someone)\b", re.I)
UNABLE = "I'm unable to verify this from our knowledge base. I will connect you with a support agent."


@app.post("/api/ai/chat")
def chat(req: ChatReq):
    if HANDOFF.search(req.question):
        return {"answer": "Sure, I will connect you with a support agent.", "escalate": True, "sources": [], "confidence": 1.0}
    label, _ = sentiment_of(req.question)
    hits = retrieve(req.org_id, req.question)
    if not hits or hits[0][0] < MIN_SCORE:
        if USE_OLLAMA:
            try:
                answer = ask_ollama(req.question)
                if answer:
                    return {"answer": answer, "escalate": label == "Very negative",
                            "sources": [], "confidence": 0.0}
            except Exception as e:
                print(f"[Ollama] General chat failed: {e}")
        return {"answer": UNABLE, "escalate": True, "sources": [], "confidence": 0.0}

    answer = None
    if USE_OLLAMA:
        try:
            answer = ask_ollama(req.question, "\n---\n".join(h[1]["text"] for h in hits))
        except Exception as e:
            print(f"[Ollama] Knowledge base chat failed: {e}")

    if not answer:
        answer = best_sentences(req.question, hits[0][1]["text"])

    return {"answer": answer, "escalate": label == "Very negative",
            "sources": sorted({h[1]["source"] for h in hits}), "confidence": round(hits[0][0], 3)}


@app.post("/api/ai/sentiment")
def sentiment(req: TextReq):
    label, score = sentiment_of(req.text)
    return {"sentiment": label, "score": score}


@app.post("/api/ai/classify")
def classify(req: TextReq):
    proba = classifier.predict_proba([req.text])[0]
    i = int(proba.argmax())
    category, conf = str(classifier.classes_[i]), float(proba[i])
    label, _ = sentiment_of(req.text)
    priority = predict_priority(req.text, label)
    uncertain = conf < 0.40
    return {"category": "General" if uncertain else category,
            "department": "Support" if uncertain else DEPARTMENTS[category],
            "priority": priority, "sentiment": label, "confidence": round(conf, 3),
            "requiresHuman": uncertain or priority in ("High", "Critical")}


@app.post("/api/ai/summarize")
def summarize(req: SummReq):
    msgs = [m.strip() for m in req.messages if m.strip()]
    if not msgs:
        return {"summary": ""}
    s = f"Customer issue: {msgs[0][:250]}"
    if len(msgs) > 1:
        s += f" | Latest message: {msgs[-1][:200]} | {len(msgs)} customer messages in total."
    return {"summary": s}


@app.post("/api/ai/suggest-reply")
def suggest_reply(req: SuggestReq):
    last = req.conversation.strip().splitlines()[-1] if req.conversation.strip() else ""
    label, _ = sentiment_of(req.conversation[-600:])
    opening = ("We sincerely apologize for the inconvenience and understand your frustration. "
               if label in ("Negative", "Very negative") else "Thank you for contacting us. ")
    hits = retrieve(req.org_id, req.conversation[-600:], 1)
    body, sources = "", []
    if hits and hits[0][0] >= MIN_SCORE:
        body = best_sentences(last or req.conversation, hits[0][1]["text"]) + " "
        sources = [hits[0][1]["source"]]
    closing = "We are looking into this and will update you as soon as possible."
    return {"reply": opening + body + closing, "sources": sources, "sentiment": label}


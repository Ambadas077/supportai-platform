# SupportAI - AI Customer Support & Ticket Platform (no paid API key)

React + Spring Boot + MySQL/H2 + Python FastAPI. The AI runs **locally**: TF-IDF retrieval (RAG), VADER sentiment,
scikit-learn ticket classifier, optional Ollama LLM. No OpenAI/Gemini key anywhere.

## What is included (MVP)
- JWT auth, roles: CUSTOMER / AGENT / ADMIN, BCrypt passwords, role-protected APIs
- AI chat grounded in uploaded documents, shows sources, escalates when it cannot answer
- Knowledge base: upload PDF/DOCX/TXT/MD/CSV or write articles; chunked + indexed; prompt-injection line filter
- Tickets: create, AI category / priority / sentiment / confidence, auto-assign to least-loaded agent,
  status workflow, comments, internal notes, re-open, chat-to-ticket handoff with AI summary
- Agent inbox with "AI suggest reply" (never auto-sent)
- Admin: dashboard charts (AI resolution rate, escalation, category/priority/sentiment, agent performance),
  agent management, knowledge base management

## Not included yet (honest list)
WebSocket live chat, email/SMS notifications, SLA timers, multi-tenant orgs, attachments, audit logs, multilingual,
automated tests. Chat uses single-question retrieval (no multi-turn memory). Add these phase by phase.

## Run option A - no Docker (simplest)
Requires Java 17+, Maven, Node 18+, Python 3.10+. Backend uses a file H2 database by default (no MySQL needed).

```bash
# 1) AI service
cd ai-service
python -m venv venv && source venv/bin/activate      # Windows: venv\Scripts\activate
pip install -r requirements.txt
uvicorn main:app --port 8000

# 2) Backend (new terminal)
cd backend && mvn spring-boot:run                    # http://localhost:8080

# 3) Frontend (new terminal)
cd frontend && npm install && npm run dev            # http://localhost:5173
```

## Run option B - Docker (MySQL included)
```bash
cp .env.example .env
docker compose up --build        # app at http://localhost:3000
```

## Try it
1. Log in as admin -> Knowledge Base -> upload `docs/sample-knowledge-base.txt`.
2. Log in as customer -> AI Chat -> "How long does a refund take?" (answered with source).
   Ask something unrelated or click "Talk to a human" -> ticket is created, classified and assigned.
3. Log in as agent -> open the ticket -> "AI suggest reply" -> edit -> send.

Demo accounts (seeded on first start - change before deploying):
- admin@supportai.com / Admin@123
- agent@supportai.com / Agent@123 , agent2@supportai.com / Agent@123
- customer@supportai.com / Customer@123

## Optional: real local LLM answers
Install Ollama, `ollama pull llama3.2:3b`, then start the AI service with `USE_OLLAMA=true`.
Without it, answers are extractive (best matching sentences from your documents) - fast and never invents facts.

## Tuning the AI
- `MIN_SCORE` (default 0.12) in the AI service: higher = escalates more, lower = answers more.
- Add more rows to `ai-service/tickets_seed.csv` to improve ticket classification (restart to retrain).
- Upgrade path: swap TF-IDF for sentence-transformers + Qdrant in `ai-service/main.py` (retrieve/ingest functions).

## Main APIs
POST /api/auth/register|login, GET /api/auth/me - POST /api/chat/message, POST /api/chat/{id}/handoff -
POST/GET /api/tickets, GET /api/tickets/{id}, PATCH .../status|assign, POST .../comments|suggest-reply -
POST /api/knowledge/upload|articles, GET/DELETE /api/knowledge/documents - GET /api/admin/dashboard, GET/POST /api/admin/agents
AI service (port 8000): /api/ai/chat, classify, sentiment, summarize, suggest-reply, ingest, ingest-file, health. Docs: http://localhost:8000/docs

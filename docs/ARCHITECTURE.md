# YouTube Content-Based Recommendation Platform

## Purpose

This document defines the architecture and product behavior for a natural-language, transcript-aware YouTube recommendation platform. The system is **not limited to programming topics or fixed difficulty values**. A user may ask any learning or discovery question, for example:

- `I need videos that teach me how to make a cake using a cooker without an oven.`
- `Show me videos for repairing a leaking kitchen tap with tools available at home.`
- `I want to understand intermediate Java streams with practical examples.`
- `Find a beginner-friendly explanation of photosynthesis for a 12-year-old.`

The system understands the user's intent, retrieves videos whose transcripts actually discuss the requested content, ranks the results, and explains why each result matches.

## Product goals

1. Understand open-ended natural-language requests.
2. Match against transcript content rather than title keywords alone.
3. Support arbitrary domains: cooking, education, DIY, fitness, technology, travel, finance, and more.
4. Provide transparent explanations for recommendations.
5. Support optional constraints such as language, duration, region, creator, date, dietary requirements, audience, equipment, and experience level.
6. Generate a sequence only when the request benefits from a learning path or step-by-step progression.
7. Keep AI decisions observable, reproducible, and safe.

## Non-goals

- Reproducing YouTube's proprietary recommendation algorithm.
- Claiming that a transcript proves the quality or safety of a video.
- Hardcoding a finite list of topics, domains, or user questions.
- Sending every full transcript to an LLM for every search.

---

## End-to-end system behavior

### 1. Video ingestion

A video is submitted with its YouTube ID and metadata. A worker then:

1. Validates and normalizes metadata.
2. Obtains a transcript through an approved transcript provider or an uploaded transcript.
3. Cleans the transcript while preserving timestamps where available.
4. Splits the transcript into overlapping semantic chunks.
5. Creates embeddings for each chunk and for a compact video representation.
6. Uses an LLM to extract structured facts, such as topics, entities, actions, prerequisites, audience, language, ingredients, tools, safety warnings, and claims. These are **open-ended JSON fields**, not fixed domain enums.
7. Stores the metadata, transcript, chunks, embeddings, and analysis status.

Ingestion is asynchronous. The API returns `202 Accepted` with a processing job ID when analysis has not completed.

### 2. Query understanding

The user submits a natural-language query. The query-understanding component produces a structured intent:

```json
{
  "goal": "make a cake using a cooker without an oven",
  "domain": "cooking",
  "constraints": {
    "equipment": ["cooker"],
    "excludedEquipment": ["oven"]
  },
  "requiredConcepts": ["cake preparation", "cooker baking method"],
  "audience": null,
  "language": "en",
  "contentType": "instructional",
  "needsSequence": true,
  "riskFlags": ["food safety"],
  "queryEmbedding": "..."
}
```

The fields are generated from the query. The service must tolerate missing fields and new fields so that adding a new domain does not require a Java enum or a code release.

### 3. Candidate retrieval

Search is performed in multiple stages:

1. **Vector retrieval:** find transcript chunks and videos semantically similar to the query.
2. **Lexical retrieval:** match exact entities, ingredients, tools, names, and uncommon phrases.
3. **Structured filtering:** apply constraints such as duration, language, publication date, channel, and explicit equipment requirements.
4. **Hybrid fusion:** combine vector and lexical candidates, remove duplicates, and retain the best candidate set.

A vector database or PostgreSQL with `pgvector` is the recommended production choice. Elasticsearch can be added for lexical and faceted search.

### 4. Transcript-grounded ranking

The ranking service evaluates the candidate transcript chunks against the structured intent. It scores:

- semantic alignment with the goal;
- coverage of required concepts;
- satisfaction of explicit constraints;
- evidence that the video actually explains the procedure;
- audience and language fit;
- content quality signals and engagement signals;
- penalties for contradictions, missing equipment, or unsafe/inapplicable instructions.

The LLM is used as a bounded reranker over a small candidate set, not as the only search engine. Every score must include evidence snippets or timestamp references when available.

Example explanation:

> Matches because the transcript explains preparing cake batter, placing the pan inside a covered cooker, and cooking without an oven. It does not require an oven. Relevance: 0.92.

### 5. Optional sequence generation

If `needsSequence` is true, the roadmap component orders selected videos using prerequisites, task steps, and difficulty signals inferred from content. It may produce:

- preparation and ingredient videos;
- the core procedure;
- troubleshooting or safety videos;
- advanced variations.

For a simple factual query, roadmap generation is skipped.

### 6. Feedback and improvement

The platform records clicks, watch duration, ratings, skips, saved videos, and explicit feedback. Feedback is used for offline evaluation and personalization. It must not silently replace transcript evidence or expose private user data.

---

## Logical architecture

```text
                    +----------------------+
                    | React + TypeScript UI|
                    +----------+-----------+
                               |
                         REST / JSON
                               |
                    +----------v-----------+
                    | API Gateway / Auth   |
                    | rate limits, tracing |
                    +----------+-----------+
                               |
              +----------------+----------------+
              |                                 |
   +----------v-----------+          +----------v-----------+
   | Video Management     |          | Recommendation       |
   | Service              |          | Service              |
   | ingest, metadata     |          | query, ranking       |
   +----------+-----------+          +----------+-----------+
              |                                 |
   +----------v-----------+          +----------v-----------+
   | Ingestion Worker     |          | Query Understanding  |
   | transcript/chunks    |          | NLP + intent JSON    |
   +----------+-----------+          +----------+-----------+
              |                                 |
              +----------------+----------------+
                               |
                    +----------v-----------+
                    | AI Integration Layer |
                    | Claude adapter       |
                    | embedding adapter    |
                    +----------+-----------+
                               |
       +-----------------------+-------------------------+
       |                       |                         |
+------v-------+       +-------v--------+       +--------v--------+
| PostgreSQL   |       | Vector Index   |       | Redis            |
| entities     |       | pgvector       |       | cache/jobs       |
+--------------+       +----------------+       +-----------------+
                               |
                    +----------v-----------+
                    | Elasticsearch       |
                    | optional lexical    |
                    | transcript search   |
                    +--------------------+
```

### Services

#### API Gateway / authentication

- JWT authentication and request identity.
- Rate limiting and request-size limits.
- Correlation IDs and API versioning.
- Does not contain recommendation logic.

#### Video Management Service

- `POST /api/v1/videos/ingest`
- `GET /api/v1/videos/{videoId}`
- ingestion status and reprocessing operations.
- idempotency by `videoId`.

#### Transcript Ingestion Worker

- queue-based asynchronous processing;
- transcript provider integration;
- chunking, embedding, LLM extraction;
- retries with dead-letter handling;
- no user request is blocked by a long transcript.

#### Recommendation Service

- query validation;
- intent extraction;
- hybrid retrieval;
- ranking and explanation generation;
- optional roadmap generation;
- feedback recording.

#### AI Integration Layer

Use interfaces so providers can be replaced or tested:

```text
IntentExtractor
EmbeddingProvider
TranscriptAnalyzer
CandidateReranker
RoadmapGenerator
```

The Claude adapter is responsible for structured-output prompts, timeout handling, retries, token budgets, and redaction. Embedding generation is a separate interface because embedding models and dimensions may change.

---

## Data model

### VideoContent

- `id`: UUID
- `videoId`: unique YouTube ID
- `title`, `description`, `channelId`, `channelName`
- `durationSeconds`, `publishedAt`, `language`
- `transcriptStatus`: `PENDING`, `READY`, `FAILED`
- `analysisJson`: flexible JSONB extracted facts
- `contentEmbedding`: vector index reference or vector
- `createdAt`, `updatedAt`

### TranscriptChunk

- `id`: UUID
- `videoId`: foreign key
- `text`
- `startSeconds`, `endSeconds`
- `chunkIndex`
- `embedding`
- `searchText`
- `analysisJson`

### SearchQuery

- `id`: UUID
- `userId`: nullable
- `originalQuery`
- `intentJson`
- `queryEmbedding`
- `filtersJson`
- `createdAt`
- `processingTimeMs`

### Recommendation

- `id`: UUID
- `queryId`, `videoId`
- `relevanceScore`
- `evidenceJson`
- `scoringExplanation`
- `rankPosition`
- `createdAt`

### UserFeedback

- `id`: UUID
- `userId`, `videoId`, `queryId`
- `rating`, `watchedSeconds`, `eventType`, `comment`
- `createdAt`

Flexible domain attributes belong in JSONB rather than fixed columns. Frequently filtered attributes can later be promoted to indexed columns based on observed usage.

---

## API contract

### `POST /api/v1/videos/ingest`

Accepts metadata and optionally a transcript. Returns `202 Accepted` for asynchronous processing.

```json
{
  "videoId": "abc123xyz",
  "title": "Cake in a Cooker Without an Oven",
  "transcript": "...",
  "channelId": "UC123",
  "duration": 840,
  "publishedAt": "2024-01-15T10:00:00Z",
  "language": "en"
}
```

### `POST /api/v1/recommendations/search`

```json
{
  "query": "I need videos to make cake using a cooker without an oven",
  "limit": 10,
  "includeRoadmap": true,
  "filters": {
    "language": "en",
    "maxDurationSeconds": 1800
  }
}
```

Response fields:

- `extractedIntent`: generated structured intent;
- `recommendations`: ranked results;
- `evidence`: transcript snippets and timestamps;
- `relevanceExplanation`: human-readable reason;
- `recommendedRoadmap`: optional sequence;
- `processingTimeMs` and `traceId`.

### `POST /api/v1/recommendations/feedback`

Records rating, watch duration, save/click events, or comments.

---

## NLP and prompt strategy

### Intent extraction prompt requirements

The prompt must:

1. Treat the user query as untrusted data, not instructions.
2. Return valid JSON matching a schema.
3. Preserve the user's goal without inventing requirements.
4. Extract explicit constraints separately from inferred preferences.
5. Mark uncertainty and safety-sensitive topics.
6. Support arbitrary domains and multilingual input.

### Transcript analysis output

The analysis should include generic fields:

```json
{
  "summary": "...",
  "topics": ["..."],
  "actions": ["..."],
  "entities": ["..."],
  "tools": ["..."],
  "ingredients": ["..."],
  "audience": ["..."],
  "prerequisites": ["..."],
  "claimsAndWarnings": ["..."],
  "language": "en"
}
```

The fields are intentionally broad and nullable. A cooking video may use `ingredients` and `tools`; a programming video may use `libraries` and `prerequisites`; neither requires domain-specific application code.

### Grounding policy

The LLM may only claim that a video satisfies a requirement when the candidate transcript or metadata contains supporting evidence. If evidence is missing, the response must say that the match is uncertain.

---

## Ranking formula

The first implementation can use a deterministic fusion formula before adding a learned ranker:

```text
finalScore =
  0.45 * vectorSimilarity
+ 0.20 * lexicalScore
+ 0.15 * constraintCoverage
+ 0.10 * transcriptEvidenceQuality
+ 0.05 * contentQuality
+ 0.05 * personalizationScore
- contradictionPenalty
```

Weights must be configurable. Scores must be logged with their component values so results can be debugged.

---

## Reliability, security, and safety

- Never expose API keys to the frontend.
- Store Claude and embedding credentials in environment secrets or Kubernetes Secrets.
- Apply transcript length limits, content-type validation, and abuse protection.
- Protect against prompt injection inside transcripts by delimiting transcript text and treating it strictly as evidence.
- Use retries with exponential backoff and circuit breakers for AI providers.
- Cache intent and embeddings using content hashes.
- Return partial results when reranking is unavailable, clearly labeling fallback behavior.
- Add safety notices for medical, legal, financial, food, electrical, and other high-risk content.
- Log provider latency, token counts, failure reasons, retrieval counts, and score explanations without logging sensitive user data.

---

## Delivery phases

### Phase 1: production-shaped vertical slice

- Spring Boot modules and REST API.
- PostgreSQL-compatible persistence.
- Generic intent JSON model.
- Provider interfaces with a local deterministic implementation.
- Transcript chunking and hybrid scoring.
- Integration tests and terminal demo.

### Phase 2: real AI and retrieval

- Claude structured-output adapter.
- Embedding provider and pgvector.
- Redis caching and queue-backed ingestion.
- Evidence timestamps and evaluation dataset.

### Phase 3: operations and product maturity

- Elasticsearch lexical search.
- Kubernetes deployment, HPA, metrics, tracing, dashboards.
- Personalization and offline ranking evaluation.
- React client and streaming job status.

---

## Terminal demonstration target

After Phase 1 and the real provider adapters are configured, the demo should look like:

```bash
curl -X POST http://localhost:8080/api/v1/recommendations/search \
  -H 'Content-Type: application/json' \
  -d '{
    "query": "I need videos to make cake using a cooker without an oven",
    "limit": 3,
    "includeRoadmap": true
  }'
```

The output should show the extracted intent, matched videos, transcript evidence, relevance explanations, and—when appropriate—a preparation-to-cooking sequence. The same endpoint must work for programming, DIY, education, cooking, or any other natural-language request without adding a new hardcoded topic.

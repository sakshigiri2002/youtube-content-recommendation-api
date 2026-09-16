# YouTube Content-Based Recommendation API

This repository implements the architecture described in [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

The key design decision is that this is a **generic natural-language recommendation system**, not a Java-only or difficulty-level-only search API. Users can ask for videos about cooking, DIY, education, programming, fitness, or any other topic. The service extracts intent from the query, searches transcript chunks using semantic and lexical retrieval, applies constraints, and produces transcript-grounded explanations.

## Current implementation plan

1. Document and validate the end-to-end architecture.
2. Build a production-shaped Spring Boot vertical slice.
3. Add provider interfaces for Claude, embeddings, transcript retrieval, and ranking.
4. Add PostgreSQL/pgvector, Redis, asynchronous ingestion, and real Claude integration.
5. Add the React client, Kubernetes deployment, observability, and evaluation tooling.

The initial local implementation will use deterministic provider adapters so the API can be tested without an external API key. Those adapters will be replaced by the real Claude and embedding providers through configuration, without changing the REST contract.

## Proposed modules

```text
src/main/java/com/openai/youtube/
├── controller/       # REST API
├── service/          # orchestration and business rules
├── ai/               # intent, transcript, embedding, ranking interfaces
├── retrieval/        # lexical/vector candidate retrieval
├── model/            # JPA entities and flexible JSON analysis
├── dto/              # API request/response contracts
├── repository/       # persistence ports
└── config/            # provider and infrastructure configuration
```

## Run locally

The code is being implemented in phases. Once the first vertical slice is committed:

```bash
./mvnw clean test
./mvnw spring-boot:run
```

Then try an arbitrary natural-language request:

```bash
curl -X POST http://localhost:8080/api/v1/recommendations/search \
  -H 'Content-Type: application/json' \
  -d '{
    "query": "I need videos to make cake using a cooker without an oven",
    "limit": 5,
    "includeRoadmap": true
  }'
```

See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for data flow, API contracts, NLP behavior, ranking, security, scaling, and the terminal demo target.

## Matching engine note

The local MVP no longer uses raw substring matching as its primary behavior. `SemanticTextMatcher` builds normalized concept profiles for the prompt and transcript, supports common equivalent phrases (for example `fix`/`repair`, `pressure cooker`/`cooker`, and `dessert`/`cake`), handles negative constraints such as `without an oven`, requires transcript evidence, and returns evidence sentences.

This is a deterministic local NLP fallback, not a neural embedding model. The matcher is isolated behind a service boundary so it can later be replaced with real embeddings, vector retrieval, and an LLM reranker without changing the REST API.

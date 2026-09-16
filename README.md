# YouTube Content-Based Recommendation API

An offline-first Spring Boot application that accepts natural-language prompts and recommends videos by matching the prompt against video titles, descriptions, and transcripts.

The current MVP uses a JSON file as its local transcript database. It requires no API key, database server, vector database, or cloud account.

## Requirements

Install the following locally:

- **Java 17 or newer**: verify with `java -version`
- **Gradle 8 or newer**: verify with `gradle -version`
- Git, if cloning the repository

Gradle downloads project dependencies from Maven Central during the first build, so internet access is required for the initial dependency download.

### Optional: generate the Gradle Wrapper

The repository is Gradle-based. If `gradlew` and `gradlew.bat` have not yet been generated, run this once from the project root:

```bash
gradle wrapper --gradle-version 8.10
```

After that, use `./gradlew` on macOS/Linux or `gradlew.bat` on Windows. The wrapper is recommended because it uses the project’s declared Gradle version consistently.

## Clone and run

```bash
git clone https://github.com/sakshigiri2002/youtube-content-recommendation-api.git
cd youtube-content-recommendation-api

gradle clean test
gradle bootRun
```

If the Gradle Wrapper has been generated:

```bash
./gradlew clean test
./gradlew bootRun
```

On Windows:

```bat
gradlew.bat clean test
gradlew.bat bootRun
```

The API starts on `http://localhost:8080`.

## Interactive terminal prompt

Start the prompt mode:

```bash
./run.sh prompt
```

or directly with Gradle:

```bash
gradle bootRun --args='--app.cli-enabled=true'
```

Enter a natural-language request:

```text
Prompt> I need videos to make cake using a cooker without an oven
```

The application prints the recommended video name, video ID, score, matching terms, and a transcript-grounded reason. Try other domains:

```text
show me videos to fix a leaking kitchen tap with basic tools
I want to learn Java streams and functional programming
```

Type `exit` to stop.

## REST API

Start the server:

```bash
gradle bootRun
```

Health check:

```bash
curl http://localhost:8080/health
```

List videos loaded from the local JSON catalog:

```bash
curl http://localhost:8080/api/v1/videos
```

Search recommendations:

```bash
curl -s -X POST http://localhost:8080/api/v1/recommendations/search \
  -H 'Content-Type: application/json' \
  -d '{
    "query": "I need videos to make cake using a cooker without an oven",
    "limit": 3,
    "includeRoadmap": false
  }'
```

The response contains:

- the original query;
- extracted search terms;
- recommended video names and IDs;
- relevance scores;
- matching evidence;
- transcript-grounded explanations;
- processing time.

Get one video:

```bash
curl http://localhost:8080/api/v1/videos/cake-cooker-101
```

## Local data

The starter catalog is stored in:

```text
src/main/resources/data/videos.json
```

Each record contains a video ID, title, channel, description, language, duration, and transcript. Add records to this file and restart the application; no schema migration is needed for the local MVP.

## How the MVP works

1. Loads video records from `videos.json` at startup.
2. Normalizes the user’s prompt.
3. Removes common stop words and extracts meaningful terms.
4. Searches the title, description, and transcript.
5. Scores title matches, transcript matches, and query-term coverage.
6. Applies a contradiction penalty for requests such as `without an oven` when a video explicitly requires an oven.
7. Returns the highest-scoring results with evidence terms.

This is a deterministic local baseline. It is not yet using Claude or embeddings, so it remains fast, free, reproducible, and easy to run.

## Build and test commands

```bash
gradle test
# Run the application
gradle bootRun
# Create an executable Spring Boot JAR
gradle bootJar
# Full clean build
gradle clean build
```

The packaged JAR is created at:

```text
build/libs/youtube-content-recommendation-api.jar
```

Run it with:

```bash
java -jar build/libs/youtube-content-recommendation-api.jar
```

## Repository files

```text
build.gradle
settings.gradle
run.sh
Dockerfile
README.md
docs/ARCHITECTURE.md
docs/LOCAL_DEMO.md
src/main/resources/application.yml
src/main/resources/data/videos.json
src/main/java/.../Application.java
src/main/java/.../controller/
src/main/java/.../service/
src/main/java/.../model/
src/main/java/.../dto/
src/main/java/.../cli/
src/test/java/...
```

## Docker (optional)

Docker is not required for local development, but a Dockerfile is included:

```bash
docker build -t youtube-content-recommendation-api .
docker run --rm -p 8080:8080 youtube-content-recommendation-api
```

## Planned scale-up

The external API contract will remain stable while the internals evolve:

- PostgreSQL for video metadata and feedback;
- transcript chunks with timestamps;
- pgvector for semantic embeddings;
- Elasticsearch for lexical search and filters;
- Claude structured-output intent extraction and transcript analysis;
- Redis caching;
- asynchronous ingestion workers and queues;
- authentication, metrics, tracing, Kubernetes, and evaluation datasets.

See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for the complete target architecture and [`docs/LOCAL_DEMO.md`](docs/LOCAL_DEMO.md) for the local walkthrough.

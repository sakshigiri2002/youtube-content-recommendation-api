# Local Demo

This MVP uses Gradle, Spring Boot, and a JSON transcript catalog.

## Prerequisites

- Java 17+
- Gradle 8+
- Internet access for the first dependency download

Generate the Gradle Wrapper once if it is not present:

```bash
gradle wrapper --gradle-version 8.10
```

## Run tests

```bash
gradle clean test
```

## Run the API

```bash
gradle bootRun
```

Then test:

```bash
curl http://localhost:8080/health
```

## Run the interactive prompt

```bash
./run.sh prompt
```

Enter:

```text
I need videos to make cake using a cooker without an oven
```

The expected top result is:

```text
Eggless Cake in a Pressure Cooker Without an Oven
```

That result comes from transcript evidence in `src/main/resources/data/videos.json`. The oven-only video is penalized because the query excludes an oven.

## Search through JSON

```bash
curl -s -X POST http://localhost:8080/api/v1/recommendations/search \
  -H 'Content-Type: application/json' \
  -d '{"query":"show videos to fix a leaking kitchen tap with basic tools","limit":3}'
```

## Add local videos

Add another JSON object to `src/main/resources/data/videos.json`, restart the application, and search for terms appearing in its transcript. This lets us validate the recommendation flow locally before adding external NLP and vector search.

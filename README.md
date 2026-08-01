# Document Search Platform

A document search service built with Java 21, Spring Boot 4, MongoDB and OpenSearch.

MongoDB is the **source of truth**. OpenSearch is the **search index**.

> Built incrementally over 10 days as a learning project. Each day ends with a
> compiling project, passing tests, and a commit.

---

## Status

**Day 1 of 10 complete** — infrastructure and application skeleton.

### Completed

- Git repository initialised
- Maven + Spring Boot 4.1.0 project on Java 21
- Maven wrapper (`./mvnw`) for reproducible builds
- Docker Compose stack: MongoDB + OpenSearch (+ optional Dashboards)
- `application.yml` with actuator exposure and log levels
- `GET /api/v1/health` endpoint
- OpenAPI 3.1 spec and Swagger UI via springdoc
- 7 passing tests (context load, MockMvc slice, generated-spec assertions)

### Not yet built

CRUD, persistence, indexing and search all arrive on later days. See
[Roadmap](#roadmap).

---

## Architecture

```text
          REST Controller          com.docsearch.api
                 │
                 ▼
        Application Service        com.docsearch.application
                 │
                 ▼
          Repository Layer         com.docsearch.infrastructure
             │        │
             ▼        ▼
         MongoDB   OpenSearch
      (source of    (search
        truth)       index)
```

Packages are created as they are needed rather than up front, so only `api`
exists today. The layering above is the target shape.

### Current layout

```text
.
├── docker-compose.yml
├── pom.xml
├── mvnw / mvnw.cmd / .mvn/
├── .env.example
├── .gitignore
├── README.md
└── src
    ├── main
    │   ├── java/com/docsearch
    │   │   ├── DocumentSearchApplication.java
    │   │   ├── api
    │   │   │   ├── HealthController.java
    │   │   │   └── dto/HealthResponse.java
    │   │   └── config/OpenApiConfig.java
    │   └── resources/application.yml
    └── test/java/com/docsearch
        ├── DocumentSearchApplicationTests.java
        └── api
            ├── HealthControllerTest.java
            └── OpenApiDocumentationTest.java
```

---

## Getting started

### Prerequisites

- Java 21
- Docker + Docker Compose v2

### 1. Configure

```bash
cp .env.example .env
```

Every variable has an inline default, so this step is optional for local work.

### 2. Start the datastores

```bash
docker compose up -d
```

Wait for both containers to report healthy:

```bash
docker compose ps
```

### 3. Run the application

```bash
./mvnw spring-boot:run
```

### 4. Verify

```bash
curl localhost:8080/api/v1/health
curl localhost:8080/actuator/health
curl localhost:9200/_cluster/health
```

Expected from the first call:

```json
{
  "status": "UP",
  "application": "document-search-platform",
  "timestamp": "2026-08-01T08:10:20.397797310Z"
}
```

Then open the interactive API docs:

**<http://localhost:8080/swagger-ui.html>**

### 5. Build and test

```bash
./mvnw clean verify
```

### 6. Shut down

```bash
docker compose down        # keep data
docker compose down -v     # also drop volumes
```

---

## APIs

| Method | Path                 | Description                                  |
|--------|----------------------|----------------------------------------------|
| `GET`  | `/api/v1/health`     | Application liveness + name + timestamp      |
| `GET`  | `/actuator/health`   | Spring Boot health, including dependencies   |
| `GET`  | `/actuator/info`     | Build and environment info                   |
| `GET`  | `/swagger-ui.html`   | Interactive API documentation (Swagger UI)   |
| `GET`  | `/v3/api-docs`       | OpenAPI 3.1 document (JSON)                  |
| `GET`  | `/v3/api-docs.yaml`  | OpenAPI 3.1 document (YAML)                  |

`/api/v1/health` answers "is the app serving requests?". `/actuator/health`
additionally reports on infrastructure the app depends on — as datastores are
wired in on later days, they will surface there automatically.

### API documentation

The OpenAPI document is **generated from the controllers at runtime**, so paths,
schemas and response shapes cannot drift from the code. Only the document-level
title and description are hand-written, in `config/OpenApiConfig.java`.

```bash
curl localhost:8080/v3/api-docs | jq .          # machine-readable spec
open http://localhost:8080/swagger-ui.html      # browse and try requests
```

Notes on how it is set up:

- **`info.version` is `v1`, not the build version.** It describes the API
  contract; the jar can be rebuilt many times without the contract changing.
- **The server URL is relative (`/`)**, so the spec stays correct behind a
  reverse proxy or on a different host.
- **Actuator endpoints are excluded** (`springdoc.show-actuator: false`) — they
  are operational, not part of the public API contract.
- **Turn it off in production** with `SPRINGDOC_ENABLED=false`, which disables
  both the spec endpoint and the UI. Day 10 wires this into the production
  profile; leaving Swagger UI publicly reachable advertises your whole API
  surface.
- `OpenApiDocumentationTest` asserts the generated document — title, version, the
  health path, its response `$ref`, and the actuator exclusion — so a bad
  springdoc upgrade fails the build instead of silently producing an empty spec.

**Version note:** this uses **springdoc 3.x**, which is the Spring Boot 4 /
Spring Framework 7 line. The much more widely documented springdoc **2.x**
targets Boot 3 and will not work here — most tutorials you find will show 2.x
coordinates.

---

## Docker services

| Service                 | Image                                     | Port   | Profile  | Purpose                    |
|-------------------------|-------------------------------------------|--------|----------|----------------------------|
| `mongodb`               | `mongo:7.0.39`                            | `27017`| default  | Source of truth            |
| `opensearch`            | `opensearchproject/opensearch:3.7.0`      | `9200` | default  | Search index               |
| `opensearch-dashboards` | `opensearchproject/opensearch-dashboards:3.7.0` | `5601` | `tools` | Dev Tools console for DSL |

Dashboards is opt-in so the default stack stays lean:

```bash
docker compose --profile tools up -d
# then open http://localhost:5601 → Management → Dev Tools
```

It becomes genuinely useful on Days 3, 6 and 7 for hand-testing mappings,
analyzers and Query DSL.

### Version pinning notes

**MongoDB is pinned to 7.0.x deliberately.** MongoDB 8.x bundles a TCMalloc that
violates the rseq ABI enforced by Linux kernel 6.19+, segfaulting `mongod`
roughly 30–60 seconds after startup
([SERVER-121912](https://jira.mongodb.org/browse/SERVER-121912)).

- `8.0.28` / `8.3.x` — detect the kernel and refuse to start
- `8.2.x` — predates the guard, starts and then crashes silently (worst case)
- `7.0.x` — older TCMalloc, unaffected

Revisit once an 8.x release ships the TCMalloc upgrade
([SERVER-125742](https://jira.mongodb.org/browse/SERVER-125742)). If you are on a
kernel older than 6.19, `mongo:8.0.28` is also fine.

**OpenSearch security is disabled** (`DISABLE_SECURITY_PLUGIN=true`), exposing
plain HTTP on 9200 with no auth. This is a local-development convenience so the
focus stays on mappings and queries. Day 10 introduces the secured, AWS-ready
configuration.

---

## Configuration

Settings resolve from environment variables with defaults baked in, so the app
runs with zero configuration locally and is container/AWS friendly.

| Variable               | Default            | Used by     |
|------------------------|--------------------|-------------|
| `SERVER_PORT`          | `8080`             | Spring Boot |
| `MONGO_PORT`           | `27017`            | Compose     |
| `MONGO_ROOT_USERNAME`  | `root`             | Compose     |
| `MONGO_ROOT_PASSWORD`  | `change-me`        | Compose     |
| `MONGO_DATABASE`       | `documents`        | Compose     |
| `OPENSEARCH_PORT`      | `9200`             | Compose     |
| `OPENSEARCH_JAVA_OPTS` | `-Xms512m -Xmx512m`| Compose     |
| `DASHBOARDS_PORT`      | `5601`             | Compose     |
| `SPRINGDOC_ENABLED`    | `true`             | Spring Boot |

---

## Tech stack

| Concern      | Choice                          |
|--------------|---------------------------------|
| Language     | Java 21                         |
| Framework    | Spring Boot 4.1.0 (Spring 7)    |
| Build        | Maven (wrapper included)        |
| Datastore    | MongoDB 7.0                     |
| Search       | OpenSearch 3.7                  |
| API docs     | springdoc-openapi 3.0.3 / OAS 3.1 |
| Testing      | JUnit 5, Mockito, MockMvc       |
| Runtime      | Docker + Docker Compose         |

> **Note on Spring Boot 4:** test slices were split out of
> `spring-boot-starter-test` into per-technology modules. `@WebMvcTest` now comes
> from `spring-boot-starter-webmvc-test`, which is declared separately in
> `pom.xml`.

---

## Roadmap

| Day | Focus                                                          | Status |
|-----|----------------------------------------------------------------|--------|
| 1   | Git, Spring Boot skeleton, Docker Compose, health endpoint       | Done   |
| 2   | MongoDB document model, OpenSearch index, basic indexing, sample data | Next |
| 3   | Mappings, analyzers, tokenizers, text vs keyword                 | —      |
| 4   | MongoDB persistence, CRUD APIs, validation, exception handling, unit tests | — |
| 5   | OpenSearch integration, auto-indexing, update/delete sync        | —      |
| 6   | Query DSL: match, bool, filter, range, pagination, sorting       | —      |
| 7   | Aggregations, relevance, boosting, search optimisation           | —      |
| 8   | Shards, replicas, cluster APIs, reindex endpoint                 | —      |
| 9   | Integration tests, performance, logging, Docker cleanup          | —      |
| 10  | AWS-ready + production config, docs, cleanup                     | —      |

### Next up — Day 2

- Define the MongoDB document model
- Create the OpenSearch index
- Basic indexing via OpenSearch APIs
- Load sample data

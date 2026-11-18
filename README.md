# DocuMind Platform

Document intelligence platform built on the starter repository: Angular frontend +
Spring Boot backend. Documents are uploaded, stored, processed through a job
pipeline, prepared for structured data extraction from templates, then submitted
to a human validation workflow.

No AI/OCR/RAG integration yet: extraction fields are prepared empty from the
selected template and are filled in by users. The processing pipeline is designed
so a future extraction worker can plug in without changing the domain model.

AI token and cost monitoring is already wired end to end: an append-only
`ai_usage_events` table feeds `GET /api/usage/summary`, and the Angular
"AI usage & cost" screen only displays those real aggregates. Until an AI worker
records usage, the screen shows an explicit empty state instead of sample data.

## Stack

- Frontend: Angular 17 standalone components, TypeScript, CSS
- Backend: Java 21, Spring Boot 3.3 (Web, Security, Data JPA, Validation, Actuator)
- Database: PostgreSQL 16 (Docker) / H2 file database (dev profile), Flyway migrations
- Storage: local filesystem (dev) / MinIO S3-compatible (Docker)
- Queue: in-process executor (dev) / RabbitMQ with DLQ (Docker)
- API docs: springdoc OpenAPI + Swagger UI

## Repository layout

```
backend/   Spring Boot API (com.documind)
frontend/  Angular application (Docker image: nginx, proxies /api to the backend)
docker-compose.yml   PostgreSQL + MinIO + RabbitMQ + backend + frontend
.env.example         Environment template for Docker
```

## Backend architecture (`com.documind`)

| Package      | Responsibility |
|--------------|----------------|
| `config`     | Web/CORS configuration |
| `security`   | JWT auth, refresh token rotation, security rules, current user provider |
| `common`     | Base entity, pagination, centralized error handling |
| `user`       | Profile and password endpoints |
| `workspace`  | Multi-tenant workspace, memberships and roles |
| `document`   | Document aggregate, versions, search/filter/sort/pagination, preview/download |
| `storage`    | `DocumentStorage` abstraction: LOCAL or MINIO (internal keys never exposed) |
| `processing` | Processing jobs, progress, retries, worker, local or RabbitMQ dispatch |
| `template`   | Document templates and typed fields (required/optional) |
| `extraction` | Per-document field values, edition, format validation, field validation |
| `validation` | Human validation workflow: submit, approve, reject, comments, history |
| `audit`      | Audit trail (auth, uploads, updates, validation decisions...) |
| `dashboard`  | Real aggregate statistics consumed by the dashboard UI |
| `usage`      | Append-only AI usage events, read-only token/cost aggregates |

### Security model

- Stateless JWT access tokens (15 min) + rotating refresh tokens (7 days, SHA-256 hashed in DB)
- BCrypt password hashing
- Roles: `SUPER_ADMIN`, `ADMIN`, `MANAGER`, `VALIDATOR`, `USER` (method-level `@PreAuthorize`)
- Strict tenant isolation: every query is scoped by the caller's workspace.
  Requests target a workspace with the `X-Workspace-Id` header; `SUPER_ADMIN` can
  operate across workspaces.
- Upload validation: PDF/PNG/JPG magic bytes + extension, size limit (25 MB/file,
  120 MB/request), sanitized file names, UUID storage keys.

### Processing pipeline

1. Upload creates a `Document` and a `ProcessingJob` in `QUEUED` state.
2. The dispatcher publishes the job to the local executor or RabbitMQ queue
   (profile `docker`), consumer = `ProcessingWorker`.
3. Progress (0-100), timestamps and error message are persisted; failures are
   retried up to `max-attempts` (3) before `FAILED`. A sweeper re-dispatches
   stale jobs.
4. On completion, template fields are prepared (empty values, `PENDING`) for the
   document so users can fill them in the extraction screen.

### Validation workflow

- Submit a completed document: all required template fields must have a value.
- Approve or reject a `PENDING` validation; rejection requires a comment.
- Anyone with the right permission can comment; history and author are exposed.
- Uploading a new document version resets the document validation status.

## API overview

All endpoints are under `/api`, JSON, authenticated with `Authorization: Bearer <token>`
except `/api/auth/register`, `/api/auth/login`, `/api/auth/refresh`, `/api/auth/logout`,
`/api/health`, `/actuator/health` and Swagger assets.

| Area        | Endpoints |
|-------------|-----------|
| Auth        | `POST /api/auth/register`, `/login`, `/refresh`, `/logout`, `GET /api/auth/me` |
| Users       | `PUT /api/users/me`, `POST /api/users/me/password` |
| Workspaces  | `GET/POST /api/workspaces`, `GET/PUT /api/workspaces/current`, members CRUD |
| Documents   | `GET/POST /api/documents`, `GET/PUT/DELETE /api/documents/{id}`, `/download`, `/preview`, `/versions`, `/processing` |
| Processing  | `GET /api/processing/jobs`, `GET /api/processing/jobs/{id}`, `POST /api/processing/jobs/{id}/retry` |
| Templates   | `GET/POST /api/templates`, `GET/PUT/DELETE /api/templates/{id}` |
| Extraction  | `GET /api/documents/{id}/fields`, `POST .../fields/from-template`, `POST .../fields/validate`, `PUT /api/fields/{id}`, `POST /api/fields/{id}/validate` |
| Validation  | `GET /api/validations`, `GET /api/validations/{id}`, comments, `approve`, `reject`, `GET/POST /api/documents/{id}/validations` |
| Dashboard   | `GET /api/dashboard/stats` |
| Audit       | `GET /api/audit-logs`, `GET /api/audit-logs/recent` |
| AI usage    | `GET /api/usage/summary?days=7` (read-only, ADMIN/MANAGER, `days` clamped to 1-90) |
| Health      | `GET /api/health`, `/actuator/health` |

Swagger UI: `http://localhost:8080/swagger-ui.html` (OpenAPI JSON at `/v3/api-docs`).

## Run locally (dev profile)

Requirements: Java 21+, Maven 3.9+, Node.js 20+.

```bash
cd backend
mvn spring-boot:run
```

- Database: H2 file database in `backend/data/` (`MODE=PostgreSQL`), Flyway
  migrations are applied automatically, Hibernate validates the schema.
- Storage: local filesystem in `backend/data/storage`.
- Processing: local executor, no external infrastructure required.
- The JWT secret falls back to a development value; set `JWT_SECRET` to override it.

Run the tests:

```bash
cd backend
mvn test
```

The suite covers JWT/auth flow, upload validation, extraction rules, validation
workflow and a full integration lifecycle (upload → processing → fields →
validation → dashboard → preview).

Frontend:

```bash
cd frontend
npm install
npm start
```

The dev server runs on `http://localhost:4200` and calls the API at
`http://localhost:8080/api` (`src/environments/environment.ts`). Production
builds use the relative `/api` base URL, served same-origin by nginx.

### Frontend screens

All screens are standalone, lazy loaded and guarded by the JWT session:

| Route            | Purpose |
|------------------|---------|
| `/dashboard`     | Real workspace statistics, document status breakdown and recent activity |
| `/documents`     | Upload, search, filter, sort and paginate documents |
| `/documents/:id` | Metadata, PDF.js preview, versions, extraction fields, validation history |
| `/processing`    | Jobs list, status filter, progress polling and retry of failed jobs |
| `/templates`     | Templates and typed field edition (name, type, required, order) |
| `/validation`    | Pending queue, document review, approve/reject with comments |
| `/usage`         | AI token and cost monitoring (read-only, ADMIN/MANAGER) |
| `/chat`          | Placeholder that states the AI layer is not enabled (no fake answers) |
| `/settings`      | Profile, password, workspace settings, members and roles |

Registering creates a workspace where the new account is the administrator.
The "AI usage & cost" screen shows total/prompt/completion tokens, recorded
cost, a daily series, a per-model breakdown and recent events, over 7, 30 or
90 days. Amounts are displayed exactly as recorded; nothing is estimated.

## Run with Docker

1. Create the environment file and set real secrets:

```bash
copy .env.example .env    # Windows
```

2. Start the stack:

```bash
docker compose up -d --build
```

Services:

| Service   | URL / Port |
|-----------|------------|
| Frontend  | http://localhost:4200 (nginx, proxies `/api` to `backend:8080`) |
| Backend   | http://localhost:8080 (Swagger at `/swagger-ui.html`) |
| PostgreSQL| `localhost:5432` |
| MinIO API | http://localhost:9000 (console http://localhost:9001) |
| RabbitMQ  | `localhost:5672` (management UI http://localhost:15672) |

The `docker` profile uses PostgreSQL + Flyway, MinIO storage and RabbitMQ with a
dead-letter queue. Logs are emitted as JSON (logstash encoder).

The frontend image is a multi-stage build (`node:20-alpine` → `nginx:1.27-alpine`).
nginx serves the SPA with history fallback, proxies `/api` same-origin (no CORS in
production) and accepts uploads up to 30 MB.

MinIO runs from the Bitnami legacy image (`bitnamilegacy/minio`) because the
upstream `minio/minio` images are no longer published on Docker Hub or quay.io.
The S3 API (port 9000) and console (port 9001) are unchanged; override the image
with `MINIO_IMAGE` if you host your own.

## Configuration

Key environment variables (see `.env.example` and `application*.yml`):

| Variable | Purpose |
|----------|---------|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | PostgreSQL connection (docker profile) |
| `MINIO_ENDPOINT`, `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY`, `MINIO_BUCKET` | Object storage |
| `MINIO_IMAGE` | MinIO server image (`bitnamilegacy/minio:latest` by default) |
| `RABBITMQ_HOST/PORT/USERNAME/PASSWORD` | Message broker |
| `JWT_SECRET` | HMAC signing secret (32+ chars), required outside dev/tests |
| `DOCUMIND_CORS_ORIGINS` | Comma-separated allowed origins |
| `BACKEND_PORT`, `FRONTEND_PORT` | Host ports published by Docker Compose (8080 / 4200) |
| `DOCUMIND_STORAGE_PROVIDER`, `DOCUMIND_STORAGE_LOCATION` | `LOCAL` or `MINIO`, local directory |
| `DOCUMIND_PROCESSING_MODE` | `local` or `rabbit` |

Profiles: `dev` (default), `docker`, `test`.

## Not implemented yet

- AI extraction and AI chat / RAG: the extraction screen only stores human input
  and the chat screen is a placeholder (no fake answers)
- AI provider calls: `ai_usage_events` stays empty until an AI worker records
  real usage, so the usage screen shows its empty state in this build
- Malware scanning and encryption at rest before production use

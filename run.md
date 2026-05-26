# IssueFlow — Run Guide

## Prerequisites

| Tool | Version | Notes |
| ---- | ------- | ----- |
| Java | 21 | `java -version` must report 21.x |
| Docker | 24+ | Engine must be running; used by both `compose.yml` and Testcontainers |
| Maven | —  | Use the included `./mvnw` / `.\mvnw.cmd`; no local install needed |

---

## 1 · Start the database

```bash
docker compose up -d
```

`compose.yml` starts a PostgreSQL 16 container on port **5432** with:

| Setting | Value |
| ------- | ----- |
| database | `issueflow` |
| username | `issueflow` |
| password | `issueflow` |

Flyway runs automatically on first boot and creates all tables plus a seed ADMIN user.

**Verify the DB is ready:**

```bash
docker compose ps                           # service db should be "healthy" / "running"
docker exec -it $(docker compose ps -q db) \
  psql -U issueflow -d issueflow -c "\dt"   # must list ~10 tables
```

---

## 2 · Build

**Skip tests (fastest):**
```bash
./mvnw clean package -DskipTests
```

**Full build + all tests** (requires Docker for Testcontainers IT classes):
```bash
./mvnw clean verify
```

The executable jar is at `target/issueflow-0.0.1-SNAPSHOT.jar`.

---

## 3 · Run

**Dev mode (live classpath, Spring Boot DevTools):**
```bash
./mvnw spring-boot:run
```

**Jar mode:**
```bash
java -jar target/issueflow-0.0.1-SNAPSHOT.jar
```

**Windows PowerShell:**
```powershell
.\mvnw.cmd spring-boot:run
```

The server starts on **http://localhost:8080**.  
Swagger UI: **http://localhost:8080/swagger-ui/index.html**  
Health check: **http://localhost:8080/actuator/health**

---

## 4 · Default credentials

Flyway seed (`V2__seed_admin.sql` + `V3__fix_admin_password_hash.sql`):

| username | password |
| -------- | -------- |
| `admin`  | `admin123` |

All endpoints require a Bearer JWT except `POST /auth/login`, `GET /actuator/health`, and the Swagger UI paths.

---

## 5 · curl recipes

All examples assume `bash`. Set the token once and reuse it:

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' \
  | sed 's/.*"accessToken":"\([^"]*\)".*/\1/')

echo $TOKEN   # sanity-check: should be a JWT string
```

### Auth

```bash
# Login
curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'

# Who am I?
curl -s http://localhost:8080/auth/me \
  -H "Authorization: Bearer $TOKEN"

# Logout (revokes the current token)
curl -s -X POST http://localhost:8080/auth/logout \
  -H "Authorization: Bearer $TOKEN"
```

### Users (ADMIN-only writes)

```bash
# Create a developer
curl -s -X POST http://localhost:8080/users \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","email":"alice@example.com","fullName":"Alice Dev","password":"secret123","role":"DEVELOPER"}'

# List all users
curl -s http://localhost:8080/users \
  -H "Authorization: Bearer $TOKEN"
```

### Projects

```bash
# Create project (ownerId = admin's id = 1 after fresh seed)
curl -s -X POST http://localhost:8080/projects \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"Alpha","description":"First project","ownerId":1}'

# List projects
curl -s http://localhost:8080/projects \
  -H "Authorization: Bearer $TOKEN"

# Update project
curl -s -X PATCH http://localhost:8080/projects/1 \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"Alpha v2"}'

# Developer workload
curl -s http://localhost:8080/projects/1/workload \
  -H "Authorization: Bearer $TOKEN"
```

### Tickets

```bash
# Create ticket (assigneeId omitted → auto-assign to least-loaded developer)
curl -s -X POST http://localhost:8080/tickets \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"title":"Fix login bug","description":"500 on bad password","status":"TODO","priority":"HIGH","type":"BUG","projectId":1}'

# Partial update — change priority (also clears isOverdue flag)
curl -s -X PATCH http://localhost:8080/tickets/1 \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"priority":"CRITICAL"}'

# Set a due date (ISO-8601 Instant)
curl -s -X PATCH http://localhost:8080/tickets/1 \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"dueDate":"2026-06-01T00:00:00Z"}'
```

### Comments (with @mention)

```bash
# Create comment — @alice gets a mention record if she exists
curl -s -X POST http://localhost:8080/tickets/1/comments \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"content":"Hey @alice, can you take a look?"}'

# List comments on a ticket
curl -s http://localhost:8080/tickets/1/comments \
  -H "Authorization: Bearer $TOKEN"

# Get mentions for user id=2
curl -s "http://localhost:8080/users/2/mentions?page=1&pageSize=20" \
  -H "Authorization: Bearer $TOKEN"
```

### Attachments

```bash
# Upload a file (must be ≤ 10 MB; allowed types: image/png, image/jpeg, application/pdf, text/plain)
curl -s -X POST http://localhost:8080/tickets/1/attachments \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@/path/to/screenshot.png"

# List attachments for a ticket
curl -s http://localhost:8080/tickets/1/attachments \
  -H "Authorization: Bearer $TOKEN"

# Download attachment id=1
curl -s http://localhost:8080/tickets/1/attachments/1/download \
  -H "Authorization: Bearer $TOKEN" \
  --output downloaded-file.png

# Delete attachment
curl -s -X DELETE http://localhost:8080/tickets/1/attachments/1 \
  -H "Authorization: Bearer $TOKEN"
```

### CSV export / import

```bash
# Export all tickets in project 1 as CSV
curl -s "http://localhost:8080/tickets/export?projectId=1" \
  -H "Authorization: Bearer $TOKEN" \
  --output tickets-project1.csv

# Import CSV into project 2 (columns: title,description,status,priority,type,assigneeId)
curl -s -X POST http://localhost:8080/tickets/import \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@tickets-project1.csv" \
  -F "projectId=2"
# Response: {"created":N,"failed":M,"errors":[...]}
```

### Audit log (ADMIN-only)

```bash
# All logs, newest first
curl -s "http://localhost:8080/audit-logs?page=1&pageSize=20" \
  -H "Authorization: Bearer $TOKEN"

# Filter by entity type and action
curl -s "http://localhost:8080/audit-logs?entityType=Ticket&action=AUTO_ESCALATE" \
  -H "Authorization: Bearer $TOKEN"

# Filter by time range (ISO-8601)
curl -s "http://localhost:8080/audit-logs?from=2026-05-01T00:00:00Z&to=2026-06-01T00:00:00Z" \
  -H "Authorization: Bearer $TOKEN"
```

---

## 6 · Environment variables

All variables have safe dev defaults; override on the command line or in a `.env` file loaded by your shell.

| Variable | Default | Purpose |
| -------- | ------- | ------- |
| `JWT_SECRET` | `dev-secret-change-in-production-must-be-at-least-256-bits-long!!` | HMAC-SHA256 signing key — **change in production** |
| `JWT_EXPIRATION_MINUTES` | `60` | Token TTL in minutes |
| `JWT_ISSUER` | `issueflow` | JWT `iss` claim |
| `STORAGE_PATH` | `./var/attachments` | Local filesystem root for uploaded files |
| `ESCALATION_CRON` | `0 */5 * * * *` | Spring cron for the auto-escalation scheduler (every 5 min) |

**Override example:**
```bash
JWT_SECRET=my-production-secret-at-least-32-chars \
ESCALATION_CRON="0 0 * * * *" \
./mvnw spring-boot:run
```

Or pass as JVM system properties:
```bash
java -jar target/issueflow-0.0.1-SNAPSHOT.jar \
  --issueflow.jwt.secret=my-secret \
  --issueflow.escalation.cron="0 0 * * * *" \
  --issueflow.storage.path=/data/attachments
```

---

## 7 · Tests

### Unit tests only (no Docker required)
```bash
./mvnw test -Dexclude="**/*IT.java,**/IssueFlowApplicationTests.java,**/RepositoryDataJpaTest.java,**/AuthControllerIntegrationTest.java"
```

### All tests including Testcontainers ITs (Docker must be running)
```bash
./mvnw test
```

> **Note:** Testcontainers IT classes (`*IT.java`, `IssueFlowApplicationTests`, `RepositoryDataJpaTest`, `AuthControllerIntegrationTest`) each spin up a private PostgreSQL container via Docker. They will fail with `Could not find a valid Docker environment` if Docker is not running.

### Run a single test class
```bash
./mvnw test -Dtest="TicketEscalationSchedulerTest"
./mvnw test -Dtest="CsvRoundTripIT"         # requires Docker
```

### Full verify (compile + unit + IT)
```bash
./mvnw clean verify
```

---

## 8 · Troubleshooting

### Port 5432 already in use
Another PostgreSQL instance is bound to 5432. Either stop it or change the published port in `compose.yml`:
```yaml
ports:
  - "5433:5432"
```
Then update `application.yaml` datasource URL to `jdbc:postgresql://localhost:5433/issueflow`.

### Docker not running
Starting `docker compose up -d` or any `*IT` test will print:
```
Could not find a valid Docker environment
```
Start Docker Desktop (or `sudo systemctl start docker` on Linux) before retrying.

### Stale Flyway migration (checksum mismatch)
If you edit a migration that was already applied to a local DB, Flyway will refuse to start:
```
FlywayException: Validate failed: Migration checksum mismatch for migration version 1
```
Options:
1. Wipe the container and restart: `docker compose down -v && docker compose up -d`  
2. Or in the DB directly: `DELETE FROM flyway_schema_history WHERE version = '1';` then restart the app.

### Application starts but returns 401 on every request
The JWT secret used to sign the token does not match the secret the running instance expects. Ensure `JWT_SECRET` (or `issueflow.jwt.secret`) is identical between the login request and all subsequent requests — especially when switching between `spring-boot:run` and `java -jar`.

### `./mvnw: Permission denied` (Linux/macOS)
```bash
chmod +x mvnw
```

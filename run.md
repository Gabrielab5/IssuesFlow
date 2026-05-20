# IssueFlow Runbook

## Prerequisites

- Java 21
- Docker Desktop
- Maven wrapper from this repository

## Start The Database

```bash
docker compose up -d
```

The application connects to the PostgreSQL service declared in `compose.yml`.

## Run The Application

```bash
./mvnw spring-boot:run
```

On Windows PowerShell:

```powershell
.\mvnw.cmd spring-boot:run
```

## Bootstrap Authentication

Flyway migration `V2__seed_admin.sql` creates the initial ADMIN account. Migration `V3__fix_admin_password_hash.sql` ensures the seeded password hash matches the documented bootstrap password:

- Username: `admin`
- Password: `admin123`

Use this account to obtain a JWT:

```bash
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
```

All endpoints require JWT authentication except `POST /auth/login`, `GET /actuator/health`, `/v3/api-docs/**`, and `/swagger-ui/**`.

## Users API Authorization

User write operations are ADMIN-only:

- `POST /users`
- `POST /users/update/{userId}`
- `DELETE /users/{userId}`

The seeded ADMIN user is the bootstrap account for creating and managing additional users. User read operations also require a valid JWT.

## Verify

Run service-level tests:

```bash
./mvnw "-Dtest=UserServiceTest" test
```

Run the Users controller integration test with Docker/Testcontainers available:

```bash
./mvnw "-Dtest=UserControllerIT" test
```

Run all tests:

```bash
./mvnw test
```

On Windows PowerShell:

```powershell
.\mvnw.cmd "-Dtest=UserServiceTest" test
.\mvnw.cmd "-Dtest=UserControllerIT" test
.\mvnw.cmd test
```

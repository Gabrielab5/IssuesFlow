# IssueFlow — Setup & Running Guide

Complete instructions for cloning, configuring, running, testing, and Dockerising the IssueFlow backend.

---

## Table of Contents

1. [Prerequisites](#1-prerequisites)
2. [Clone the Repository](#2-clone-the-repository)
3. [Quick Start — Local Development](#3-quick-start--local-development)
4. [Build & Run as a Docker Image](#4-build--run-as-a-docker-image)
5. [Push the Image to Docker Hub](#5-push-the-image-to-docker-hub)
6. [Run the Full Stack in Docker (App + DB)](#6-run-the-full-stack-in-docker-app--db)
7. [Configuration Reference](#7-configuration-reference)
8. [Running Tests](#8-running-tests)
9. [API Exploration — Swagger UI](#9-api-exploration--swagger-ui)
10. [Default Credentials](#10-default-credentials)
11. [Troubleshooting](#11-troubleshooting)

---

## 1. Prerequisites

Install the following before you begin.

| Tool | Minimum version | Download |
|------|----------------|---------|
| Git | any recent | https://git-scm.com |
| Java (JDK) | **21** | https://adoptium.net — choose "Temurin 21 LTS" |
| Docker Desktop | any recent | https://www.docker.com/products/docker-desktop |
| (Optional) Maven | 3.9+ | bundled via `mvnw` — no separate install needed |

> Docker Desktop must be **running** for both `docker compose` commands and Testcontainers-backed tests.

### Verify installations

**macOS / Linux**
```bash
java -version          # must show openjdk 21
docker --version
docker compose version
```

**Windows (PowerShell)**
```powershell
java -version          # must show openjdk 21
docker --version
docker compose version
```

---

## 2. Clone the Repository

**macOS / Linux**
```bash
git clone https://github.com/<your-org>/issueflow-java.git
cd issueflow-java
```

**Windows (PowerShell)**
```powershell
git clone https://github.com/<your-org>/issueflow-java.git
cd issueflow-java
```

> Replace `<your-org>` with the actual GitHub user or organisation name.

---

## 3. Quick Start — Local Development

This is the fastest way to run the app: database in Docker, application on the host JVM.

### 3.1 Start the database

The project ships with a `compose.yml` that starts a PostgreSQL 16 container preconfigured with the correct credentials.

**macOS / Linux**
```bash
docker compose up -d
```

**Windows (PowerShell)**
```powershell
docker compose up -d
```

The container listens on **localhost:5432** with:
- Database: `issueflow`
- Username: `issueflow`
- Password: `issueflow`

Verify it is up:
```bash
docker compose ps
```

### 3.2 Run the application

Flyway migrations (schema + seed data) run automatically on startup.

**macOS / Linux**
```bash
./mvnw spring-boot:run
```

**Windows (PowerShell)**
```powershell
.\mvnw.cmd spring-boot:run
```

The server starts on **http://localhost:8080**.

You should see a line similar to:
```
Started IssueFlowApplication in 3.2 seconds
```

### 3.3 Verify it is running

```bash
curl http://localhost:8080/actuator/health
# {"status":"UP"}
```

### 3.4 Stop everything

**macOS / Linux**
```bash
# Stop the app: Ctrl+C in the terminal where spring-boot:run is running

# Stop and remove the database container
docker compose down
```

**Windows (PowerShell)**
```powershell
# Stop the app: Ctrl+C in the terminal

# Stop and remove the database container
docker compose down
```

To also **delete all stored data** (wipes the volume):
```bash
docker compose down -v
```

---

## 4. Build & Run as a Docker Image

This section builds an image of the IssueFlow application itself so it can run in Docker alongside the database.

### 4.1 Package the JAR (skip if using multi-stage Dockerfile)

**macOS / Linux**
```bash
./mvnw clean package -DskipTests
```

**Windows (PowerShell)**
```powershell
.\mvnw.cmd clean package -DskipTests
```

This produces `target/issueflow-0.0.1-SNAPSHOT.jar`.

### 4.2 Build the Docker image

The project includes a multi-stage `Dockerfile` at the repository root. It compiles the code inside Docker, so you do **not** need a local Maven install — only the JDK and Docker.

**macOS / Linux**
```bash
docker build -t issueflow:latest .
```

**Windows (PowerShell)**
```powershell
docker build -t issueflow:latest .
```

Verify the image was created:
```bash
docker images issueflow
```

### 4.3 Run the application container (database must be running first)

Start the database if it is not already up:
```bash
docker compose up -d
```

Run the app container, connecting it to the same Docker network as the database:

**macOS / Linux**
```bash
docker run --rm \
  --network issueflow-java_default \
  -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/issueflow \
  -e SPRING_DATASOURCE_USERNAME=issueflow \
  -e SPRING_DATASOURCE_PASSWORD=issueflow \
  -e JWT_SECRET="my-super-secret-key-must-be-at-least-256-bits-long!!" \
  issueflow:latest
```

**Windows (PowerShell)**
```powershell
docker run --rm `
  --network issueflow-java_default `
  -p 8080:8080 `
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/issueflow `
  -e SPRING_DATASOURCE_USERNAME=issueflow `
  -e SPRING_DATASOURCE_PASSWORD=issueflow `
  -e JWT_SECRET="my-super-secret-key-must-be-at-least-256-bits-long!!" `
  issueflow:latest
```

> `issueflow-java_default` is the Docker network created by `compose.yml`. The database hostname inside that network is `db` (the service name in `compose.yml`).

---

## 5. Push the Image to Docker Hub

Use this to share the image or deploy it to a remote server.

### 5.1 Log in to Docker Hub

```bash
docker login
# Enter your Docker Hub username and password when prompted
```

### 5.2 Tag the image with your Docker Hub username

**macOS / Linux**
```bash
docker tag issueflow:latest <your-dockerhub-username>/issueflow:latest
```

**Windows (PowerShell)**
```powershell
docker tag issueflow:latest <your-dockerhub-username>/issueflow:latest
```

### 5.3 Push

**macOS / Linux**
```bash
docker push <your-dockerhub-username>/issueflow:latest
```

**Windows (PowerShell)**
```powershell
docker push <your-dockerhub-username>/issueflow:latest
```

### 5.4 Pull on another machine

```bash
docker pull <your-dockerhub-username>/issueflow:latest
```

---

## 6. Run the Full Stack in Docker (App + DB)

For a self-contained environment where **both** the database and the application run in Docker, create a `compose.override.yml` file at the project root:

```yaml
# compose.override.yml
services:
  app:
    image: issueflow:latest        # built in Section 4
    ports:
      - "8080:8080"
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://db:5432/issueflow
      SPRING_DATASOURCE_USERNAME: issueflow
      SPRING_DATASOURCE_PASSWORD: issueflow
      JWT_SECRET: "my-super-secret-key-must-be-at-least-256-bits-long!!"
    depends_on:
      - db
```

Then start everything with one command:

**macOS / Linux**
```bash
docker build -t issueflow:latest .        # build once
docker compose -f compose.yml -f compose.override.yml up -d
```

**Windows (PowerShell)**
```powershell
docker build -t issueflow:latest .
docker compose -f compose.yml -f compose.override.yml up -d
```

Check logs:
```bash
docker compose -f compose.yml -f compose.override.yml logs -f app
```

Stop everything:
```bash
docker compose -f compose.yml -f compose.override.yml down
```

---

## 7. Configuration Reference

All settings are in `src/main/resources/application.yaml`. Sensitive values are driven by environment variables with development fallbacks.

| Environment variable | Default (dev) | Description |
|---------------------|--------------|-------------|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/issueflow` | JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | `issueflow` | DB username |
| `SPRING_DATASOURCE_PASSWORD` | `issueflow` | DB password |
| `JWT_SECRET` | `dev-secret-change-in-production-must-be-at-least-256-bits-long!!` | HS256 signing key — **change in production** |
| `JWT_EXPIRATION_MINUTES` | `60` | Token lifetime in minutes |
| `JWT_ISSUER` | `issueflow` | Token issuer claim |

To override any variable when running locally:

**macOS / Linux**
```bash
JWT_SECRET="my-custom-secret" ./mvnw spring-boot:run
```

**Windows (PowerShell)**
```powershell
$env:JWT_SECRET = "my-custom-secret"
.\mvnw.cmd spring-boot:run
```

---

## 8. Running Tests

Tests are split into unit tests (Mockito, no infrastructure) and integration tests (Testcontainers — **Docker must be running**).

### Run all tests

**macOS / Linux**
```bash
./mvnw test
```

**Windows (PowerShell)**
```powershell
.\mvnw.cmd test
```

### Run only unit tests (no Docker required)

**macOS / Linux**
```bash
./mvnw test -Dtest="UserServiceTest,AuthServiceTest,AuditServiceTest,JwtServiceTest,TokenDenyListServiceTest,CustomUserDetailsServiceTest,GlobalExceptionHandlerTest,ProjectWorkloadTest,TicketServiceTest"
```

**Windows (PowerShell)**
```powershell
.\mvnw.cmd test "-Dtest=UserServiceTest,AuthServiceTest,AuditServiceTest,JwtServiceTest,TokenDenyListServiceTest,CustomUserDetailsServiceTest,GlobalExceptionHandlerTest,ProjectWorkloadTest,TicketServiceTest"
```

### Run only integration tests (Docker required)

**macOS / Linux**
```bash
./mvnw test -Dtest="AuthControllerIntegrationTest,UserControllerIT,ProjectControllerIT"
```

**Windows (PowerShell)**
```powershell
.\mvnw.cmd test "-Dtest=AuthControllerIntegrationTest,UserControllerIT,ProjectControllerIT"
```

### Run a single test class

**macOS / Linux**
```bash
./mvnw test -Dtest=ProjectControllerIT
```

**Windows (PowerShell)**
```powershell
.\mvnw.cmd test "-Dtest=ProjectControllerIT"
```

### Run a single test method

**macOS / Linux**
```bash
./mvnw test -Dtest="TicketServiceTest#autoAssignPicksDeveloperWithLowestOpenCount"
```

**Windows (PowerShell)**
```powershell
.\mvnw.cmd test "-Dtest=TicketServiceTest#autoAssignPicksDeveloperWithLowestOpenCount"
```

### Build without running tests

**macOS / Linux**
```bash
./mvnw clean package -DskipTests
```

**Windows (PowerShell)**
```powershell
.\mvnw.cmd clean package -DskipTests
```

---

## 9. API Exploration — Swagger UI

With the application running, open your browser:

```
http://localhost:8080/swagger-ui/index.html
```

The raw OpenAPI spec is also available at:

```
http://localhost:8080/v3/api-docs
```

### Authenticating in Swagger UI

1. Call `POST /auth/login` with the admin credentials (see Section 10).
2. Copy the `accessToken` from the response.
3. Click **Authorize** (the padlock icon at the top right of Swagger UI).
4. Enter `Bearer <paste-token-here>` and click **Authorize**.
5. All subsequent requests will include the JWT automatically.

---

## 10. Default Credentials

The seed migration (`V2__seed_admin.sql`) creates one admin user:

| Field | Value |
|-------|-------|
| Username | `admin` |
| Password | `admin123` |
| Role | `ADMIN` |
| Email | `admin@issueflow.local` |

Login example:

**macOS / Linux (curl)**
```bash
curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | python3 -m json.tool
```

**Windows (PowerShell)**
```powershell
$response = Invoke-RestMethod -Uri "http://localhost:8080/auth/login" `
  -Method Post `
  -ContentType "application/json" `
  -Body '{"username":"admin","password":"admin123"}'

$token = $response.accessToken
Write-Host "Token: $token"
```

Use the token in subsequent requests:

**macOS / Linux (curl)**
```bash
TOKEN="<paste-token-here>"

curl -s http://localhost:8080/projects \
  -H "Authorization: Bearer $TOKEN" | python3 -m json.tool
```

**Windows (PowerShell)**
```powershell
Invoke-RestMethod -Uri "http://localhost:8080/projects" `
  -Headers @{ Authorization = "Bearer $token" }
```

---

## 11. Troubleshooting

### Port 5432 is already in use

Another PostgreSQL instance is running locally.

```bash
# macOS — stop the system PostgreSQL service
brew services stop postgresql

# Windows — stop the service
Stop-Service -Name postgresql*
```

Or change the published port in `compose.yml`:
```yaml
ports:
  - "5433:5432"   # host:container
```
And update `application.yaml`:
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5433/issueflow
```

---

### Port 8080 is already in use

Change the server port in `application.yaml`:
```yaml
server:
  port: 9090
```
Or pass it at runtime:

**macOS / Linux**
```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments="--server.port=9090"
```

**Windows (PowerShell)**
```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--server.port=9090"
```

---

### Tests fail with "Could not find a valid Docker environment"

Docker Desktop is not running. Start Docker Desktop and re-run the tests.

On Linux, ensure your user is in the `docker` group:
```bash
sudo usermod -aG docker $USER
newgrp docker
```

---

### Application fails with "Flyway found an error" on startup

The schema is out of sync with the migrations. This can happen after switching branches.

```bash
# Drop the database and let Flyway recreate it from scratch
docker compose down -v   # -v removes the volume
docker compose up -d
./mvnw spring-boot:run   # Flyway re-runs all migrations
```

---

### `java: error: release version 21 not supported`

The JAVA_HOME points to a JDK older than 21.

**macOS / Linux**
```bash
java -version            # check current version
export JAVA_HOME=$(/usr/libexec/java_home -v 21)   # macOS only
./mvnw spring-boot:run
```

**Windows (PowerShell)**
```powershell
java -version

# Set JAVA_HOME to the JDK 21 installation path, e.g.:
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.x"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
.\mvnw.cmd spring-boot:run
```

---

### Docker build fails on `./mvnw` inside container (Windows line endings)

If you cloned on Windows, the `mvnw` script may have CRLF line endings.

```bash
# Fix line endings before building the image
sed -i 's/\r$//' mvnw

docker build -t issueflow:latest .
```

Or configure Git to preserve Unix line endings:
```bash
git config core.autocrlf input
git checkout mvnw
```

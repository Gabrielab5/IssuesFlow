# IssueFlow - Ticket Management Backend Platform

IssueFlow is a Spring Boot REST backend for project and ticket tracking. It models users, projects, tickets, comments, mentions, dependencies, attachments, audit logs, soft deletion, and JWT-based authentication.

## Tech Stack

- Java 21
- Spring Boot 3.3.x
- Spring Web
- Spring Security
- Spring Data JPA with Hibernate
- PostgreSQL
- Flyway database migrations
- JJWT for HS256 JWT handling
- Testcontainers, JUnit 5, Mockito, and Spring Security Test

## Architecture

IssueFlow follows a conventional layered backend architecture:

- Controllers expose REST endpoints and accept/return DTO records.
- Services contain business logic and own transactional boundaries.
- Repositories provide persistence through Spring Data JPA.
- JPA entities stay inside the application boundary and are not returned directly by controllers.
- Domain exceptions are mapped centrally to `ApiError` JSON by `GlobalExceptionHandler`.

## Core Domain

The system is designed around these capabilities:

- User management for ticket ownership, assignments, comments, and authorization roles.
- Project management as top-level containers for tickets.
- Ticket lifecycle management, including status, priority, type, due dates, assignment, and optimistic locking.
- Comment management with persisted `@username` mentions.
- Ticket dependency tracking for blocker relationships.
- Attachment metadata management.
- Audit logging for state-changing actions.
- Soft deletion and restore flows for projects and tickets.
- CSV import/export workflows for bulk ticket operations.
- Scheduled workflows such as auto-escalation and auto-assignment.

## Security And Authentication

IssueFlow uses stateless JWT authentication.

### JWT Configuration

JWT settings are configured with the `issueflow.jwt` prefix:

```yaml
issueflow:
  jwt:
    secret: ${JWT_SECRET:dev-secret-change-in-production-must-be-at-least-256-bits-long!!}
    expiration-minutes: ${JWT_EXPIRATION_MINUTES:60}
    issuer: ${JWT_ISSUER:issueflow}
```

The application signs tokens with HS256. Tokens include a subject, issuer, role claim, issued-at timestamp, expiry, and JTI.

### Implemented Security Components

- `JwtProperties` binds `issueflow.jwt.secret`, `issueflow.jwt.expiration-minutes`, and `issueflow.jwt.issuer`.
- `JwtService` issues tokens and validates signature, expiry, and issuer while parsing claims.
- `TokenDenyListService` stores revoked token JTIs and removes expired revoked tokens every hour.
- `JwtAuthenticationFilter` reads `Authorization: Bearer <token>`, validates the JWT, checks the deny-list, loads the user, and populates the Spring Security context.
- `CustomUserDetailsService` loads active users from `UserRepository` and maps roles to `ROLE_ADMIN` or `ROLE_DEVELOPER`.
- `AuthService` authenticates login requests, issues JWTs, returns the current user profile, revokes logout tokens, and audit-logs LOGIN and LOGOUT events.
- `AuthController` exposes `POST /auth/login`, `POST /auth/logout`, and `GET /auth/me`.
- `AuditService` persists structured audit entries for authentication events and other state-changing service workflows.
- `SecurityConfig` configures stateless sessions, disables CSRF, enables method security, registers the JWT filter, and exposes a `BCryptPasswordEncoder` bean.

### Public Endpoints

The security chain allows unauthenticated access to:

- `POST /auth/login`
- `GET /actuator/health`
- `/v3/api-docs/**`
- `/swagger-ui/**`

All other endpoints require a valid JWT. ADMIN-only endpoints should use:

```java
@PreAuthorize("hasRole('ADMIN')")
```

### Authentication API Contract

| API | Endpoint | Request | Response |
| --- | --- | --- | --- |
| Login | `POST /auth/login` | `{ "username": "jdoe", "password": "secret" }` | `{ "accessToken": "<jwt>", "tokenType": "Bearer", "expiresIn": 3600 }` |
| Logout | `POST /auth/logout` | Bearer token | `200 OK`; the token JTI is added to the deny-list until expiry. |
| Current user | `GET /auth/me` | Bearer token | `{ "id": 1, "username": "jdoe", "email": "jdoe@example.com", "fullName": "John Doe", "role": "DEVELOPER" }` |

## API Contract

The following API surface describes the target IssueFlow REST contract.

### Users

| Operation | Endpoint | Notes |
| --- | --- | --- |
| List users | `GET /users` | Returns user summaries. |
| Get user | `GET /users/{userId}` | Returns one user by ID. |
| Create user | `POST /users` | Creates a user with username, email, full name, role, and password data. |
| Update user | `PATCH /users/{userId}` | Updates editable user fields. |
| Delete user | `DELETE /users/{userId}` | Deletes or deactivates a user according to domain rules. |

### Projects

| Operation | Endpoint | Notes |
| --- | --- | --- |
| List projects | `GET /projects` | Returns visible projects. |
| Get project | `GET /projects/{projectId}` | Returns one project. |
| Create project | `POST /projects` | Creates a project owned by a user. |
| Update project | `PATCH /projects/{projectId}` | Updates project metadata. |
| Soft-delete project | `DELETE /projects/{projectId}` | Hides the project from default queries. |
| List deleted projects | `GET /projects/deleted` | ADMIN-only restore support. |
| Restore project | `POST /projects/{projectId}/restore` | ADMIN-only restore operation. |
| Project workload | `GET /projects/{projectId}/workload` | Returns open ticket counts by user. |

### Tickets

| Operation | Endpoint | Notes |
| --- | --- | --- |
| List tickets by project | `GET /tickets?projectId={projectId}` | Returns visible tickets for a project. |
| Get ticket | `GET /tickets/{ticketId}` | Returns one ticket. |
| Create ticket | `POST /tickets` | Creates a ticket with status, priority, type, project, assignee, and due date. |
| Update ticket | `PATCH /tickets/{ticketId}` | Updates editable ticket fields. |
| Soft-delete ticket | `DELETE /tickets/{ticketId}` | Hides the ticket from default queries. |
| List deleted tickets | `GET /tickets/deleted?projectId={projectId}` | ADMIN-only restore support. |
| Restore ticket | `POST /tickets/{ticketId}/restore` | ADMIN-only restore operation. |
| Export tickets | `GET /tickets/export?projectId={projectId}` | Exports project tickets to CSV. |
| Import tickets | `POST /tickets/import` | Imports tickets from multipart CSV upload. |

### Comments And Mentions

| Operation | Endpoint | Notes |
| --- | --- | --- |
| List comments | `GET /tickets/{ticketId}/comments` | Returns comments for a ticket. |
| Add comment | `POST /tickets/{ticketId}/comments` | Supports persisted `@username` mentions. |
| Update comment | `PATCH /tickets/{ticketId}/comments/{commentId}` | Updates comment content. |
| Delete comment | `DELETE /tickets/{ticketId}/comments/{commentId}` | Removes a comment according to domain rules. |
| List mentions for user | `GET /users/{userId}/mentions` | Supports optional pagination. |

### Dependencies

| Operation | Endpoint | Notes |
| --- | --- | --- |
| Add dependency | `POST /tickets/{ticketId}/dependencies` | Adds a blocker relationship. |
| List dependencies | `GET /tickets/{ticketId}/dependencies` | Lists blockers for a ticket. |
| Remove dependency | `DELETE /tickets/{ticketId}/dependencies/{blockerId}` | Removes a blocker relationship. |

### Attachments

| Operation | Endpoint | Notes |
| --- | --- | --- |
| Upload attachment | `POST /tickets/{ticketId}/attachments` | Accepts multipart file upload. |
| Delete attachment | `DELETE /tickets/{ticketId}/attachments/{attachmentId}` | Removes attachment metadata and storage reference. |

### Audit Logs

| Operation | Endpoint | Notes |
| --- | --- | --- |
| List audit logs | `GET /audit-logs` | Supports filters such as `entityType`, `entityId`, `action`, and `actor`. |

## Error Responses

Errors are returned as `ApiError` JSON:

```json
{
  "timestamp": "2026-05-20T12:00:00Z",
  "status": 401,
  "error": "Unauthorized",
  "message": "Invalid or expired token",
  "path": "/tickets",
  "details": []
}
```

Domain and infrastructure errors are mapped consistently:

- `404 Not Found` for missing resources.
- `409 Conflict` for conflicts and optimistic locking failures.
- `403 Forbidden` for authorization failures.
- `400 Bad Request` for malformed JSON and validation failures.
- `422 Unprocessable Entity` for domain validation errors.
- `500 Internal Server Error` for unexpected failures with a correlation reference.

Optimistic lock conflicts return:

```text
Resource was modified by another user, please retry
```

## Database

The project uses Flyway migrations under `src/main/resources/db/migration`.

`compose.yml` provides a local PostgreSQL database configured for the default application profile:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/issueflow
    username: issueflow
    password: issueflow
```

The development seed migration creates an initial ADMIN user:

- Username: `admin`
- Password: `admin123`

## Running Locally

Start PostgreSQL:

```bash
docker compose up -d
```

Run the application:

```bash
./mvnw spring-boot:run
```

Build the application:

```bash
./mvnw clean package
```

Run tests:

```bash
./mvnw test
```

Run the focused Phase 3 security tests:

```bash
./mvnw "-Dtest=JwtServiceTest,TokenDenyListServiceTest,CustomUserDetailsServiceTest,AuditServiceTest,AuthServiceTest" test
```

On Windows PowerShell:

```powershell
.\mvnw.cmd "-Dtest=JwtServiceTest,TokenDenyListServiceTest,CustomUserDetailsServiceTest,AuditServiceTest,AuthServiceTest" test
```

Run the authentication integration test with Docker/Testcontainers available:

```powershell
.\mvnw.cmd "-Dtest=AuthControllerIntegrationTest" test
```

## Testing Notes

- Service-level behavior is covered with JUnit 5 and Mockito.
- Repository and controller integration tests use Testcontainers PostgreSQL.
- Full-suite runs require Docker for Testcontainers-backed tests.

## AI And Agent Usage

If AI assistance is used during development, document meaningful prompts, plans, and generated artifacts in `prompts.md` or another project-tracked note as required by the assignment.

## License

This project is MIT licensed.

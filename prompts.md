# IssueFlow — Prompt Log

**Model used: Claude Sonnet 4.6 (`claude-sonnet-4-6`) via Claude Code.**

> Note: the user asked to record "Claude Opus 4.7" but the active model ID confirmed by the runtime
> is `claude-sonnet-4-6`. Earlier context windows (phases 1–8) may have used different model
> versions; those sessions are not directly accessible to verify.

Prompts marked **\[verbatim\]** are quoted exactly from the conversation transcript.
Prompts marked **\[reconstructed\]** are inferred from git commit messages and code structure
because they occurred in sessions that have since been compacted or closed.

---

## Skills & files

| File | Purpose |
| ---- | ------- |
| [CLAUDE.md](CLAUDE.md) | Hard rules applied to every change: layering, transactions, validation, error hierarchy, concurrency, soft delete, auditing, auth, test requirements, and output format. |
| [prompts.md](prompts.md) | This file — chronological prompt log with per-phase grouping. |

---

## Phase 1 · Project bootstrap

### 1. Initial scaffold \[reconstructed\]

> Set up a Spring Boot 3 / Java 21 Maven project for a ticket-management REST backend called
> IssueFlow. Include: PostgreSQL + Flyway, Spring Security (JWT stateless), Spring Data JPA,
> Jakarta Validation, Spring Actuator, SpringDoc OpenAPI, and Testcontainers for tests.
> Add a `compose.yml` for a local Postgres container.

**Delivered:** `pom.xml`, `compose.yml`, `application.yaml`, `IssueFlowApplication.java`,
initial package structure. Maven wrapper included.

---

### 2. Database schema and seed \[reconstructed\]

> Write `V1__init.sql` with tables for users, projects, tickets, comments, comment_mentions,
> ticket_dependencies, attachments, audit_logs, and revoked_tokens. Include `is_overdue`,
> `version` (optimistic lock), `deleted_at` (soft delete), and `created_at`/`updated_at`
> on every relevant table. Add `V2__seed_admin.sql` with an ADMIN bootstrap account
> (admin / admin123).

**Delivered:** `V1__init.sql`, `V2__seed_admin.sql`, `V3__fix_admin_password_hash.sql`.
Base entity `BaseEntity` with `@CreationTimestamp`, `@UpdateTimestamp`, and `deletedAt`.

---

### 3. Exception handling and domain errors \[reconstructed\]

> Add a sealed `DomainException` hierarchy (`NotFoundException`, `ConflictException`,
> `ForbiddenException`, `ValidationException`, `BusinessRuleException`).
> Map them via a single `@RestControllerAdvice` to `ApiError` JSON.

**Delivered:** `DomainException` sealed class, all initial subtypes, `GlobalExceptionHandler`,
`ApiError` record, `GlobalExceptionHandlerTest`.

---

## Phase 2 · Auth & security

### 4. JWT infrastructure \[reconstructed\]

> Implement stateless JWT auth with HS256. Add `JwtProperties` (`@ConfigurationProperties`),
> `JwtService` (sign + validate + claims), a token deny-list for logout
> (`TokenDenyListService`, `RevokedToken` entity, hourly cleanup), and
> `JwtAuthenticationFilter`. Wire `SecurityConfig` with stateless sessions,
> CSRF disabled, and method security enabled.

**Delivered:** Full JWT pipeline. `CustomUserDetailsService`, `SecurityConfig` with public
endpoints `/auth/login`, `/actuator/health`, `/v3/api-docs/**`, `/swagger-ui/**`.
Unit tests: `JwtServiceTest`, `TokenDenyListServiceTest`, `CustomUserDetailsServiceTest`.

---

### 5. Auth controller \[reconstructed\]

> Add `AuthController` with `POST /auth/login`, `POST /auth/logout`, `GET /auth/me`.
> Login returns `{ accessToken, tokenType, expiresIn }`. Logout revokes the JTI.
> Audit-log LOGIN and LOGOUT via `AuditService.log(...)`.

**Delivered:** `AuthController`, `AuthService`, `LoginRequest`, `AuthTokenResponse`,
`CurrentUserResponse`. IT test `AuthControllerIntegrationTest` (Testcontainers).
`AuthServiceTest` (Mockito).

---

## Phase 3 · Users

### 6. User management \[reconstructed\]

> Implement user CRUD: `GET /users`, `GET /users/{id}`, `POST /users` (ADMIN),
> `POST /users/update/{id}` (ADMIN), `DELETE /users/{id}` (ADMIN).
> Soft-delete on the entity. Validate `@Email`, `@NotBlank`, `@Pattern` on DTO fields.
> Every service method gets a Mockito unit test.

**Delivered:** `User` entity, `UserController`, `UserService`, `UserRepository`,
`CreateUserRequest`, `UpdateUserRequest`, `UserResponse`, `UserMapper`.
`UserServiceTest` (8 tests), plus follow-up tightening of API docs and repository null-checks
in commit `4d2cc25`.

---

## Phase 4 · Projects

### 7. Project management \[reconstructed\]

> Implement project CRUD with soft delete and restore:
> `POST /projects`, `GET /projects`, `GET /projects/{id}`,
> `PATCH /projects/{id}`, `DELETE /projects/{id}`,
> `GET /projects/deleted` (ADMIN), `POST /projects/{id}/restore` (ADMIN),
> `GET /projects/{id}/workload`.
> Workload returns DEVELOPERs sorted by open-ticket count ASC, then `createdAt` ASC.
> Use `@SQLDelete` + `@SQLRestriction` for soft delete.

**Delivered:** `Project` entity with `@SQLDelete`/`@SQLRestriction`, full controller/service,
`workload` native SQL query, `ProjectWorkloadTest` (5 tests).

---

## Phase 5 · Tickets

### 8. Ticket creation and auto-assign \[reconstructed\]

> Add `POST /tickets`. If `assigneeId` is omitted, auto-assign to the DEVELOPER
> with the fewest open tickets in the project (tie-break by `user.createdAt ASC`).
> If no DEVELOPER exists, leave unassigned — not an error.
> Auto-assign writes one `AUTO_ASSIGN` audit row (actor=SYSTEM) with the candidate list.
> Ticket uses `@Version` for optimistic locking; `OptimisticLockException` → 409.

**Delivered:** `Ticket` entity, `TicketController` (`POST /tickets`), `TicketService.create()`,
`TicketRepository` with `findCandidatesSortedByWorkload` native query.
`TicketServiceTest` (8 tests including auto-assign, tie-break, empty pool, audit payload).
`Dockerfile` added in same commit group.

---

## Phase 6 · Comments

### 9. Comment management \[reconstructed\]

> Implement `GET/POST /tickets/{id}/comments` and
> `PATCH/DELETE /tickets/{id}/comments/{commentId}`.
> Only the comment author or an ADMIN may update/delete. Throw 403 otherwise.
> Comments use `@Version` for optimistic locking. Soft delete via `deletedAt`.

**Delivered:** `Comment` entity, `CommentController`, `CommentService`, `CommentRepository`,
`CreateCommentRequest`, `UpdateCommentRequest`, `CommentResponse`.
`CommentServiceTest` (12 tests).

---

## Phase 7 · Mentions

### 10. @mention parsing and sync \[reconstructed\]

> Add `@username` mention support on comments. Parse `@word` tokens from comment content,
> resolve to users case-insensitively (unknown names silently ignored), store as
> `CommentMention` join entities. On comment update, sync the mention set: add new, remove
> dropped. Expose `GET /users/{userId}/mentions?page=&pageSize=`.

**Delivered:** `MentionParser`, `MentionService.syncMentions()`, `CommentMention` entity,
`MentionRepository`, `MentionController`, `MentionResponse`.
`MentionServiceTest` (9 tests including case-insensitive resolution, deduplication, re-add).

**Follow-up fix (this session):** Two tests had unnecessary Mockito stubs (`syncMentionsRemovesDroppedMentionsOnUpdate`,
`syncMentionsDoesNotDuplicateExistingMention`) — `syncMentions` skips the repository lookup for
usernames already in the existing mention set, so those stubs were never invoked.
Stubs removed; test comments clarified.

---

## Phase 8 · Audit logging

### 11. Audit infrastructure \[reconstructed\]

> Add an `AuditLog` entity and `AuditService.log(action, entityType, entityId, performedBy, actor, payload)`.
> Create `@Audited(action, entityType, idExpression)` — an AOP annotation processed by
> `AuditAspect` that captures a shallow pre-state snapshot for UPDATE actions and
> writes the audit row in the same transaction.
> Expose `GET /audit-logs` (ADMIN, paginated, filterable by entityType/action/actor/time range).

**Delivered:** `AuditLog`, `AuditAction` enum, `AuditActor` enum, `AuditService`,
`AuditAspect`, `@Audited`, `AuditController`, `AuditLogSpecifications`, `AuditMapper`,
`AuditLogResponse`. `AuditServiceTest`. Testcontainers IT `AuditControllerIT`.

**Follow-up fix (this session):** `AuditControllerIT` had a private `assertThat(String)` helper
that shadowed AssertJ's `assertThat`, causing a void-method chaining compile error.
Fix: removed the bogus helper, added the correct static import, removed the unused `long userId`
variable. Also added `UnsupportedMediaTypeException`, `FileTooLargeException`, and
`BadRequestException` to the `DomainException` sealed `permits` clause (required for Phase 9–10).

---

## Phase 9 · Attachments

### 12. File attachment management \[verbatim — from session summary\]

> Implement Attachments (com.issueflow.attachment):
> - `POST /tickets/{ticketId}/attachments` (multipart `file`) → upload
> - `DELETE /tickets/{ticketId}/attachments/{attachmentId}`
> - `GET /tickets/{ticketId}/attachments` (list metadata)
> - `GET /tickets/{ticketId}/attachments/{id}/download` (stream bytes)
> - Max 10 MB; MIME whitelist {image/png, image/jpeg, application/pdf, text/plain}; reject 415 on
>   wrong type, 413 on size
> - Magic-byte verification (not trusting client Content-Type alone) for png/jpeg/pdf
> - Local storage under `${issueflow.storage.path:./var/attachments}/{ticketId}/{uuid}-{sanitizedFilename}`
> - `@Audited` on upload (CREATE) and delete (DELETE)
> - Tests: upload happy path; oversized → 413; wrong MIME → 415; magic-byte mismatch → 415;
>   download with correct headers/bytes

**Delivered:** `MimeValidator` (magic-byte checks for PNG/JPEG/PDF), `AttachmentService`,
`AttachmentController`, `AttachmentResponse`, `DownloadResult`, `AttachmentMapper`,
`AttachmentRepository`. New exceptions: `UnsupportedMediaTypeException` (415),
`FileTooLargeException` (413). `application.yaml` multipart limit raised to 11 MB.
`AttachmentServiceTest` (10 Mockito tests), `AttachmentControllerIT` (7 Testcontainers tests).
Static initializer pattern used for temp storage dir to guarantee ordering with `@DynamicPropertySource`.

**Key design decision:** Service-level size check (`file.getSize() > MAX_SIZE_BYTES`) used instead
of relying on Spring's multipart filter because MockMvc does not trigger `MaxUploadSizeExceededException`.

---

## Phase 10 · CSV export / import

### 13. Bulk ticket CSV export and import \[verbatim — from session summary\]

> Implement Export & Import — CSV export/import (com.issueflow.importexport):
> - `GET /tickets/export?projectId=` → streams `text/csv` via `StreamingResponseBody`,
>   Content-Disposition: attachment, `CSVFormat.DEFAULT` with `QuoteMode.ALL_NON_NULL`.
> - `POST /tickets/import` multipart `file` + form field `projectId`.
> - Columns: `id,title,description,status,priority,type,assigneeId`.
>   Required columns: `title,status,priority,type`. Missing required column → 400.
> - Per-row: validate fields, run `TicketService.create()`. Each ticket in its own TX
>   (partial success — document the trade-off in Javadoc).
> - Collect per-row failures `{row, reason}`. Never abort batch on one bad row.
> - Tests: round-trip export→import; commas/quotes preserved; malformed row reported while
>   valid rows succeed; missing column → 400.

**Delivered:** `CsvColumns`, `ImportResult`, `RowError`, `TicketCsvService`, `TicketCsvController`.
New exception `BadRequestException` → 400 (cannot reuse `ValidationException` which maps to 422).
`TicketCsvServiceTest` (11 Mockito tests), `CsvRoundTripIT` (6 Testcontainers tests).

**Follow-up fix:** `TicketCsvServiceTest` and `CsvRoundTripIT` both asserted an unquoted header
`id,title,...` but `QuoteMode.ALL_NON_NULL` quotes header cells too, producing
`"id","title",...`. Assertions updated to match actual output.

---

## Phase 11 · Scheduler

### 14. Auto-escalation scheduler \[verbatim\]

> Scheduler — Implement auto-escalation (com.issueflow.scheduling):
> - `@EnableScheduling` on the main app.
> - `TicketEscalationScheduler` with `@Scheduled(cron = "${issueflow.escalation.cron:0 */5 * * * *}")`.
> - Logic: select tickets where `dueDate < now AND status != DONE AND deletedAt IS NULL`.
>   * If priority < CRITICAL: promote one level (LOW→MEDIUM→HIGH→CRITICAL), keep isOverdue per below.
>   * If priority == CRITICAL: set `isOverdue = true`.
>   * Each promotion is one AuditLog row with `action=AUTO_ESCALATE, actor=SYSTEM,
>     payload={oldPriority, newPriority, dueDate}`.
> - Manual PATCH that changes priority must reset `isOverdue = false` — wire into `TicketService.update`.
> - The scheduler is idempotent and safe under restart.
> - Use `SELECT ... FOR UPDATE SKIP LOCKED` or `@Lock(PESSIMISTIC_WRITE)` per row.
> - Tests (clock-injectable `Clock` bean, inject a fixed Clock in tests):
>   LOW + overdue → MEDIUM, one audit row.
>   HIGH + overdue → CRITICAL.
>   CRITICAL + overdue → isOverdue true, one audit row, second run no extra audit.
>   Manual priority change clears isOverdue. DONE tickets ignored.

**Delivered:** `TicketEscalationScheduler` (injected `Clock`, `@Transactional`, `promote()` static helper),
`UpdateTicketRequest` (all-nullable PATCH DTO), `TicketService.update()` (`@Audited(action="UPDATE")`
with `isOverdue=false` reset on priority change), `PATCH /tickets/{id}` in `TicketController`.
`Clock` bean added to `ApplicationConfig`. `findOverdueForEscalation` query added to
`TicketRepository` with `@Lock(PESSIMISTIC_WRITE)` + `jakarta.persistence.lock.timeout = -2`
(Hibernate SKIP_LOCKED hint). `issueflow.escalation.cron` added to `application.yaml`.
`TicketEscalationSchedulerTest` (6 Mockito tests), 3 new tests in `TicketServiceTest`.

---

## Phase 12 · Documentation

### 15. Run guide \[verbatim\]

> Write run.md from scratch. Must cover: prerequisites (Java 21, Docker, Maven wrapper);
> start DB with docker compose + psql verification; build (`-DskipTests` and full verify);
> run (spring-boot:run and jar); default seeded credentials with curl example;
> a curl recipe for every endpoint family; environment variables and overrides;
> how to run tests (unit vs IT, Testcontainers note); troubleshooting (port conflicts,
> Docker not running, stale Flyway migration).

**Delivered:** [run.md](run.md) — 8-section guide covering all endpoint families with
copy-pasteable curl recipes using a `$TOKEN` variable, environment variable table,
test invocation patterns, and 4 troubleshooting scenarios.

---



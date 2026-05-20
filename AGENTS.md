You are helping me build IssueFlow, a Spring Boot 3 / Java 21 REST backend for project & ticket tracking. Read README.md and ASSIGNMENT.md before any task.

Hard rules — apply to EVERY change you make, do not ask me to re-confirm them:
1. Layering: Controller → Service → Repository. No JPA entities cross the controller boundary. Use Java records for DTOs.
2. Transactions: @Transactional lives on @Service methods. Controllers are never transactional.
3. Validation: jakarta.validation on DTOs (@NotBlank, @Email, @NotNull, @Size, @Pattern). Enums validated via @Pattern or custom @ValueOfEnum.
4. Errors: throw domain exceptions (NotFoundException, ConflictException, ForbiddenException, ValidationException). A single @RestControllerAdvice maps them to ApiError JSON.
5. Concurrency: Ticket and Comment use @Version. OptimisticLockException → 409 Conflict with message "Resource was modified by another user, please retry".
6. Soft delete: BaseEntity has deletedAt. Use Hibernate @SQLDelete + @SQLRestriction so default queries hide deleted rows.
7. Auditing: a service method that mutates state must either be annotated @Audited(action=..., entityType=...) OR call AuditService.log(...) explicitly (for system actions).
8. Auth: every endpoint requires JWT except /auth/login and /actuator/health. ADMIN-only endpoints use @PreAuthorize("hasRole('ADMIN')").
9. Tests: every service method gets a unit test (Mockito). Every controller gets at least one @SpringBootTest slice with Testcontainers Postgres covering happy path + one failure case.
10. Style: no Lombok on entities (it hides JPA pitfalls); records for DTOs; constructor injection only (no @Autowired on fields).

When you finish a task, output:
- a short summary of what changed,
- the exact `./mvnw` commands to verify,
- a "follow-ups" list of anything you stubbed or skipped.
- Update the README.md Accordingly to the changed after each step.
Do not run destructive commands. Do not edit ASSIGNMENT.md or README.md.
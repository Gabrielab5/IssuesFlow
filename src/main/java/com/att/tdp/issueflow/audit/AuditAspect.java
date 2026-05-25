package com.att.tdp.issueflow.audit;

import com.att.tdp.issueflow.comment.Comment;
import com.att.tdp.issueflow.common.annotation.Audited;
import com.att.tdp.issueflow.project.Project;
import com.att.tdp.issueflow.ticket.Ticket;
import com.att.tdp.issueflow.user.User;
import com.att.tdp.issueflow.user.UserRepository;
import jakarta.persistence.Column;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Id;
import jakarta.persistence.Version;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Intercepts @Audited service methods and writes an AuditLog row IN THE SAME TRANSACTION.
 *
 * Deliberate trade-off vs. OUTBOX: if the business TX rolls back, the audit row rolls back too.
 * This gives "audit on success" semantics — every committed row corresponds to a real state change —
 * at the cost of not capturing failed-attempt rows. An OUTBOX / CDC approach would decouple
 * audit durability from the business TX and could survive rollbacks, but adds operational
 * complexity (broker, consumer) not required by the current spec.
 */
@Aspect
@Component
public class AuditAspect {

    private static final SpelExpressionParser SPEL = new SpelExpressionParser();

    private static final Map<String, Class<?>> ENTITY_CLASSES = Map.of(
            "User", User.class,
            "Project", Project.class,
            "Ticket", Ticket.class,
            "Comment", Comment.class
    );

    private final AuditService auditService;
    private final UserRepository userRepository;
    private final EntityManager entityManager;

    public AuditAspect(
            AuditService auditService,
            UserRepository userRepository,
            EntityManager entityManager
    ) {
        this.auditService = auditService;
        this.userRepository = userRepository;
        this.entityManager = entityManager;
    }

    @Around("@annotation(audited)")
    public Object around(ProceedingJoinPoint pjp, Audited audited) throws Throwable {
        // Pre-load entity state for UPDATE before the business call mutates it
        Map<String, Object> preSnap = null;
        if ("UPDATE".equals(audited.action())) {
            Long preId = evalId(audited.idExpression(), pjp, null);
            preSnap = snapEntity(audited.entityType(), preId);
        }

        // Execute the business method — if it throws, we rethrow and write NO audit row
        Object result = pjp.proceed();

        Long entityId = evalId(audited.idExpression(), pjp, result);
        Long performedBy = resolvePerformedBy();
        AuditActor actor = (performedBy != null) ? AuditActor.USER : AuditActor.SYSTEM;

        auditService.log(
                AuditAction.valueOf(audited.action()),
                audited.entityType(),
                entityId,
                performedBy,
                actor,
                buildPayload(audited.action(), result, preSnap)
        );

        return result;
    }

    // ── SpEL id resolution ────────────────────────────────────────────────────

    private Long evalId(String expression, ProceedingJoinPoint pjp, Object result) {
        if (expression == null || expression.isBlank()) return null;
        try {
            EvaluationContext ctx = buildContext(pjp, result);
            Object value = SPEL.parseExpression(expression).getValue(ctx);
            return (value instanceof Number n) ? n.longValue() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private EvaluationContext buildContext(ProceedingJoinPoint pjp, Object result) {
        StandardEvaluationContext ctx = new StandardEvaluationContext();
        MethodSignature sig = (MethodSignature) pjp.getSignature();
        String[] names = sig.getParameterNames();
        Object[] args = pjp.getArgs();
        for (int i = 0; i < names.length; i++) {
            ctx.setVariable(names[i], args[i]);
        }
        ctx.setVariable("result", result);
        return ctx;
    }

    // ── Caller identity ───────────────────────────────────────────────────────

    private Long resolvePerformedBy() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return userRepository.findByUsernameAndDeletedAtIsNull(auth.getName())
                .map(User::getId)
                .orElse(null);
    }

    // ── Shallow pre-state snapshot ────────────────────────────────────────────

    private Map<String, Object> snapEntity(String entityType, Long id) {
        if (id == null) return null;
        Class<?> cls = ENTITY_CLASSES.get(entityType);
        if (cls == null) return null;
        try {
            Object entity = entityManager.find(cls, id);
            return entity != null ? shallowFields(entity) : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Serializes only @Column, @Id, and @Version fields — never associations.
     * This gives a "shallow snapshot" without triggering lazy loads or circular refs.
     */
    private Map<String, Object> shallowFields(Object entity) {
        Map<String, Object> snap = new LinkedHashMap<>();
        Class<?> cls = entity.getClass();
        while (cls != null && cls != Object.class) {
            for (Field f : cls.getDeclaredFields()) {
                if (f.isAnnotationPresent(Column.class)
                        || f.isAnnotationPresent(Id.class)
                        || f.isAnnotationPresent(Version.class)) {
                    f.setAccessible(true);
                    try {
                        snap.put(f.getName(), f.get(entity));
                    } catch (Exception ignored) {}
                }
            }
            cls = cls.getSuperclass();
        }
        return snap;
    }

    // ── Payload builder ───────────────────────────────────────────────────────

    private Object buildPayload(String action, Object result, Map<String, Object> preSnap) {
        return switch (action) {
            case "CREATE", "RESTORE" -> result;
            case "UPDATE" -> {
                Map<String, Object> p = new LinkedHashMap<>();
                if (preSnap != null) p.put("before", preSnap);
                if (result != null) p.put("after", result);
                yield p.isEmpty() ? null : p;
            }
            case "DELETE" -> null;
            default -> result;
        };
    }
}

package com.att.tdp.issueflow.audit;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class AuditLogSpecifications {

    private AuditLogSpecifications() {
    }

    public static Specification<AuditLog> filter(
            AuditAction action,
            String entityType,
            Long entityId,
            Long performedBy,
            AuditActor actor,
            Instant from,
            Instant to
    ) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (action != null) {
                predicates.add(criteriaBuilder.equal(root.get("action"), action));
            }
            if (entityType != null) {
                predicates.add(criteriaBuilder.equal(root.get("entityType"), entityType));
            }
            if (entityId != null) {
                predicates.add(criteriaBuilder.equal(root.get("entityId"), entityId));
            }
            if (performedBy != null) {
                predicates.add(criteriaBuilder.equal(root.get("performedBy"), performedBy));
            }
            if (actor != null) {
                predicates.add(criteriaBuilder.equal(root.get("actor"), actor));
            }
            if (from != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("timestamp"), from));
            }
            if (to != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("timestamp"), to));
            }

            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }
}

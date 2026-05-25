package com.att.tdp.issueflow.audit;

import com.att.tdp.issueflow.common.PagedResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/audit-logs")
@PreAuthorize("hasRole('ADMIN')")
public class AuditController {

    private final AuditLogRepository auditLogRepository;

    public AuditController(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @GetMapping
    public PagedResponse<AuditLogResponse> findAll(
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) Long entityId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) Long performedBy,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        AuditAction actionEnum = (action != null) ? AuditAction.valueOf(action) : null;
        AuditActor actorEnum = (actor != null) ? AuditActor.valueOf(actor) : null;

        Specification<AuditLog> spec = AuditLogSpecifications.filter(
                actionEnum, entityType, entityId, performedBy, actorEnum, from, to);

        Page<AuditLog> result = auditLogRepository.findAll(
                spec, PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.DESC, "timestamp")));

        List<AuditLogResponse> data = result.getContent().stream()
                .map(AuditMapper::toResponse)
                .toList();

        return new PagedResponse<>(data, result.getTotalElements(), page);
    }
}

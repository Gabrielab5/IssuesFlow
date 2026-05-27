package com.att.tdp.issueflow.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class AuditServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    private AuditService auditService;

    @BeforeEach
    void setUp() {
        auditService = new AuditService(auditLogRepository, new ObjectMapper());
    }

    @Test
    void logPersistsAuditEntryWithSerializedPayload() {
        auditService.log(
                AuditAction.LOGIN,
                "User",
                1L,
                1L,
                AuditActor.USER,
                Map.of("username", "admin")
        );

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLog auditLog = captor.getValue();
        assertThat(auditLog.getAction()).isEqualTo(AuditAction.LOGIN);
        assertThat(auditLog.getEntityType()).isEqualTo("User");
        assertThat(auditLog.getEntityId()).isEqualTo(1L);
        assertThat(auditLog.getPerformedBy()).isEqualTo(1L);
        assertThat(auditLog.getActor()).isEqualTo(AuditActor.USER);
        assertThat(auditLog.getPayload()).isEqualTo("{\"username\":\"admin\"}");
        assertThat(auditLog.getTimestamp()).isNotNull();
    }
}

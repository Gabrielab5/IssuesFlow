package com.att.tdp.issueflow.project;

import com.att.tdp.issueflow.common.exception.NotFoundException;
import com.att.tdp.issueflow.ticket.TicketRepository;
import com.att.tdp.issueflow.user.User;
import com.att.tdp.issueflow.user.UserRepository;
import com.att.tdp.issueflow.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectWorkloadTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TicketRepository ticketRepository;

    private ProjectService projectService;

    @BeforeEach
    void setUp() {
        projectService = new ProjectService(projectRepository, userRepository, ticketRepository);
    }

    @Test
    void workloadReturnsEntriesMappedFromQueryRows() {
        Project project = project(1L, "Test Project");
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(ticketRepository.findWorkloadByProjectId(1L)).thenReturn(List.of(
                new Object[]{2L, "alice", 1L},
                new Object[]{5L, "bob", 3L}
        ));

        List<WorkloadEntry> result = projectService.getWorkload(1L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).userId()).isEqualTo(2L);
        assertThat(result.get(0).username()).isEqualTo("alice");
        assertThat(result.get(0).openTicketCount()).isEqualTo(1L);
        assertThat(result.get(1).userId()).isEqualTo(5L);
        assertThat(result.get(1).username()).isEqualTo("bob");
        assertThat(result.get(1).openTicketCount()).isEqualTo(3L);
    }

    @Test
    void workloadPreservesQueryOrderForSortingAndTieBreaker() {
        // The query sorts by openTicketCount ASC, user.created_at ASC.
        // The service must preserve that order — no re-sorting in Java.
        Project project = project(1L, "Test Project");
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        // alice and bob have the same count; query returns alice first (older created_at)
        when(ticketRepository.findWorkloadByProjectId(1L)).thenReturn(List.of(
                new Object[]{2L, "alice", 2L},
                new Object[]{5L, "bob", 2L}
        ));

        List<WorkloadEntry> result = projectService.getWorkload(1L);

        assertThat(result.get(0).username()).isEqualTo("alice");
        assertThat(result.get(1).username()).isEqualTo("bob");
    }

    @Test
    void workloadReturnsEmptyListWhenNoParticipants() {
        Project project = project(1L, "Empty Project");
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(ticketRepository.findWorkloadByProjectId(1L)).thenReturn(List.of());

        List<WorkloadEntry> result = projectService.getWorkload(1L);

        assertThat(result).isEmpty();
    }

    @Test
    void workloadThrowsNotFoundForUnknownProject() {
        when(projectRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.getWorkload(99L))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void workloadDelegatesQueryToTicketRepository() {
        Project project = project(1L, "Test Project");
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(ticketRepository.findWorkloadByProjectId(1L)).thenReturn(List.of());

        projectService.getWorkload(1L);

        verify(ticketRepository).findWorkloadByProjectId(1L);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Project project(Long id, String name) {
        User owner = new User();
        owner.setId(10L);
        owner.setUsername("owner");
        owner.setRole(UserRole.ADMIN);

        Project project = new Project();
        project.setId(id);
        project.setName(name);
        project.setOwner(owner);
        return project;
    }
}

package com.att.tdp.issueflow.project;

import com.att.tdp.issueflow.common.annotation.Audited;
import com.att.tdp.issueflow.common.exception.NotFoundException;
import com.att.tdp.issueflow.ticket.TicketRepository;
import com.att.tdp.issueflow.user.User;
import com.att.tdp.issueflow.user.UserRepository;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final TicketRepository ticketRepository;

    public ProjectService(
            ProjectRepository projectRepository,
            UserRepository userRepository,
            TicketRepository ticketRepository
    ) {
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.ticketRepository = ticketRepository;
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> findAll() {
        return projectRepository.findAll().stream()
                .map(ProjectMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProjectResponse findById(@NonNull Long projectId) {
        return ProjectMapper.toResponse(findProject(projectId));
    }

    @Transactional
    @Audited(action = "CREATE", entityType = "Project")
    public ProjectResponse create(CreateProjectRequest request) {
        Long ownerId = Objects.requireNonNull(request.ownerId());
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> NotFoundException.of("User", ownerId));
        Project project = Objects.requireNonNull(ProjectMapper.toEntity(request, owner));
        return ProjectMapper.toResponse(projectRepository.save(project));
    }

    @Transactional
    @Audited(action = "UPDATE", entityType = "Project")
    public ProjectResponse update(@NonNull Long projectId, UpdateProjectRequest request) {
        Project project = findProject(projectId);
        ProjectMapper.updateEntity(project, request);
        return ProjectMapper.toResponse(project);
    }

    /**
     * Soft-deletes the project and cascades to all its tickets.
     * Tickets are individually valuable records and must be explicitly restored by an admin.
     * Restore does NOT cascade-restore tickets for the same reason: bulk restore could
     * silently revive resolved or intentionally-closed tickets.
     */
    @Transactional
    @Audited(action = "DELETE", entityType = "Project", idExpression = "#projectId")
    public void delete(@NonNull Long projectId) {
        Project project = findProject(projectId);
        ticketRepository.softDeleteAllByProjectId(projectId);
        projectRepository.delete(Objects.requireNonNull(project));
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> findAllDeleted() {
        return projectRepository.findAllDeleted().stream()
                .map(ProjectMapper::toResponse)
                .toList();
    }

    @Transactional
    @Audited(action = "RESTORE", entityType = "Project")
    public ProjectResponse restore(@NonNull Long projectId) {
        projectRepository.findDeletedById(projectId)
                .orElseThrow(() -> NotFoundException.of("Deleted project", projectId));
        projectRepository.restoreById(projectId);
        return ProjectMapper.toResponse(
                projectRepository.findById(projectId)
                        .orElseThrow(() -> NotFoundException.of("Project", projectId))
        );
    }

    @Transactional(readOnly = true)
    public List<WorkloadEntry> getWorkload(@NonNull Long projectId) {
        findProject(projectId);
        return ticketRepository.findWorkloadByProjectId(projectId).stream()
                .map(row -> new WorkloadEntry(
                        ((Number) row[0]).longValue(),
                        (String) row[1],
                        ((Number) row[2]).longValue()
                ))
                .toList();
    }

    private Project findProject(@NonNull Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> NotFoundException.of("Project", projectId));
    }
}

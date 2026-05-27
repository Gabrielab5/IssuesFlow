package com.att.tdp.issueflow.project;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping
    public List<ProjectResponse> findAll() {
        return projectService.findAll();
    }

    @GetMapping("/deleted")
    @PreAuthorize("hasRole('ADMIN')")
    public List<ProjectResponse> findAllDeleted() {
        return projectService.findAllDeleted();
    }

    @GetMapping("/{projectId}")
    public ProjectResponse findById(@PathVariable @NonNull Long projectId) {
        return projectService.findById(projectId);
    }

    @PostMapping
    public ResponseEntity<ProjectResponse> create(@Valid @RequestBody CreateProjectRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(projectService.create(request));
    }

    @PatchMapping("/{projectId}")
    public ProjectResponse update(
            @PathVariable @NonNull Long projectId,
            @Valid @RequestBody UpdateProjectRequest request
    ) {
        return projectService.update(projectId, request);
    }

    @DeleteMapping("/{projectId}")
    public ResponseEntity<Void> delete(@PathVariable @NonNull Long projectId) {
        projectService.delete(projectId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{projectId}/workload")
    public List<WorkloadEntry> getWorkload(@PathVariable @NonNull Long projectId) {
        return projectService.getWorkload(projectId);
    }

    @PostMapping("/{projectId}/restore")
    @PreAuthorize("hasRole('ADMIN')")
    public ProjectResponse restore(@PathVariable @NonNull Long projectId) {
        return projectService.restore(projectId);
    }
}

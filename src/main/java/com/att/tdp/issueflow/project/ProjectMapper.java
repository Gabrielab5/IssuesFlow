package com.att.tdp.issueflow.project;

import com.att.tdp.issueflow.user.User;

public final class ProjectMapper {

    private ProjectMapper() {
    }

    public static Project toEntity(CreateProjectRequest request, User owner) {
        Project project = new Project();
        project.setName(request.name());
        project.setDescription(request.description());
        project.setOwner(owner);
        return project;
    }

    public static void updateEntity(Project project, UpdateProjectRequest request) {
        if (request.name() != null) {
            project.setName(request.name());
        }
        if (request.description() != null) {
            project.setDescription(request.description());
        }
    }

    public static ProjectResponse toResponse(Project project) {
        return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.getDescription(),
                project.getOwner().getId(),
                project.getOwner().getUsername(),
                project.getCreatedAt(),
                project.getUpdatedAt(),
                project.getDeletedAt()
        );
    }
}

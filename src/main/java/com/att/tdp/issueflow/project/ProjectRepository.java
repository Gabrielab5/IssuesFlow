package com.att.tdp.issueflow.project;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    @Query(value = "SELECT * FROM projects WHERE deleted_at IS NOT NULL", nativeQuery = true)
    List<Project> findAllDeleted();

    @Query(value = "SELECT * FROM projects WHERE deleted_at IS NOT NULL AND id = :id", nativeQuery = true)
    Optional<Project> findDeletedById(@Param("id") Long id);
}

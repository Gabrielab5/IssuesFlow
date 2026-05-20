package com.att.tdp.issueflow.ticket;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    List<Ticket> findByProjectId(Long projectId);

    List<Ticket> findByProjectIdAndStatusNot(Long projectId, TicketStatus status);

    @Query("""
            SELECT COUNT(t)
            FROM Ticket t
            WHERE t.assignee.id = :userId
              AND t.project.id = :projectId
              AND t.status <> com.att.tdp.issueflow.ticket.TicketStatus.DONE
            """)
    long countOpenByAssigneeAndProject(@Param("userId") Long userId, @Param("projectId") Long projectId);

    @Query(value = "SELECT * FROM tickets WHERE deleted_at IS NOT NULL", nativeQuery = true)
    List<Ticket> findAllDeleted();

    @Query(value = "SELECT * FROM tickets WHERE deleted_at IS NOT NULL AND id = :id", nativeQuery = true)
    Optional<Ticket> findDeletedById(@Param("id") Long id);
}

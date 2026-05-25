package com.att.tdp.issueflow.ticket;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
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

    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE tickets SET deleted_at = NOW() WHERE project_id = :projectId AND deleted_at IS NULL",
            nativeQuery = true)
    void softDeleteAllByProjectId(@Param("projectId") Long projectId);

    /**
     * Workload for the /projects/{id}/workload endpoint.
     * Returns DEVELOPERs who either have at least one non-deleted ticket in the project
     * OR are the project owner (if DEVELOPER), together with their open (status != DONE)
     * ticket count, sorted by open count ASC then user.created_at ASC.
     * Each row: [userId (Long), username (String), openTicketCount (Long)].
     */
    /**
     * Locks each returned row for update and skips any row already locked by another session
     * (SKIP_LOCKED via hint value -2). Soft-deleted tickets are excluded automatically by the
     * @SQLRestriction on the Ticket entity. Called by TicketEscalationScheduler.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("SELECT t FROM Ticket t WHERE t.dueDate < :now AND t.status <> com.att.tdp.issueflow.ticket.TicketStatus.DONE")
    List<Ticket> findOverdueForEscalation(@Param("now") Instant now);

    @Query(value = """
            SELECT u.id        AS userId,
                   u.username,
                   COUNT(t.id) AS openTicketCount
            FROM users u
            LEFT JOIN tickets t
                ON  t.assignee_id = u.id
                AND t.project_id  = :projectId
                AND t.status      <> 'DONE'
                AND t.deleted_at  IS NULL
            WHERE u.role = 'DEVELOPER'
              AND u.deleted_at IS NULL
              AND (
                    EXISTS (
                        SELECT 1 FROM tickets t2
                        WHERE  t2.assignee_id = u.id
                          AND  t2.project_id  = :projectId
                          AND  t2.deleted_at  IS NULL
                    )
                    OR u.id = (
                        SELECT p.owner_id FROM projects p
                        WHERE  p.id = :projectId AND p.deleted_at IS NULL
                    )
                  )
            GROUP BY u.id, u.username, u.created_at
            ORDER BY openTicketCount ASC, u.created_at ASC
            """, nativeQuery = true)
    List<Object[]> findWorkloadByProjectId(@Param("projectId") Long projectId);

    /**
     * Auto-assign candidate pool: ALL non-deleted DEVELOPERs system-wide, sorted by their
     * open ticket count in the given project ASC, then user.created_at ASC (tie-breaker).
     * The first row is the best candidate; the full list is used for the AUTO_ASSIGN audit payload.
     * Each row: [userId (Long), username (String), openTicketCount (Long)].
     */
    @Query(value = """
            SELECT u.id        AS userId,
                   u.username,
                   COUNT(t.id) AS openTicketCount
            FROM users u
            LEFT JOIN tickets t
                ON  t.assignee_id = u.id
                AND t.project_id  = :projectId
                AND t.status      <> 'DONE'
                AND t.deleted_at  IS NULL
            WHERE u.role       = 'DEVELOPER'
              AND u.deleted_at IS NULL
            GROUP BY u.id, u.username, u.created_at
            ORDER BY openTicketCount ASC, u.created_at ASC
            """, nativeQuery = true)
    List<Object[]> findCandidatesSortedByWorkload(@Param("projectId") Long projectId);
}

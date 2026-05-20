package com.att.tdp.issueflow.dependency;

import com.att.tdp.issueflow.common.entity.BaseEntity;
import com.att.tdp.issueflow.ticket.Ticket;
import jakarta.persistence.*;

@Entity
@Table(
        name = "ticket_dependencies",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_ticket_dependencies",
                columnNames = {"ticket_id", "blocked_by_id"}
        )
)
public class TicketDependency extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "blocked_by_id", nullable = false)
    private Ticket blockedBy;

    public Ticket getTicket() { return ticket; }
    public void setTicket(Ticket ticket) { this.ticket = ticket; }

    public Ticket getBlockedBy() { return blockedBy; }
    public void setBlockedBy(Ticket blockedBy) { this.blockedBy = blockedBy; }
}

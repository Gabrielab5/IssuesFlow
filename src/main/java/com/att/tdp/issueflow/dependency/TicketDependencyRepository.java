package com.att.tdp.issueflow.dependency;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketDependencyRepository extends JpaRepository<TicketDependency, Long> {
}

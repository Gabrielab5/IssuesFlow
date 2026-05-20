package com.att.tdp.issueflow.project;

/**
 * Assumption: "all users in the project" is interpreted as every DEVELOPER who currently
 * has at least one non-deleted ticket assigned in this project, PLUS the project owner
 * if their role is DEVELOPER (even if they have zero tickets). Admins are excluded because
 * they do not appear in the auto-assign pool and contribute no ticket work.
 */
public record WorkloadEntry(Long userId, String username, long openTicketCount) {
}

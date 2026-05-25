package com.att.tdp.issueflow.importexport;

final class CsvColumns {

    static final String ID          = "id";
    static final String TITLE       = "title";
    static final String DESCRIPTION = "description";
    static final String STATUS      = "status";
    static final String PRIORITY    = "priority";
    static final String TYPE        = "type";
    static final String ASSIGNEE_ID = "assigneeId";

    /** Column order in the exported CSV. */
    static final String[] HEADERS = {ID, TITLE, DESCRIPTION, STATUS, PRIORITY, TYPE, ASSIGNEE_ID};

    /** Columns that must be present in an imported CSV header row. */
    static final String[] REQUIRED = {TITLE, STATUS, PRIORITY, TYPE};

    private CsvColumns() {}
}

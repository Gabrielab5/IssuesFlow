package com.att.tdp.issueflow.importexport;

import java.util.List;

public record ImportResult(int created, int failed, List<RowError> errors) {
}

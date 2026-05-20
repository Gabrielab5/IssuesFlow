package com.att.tdp.issueflow.common;

import java.util.List;

public record PagedResponse<T>(List<T> data, long total, int page) {}

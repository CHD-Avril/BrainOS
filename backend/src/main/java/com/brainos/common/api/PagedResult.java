package com.brainos.common.api;

import java.util.List;

public record PagedResult<T>(List<T> items, long total, int page, int size) {
    public PagedResult {
        items = List.copyOf(items);
    }
}

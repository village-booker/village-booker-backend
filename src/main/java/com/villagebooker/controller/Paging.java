package com.villagebooker.controller;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

// ?page=&size= from the client; out-of-range values are clamped instead of rejected
final class Paging {

    static final int MAX_PAGE_SIZE = 100;

    private Paging() {
    }

    static PageRequest of(int page, int size, Sort sort) {
        return PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE), sort);
    }
}

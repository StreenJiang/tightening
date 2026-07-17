package com.tightening.dto;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.List;

public record PageResult<T>(
    List<T> records,
    long total,
    long size,
    long current
) {
    public static <T> PageResult<T> of(Page<?> page, List<T> records) {
        return new PageResult<>(records, page.getTotal(), page.getSize(), page.getCurrent());
    }
}

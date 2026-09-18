package com.lumen.common.core.domain;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

@Data
@NoArgsConstructor
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private long total;
    private long pageNum;
    private long pageSize;
    private List<T> records;

    public PageResult(long total, long pageNum, long pageSize, List<T> records) {
        this.total = total;
        this.pageNum = pageNum;
        this.pageSize = pageSize;
        this.records = records;
    }

    public static <T> PageResult<T> empty(long pageNum, long pageSize) {
        return new PageResult<>(0L, pageNum, pageSize, Collections.emptyList());
    }

    public static <T> PageResult<T> of(long total, long pageNum, long pageSize, List<T> records) {
        return new PageResult<>(total, pageNum, pageSize, records);
    }
}
package com.example.usercenter.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PageResult<T> {
    private long total;
    private long current;
    private long pageSize;
    private List<T> records;
}

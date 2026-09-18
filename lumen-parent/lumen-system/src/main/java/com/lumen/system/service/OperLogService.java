package com.lumen.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lumen.system.entity.SysOperLog;
import com.lumen.system.mapper.SysOperLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class OperLogService {

    private final SysOperLogMapper operLogMapper;

    public IPage<SysOperLog> list(int pageNum, int pageSize,
                                  String operName, String title,
                                  LocalDateTime startTime, LocalDateTime endTime) {
        var w = new LambdaQueryWrapper<SysOperLog>().orderByDesc(SysOperLog::getOperTime);
        if (operName != null && !operName.isBlank()) w.like(SysOperLog::getOperName, operName);
        if (title != null && !title.isBlank()) w.like(SysOperLog::getTitle, title);
        if (startTime != null) w.ge(SysOperLog::getOperTime, startTime);
        if (endTime != null) w.le(SysOperLog::getOperTime, endTime);
        return operLogMapper.selectPage(Page.of(pageNum, pageSize), w);
    }
}

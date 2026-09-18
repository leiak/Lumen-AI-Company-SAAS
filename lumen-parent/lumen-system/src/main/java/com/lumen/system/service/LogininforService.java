package com.lumen.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lumen.system.entity.SysLogininfor;
import com.lumen.system.mapper.SysLogininforMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class LogininforService {

    private final SysLogininforMapper logininforMapper;

    public IPage<SysLogininfor> list(int pageNum, int pageSize,
                                     String userName, String ipaddr, String status,
                                     LocalDateTime startTime, LocalDateTime endTime) {
        var w = new LambdaQueryWrapper<SysLogininfor>().orderByDesc(SysLogininfor::getLoginTime);
        if (userName != null && !userName.isBlank()) w.like(SysLogininfor::getUserName, userName);
        if (ipaddr != null && !ipaddr.isBlank()) w.like(SysLogininfor::getIpaddr, ipaddr);
        if (status != null && !status.isBlank()) w.eq(SysLogininfor::getStatus, status);
        if (startTime != null) w.ge(SysLogininfor::getLoginTime, startTime);
        if (endTime != null) w.le(SysLogininfor::getLoginTime, endTime);
        return logininforMapper.selectPage(Page.of(pageNum, pageSize), w);
    }
}

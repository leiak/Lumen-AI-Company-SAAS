package com.lumen.system.controller;

import com.lumen.common.core.domain.R;
import com.lumen.system.service.LogininforService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/system/logininfor")
@RequiredArgsConstructor
public class LogininforController {

    private final LogininforService logininforService;

    @GetMapping("/list")
    public R<?> list(@RequestParam(defaultValue = "1") int pageNum,
                     @RequestParam(defaultValue = "10") int pageSize,
                     @RequestParam(required = false) String userName,
                     @RequestParam(required = false) String ipaddr,
                     @RequestParam(required = false) String status,
                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return R.ok(logininforService.list(pageNum, pageSize, userName, ipaddr, status, startTime, endTime));
    }
}

package com.lumen.system.controller;

import com.lumen.common.core.domain.R;
import com.lumen.system.service.OnlineUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/system/online")
@RequiredArgsConstructor
public class OnlineUserController {

    private final OnlineUserService onlineUserService;

    @GetMapping("/list")
    public R<List<Map<String, String>>> list(@RequestParam(defaultValue = "100") int limit) {
        return R.ok(onlineUserService.list(limit));
    }
}

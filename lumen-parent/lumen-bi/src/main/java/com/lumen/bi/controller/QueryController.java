package com.lumen.bi.controller;

import com.lumen.bi.dto.AdHocQueryRequest;
import com.lumen.bi.service.QueryService;
import com.lumen.common.core.domain.R;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Ad-hoc 查询接口。安全要求 #4: 仅 super_admin / bi_admin。
 */
@RestController
@RequestMapping("/bi/query")
@RequiredArgsConstructor
public class QueryController {

    private final QueryService queryService;

    /**
     * 安全要求 #4: 仅 super_admin / bi_admin。
     */
    @PostMapping("/execute")
    @PreAuthorize("hasAnyRole('super_admin','bi_admin')")
    public R<Map<String, Object>> execute(@RequestBody @Valid AdHocQueryRequest req) {
        return R.ok(queryService.executeAdHoc(req.getSql(), req.getParams()));
    }
}
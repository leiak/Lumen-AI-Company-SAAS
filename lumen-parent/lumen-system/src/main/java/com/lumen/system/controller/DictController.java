package com.lumen.system.controller;

import com.lumen.common.core.domain.R;
import com.lumen.system.entity.SysDictData;
import com.lumen.system.service.DictService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/system/dict")
@RequiredArgsConstructor
public class DictController {

    private final DictService dictService;

    @GetMapping("/list")
    public R<?> list(@RequestParam(defaultValue = "1") int pageNum,
                     @RequestParam(defaultValue = "10") int pageSize,
                     @RequestParam(required = false) String keyword) {
        return R.ok(dictService.listTypes(pageNum, pageSize, keyword));
    }

    @GetMapping("/type/{dictType}")
    public R<List<SysDictData>> listByType(@PathVariable String dictType) {
        return R.ok(dictService.listDataByType(dictType));
    }
}

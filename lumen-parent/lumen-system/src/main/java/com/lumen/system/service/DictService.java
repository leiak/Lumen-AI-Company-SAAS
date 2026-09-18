package com.lumen.system.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lumen.system.entity.SysDictData;
import com.lumen.system.entity.SysDictType;
import com.lumen.system.mapper.SysDictDataMapper;
import com.lumen.system.mapper.SysDictTypeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DictService {

    private final SysDictTypeMapper typeMapper;
    private final SysDictDataMapper dataMapper;

    public IPage<SysDictType> listTypes(int pageNum, int pageSize, String keyword) {
        var w = new LambdaQueryWrapper<SysDictType>()
            .eq(SysDictType::getStatus, "0")
            .orderByDesc(SysDictType::getDictId);
        if (keyword != null && !keyword.isBlank()) {
            w.and(q -> q.like(SysDictType::getDictName, keyword)
                .or().like(SysDictType::getDictType, keyword));
        }
        return typeMapper.selectPage(Page.of(pageNum, pageSize), w);
    }

    public List<SysDictData> listDataByType(String dictType) {
        return dataMapper.selectList(new LambdaQueryWrapper<SysDictData>()
            .eq(SysDictData::getDictType, dictType)
            .eq(SysDictData::getStatus, "0")
            .orderByAsc(SysDictData::getDictSort));
    }
}

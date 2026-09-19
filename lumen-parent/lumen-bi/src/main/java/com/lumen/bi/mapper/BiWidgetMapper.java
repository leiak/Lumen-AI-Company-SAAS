package com.lumen.bi.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumen.bi.entity.BiWidget;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 看板组件 mapper。
 */
@Mapper
public interface BiWidgetMapper extends BaseMapper<BiWidget> {

    @Select("SELECT * FROM bi_widget WHERE dashboard_id = #{dashboardId} AND deleted = 0 ORDER BY id")
    List<BiWidget> findByDashboard(@Param("dashboardId") Long dashboardId);

    @Select("SELECT * FROM bi_widget WHERE dataset_id = #{datasetId} AND deleted = 0 ORDER BY id")
    List<BiWidget> findByDataset(@Param("datasetId") Long datasetId);

    default List<BiWidget> findByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapper<BiWidget>()
            .in(BiWidget::getId, ids)
            .eq(BiWidget::getDeleted, 0)
            .orderByAsc(BiWidget::getId));
    }
}
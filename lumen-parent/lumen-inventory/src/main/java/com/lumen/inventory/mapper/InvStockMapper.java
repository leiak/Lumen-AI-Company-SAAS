package com.lumen.inventory.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumen.inventory.entity.InvStock;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface InvStockMapper extends BaseMapper<InvStock> {

    /**
     * 查某仓库某 SKU 的所有批次库存。FIFO consume 时按 last_in_at ASC 取。
     */
    @Select("SELECT * FROM inv_stock WHERE tenant_id = #{tenantId} "
        + "AND warehouse_id = #{warehouseId} AND item_id = #{itemId} AND deleted = 0 "
        + "ORDER BY (last_in_at IS NULL), last_in_at ASC, id ASC")
    List<InvStock> findByWarehouseAndItem(@Param("tenantId") Long tenantId,
                                          @Param("warehouseId") Long warehouseId,
                                          @Param("itemId") Long itemId);

    /**
     * 按唯一批次 (warehouse + location + item + batch_no) 查一条。
     */
    @Select("SELECT * FROM inv_stock WHERE tenant_id = #{tenantId} "
        + "AND warehouse_id = #{warehouseId} AND location_id = #{locationId} "
        + "AND item_id = #{itemId} AND batch_no = #{batchNo} AND deleted = 0 LIMIT 1")
    InvStock findByBatch(@Param("tenantId") Long tenantId,
                         @Param("warehouseId") Long warehouseId,
                         @Param("locationId") Long locationId,
                         @Param("itemId") Long itemId,
                         @Param("batchNo") String batchNo);

    /**
     * SKU 总库存 (跨所有仓库/批次)。
     */
    default BigDecimal sumByItem(Long tenantId, Long itemId) {
        List<InvStock> rows = selectList(new LambdaQueryWrapper<InvStock>()
            .eq(InvStock::getTenantId, tenantId)
            .eq(InvStock::getItemId, itemId));
        BigDecimal sum = BigDecimal.ZERO;
        for (InvStock s : rows) {
            if (s.getQuantity() != null) sum = sum.add(s.getQuantity());
        }
        return sum;
    }

    /**
     * 仓库总值 (按 quantity 求和)。简化版 — 不乘以单价,留 dashboard 计算。
     */
    default BigDecimal sumByWarehouse(Long tenantId, Long warehouseId) {
        List<InvStock> rows = selectList(new LambdaQueryWrapper<InvStock>()
            .eq(InvStock::getTenantId, tenantId)
            .eq(InvStock::getWarehouseId, warehouseId));
        BigDecimal sum = BigDecimal.ZERO;
        for (InvStock s : rows) {
            if (s.getQuantity() != null) sum = sum.add(s.getQuantity());
        }
        return sum;
    }

    /**
     * 跨仓库某 SKU 的 FIFO 队列 (跨 warehouse 选最后 1 个参数)。
     * 这里简化: warehouse 透传,业务按 last_in_at ASC 处理。
     */
    @Select("SELECT * FROM inv_stock WHERE tenant_id = #{tenantId} "
        + "AND item_id = #{itemId} AND deleted = 0 "
        + "ORDER BY (last_in_at IS NULL), last_in_at ASC, id ASC")
    List<InvStock> findByItemOrderByLastIn(@Param("tenantId") Long tenantId, @Param("itemId") Long itemId);
}
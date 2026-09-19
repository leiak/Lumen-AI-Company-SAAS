package com.lumen.procurement.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumen.procurement.entity.ProcSupplierQualification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface ProcSupplierQualificationMapper extends BaseMapper<ProcSupplierQualification> {

    /**
     * 按 supplier 查询资质列表。
     */
    @Select("SELECT * FROM proc_supplier_qualification WHERE tenant_id = #{tenantId} "
        + "AND supplier_id = #{supplierId} AND deleted = 0 ORDER BY id DESC")
    List<ProcSupplierQualification> findBySupplier(@Param("tenantId") Long tenantId,
                                                   @Param("supplierId") Long supplierId);

    /**
     * 查询将在 {@code now + days} 内过期 且 status=approved 的资质。
     * 用于到期提醒 (30/15/7 天)。
     */
    @Select("SELECT * FROM proc_supplier_qualification "
        + "WHERE tenant_id = #{tenantId} AND status = 'approved' AND deleted = 0 "
        + "AND expire_at IS NOT NULL AND expire_at <= DATE_ADD(CURDATE(), INTERVAL #{days} DAY) "
        + "ORDER BY expire_at ASC")
    List<ProcSupplierQualification> findExpiring(@Param("tenantId") Long tenantId,
                                                 @Param("days") int days);
}
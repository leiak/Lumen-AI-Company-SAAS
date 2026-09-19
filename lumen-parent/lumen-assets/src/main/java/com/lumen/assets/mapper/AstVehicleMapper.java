package com.lumen.assets.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumen.assets.entity.AstVehicle;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AstVehicleMapper extends BaseMapper<AstVehicle> {

    /**
     * 按车牌查询。Tenant filter by interceptor. UNIQUE(tenant_id, plate_no, deleted).
     */
    @Select("SELECT * FROM ast_vehicle WHERE plate_no = #{plateNo} AND deleted = 0 LIMIT 1")
    AstVehicle findByPlate(@Param("plateNo") String plateNo);
}
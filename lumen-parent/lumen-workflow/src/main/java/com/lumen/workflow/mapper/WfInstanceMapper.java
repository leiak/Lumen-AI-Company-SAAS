package com.lumen.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumen.workflow.entity.WfInstance;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface WfInstanceMapper extends BaseMapper<WfInstance> {

    /**
     * 返回指定 businessKey 对应的进行中 (status=0) 实例。
     * 用于唯一性校验：同一业务键不应启动两个进行中流程。
     */
    @Select("SELECT * FROM wf_instance WHERE business_key = #{businessKey} AND status = 0 AND deleted = 0 ORDER BY id DESC LIMIT 1")
    WfInstance selectByBusinessKey(@Param("businessKey") String businessKey);
}
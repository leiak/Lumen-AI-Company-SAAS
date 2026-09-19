package com.lumen.mobile.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumen.mobile.entity.IntegrationAppConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 协作平台应用配置 mapper。
 *
 * <p>返回的 {@link IntegrationAppConfig} 含 {@code appSecretEnc} 与 {@code webhookUrlEnc} 加密字段；
 * service 层根据调用方角色决定是否解密（安全要求 #12）。</p>
 */
@Mapper
public interface IntegrationAppConfigMapper extends BaseMapper<IntegrationAppConfig> {

    /**
     * 按 code 唯一查（租户内）。
     */
    default IntegrationAppConfig findByCode(String code) {
        return selectOne(new LambdaQueryWrapper<IntegrationAppConfig>()
            .eq(IntegrationAppConfig::getCode, code)
            .eq(IntegrationAppConfig::getDeleted, 0));
    }

    /**
     * 平台下全部配置（启用/禁用都列出）。
     */
    default List<IntegrationAppConfig> findByPlatform(String platform) {
        return selectList(new LambdaQueryWrapper<IntegrationAppConfig>()
            .eq(IntegrationAppConfig::getPlatform, platform)
            .eq(IntegrationAppConfig::getDeleted, 0)
            .orderByDesc(IntegrationAppConfig::getId));
    }

    /**
     * 仅 enabled=1 的配置（业务运行时只调启用项）。
     */
    @Select("SELECT * FROM int_app_config WHERE enabled = 1 AND deleted = 0 ORDER BY id DESC")
    List<IntegrationAppConfig> findEnabled();
}

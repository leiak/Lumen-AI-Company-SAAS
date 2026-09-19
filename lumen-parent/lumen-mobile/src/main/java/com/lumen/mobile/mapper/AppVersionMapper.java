package com.lumen.mobile.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumen.mobile.entity.MobAppVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * App 版本 mapper。
 *
 * <p>{@code @InterceptorIgnore(tenantLine = "true")} — App 版本是平台级资源（同一版本对所有租户生效），
 * {@link com.lumen.common.mybatis.interceptor.TenantInterceptor} 不应自动注入 tenant_id 过滤。</p>
 *
 * <p>关键自定义查询：{@code findByPlatform} / {@code findLatestReleased} / {@code findByPlatformAndVersion}。
 * service 层 {@code checkUpdate} / {@code latest} 直接调用。</p>
 */
@InterceptorIgnore(tenantLine = "true")
@Mapper
public interface AppVersionMapper extends BaseMapper<MobAppVersion> {

    /**
     * 平台下全部版本（按 version DESC）。
     */
    default List<MobAppVersion> findByPlatform(String platform) {
        return selectList(new LambdaQueryWrapper<MobAppVersion>()
            .eq(MobAppVersion::getPlatform, platform)
            .eq(MobAppVersion::getDeleted, 0)
            .orderByDesc(MobAppVersion::getId));
    }

    /**
     * 平台下最新已发布版本（status=released）。
     */
    @Select("SELECT * FROM mob_app_version WHERE platform = #{platform} "
        + "AND status = 'released' AND deleted = 0 ORDER BY id DESC LIMIT 1")
    MobAppVersion findLatestReleased(@Param("platform") String platform);

    /**
     * 平台 + 版本号 唯一查询（用于 checkUpdate 判定当前版本是否需要升级）。
     */
    default MobAppVersion findByPlatformAndVersion(String platform, String version) {
        return selectOne(new LambdaQueryWrapper<MobAppVersion>()
            .eq(MobAppVersion::getPlatform, platform)
            .eq(MobAppVersion::getVersion, version)
            .eq(MobAppVersion::getDeleted, 0));
    }
}

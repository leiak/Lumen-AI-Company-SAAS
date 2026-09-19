package com.lumen.mobile.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumen.mobile.entity.MobPushToken;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 推送 Token mapper。token 加密存储（AES-GCM），md5(token) 作为查询索引（不存明文）。
 */
@Mapper
public interface PushTokenMapper extends BaseMapper<MobPushToken> {

    /**
     * 用户全部 token（含 inactive）。用于 user 端的设备管理。
     */
    default List<MobPushToken> findByUser(Long userId) {
        return selectList(new LambdaQueryWrapper<MobPushToken>()
            .eq(MobPushToken::getUserId, userId)
            .eq(MobPushToken::getDeleted, 0)
            .orderByDesc(MobPushToken::getId));
    }

    /**
     * 用户全部 active token — 用于 pushToUser 时一次性获取所有推送通道。
     */
    default List<MobPushToken> findActiveByUser(Long userId) {
        return selectList(new LambdaQueryWrapper<MobPushToken>()
            .eq(MobPushToken::getUserId, userId)
            .eq(MobPushToken::getStatus, "active")
            .eq(MobPushToken::getDeleted, 0));
    }

    /**
     * 按 (platform, tokenHash) 唯一查询 — 避免密文比对。
     * service register 时先调用此方法检测是否已注册（同设备/同 token 重启 App）。
     */
    @Select("SELECT * FROM mob_push_token WHERE platform = #{platform} "
        + "AND token_hash = #{tokenHash} AND deleted = 0 LIMIT 1")
    MobPushToken findByPlatformAndToken(@Param("platform") String platform,
                                       @Param("tokenHash") String tokenHash);
}

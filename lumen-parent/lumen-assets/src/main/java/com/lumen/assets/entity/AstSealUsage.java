package com.lumen.assets.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumen.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 印章用印记录。returned_at=null 表示尚未归还。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ast_seal_usage")
public class AstSealUsage extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long sealId;
    private String documentName;
    private String documentId;
    private Long userId;
    private LocalDateTime usedAt;
    private LocalDateTime returnedAt;
    private Long witnessId;
}
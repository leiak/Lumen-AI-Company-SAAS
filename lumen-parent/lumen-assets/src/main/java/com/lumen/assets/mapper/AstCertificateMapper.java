package com.lumen.assets.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumen.assets.entity.AstCertificate;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface AstCertificateMapper extends BaseMapper<AstCertificate> {

    /**
     * 即将到期（expire_at <= endDate 且 >= today，按过期顺序）。
     */
    default List<AstCertificate> findExpiringWithin(LocalDate endDate) {
        return selectList(new LambdaQueryWrapper<AstCertificate>()
            .isNotNull(AstCertificate::getExpireAt)
            .le(AstCertificate::getExpireAt, endDate)
            .orderByAsc(AstCertificate::getExpireAt));
    }
}
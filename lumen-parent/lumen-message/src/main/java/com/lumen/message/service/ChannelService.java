package com.lumen.message.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.message.entity.MsgChannel;
import com.lumen.message.mapper.MsgChannelMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChannelService {

    private final MsgChannelMapper channelMapper;

    public IPage<MsgChannel> page(int pageNum, int pageSize, String keyword) {
        var w = new LambdaQueryWrapper<MsgChannel>().orderByDesc(MsgChannel::getId);
        if (keyword != null && !keyword.isBlank()) {
            w.and(q -> q.like(MsgChannel::getCode, keyword)
                .or().like(MsgChannel::getName, keyword));
        }
        return channelMapper.selectPage(Page.of(pageNum, pageSize), w);
    }

    public MsgChannel getById(Long id) {
        MsgChannel c = channelMapper.selectById(id);
        if (c == null) throw new ServiceException(404, "Channel not found: " + id);
        return c;
    }

    public MsgChannel create(MsgChannel req) {
        if (req.getCode() == null || req.getCode().isBlank()) {
            throw new ServiceException(400, "code is required");
        }
        if (req.getName() == null || req.getName().isBlank()) {
            throw new ServiceException(400, "name is required");
        }
        if (req.getType() == null || req.getType().isBlank()) {
            throw new ServiceException(400, "type is required");
        }
        // Validate type — must be one of the supported channel shapes.
        switch (req.getType()) {
            case "email": case "sms": case "site":
            case "dingtalk": case "wechat": case "webhook":
                break;
            default:
                throw new ServiceException(400, "Unsupported channel type: " + req.getType());
        }
        if (req.getEnabled() == null) req.setEnabled(1);
        // config JSON MUST NOT contain raw credentials in production;
        // use jasypt or Nacos encrypted config (enforcement TODO).
        MsgChannel toCreate = new MsgChannel();
        toCreate.setCode(req.getCode());
        toCreate.setName(req.getName());
        toCreate.setType(req.getType());
        toCreate.setConfig(req.getConfig());
        toCreate.setEnabled(req.getEnabled());
        channelMapper.insert(toCreate);
        log.info("Created channel id={} code={} type={}",
            toCreate.getId(), toCreate.getCode(), toCreate.getType());
        return toCreate;
    }

    public MsgChannel update(Long id, MsgChannel req) {
        MsgChannel existing = getById(id);
        if (req.getName() != null) existing.setName(req.getName());
        if (req.getType() != null) existing.setType(req.getType());
        if (req.getConfig() != null) existing.setConfig(req.getConfig());
        if (req.getEnabled() != null) existing.setEnabled(req.getEnabled());
        channelMapper.updateById(existing);
        return existing;
    }

    public void delete(Long id) {
        getById(id);
        channelMapper.deleteById(id);
    }

    /**
     * Look up a channel by code within the caller's tenant. Returns null if
     * missing OR disabled — caller is responsible for falling back.
     */
    public MsgChannel findEnabledByCodeAndTenant(String code, Long tenantId) {
        if (code == null || code.isBlank() || tenantId == null) return null;
        MsgChannel c = channelMapper.findByCodeAndTenant(code, tenantId);
        if (c == null || c.getEnabled() == null || c.getEnabled() != 1) return null;
        return c;
    }
}
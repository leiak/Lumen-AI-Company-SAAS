package com.lumen.org.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.org.entity.SysPost;
import com.lumen.org.mapper.SysPostMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PostService {

    private final SysPostMapper postMapper;

    public IPage<SysPost> list(int pageNum, int pageSize, String keyword) {
        var w = new LambdaQueryWrapper<SysPost>()
            .eq(SysPost::getStatus, "0")
            .orderByAsc(SysPost::getPostSort);
        if (keyword != null && !keyword.isBlank()) {
            w.and(q -> q.like(SysPost::getPostCode, keyword)
                .or().like(SysPost::getPostName, keyword));
        }
        return postMapper.selectPage(Page.of(pageNum, pageSize), w);
    }

    public SysPost getById(Long id) {
        SysPost p = postMapper.selectById(id);
        if (p == null) throw new ServiceException(404, "Post not found: " + id);
        return p;
    }

    @Transactional
    public SysPost create(SysPost post) {
        if (post.getPostCode() == null || post.getPostCode().isBlank()) {
            throw new ServiceException(400, "postCode is required");
        }
        if (post.getPostName() == null || post.getPostName().isBlank()) {
            throw new ServiceException(400, "postName is required");
        }
        if (post.getStatus() == null) post.setStatus("0");
        if (post.getPostSort() == null) post.setPostSort(0);

        SysPost toCreate = new SysPost();
        toCreate.setPostCode(post.getPostCode());
        toCreate.setPostName(post.getPostName());
        toCreate.setPostSort(post.getPostSort());
        toCreate.setStatus(post.getStatus());
        toCreate.setRemark(post.getRemark());
        // tenantId is injected by TenantLineInnerInterceptor
        postMapper.insert(toCreate);
        return toCreate;
    }

    @Transactional
    public SysPost update(SysPost post) {
        SysPost existing = getById(post.getPostId());
        if (post.getPostCode() != null) existing.setPostCode(post.getPostCode());
        if (post.getPostName() != null) existing.setPostName(post.getPostName());
        if (post.getPostSort() != null) existing.setPostSort(post.getPostSort());
        if (post.getStatus() != null) existing.setStatus(post.getStatus());
        if (post.getRemark() != null) existing.setRemark(post.getRemark());
        postMapper.updateById(existing);
        return existing;
    }

    @Transactional
    public void delete(Long id) {
        getById(id);
        postMapper.deleteById(id);
    }
}
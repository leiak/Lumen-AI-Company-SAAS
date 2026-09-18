package com.lumen.org.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.org.entity.SysPost;
import com.lumen.org.mapper.SysPostMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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

    public SysPost create(SysPost post) {
        if (post.getStatus() == null) post.setStatus("0");
        if (post.getPostSort() == null) post.setPostSort(0);
        postMapper.insert(post);
        return post;
    }

    public SysPost update(SysPost post) {
        getById(post.getPostId()); // 404 if missing
        postMapper.updateById(post);
        return post;
    }

    public void delete(Long id) {
        getById(id);
        postMapper.deleteById(id);
    }
}
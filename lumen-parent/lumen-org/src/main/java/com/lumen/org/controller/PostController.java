package com.lumen.org.controller;

import com.lumen.common.core.domain.R;
import com.lumen.org.entity.SysPost;
import com.lumen.org.service.PostService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/org/post")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    @GetMapping("/list")
    public R<?> list(@RequestParam(defaultValue = "1") int pageNum,
                     @RequestParam(defaultValue = "10") int pageSize,
                     @RequestParam(required = false) String keyword) {
        return R.ok(postService.list(pageNum, pageSize, keyword));
    }

    @GetMapping("/{id}")
    public R<SysPost> get(@PathVariable Long id) {
        return R.ok(postService.getById(id));
    }

    @PostMapping
    public R<SysPost> create(@RequestBody SysPost post) {
        return R.ok(postService.create(post));
    }

    @PutMapping("/{id}")
    public R<SysPost> update(@PathVariable Long id, @RequestBody SysPost post) {
        post.setPostId(id);
        return R.ok(postService.update(post));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        postService.delete(id);
        return R.ok();
    }
}
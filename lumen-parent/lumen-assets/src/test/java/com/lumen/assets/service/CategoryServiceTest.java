package com.lumen.assets.service;

import com.lumen.assets.dto.CategoryNode;
import com.lumen.assets.entity.AstCategory;
import com.lumen.assets.mapper.AstCategoryMapper;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Category tree + cross-tenant 404.
 */
@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock private AstCategoryMapper categoryMapper;
    @InjectMocks private CategoryService categoryService;

    private static final long TENANT = 1L;
    private static final long USER = 10L;

    @BeforeEach
    void setUp() {
        UserContextHolder.set(UserContext.builder()
            .userId(USER).tenantId(TENANT).userName("alice")
            .roles(java.util.Set.of("assets_admin")).build());
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    private AstCategory cat(long id, long parentId, int level, String path, String code) {
        AstCategory c = new AstCategory();
        c.setId(id);
        c.setTenantId(TENANT);
        c.setParentId(parentId);
        c.setCode(code);
        c.setName(code);
        c.setLevel(level);
        c.setPath(path);
        c.setDepreciationMethodDefault(AssetService.METHOD_STRAIGHT_LINE);
        c.setUsefulLifeDefault(36);
        return c;
    }

    // 1. tree 把扁平列表拼接成树
    @Test
    void tree_buildsHierarchy() {
        when(categoryMapper.selectList(any())).thenReturn(List.of(
            cat(1L, 0L, 1, "/1", "IT"),
            cat(2L, 1L, 2, "/1/2", "LAPTOP"),
            cat(3L, 1L, 2, "/1/3", "DESKTOP"),
            cat(4L, 0L, 1, "/4", "OFFICE")
        ));
        List<CategoryNode> tree = categoryService.tree();
        assertEquals(2, tree.size(), "should have 2 roots");
        CategoryNode it = tree.get(0);
        assertEquals("IT", it.getName());
        assertEquals(2, it.getChildren().size(), "IT should have 2 children");
    }

    // 2. 跨租户 getById → 404
    @Test
    void getById_crossTenant_returns404() {
        AstCategory c = cat(1L, 0L, 1, "/1", "IT");
        c.setTenantId(99L);
        when(categoryMapper.selectById(1L)).thenReturn(c);
        ServiceException ex = assertThrows(ServiceException.class, () -> categoryService.getById(1L));
        assertEquals(404, ex.getCode());
    }

    // 3. delete 有子节点 → 409
    @Test
    void delete_withChildren_rejected() {
        when(categoryMapper.selectById(1L)).thenReturn(cat(1L, 0L, 1, "/1", "IT"));
        when(categoryMapper.selectCount(any())).thenReturn(2L);
        ServiceException ex = assertThrows(ServiceException.class, () -> categoryService.delete(1L));
        assertEquals(409, ex.getCode());
    }

    // 4. 缺失 tenant context → 401
    @Test
    void getById_missingContext_returns401() {
        UserContextHolder.clear();
        ServiceException ex = assertThrows(ServiceException.class, () -> categoryService.getById(1L));
        assertEquals(401, ex.getCode());
    }

    // 5. create 写入 root 节点（parent_id=0）→ path="/id"
    @Test
    void create_rootPath() {
        AstCategory req = new AstCategory();
        req.setCode("ROOT");
        req.setName("Root");
        AstCategory out = categoryService.create(req);
        // path 应为 "/" + out.id；因为 insert 会回写 id
        assertTrue(out.getPath().startsWith("/"));
        verify(categoryMapper).updateById(any(AstCategory.class));
    }
}
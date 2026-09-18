# ADR-0011: 数据权限 5 级模型

## 状态
2026-09-18 已决策

## 背景
企业内部常见权限需求：
- HR 总监看全公司
- HR 专员只看本部门
- 普通员工只看自己
- 销售经理看本部门及下级

需要一套可配置、与角色解耦的数据权限模型。

## 决策
**5 级 data_scope 模型**，存于角色表 `sys_role.data_scope`，由 MyBatis 拦截器自动拼接 SQL。

| 值 | 名称 | 语义 | SQL 注入 |
|---|---|---|---|
| 1 | ALL | 全部数据 | 无 |
| 2 | DEPT | 本部门 | `AND dept_id = #{currentDeptId}` |
| 3 | DEPT_AND_CHILD | 本部门及下级 | `AND dept_id IN (递归子部门)` |
| 4 | SELF | 本人 | `AND user_id = #{currentUserId}` |
| 5 | CUSTOM | 自定义 | `AND dept_id IN #{customDeptIds}` |

## 数据结构

```sql
ALTER TABLE sys_role ADD COLUMN data_scope TINYINT DEFAULT 1 NOT NULL COMMENT '1-全部 2-本部门 3-本部门及下级 4-本人 5-自定义';
CREATE TABLE sys_role_custom_dept (
    role_id BIGINT NOT NULL,
    dept_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, dept_id)
);
```

## 实现

```java
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DataScope {
    /** 部门表别名 */
    String deptAlias() default "";
    /** 用户表别名 */
    String userAlias() default "";
    /** 权限字符（如 hr:employee:list） */
    String permission() default "";
}

@Component
public class DataScopeInterceptor implements InnerInterceptor {
    public void beforeQuery(Executor executor, MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) {
        // 1. 取当前用户 data_scope
        // 2. 解析 @DataScope 注解
        // 3. 改写 SQL 追加 WHERE 条件
    }
}
```

## 使用示例

```java
@DataScope(deptAlias = "d", userAlias = "u")
public List<EmployeeVO> listEmployees(EmployeeQuery query) {
    return employeeMapper.selectList(query);   // SQL 已被拦截器改写
}
```

```xml
<select id="selectList">
    SELECT e.*, d.name as dept_name
    FROM hr_employee e
    LEFT JOIN sys_dept d ON e.dept_id = d.id
    WHERE e.deleted = 0
    <if test="params.name != null">AND e.name LIKE #{params.name}</if>
    <!-- ${dataScopeSql}  拦截器注入 -->
</select>
```

## 与多租户的区别（关键）

| 维度 | 多租户 | 数据权限 |
|---|---|---|
| 目的 | 公司间隔离 | 公司内层级隔离 |
| 字段 | `tenant_id` | `dept_id` / `user_id` |
| 强制度 | 不可绕过 | 可由管理员绕过 |
| 拦截器 | TenantLineInterceptor | DataScopeInterceptor |

**两个拦截器叠加执行**，最终 SQL：

```sql
SELECT * FROM hr_employee
WHERE tenant_id = 1                    -- 多租户隔离（强制）
  AND deleted = 0
  AND dept_id IN (100, 101, 102)       -- 数据权限（自动注入）
  AND name LIKE '%张%'                  -- 业务参数
```

## 边界情况

1. **超管**：data_scope = 1，绕过数据权限拦截
2. **本人查询**：通常配合 data_scope = 4
3. **跨部门查询**（如"查看所有员工生日"）：用 `permission() = "hr:employee:view_all"` 标记为特殊查询，跳过拦截
4. **递归子部门查询**：用 `sys_dept.path`（如 `100,100,101,100,102`）LIKE 匹配

## 后果

### 优点
- 单一角色覆盖 80% 场景
- 用户无需手动加 WHERE 条件
- 自定义（5）支持复杂场景

### 缺点
- 拦截器对原生 SQL（@Select 注解）支持弱，需 XML 写 `${dataScopeSql}`
- 自定义（5）性能较差（IN 100+ 个 dept_id）
- 子部门递归查询需额外 SQL

### 风险与缓解
- **风险**：超管账号泄露导致全公司数据泄露
- **缓解**：超管开启 MFA + IP 白名单 + 操作实时告警

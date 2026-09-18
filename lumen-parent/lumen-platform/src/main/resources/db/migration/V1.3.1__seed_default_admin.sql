-- 默认部门（必须在 admin user 之前，因为它有外键概念）
INSERT INTO sys_dept (dept_id, tenant_id, parent_id, ancestors, dept_name, order_num, leader_name, status, is_builtin) VALUES
(100, 1, 0,   '0',       'Lumen 总部',  1, 'admin', '0', 1),
(101, 1, 100, '0,100',   '技术部',      1, NULL,    '0', 0),
(102, 1, 100, '0,100',   '人事部',      2, NULL,    '0', 0),
(103, 1, 100, '0,100',   '财务部',      3, NULL,    '0', 0);

-- 默认超级管理员（密码 admin123 的 BCrypt 哈希）
-- 原始密码: admin123
-- 哈希: $2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2
INSERT INTO sys_user (user_id, tenant_id, dept_id, user_name, nick_name, password, status, data_scope, mfa_enabled)
VALUES (1, 1, 100, 'admin', '超级管理员', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '0', 1, 0);

-- 默认角色
INSERT INTO sys_role (role_id, tenant_id, role_name, role_key, role_sort, data_scope, status) VALUES
(1, 1, '超级管理员', 'super_admin', 1, 1, '0'),
(2, 1, '普通用户',   'user',        2, 4, '0');

-- 用户-角色绑定
INSERT INTO sys_user_role (user_id, role_id) VALUES (1, 1);

-- 默认岗位
INSERT INTO sys_post (post_id, tenant_id, post_code, post_name, post_sort, status) VALUES
(1, 1, 'CEO',  '首席执行官', 1, '0'),
(2, 1, 'CTO',  '首席技术官', 2, '0'),
(3, 1, 'HR',   '人力资源',   3, '0'),
(4, 1, 'DEV',  '开发工程师', 4, '0');

-- 默认字典（用户性别 / 系统状态）
INSERT INTO sys_dict_type (dict_id, tenant_id, dict_name, dict_type, status) VALUES
(1, 1, '用户性别', 'sys_user_sex',     '0'),
(2, 1, '菜单状态', 'sys_show_hide',    '0'),
(3, 1, '系统开关', 'sys_normal_disable','0'),
(4, 1, '系统是否', 'sys_yes_no',       '0'),
(5, 1, '操作类型', 'sys_oper_type',    '0');

INSERT INTO sys_dict_data (dict_sort, tenant_id, dict_label, dict_value, dict_type, status, is_default) VALUES
(1, 1, '男', '0', 'sys_user_sex', '0', 'Y'),
(2, 1, '女', '1', 'sys_user_sex', '0', 'N'),
(3, 1, '未知', '2', 'sys_user_sex', '0', 'N'),
(1, 1, '显示', '0', 'sys_show_hide', '0', 'Y'),
(2, 1, '隐藏', '1', 'sys_show_hide', '0', 'N'),
(1, 1, '正常', '0', 'sys_normal_disable', '0', 'Y'),
(2, 1, '停用', '1', 'sys_normal_disable', '0', 'N'),
(1, 1, '是', 'Y', 'sys_yes_no', '0', 'Y'),
(2, 1, '否', 'N', 'sys_yes_no', '0', 'N'),
(1, 1, '新增', '1', 'sys_oper_type', '0', 'N'),
(2, 1, '修改', '2', 'sys_oper_type', '0', 'N'),
(3, 1, '删除', '3', 'sys_oper_type', '0', 'N');

-- 默认参数配置
INSERT INTO sys_config (tenant_id, config_name, config_key, config_value, config_type, is_builtin) VALUES
(1, '账号自助注册',  'sys.account.registerUser', 'false',                  'Y', 1),
(1, '用户管理-账号初始密码', 'sys.user.initPassword',  '123456',           'Y', 1),
(1, '登录-验证码开关',       'sys.account.captchaEnabled', 'true',        'Y', 1);

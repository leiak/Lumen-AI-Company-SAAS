DROP TABLE IF EXISTS sys_role_menu;
DROP TABLE IF EXISTS sys_role_dept;
DROP TABLE IF EXISTS sys_user_role;
DROP TABLE IF EXISTS sys_role;
CREATE TABLE sys_role (
    role_id            BIGINT          NOT NULL AUTO_INCREMENT,
    tenant_id          BIGINT          NOT NULL DEFAULT 1,
    role_name          VARCHAR(50)     NOT NULL,
    role_key           VARCHAR(50)     NOT NULL,
    role_sort          INT             NOT NULL DEFAULT 0,
    data_scope         TINYINT         NOT NULL DEFAULT 1 COMMENT '1-全部 2-本部门 3-本部门及下级 4-本人 5-自定义',
    menu_check_strictly TINYINT        NOT NULL DEFAULT 1,
    dept_check_strictly TINYINT        NOT NULL DEFAULT 1,
    status             CHAR(1)         NOT NULL DEFAULT '0',
    api_pattern        VARCHAR(500)    DEFAULT NULL,
    i18n_key           VARCHAR(100)    DEFAULT NULL,
    remark             VARCHAR(500)    DEFAULT NULL,
    create_by          BIGINT          NOT NULL DEFAULT 0,
    create_time        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by          BIGINT          NOT NULL DEFAULT 0,
    update_time        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted            TINYINT         NOT NULL DEFAULT 0,
    PRIMARY KEY (role_id),
    UNIQUE KEY uk_sys_role_tenant_key (tenant_id, role_key, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

CREATE TABLE sys_user_role (
    user_id            BIGINT          NOT NULL,
    role_id            BIGINT          NOT NULL,
    PRIMARY KEY (user_id, role_id),
    KEY idx_user_role_role (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户-角色关联';

CREATE TABLE sys_role_dept (
    role_id            BIGINT          NOT NULL,
    dept_id            BIGINT          NOT NULL,
    PRIMARY KEY (role_id, dept_id),
    KEY idx_role_dept_dept (dept_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色-部门（自定义 data_scope 用）';

CREATE TABLE sys_role_menu (
    role_id            BIGINT          NOT NULL,
    menu_id            BIGINT          NOT NULL,
    PRIMARY KEY (role_id, menu_id),
    KEY idx_role_menu_menu (menu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色-菜单';

CREATE TABLE sys_menu (
    menu_id            BIGINT          NOT NULL AUTO_INCREMENT,
    menu_name          VARCHAR(50)     NOT NULL,
    parent_id          BIGINT          NOT NULL DEFAULT 0,
    order_num          INT             NOT NULL DEFAULT 0,
    path               VARCHAR(200)    DEFAULT '',
    component          VARCHAR(255)    DEFAULT NULL,
    query              VARCHAR(255)    DEFAULT NULL,
    is_frame           CHAR(1)         NOT NULL DEFAULT '1',
    is_cache           CHAR(1)         NOT NULL DEFAULT '0',
    menu_type          CHAR(1)         NOT NULL DEFAULT '',
    visible            CHAR(1)         NOT NULL DEFAULT '0',
    status             CHAR(1)         NOT NULL DEFAULT '0',
    perms              VARCHAR(100)    DEFAULT NULL,
    icon               VARCHAR(100)    DEFAULT '#',
    api_pattern        VARCHAR(500)    DEFAULT NULL,
    remark             VARCHAR(500)    DEFAULT NULL,
    create_by          BIGINT          NOT NULL DEFAULT 0,
    create_time        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by          BIGINT          NOT NULL DEFAULT 0,
    update_time        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted            TINYINT         NOT NULL DEFAULT 0,
    PRIMARY KEY (menu_id),
    KEY idx_menu_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='菜单权限表';

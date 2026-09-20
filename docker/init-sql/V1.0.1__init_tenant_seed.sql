INSERT IGNORE INTO tenant (id, code, name, short_name, package_id, status, expire_at) VALUES
(1, 'default', '默认租户', '默认', 1, 2, DATE_ADD(NOW(), INTERVAL 100 YEAR));

INSERT IGNORE INTO tenant_package (id, code, name, modules, max_users, max_storage_gb, max_employees, price_cents, is_builtin) VALUES
(1, 'free',       '免费版', JSON_ARRAY('hr_basic','workflow'),                            10,   1,    50,    0,        1),
(2, 'standard',   '标准版', JSON_ARRAY('hr','finance','contract','procurement','assets'),  100,  50,   1000,  99900,    0),
(3, 'enterprise', '企业版', JSON_ARRAY('hr','finance','contract','procurement','assets',
                                       'inventory','sales','payroll','bi','mobile'),       9999, 9999, 999999, 999900, 0);

INSERT IGNORE INTO common_seq (seq_name, current_val, prefix, description) VALUES
('hr_employee_no', 1, 'E', '员工编号'),
('hr_contract_no', 1, 'C', '合同编号');

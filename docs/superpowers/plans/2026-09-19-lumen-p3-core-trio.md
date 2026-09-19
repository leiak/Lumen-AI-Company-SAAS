# Lumen P3 核心三件套 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 P1 (5 service 骨架) + P2 (SSO/MFA/加密/Sentinel/Canary) 的基础上,新增 3 个核心业务服务骨架: 工作流引擎、文件存储、消息中心。后续 14 个业务模块 (P2 业务层 + P3 协同 + P4 高级) 都依赖这三个底座。

**Architecture:** 沿用 P1 的 service-per-bounded-context 模式: 一个 Maven module = 一个 Spring Boot 服务 = 一个 MySQL 表前缀 = 一组 CRUD endpoints。复用 P2 已建好的 `lumen-common-{core,web,security,log,mybatis,redis,crypto,sso,sentinel}` 共用层。

**Tech Stack:** Spring Boot 3.2.5 + Spring Cloud 2023.0.1 + Spring Cloud Alibaba 2023.0.1.0, Java 17, MyBatis-Plus 3.5.5, jjwt 0.12.5, Flyway 9, MinIO 8.5.x (file-storage), WebSocket (message-center).

**实现粒度:** P1+P2 模式 = service + entity + controller + Flyway + 单测 + E2E。骨架完整但业务规则较浅; 接口签名稳定,业务逻辑留 TODO。

---

## P3 范围

3 个新服务:
- **lumen-workflow** (port 9205, app `workflow-service`, 表前缀 `wf_`)
- **lumen-file** (port 9206, app `file-service`, 表前缀 `file_`)
- **lumen-message** (port 9207, app `message-service`, 表前缀 `msg_`)

每个服务独立做 Subagent-Driven Development: implement → spec-review → fix → code-quality-review → fix → done。

---

## Task 1: 工作流引擎 (lumen-workflow)

### 文件

**创建:**
- `lumen-parent/lumen-workflow/pom.xml` — parent reference, deps: `lumen-common-{core,web,security,mybatis}`, MySQL connector, Spring Boot plugin
- `lumen-parent/lumen-workflow/src/main/java/com/lumen/workflow/WorkflowApplication.java`
- `lumen-parent/lumen-workflow/src/main/java/com/lumen/workflow/entity/WfDefinition.java` — 流程定义 (id, key, name, version, bpmnXml TEXT, status, category)
- `lumen-parent/lumen-workflow/src/main/java/com/lumen/workflow/entity/WfInstance.java` — 流程实例 (id, definitionId, businessKey, status, currentNodeKey, starter, startTime, endTime)
- `lumen-parent/lumen-workflow/src/main/java/com/lumen/workflow/entity/WfTask.java` — 待办任务 (id, instanceId, nodeKey, assignee, candidateUsers JSON, status, dueTime, completeTime)
- `lumen-parent/lumen-workflow/src/main/java/com/lumen/workflow/entity/WfTaskHistory.java` — 历史任务 (只读归档)
- `lumen-parent/lumen-workflow/src/main/java/com/lumen/workflow/mapper/WfDefinitionMapper.java` + `WfInstanceMapper.java` + `WfTaskMapper.java` + `WfTaskHistoryMapper.java`
- `lumen-parent/lumen-workflow/src/main/java/com/lumen/workflow/service/DefinitionService.java` — `list/page/create/update/delete/deploy(bpmnXml)`
- `lumen-parent/lumen-workflow/src/main/java/com/lumen/workflow/service/InstanceService.java` — `start(definitionKey, businessKey, variables)`, `cancel(instanceId, reason)`, `get(instanceId)`, `pageByCurrentUser`
- `lumen-parent/lumen-workflow/src/main/java/com/lumen/workflow/service/TaskService.java` — `todoList(userId)`, `done(taskId, comment)`, `transfer(taskId, toUserId, comment)`, `addSign(taskId, userIds, comment)`, `reject(taskId, targetNodeKey, comment)`
- `lumen-parent/lumen-workflow/src/main/java/com/lumen/workflow/service/EngineService.java` — 简化的状态机执行器: 解析 BPMN 节点的 outgoing edges,根据 assignee/候选人规则定位下一节点 (留 TODO 给复杂网关: parallel/inclusive/exclusive 完整支持)
- `lumen-parent/lumen-workflow/src/main/java/com/lumen/workflow/controller/DefinitionController.java` — `GET/POST/PUT/DELETE /workflow/definition`
- `lumen-parent/lumen-workflow/src/main/java/com/lumen/workflow/controller/InstanceController.java` — `POST /workflow/instance/start`, `GET /workflow/instance/{id}`, `GET /workflow/instance/page`, `POST /workflow/instance/{id}/cancel`
- `lumen-parent/lumen-workflow/src/main/java/com/lumen/workflow/controller/TaskController.java` — `GET /workflow/task/todo`, `POST /workflow/task/{id}/done`, `POST /workflow/task/{id}/transfer`, `POST /workflow/task/{id}/addSign`, `POST /workflow/task/{id}/reject`
- `lumen-parent/lumen-workflow/src/main/java/com/lumen/workflow/controller/HealthController.java` — `/workflow/health`
- `lumen-parent/lumen-workflow/src/main/java/com/lumen/workflow/dto/StartInstanceRequest.java` — `{definitionKey, businessKey, variables: Map<String,Object>}`
- `lumen-parent/lumen-workflow/src/main/java/com/lumen/workflow/dto/TaskActionRequest.java` — `{comment, ...actionSpecific}`
- `lumen-parent/lumen-workflow/src/main/resources/application.yml` — port 9205, app `workflow-service`, Nacos `lumen-public`, MySQL `lumen_db`
- `lumen-parent/lumen-platform/src/main/resources/db/migration/V2.0.0__init_workflow.sql` — 表结构 (lumen-platform 拥有所有 Flyway)
- `lumen-parent/lumen-workflow/src/test/java/com/lumen/workflow/service/InstanceServiceTest.java` — Mockito 单测
- `lumen-parent/lumen-workflow/src/test/java/com/lumen/workflow/service/TaskServiceTest.java` — Mockito 单测

**修改:**
- `lumen-parent/pom.xml` — 加 `<module>lumen-workflow</module>`

### Schema (`V2.0.0__init_workflow.sql`)

```sql
-- 流程定义
CREATE TABLE wf_definition (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    def_key VARCHAR(64) NOT NULL COMMENT '流程定义KEY',
    name VARCHAR(128) NOT NULL,
    version INT NOT NULL DEFAULT 1,
    category VARCHAR(32) DEFAULT NULL,
    bpmn_xml LONGTEXT COMMENT 'BPMN 2.0 XML',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0=草稿 1=已发布 2=已下线',
    create_by BIGINT DEFAULT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_by BIGINT DEFAULT NULL,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    UNIQUE KEY uk_def_key_version (def_key, version, deleted)
) COMMENT '流程定义';

-- 流程实例
CREATE TABLE wf_instance (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    definition_id BIGINT NOT NULL,
    def_key VARCHAR(64) NOT NULL,
    business_key VARCHAR(128) NOT NULL COMMENT '业务单据ID',
    tenant_id BIGINT NOT NULL DEFAULT 0,
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0=进行中 1=已完成 2=已取消',
    current_node_key VARCHAR(64) DEFAULT NULL,
    variables JSON DEFAULT NULL,
    starter BIGINT NOT NULL,
    start_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    end_time DATETIME DEFAULT NULL,
    create_by BIGINT DEFAULT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_by BIGINT DEFAULT NULL,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    INDEX idx_business_key (business_key),
    INDEX idx_starter (starter, status),
    INDEX idx_def_key (def_key)
) COMMENT '流程实例';

-- 待办任务
CREATE TABLE wf_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    instance_id BIGINT NOT NULL,
    node_key VARCHAR(64) NOT NULL,
    node_name VARCHAR(128) DEFAULT NULL,
    assignee BIGINT DEFAULT NULL,
    candidate_users JSON DEFAULT NULL COMMENT '候选人数组',
    candidate_roles JSON DEFAULT NULL,
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0=待办 1=已办 2=已转办 3=已加签 4=已驳回',
    due_time DATETIME DEFAULT NULL,
    complete_time DATETIME DEFAULT NULL,
    comment TEXT DEFAULT NULL,
    create_by BIGINT DEFAULT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_by BIGINT DEFAULT NULL,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    INDEX idx_assignee (assignee, status),
    INDEX idx_instance (instance_id, status),
    INDEX idx_due (due_time, status)
) COMMENT '待办任务';

-- 历史任务归档 (任务完成后异步迁移, 或简单复制)
CREATE TABLE wf_task_history (
    id BIGINT PRIMARY KEY,
    instance_id BIGINT NOT NULL,
    node_key VARCHAR(64) NOT NULL,
    assignee BIGINT DEFAULT NULL,
    action VARCHAR(32) NOT NULL COMMENT 'done/transfer/addSign/reject',
    comment TEXT DEFAULT NULL,
    operated_by BIGINT NOT NULL,
    operated_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_instance (instance_id)
) COMMENT '任务操作历史';
```

### 业务规则 (P1 模式: 留 TODO 给复杂逻辑)

- `EngineService.startInstance(definitionKey, businessKey, variables)`:
  1. 找最新已发布 (status=1) 的 definition
  2. 解析 BPMN: 用 `org.activiti.bpmn.converter` 或 JDK `javax.xml.parsers` 简单解析 (留 TODO: 完整 BPMN 2.0 解析)
  3. 找到 startEvent → 第一个 userTask → 写 `wf_instance` + 第一个 `wf_task`
  4. 返回 instanceId + 第一个 taskId
- `EngineService.completeTask(taskId, action, comment)`:
  1. 找当前 task + instance
  2. 写 `wf_task_history`
  3. 更新 task.status = action 对应值
  4. 解析当前节点的 outgoing edge → 下一个节点
  5. 创建下一个 task, 更新 instance.current_node_key
  6. 如果到达 endEvent → instance.status = 1
- assignee 匹配规则:
  - 有 assignee → 直接分配
  - candidate_users 包含 userId → 认领
  - candidate_roles 包含用户角色 → 候选
  - 都没有 → 抛 `ServiceException(404, "No assignee matched")`

### 跨服务集成 (留 TODO)

- 启动流程时通过 OpenFeign 调用 auth-service 校验用户存在 (P3.5)
- 任务完成时通过 MQ 发送 `workflow.task.completed.v1` 事件给 message-center (P3.5)
- 当前 P3 阶段: 同步 HTTP + TODO 注释即可

### 单测覆盖

- `InstanceServiceTest`: startInstance happy path / 已存在业务键冲突 / definition 未发布
- `TaskServiceTest`: done happy path / transfer 流转 / reject 退回上一节点 / addSign 加签 / assignee 不匹配

### Commit

```
git add -A && git commit -m "feat(workflow): definition + instance + task + history with BPMN state machine"
```

---

## Task 2: 文件存储 (lumen-file)

### 文件

**创建:**
- `lumen-parent/lumen-file/pom.xml` — deps: `lumen-common-{core,web,security,mybatis}`, MinIO `io.minio:minio:8.5.10`, Spring Boot plugin
- `lumen-parent/lumen-file/src/main/java/com/lumen/file/FileApplication.java`
- `lumen-parent/lumen-file/src/main/java/com/lumen/file/entity/FileMetadata.java` — (id, originalName, storagePath, bucket, size, contentType, md5, sha256, businessType, uploader, tenantId, status, accessUrl)
- `lumen-parent/lumen-file/src/main/java/com/lumen/file/entity/FileChunk.java` — 分片上传 (id, uploadId, chunkNumber, chunkSize, md5)
- `lumen-parent/lumen-file/src/main/java/com/lumen/file/mapper/FileMetadataMapper.java` + `FileChunkMapper.java`
- `lumen-parent/lumen-file/src/main/java/com/lumen/file/storage/StorageProvider.java` — interface: `put(bucket, key, stream)`, `get(bucket, key)`, `delete(bucket, key)`, `presignedUrl(bucket, key, expiry)`, `initMultipartUpload(bucket, key)`, `uploadPart(...)`, `completeMultipartUpload(...)`
- `lumen-parent/lumen-file/src/main/java/com/lumen/file/storage/MinIoStorageProvider.java` — MinIO 实现 (核心: `io.minio.MinioClient`)
- `lumen-parent/lumen-file/src/main/java/com/lumen/file/service/FileService.java` — `upload`, `download(id)`, `delete(id)`, `getMetadata(id)`, `presignedUrl(id, expiry)`, `preview(id)` (inline disposition)
- `lumen-parent/lumen-file/src/main/java/com/lumen/file/service/ChunkService.java` — `initMultipartUpload`, `uploadPart`, `complete`, `abort`
- `lumen-parent/lumen-file/src/main/java/com/lumen/file/controller/FileController.java` — `POST /file/upload` (multipart), `GET /file/{id}`, `GET /file/{id}/download`, `GET /file/{id}/preview`, `DELETE /file/{id}`, `GET /file/{id}/url?expiry=3600`
- `lumen-parent/lumen-file/src/main/java/com/lumen/file/controller/ChunkController.java` — `POST /file/chunk/init`, `POST /file/chunk/upload`, `POST /file/chunk/complete`, `POST /file/chunk/abort`
- `lumen-parent/lumen-file/src/main/java/com/lumen/file/controller/HealthController.java` — `/file/health`
- `lumen-parent/lumen-file/src/main/resources/application.yml` — port 9206, MinIO config (`lumen.storage.minio.endpoint=http://localhost:9000`, access/secret key, default bucket)
- `lumen-parent/lumen-platform/src/main/resources/db/migration/V2.1.0__init_file.sql` — 表结构
- `lumen-parent/lumen-file/src/test/java/com/lumen/file/service/FileServiceTest.java`

**修改:**
- `lumen-parent/pom.xml` — 加 `<module>lumen-file</module>`

### Schema (`V2.1.0__init_file.sql`)

```sql
CREATE TABLE file_metadata (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    original_name VARCHAR(255) NOT NULL,
    storage_path VARCHAR(512) NOT NULL COMMENT '对象存储 key',
    bucket VARCHAR(64) NOT NULL,
    size_bytes BIGINT NOT NULL,
    content_type VARCHAR(128) DEFAULT NULL,
    md5 VARCHAR(32) DEFAULT NULL,
    sha256 VARCHAR(64) DEFAULT NULL,
    business_type VARCHAR(32) DEFAULT NULL COMMENT 'avatar/contract/expense/...',
    business_id VARCHAR(128) DEFAULT NULL,
    uploader BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1=可用 0=已删除',
    access_count INT NOT NULL DEFAULT 0,
    create_by BIGINT DEFAULT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_by BIGINT DEFAULT NULL,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    INDEX idx_business (business_type, business_id),
    INDEX idx_uploader (uploader, create_time),
    INDEX idx_md5 (md5),
    INDEX idx_sha256 (sha256)
) COMMENT '文件元数据';

CREATE TABLE file_chunk (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    upload_id VARCHAR(64) NOT NULL,
    file_md5 VARCHAR(32) NOT NULL,
    chunk_number INT NOT NULL,
    chunk_size INT NOT NULL,
    chunk_md5 VARCHAR(32) DEFAULT NULL,
    storage_path VARCHAR(512) DEFAULT NULL,
    uploaded TINYINT NOT NULL DEFAULT 0,
    uploader BIGINT NOT NULL,
    total_chunks INT NOT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    UNIQUE KEY uk_upload_chunk (upload_id, chunk_number, deleted),
    INDEX idx_upload (upload_id, uploader)
) COMMENT '分片上传';
```

### 业务规则

- `FileService.upload(MultipartFile, businessType, businessId)`:
  1. 计算 md5 + sha256
  2. 检查是否已存在 (按 md5 查) → 秒传 (返回已有记录)
  3. 否则上传到 MinIO `bucket/path/uuid.{ext}`
  4. 写 file_metadata
- `FileService.presignedUrl(id, expiry)`:
  1. 查 metadata
  2. MinIO `presignedGetObject(bucket, objectName, expiry)`
- 分片上传: 客户端先 `init` 拿到 `uploadId`,然后 `uploadPart` 传每个分片,最后 `complete` 触发服务端合并

### 集成 (留 TODO)

- 当前用本地 MinIO (或 S3-compatible)。生产部署改 endpoint 即可
- 文件去重: 同租户内 md5 唯一去重 (跨租户不去重)

### Commit

```
git add -A && git commit -m "feat(file): MinIO storage + metadata + chunked upload + presigned URL"
```

---

## Task 3: 消息中心 (lumen-message)

### 文件

**创建:**
- `lumen-parent/lumen-message/pom.xml` — deps: `lumen-common-{core,web,security,mybatis,redis}`, Spring Boot plugin
- `lumen-parent/lumen-message/src/main/java/com/lumen/message/MessageApplication.java`
- `lumen-parent/lumen-message/src/main/java/com/lumen/message/entity/MsgChannel.java` — (id, code, name, type [email/sms/site/dingtalk/wechat/webhook], config JSON, enabled, tenantId)
- `lumen-parent/lumen-message/src/main/java/com/lumen/message/entity/MsgTemplate.java` — (id, code, channelCode, subject, content, variables JSON, enabled, tenantId)
- `lumen-parent/lumen-message/src/main/java/com/lumen/message/entity/MsgNotification.java` — (id, channelCode, templateCode, recipientUserId, recipientAddress, subject, content, status [0=pending 1=sent 2=failed 3=read], retryCount, sentTime, readTime)
- `lumen-parent/lumen-message/src/main/java/com/lumen/message/entity/MsgSubscription.java` — (id, userId, eventType, channelCode, enabled) — 用户订阅配置
- `lumen-parent/lumen-message/src/main/java/com/lumen/message/mapper/MsgChannelMapper.java` + `MsgTemplateMapper.java` + `MsgNotificationMapper.java` + `MsgSubscriptionMapper.java`
- `lumen-parent/lumen-message/src/main/java/com/lumen/message/sender/Sender.java` — interface: `send(notification): SendResult`
- `lumen-parent/lumen-message/src/main/java/com/lumen/message/sender/SiteSender.java` — 站内信 (写 DB)
- `lumen-parent/lumen-message/src/main/java/com/lumen/message/sender/EmailSender.java` — JavaMailSender (留 TODO: 真实 SMTP 配置)
- `lumen-parent/lumen-message/src/main/java/com/lumen/message/sender/SmsSender.java` — 留 TODO: 接阿里云/腾讯云短信
- `lumen-parent/lumen-message/src/main/java/com/lumen/message/sender/WebSocketSender.java` — STOMP push (留 TODO: STOMP server config)
- `lumen-parent/lumen-message/src/main/java/com/lumen/message/service/ChannelService.java` — channel CRUD
- `lumen-parent/lumen-message/src/main/java/com/lumen/message/service/TemplateService.java` — template CRUD + render (variable substitution)
- `lumen-parent/lumen-message/src/main/java/com/lumen/message/service/NotificationService.java` — `send(templateCode, recipientUserIds, variables)`, `listByUser(userId)`, `markRead(id)`
- `lumen-parent/lumen-message/src/main/java/com/lumen/message/service/SubscriptionService.java` — 订阅 CRUD
- `lumen-parent/lumen-message/src/main/java/com/lumen/message/controller/ChannelController.java` + `TemplateController.java` + `NotificationController.java` + `SubscriptionController.java` + `HealthController.java`
- `lumen-parent/lumen-message/src/main/java/com/lumen/message/websocket/NotificationWebSocketHandler.java` — 简单的 WebSocket handler (推送站内信)
- `lumen-parent/lumen-message/src/main/resources/application.yml` — port 9207
- `lumen-parent/lumen-platform/src/main/resources/db/migration/V2.2.0__init_message.sql`
- `lumen-parent/lumen-message/src/test/java/com/lumen/message/service/NotificationServiceTest.java` + `TemplateServiceTest.java`

**修改:**
- `lumen-parent/pom.xml` — 加 `<module>lumen-message</module>`
- `lumen-parent/lumen-message/pom.xml` — 加 `org.springframework.boot:spring-boot-starter-websocket`

### Schema (`V2.2.0__init_message.sql`)

```sql
CREATE TABLE msg_channel (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(32) NOT NULL,
    name VARCHAR(64) NOT NULL,
    type VARCHAR(16) NOT NULL COMMENT 'email/sms/site/dingtalk/wechat/webhook',
    config JSON DEFAULT NULL COMMENT 'SMTP host, app secret, etc.',
    enabled TINYINT NOT NULL DEFAULT 1,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    create_by BIGINT DEFAULT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_by BIGINT DEFAULT NULL,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    UNIQUE KEY uk_code_tenant (code, tenant_id, deleted)
) COMMENT '消息渠道';

CREATE TABLE msg_template (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(64) NOT NULL,
    channel_code VARCHAR(32) NOT NULL,
    subject VARCHAR(255) DEFAULT NULL,
    content TEXT NOT NULL,
    variables JSON DEFAULT NULL COMMENT '变量列表 [name, code]',
    enabled TINYINT NOT NULL DEFAULT 1,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    create_by BIGINT DEFAULT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_by BIGINT DEFAULT NULL,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    UNIQUE KEY uk_code_channel_tenant (code, channel_code, tenant_id, deleted)
) COMMENT '消息模板';

CREATE TABLE msg_notification (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    channel_code VARCHAR(32) NOT NULL,
    template_code VARCHAR(64) DEFAULT NULL,
    recipient_user_id BIGINT DEFAULT NULL,
    recipient_address VARCHAR(255) DEFAULT NULL COMMENT 'email/phone/webhook url',
    subject VARCHAR(255) DEFAULT NULL,
    content TEXT DEFAULT NULL,
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0=pending 1=sent 2=failed 3=read',
    retry_count INT NOT NULL DEFAULT 0,
    error_message VARCHAR(512) DEFAULT NULL,
    sent_time DATETIME DEFAULT NULL,
    read_time DATETIME DEFAULT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    create_by BIGINT DEFAULT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_by BIGINT DEFAULT NULL,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    INDEX idx_recipient_status (recipient_user_id, status, create_time),
    INDEX idx_status (status, create_time),
    INDEX idx_template (template_code, create_time)
) COMMENT '消息通知';

CREATE TABLE msg_subscription (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    event_type VARCHAR(64) NOT NULL COMMENT 'workflow.task.created, finance.expense.approved, ...',
    channel_code VARCHAR(32) NOT NULL,
    enabled TINYINT NOT NULL DEFAULT 1,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    create_by BIGINT DEFAULT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_by BIGINT DEFAULT NULL,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    UNIQUE KEY uk_user_event_channel (user_id, event_type, channel_code, deleted)
) COMMENT '用户订阅';
```

### 业务规则

- `NotificationService.send(templateCode, recipientUserIds, variables)`:
  1. 查 template → channel
  2. 渲染 subject + content (替换 `{{varName}}` 为 variables 值)
  3. 按 channel type 路由到对应 Sender
  4. 写 `msg_notification` (status=pending → sent/failed)
  5. 失败自动重试 (retry_count < 3 → 重新发送)
- `NotificationService.listByUser(userId, status)`: 分页查用户的通知
- `NotificationService.markRead(id)`: 用户点击站内信 → status=3, read_time=now()
- WebSocket: 注册端点 `/ws/message`,用户登录后建立连接,后端 push 站内信

### 集成 (留 TODO)

- EmailSender 留 TODO: 接 JavaMailSender (smtp host/port/user/pass 在 channel.config JSON 里)
- SmsSender 留 TODO: 接阿里云短信 SDK (channel.config 里有 accessKey/templateCode)
- Dingtalk/Wechat Sender 留 TODO: Webhook 模式 (channel.config 里有 webhook URL)
- WebSocket STOMP 留 TODO: 完整 STOMP broker 配置

### Commit

```
git add -A && git commit -m "feat(message): channel + template + notification + subscription + WebSocket"
```

---

## 任务执行顺序

1. Task 1: workflow → Task 2: file → Task 3: message
2. 每个 task 走完整 Subagent-Driven Development: implement → spec-review → fix → code-quality-review → fix
3. 全部完成后 → Task 4: 联调验证 (重启服务 + gateway E2E)

## Self-Review

**Spec coverage:**
- 设计文档里的 17 模块 → P3 先建 3 个底座 (workflow/file/message) ✅
- 后续 14 个业务模块都会引用 ✅

**Placeholder scan:**
- 所有代码块完整 ✅
- 留 TODO 的地方明确标注 P3.5 处理 ✅

**Type consistency:**
- 实体继承 BaseEntity ✅
- R<T> 统一响应 ✅
- 端口号不冲突 (9205/9206/9207) ✅

**Nacos 注册:**
- 三个服务都注册到 `lumen-public` 命名空间,gateway 自动发现 ✅
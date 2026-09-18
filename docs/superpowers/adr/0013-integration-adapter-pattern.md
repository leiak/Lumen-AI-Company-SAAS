# ADR-0013: 集成层 Adapter 模式

## 状态
2026-09-18 已决策

## 背景
系统需对接多个第三方：钉钉/企微/飞书、电子签章（法大大/契约锁/e签宝）、金税、银行、SMS/邮件/OCR。
直接散落在业务代码里会导致：
- 第三方 SDK 升级影响业务
- 切换供应商要改 N 个业务文件
- 单元测试难以 mock

## 决策
**所有第三方集成通过 Adapter 抽象层**，业务代码只依赖接口。

## Adapter 接口设计

### 协作平台 Adapter

```java
public interface CollaborationAdapter {
    /** SSO 登录 - 用授权码换用户信息 */
    SsoUserInfo ssoLogin(String code);
    
    /** 推送工作通知 */
    void pushWorkNotice(String userId, NoticeMessage msg);
    
    /** 发起审批 */
    String startApproval(ApprovalRequest req);
    
    /** 注册回调 */
    void registerCallback(String url, EventType type);
    
    /** 同步通讯录 */
    List<ExternalUser> syncContacts();
}

public interface ApprovalCallbackHandler {
    void handleApprovalResult(ApprovalResultEvent event);
}
```

### 电子签章 Adapter

```java
public interface ESignAdapter {
    /** 创建签署任务 */
    SignTaskId createSignTask(SignTask task);
    
    /** 查询签署状态 */
    SignStatus queryStatus(SignTaskId taskId);
    
    /** 下载签署后的文件 */
    byte[] downloadSignedDoc(SignTaskId taskId);
    
    /** 撤销签署 */
    void revoke(SignTaskId taskId, String reason);
}
```

### 短信 Adapter

```java
public interface SmsAdapter {
    SendResult send(String phone, String templateCode, Map<String, String> params);
    SendResult batchSend(List<String> phones, String templateCode, Map<String, String> params);
    SendResult queryStatus(String bizId);
}
```

### 银行 Adapter

```java
public interface BankAdapter {
    /** 生成代发文件 */
    BankFile generatePaymentFile(List<PaymentItem> items);
    
    /** 上传代发文件到银行 */
    String uploadPaymentFile(BankFile file);
    
    /** 查询代发结果 */
    List<PaymentResult> queryPaymentResult(String batchId);
    
    /** 查询账户余额 */
    BigDecimal queryBalance(String accountNo);
}
```

## 实现与切换

```java
// adapter-dingtalk/src/main/java
@Component("dingtalkAdapter")
public class DingtalkAdapter implements CollaborationAdapter { ... }

// adapter-wechatwork/src/main/java
@Component("wechatworkAdapter")
public class WechatworkAdapter implements CollaborationAdapter { ... }

// 业务代码
@Service
public class AuthService {
    @Autowired
    @Qualifier("dingtalkAdapter")     // 通过配置切换
    private CollaborationAdapter collab;
}
```

## 适配器规范

### 1. 业务字段 ↔ 第三方字段映射在 Adapter 内部
业务方不感知第三方字段命名。

### 2. 失败重试 + 熔断
```java
@SentinelResource(value = "esign.createSignTask", fallback = "createSignTaskFallback")
public SignTaskId createSignTask(SignTask task) {
    return eignClient.create(task);
}

public SignTaskId createSignTaskFallback(SignTask task, Throwable e) {
    log.error("e-sign failed, fallback to manual", e);
    notificationService.alertAdmin("电子签调用失败，请人工处理");
    throw new ThirdPartyException("08E001", e);
}
```

### 3. 适配器自包含配置
```yaml
# application-dingtalk.yml
lumen.integration.dingtalk:
  app-key: ${DINGTALK_APP_KEY}
  app-secret: ${DINGTALK_APP_SECRET}
  agent-id: ${DINGTALK_AGENT_ID}
  enabled: true
```

### 4. 接口幂等
所有 Adapter 调用必须支持幂等（biz_no 或 task_id）。

### 5. 单元测试 mock
```java
@MockBean
private CollaborationAdapter collabAdapter;

@Test
public void testSsoLogin() {
    when(collabAdapter.ssoLogin("test_code")).thenReturn(mockUserInfo);
    // ...
}
```

## 第三方切换流程

```
1. 实现新 Adapter（如从 DingtalkAdapter 切换到 WechatworkAdapter）
2. 在 application.yml 切换 @Qualifier
3. 数据迁移：账号绑定关系迁移
4. 灰度：1% → 100% 流量切换
5. 旧 Adapter 保留 1 个月可回滚
```

## 后果

### 优点
- 业务代码与第三方 SDK 解耦
- 切换供应商改 Adapter 不改业务
- 单元测试易 mock
- 第三方故障可熔断降级

### 缺点
- 抽象设计成本（需评估第三方能力）
- 抽象可能引入"最低公约数"问题

### 风险与缓解
- **风险**：Adapter 抽象与第三方特性冲突（如钉钉独有的群机器人）
- **缓解**：抽象接口 + 平台特定接口（`DingtalkExtraAdapter`）双层设计

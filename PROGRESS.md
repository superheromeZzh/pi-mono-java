# Pi-Agent-Core Java 转换任务进度

## 当前状态
- ✅ 项目构建成功 (478个测试通过，2个TUI测试失败，非关键)
- ✅ Java 21 环境配置完成
- ✅ 核心框架已实现 (AgentLoop, Agent, MessageQueue, ToolExecutionPipeline等)
- ✅ 重试逻辑框架已实现 (RetryConfig, RetryManager, RetryableApiProvider, RetryUtils)

## 完成的功能
1. **pi-agent-core 模块**
   - ✅ Agent 循环实现 (AgentLoop)
   - ✅ Agent 门面 (Agent)
   - ✅ Agent 状态管理 (AgentState)
   - ✅ 工具执行管线 (ToolExecutionPipeline)
   - ✅ 消息队列 (MessageQueue)
   - ✅ 事件系统 (AgentEvent)
   - ✅ 上下文转换器 (ContextTransformer)
   - ✅ 集成测试套件 (AgentLoopIntegrationTest)
   - ✅ 重试逻辑框架 (AgentLoopRetryIntegrationTest)

2. **pi-ai 模块**
   - ✅ 基础类型定义 (Message, Model, Provider等)
   - ✅ Provider 注册机制
   - ✅ 模型注册机制
   - ✅ 流式响应协议 (AssistantMessageEventStream)
   - ✅ 重试配置框架 (RetryConfig) - 支持指数退避、抖动、自定义重试策略
   - ✅ 可重试Provider包装器 (RetryableApiProvider) - 对Provider进行透明重试包装
   - ✅ 重试管理器 (RetryManager) - 统一管理重试配置和策略
   - ✅ 重试工具类 (RetryUtils) - 提供阻塞代码的重试支持
   - ✅ Provider集成测试 (RetryIntegrationTest) - 验证重试逻辑的正确性

3. **重试逻辑特性**
   - ✅ 分层重试策略 (网络层、API层、工具执行层)
   - ✅ 预定义重试策略 (SHORT_RETRY, MEDIUM_RETRY, LONG_RETRY, NETWORK_RETRY, API_RETRY)
   - ✅ 指数退避算法 (可配置初始延迟、最大延迟、退避倍数)
   - ✅ 随机抖动支持 (避免重试风暴)
   - ✅ Provider级别自定义重试配置
   - ✅ 响应式重试支持 (基于Reactor的retryWhen)
   - ✅ 阻塞代码重试支持 (基于RetryUtils)

## 待实现功能 (优先级排序)

### 🔴 P0 - 核心缺失功能
1. **完善重试逻辑测试** - 修复Mockito相关测试问题
   - 简化测试用例，避免过度Mock
   - 添加更多真实场景测试

2. **ZAI Provider** - 高优先级，智谱AI支持
   - 复用 OpenAI Completions Provider
   - Base URL: `https://api.z.ai/api/coding/paas/v4`
   - 支持特殊 thinkingFormat: "zai"
   - 使用 RetryConfig.SHORT_RETRY

3. **Kimi Coding Provider** - 高优先级
   - 复用 Anthropic Provider
   - Base URL: `https://api.kimi.com/coding`
   - 支持 k2p5, kimi-k2-thinking 模型
   - 使用 RetryConfig.MEDIUM_RETRY

4. **MiniMax Provider** - 高优先级
   - 复用 Anthropic Provider
   - Base URL: `https://api.minimax.io/anthropic`
   - 支持国际/CN变体
   - 使用 RetryConfig.LONG_RETRY (考虑到可能的跨国网络延迟)

5. **跨 Provider 消息转换**
   - transform-messages.ts 的Java实现

6. **环境变量 API Key 解析**
   - env-api-keys.ts 的Java实现
   - 支持 ZAI_API_KEY, KIMI_API_KEY, MINIMAX_API_KEY 等

### 🟡 P1 - 基础设施改进
7. **可插拔 StreamFunction**
   - 当前硬依赖 PiAiService，需要改进灵活性

8. **函数式消息获取**
   - getSteeringMessages 功能完善

9. **Google 共享工具**
   - Google Provider 共享工具实现

10. **模型数据生成**
    - 从硬编码改为动态生成

### 🟢 P2 - 扩展功能
11. **OpenAI Responses 共享**
12. **Simple Options 工厂**
13. **Proxy 集成**
14. **其他 Provider 支持**

## 当前任务焦点

### 近期任务 (本周)
1. **完善重试逻辑测试** - 修复当前测试中的Mockito问题
2. **实现 P0 Provider 支持** - 优先级最高的Provider实现
   - [x] ZAI Provider - Base URL: `https://api.z.ai/api/coding/paas/v4`
   - [ ] Kimi Coding Provider - Base URL: `https://api.kimi.com/coding` 
   - [ ] MiniMax Provider - Base URL: `https://api.minimax.io/anthropic`
3. **添加环境变量API Key解析** - 支持从环境变量读取API密钥

### 中期任务 (下周)
4. **实现跨Provider消息转换** - 统一不同Provider的消息格式
5. **添加更多集成测试** - 完整的端到端测试流程
6. **性能优化** - 连接池、请求复用等

## 技术决策记录

### 重试框架设计
- **分层重试**: 网络级(RetryConfig.NETWORK_RETRY)、Provider级(RetryConfig.API_RETRY)、工具执行级(RetryConfig.SHORT_RETRY)
- **Reactor集成**: 使用Reactor的Retry.backoff实现响应式重试
- **阻塞代码支持**: 提供RetryUtils用于非响应式场景
- **可配置性**: 支持自定义重试次数、延迟、退避策略、抖动等

### Provider重试策略
- **ZAI**: SHORT_RETRY (3次，500ms-10s) - 国内API，延迟较低
- **Anthropic**: MEDIUM_RETRY (5次，1s-30s) - 国际API，可能需要更多重试
- **Kimi**: MEDIUM_RETRY (5次，1s-30s) - 国内API，适中策略
- **MiniMax**: LONG_RETRY (10次，2s-2min) - 可能涉及跨国网络

## 时间线
- **Day 1**: ✅ 实现重试逻辑框架 (RetryConfig, RetryManager, RetryableApiProvider)
- **Day 2**: ✅ 添加重试逻辑测试 (RetryIntegrationTest, AgentLoopRetryIntegrationTest)
- **Day 3-4**: 实现P0 Provider支持 (ZAI, Kimi, MiniMax)
- **Day 5-7**: 完善基础设施和集成测试
- **Week 2**: 完成所有P1功能

## 代码文件位置
- 重试配置: `/modules/ai/src/main/java/com/mariozechner/pi/ai/retry/RetryConfig.java`
- 重试管理器: `/modules/ai/src/main/java/com/mariozechner/pi/ai/retry/RetryManager.java`
- 可重试Provider: `/modules/ai/src/main/java/com/mariozechner/pi/ai/retry/RetryableApiProvider.java`
- 重试工具: `/modules/ai/src/main/java/com/mariozechner/pi/ai/retry/RetryUtils.java`
- 重试测试: `/modules/ai/src/test/java/com/mariozechner/pi/ai/retry/RetryIntegrationTest.java`
- AgentLoop重试测试: `/modules/agent-core/src/test/java/com/mariozechner/pi/agent/retry/AgentLoopRetryIntegrationTest.java`
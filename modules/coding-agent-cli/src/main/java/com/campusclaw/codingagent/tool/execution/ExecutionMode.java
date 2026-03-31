package com.campusclaw.codingagent.tool.execution;

/**
 * 工具执行模式枚举
 */
public enum ExecutionMode {
    /**
     * 本地执行 - 直接在 JVM 进程中操作文件/执行命令
     * 优点：性能高、无额外开销
     * 风险：可能影响宿主机
     */
    LOCAL,

    /**
     * 沙箱执行 - 在 Docker 容器中执行
     * 优点：隔离安全、可控资源
     * 缺点：有启动开销
     */
    SANDBOX,

    /**
     * 自动模式 - 根据命令特征智能选择
     * 低风险操作 → 本地
     * 高风险操作 → 沙箱
     */
    AUTO
}

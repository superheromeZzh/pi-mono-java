package com.campusclaw.codingagent.tool.sandbox;

import lombok.Builder;
import lombok.Data;

/**
 * 沙箱资源限制配置
 */
@Data
@Builder
public class ResourceLimits {
    private int memoryMb;           // 内存限制 (MB)
    private double cpuQuota;        // CPU 核心数
    private int timeoutSeconds;     // 超时时间
    private boolean networkEnabled; // 是否允许网络
    private String image;           // 自定义镜像
    private boolean privileged;     // 是否特权模式
    private boolean autoRemove;     // 执行后自动删除容器

    public static ResourceLimits defaults() {
        return ResourceLimits.builder()
            .memoryMb(512)
            .cpuQuota(1.0)
            .timeoutSeconds(60)
            .networkEnabled(false)
            .privileged(false)
            .autoRemove(true)
            .build();
    }

    public static ResourceLimits highPerformance() {
        return ResourceLimits.builder()
            .memoryMb(2048)
            .cpuQuota(2.0)
            .timeoutSeconds(300)
            .networkEnabled(true)
            .privileged(false)
            .autoRemove(true)
            .build();
    }

    public static ResourceLimits readonly() {
        return ResourceLimits.builder()
            .memoryMb(256)
            .cpuQuota(0.5)
            .timeoutSeconds(30)
            .networkEnabled(false)
            .privileged(false)
            .autoRemove(true)
            .build();
    }
}

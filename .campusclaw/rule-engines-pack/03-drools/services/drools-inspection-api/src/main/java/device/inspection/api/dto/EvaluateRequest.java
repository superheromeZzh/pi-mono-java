package device.inspection.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

/** 单条规则在窗口内各采样点上的评估请求（与 Python 侧字段对齐）。 */
public record EvaluateRequest(
    @NotBlank String ruleId,
    @NotNull List<Map<String, Object>> series) {}

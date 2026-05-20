package device.inspection.api.dto;

/** Drools 评估结果：命中点数、总点数。 */
public record EvaluateResponse(String ruleId, int hits, int total) {}

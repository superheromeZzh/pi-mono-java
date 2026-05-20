package device.inspection.api.web;

import device.inspection.api.dto.EvaluateRequest;
import device.inspection.api.dto.EvaluateResponse;
import device.inspection.api.service.RuleEvaluationService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
public class EvaluateController {

  private final RuleEvaluationService ruleEvaluationService;

  public EvaluateController(RuleEvaluationService ruleEvaluationService) {
    this.ruleEvaluationService = ruleEvaluationService;
  }

  /** 健康检查，便于 Python / 负载均衡探测。 */
  @GetMapping("/health")
  public Map<String, String> health() {
    return Map.of("status", "OK");
  }

  /** 评估单条规则在窗口内时序上的命中次数（DRL 与 ruleId 同名）。 */
  @PostMapping(path = "/evaluate", consumes = MediaType.APPLICATION_JSON_VALUE)
  public EvaluateResponse evaluate(@Valid @RequestBody EvaluateRequest body) {
    return ruleEvaluationService.evaluate(body);
  }
}

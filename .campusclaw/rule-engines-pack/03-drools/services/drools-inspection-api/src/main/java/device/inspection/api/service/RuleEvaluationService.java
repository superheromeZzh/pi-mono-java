package device.inspection.api.service;

import device.inspection.api.dto.EvaluateRequest;
import device.inspection.api.dto.EvaluateResponse;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.rule.AgendaFilter;
import org.springframework.stereotype.Service;

@Service
public class RuleEvaluationService {

  private static final String KSESSION_NAME = "inspectionSession";

  private final KieContainer kieContainer;

  public RuleEvaluationService(KieContainer kieContainer) {
    this.kieContainer = kieContainer;
  }

  /**
   * 对窗口内每个采样点 insert 为 Map 事实，仅触发 ruleId 与 DRL 中 rule 名称一致的那条规则，统计 hits。
   */
  public EvaluateResponse evaluate(EvaluateRequest req) {
    String ruleId = req.ruleId().trim();
    List<Map<String, Object>> series = req.series();
    AtomicInteger hits = new AtomicInteger(0);

    KieSession session = kieContainer.newKieSession(KSESSION_NAME);
    if (session == null) {
      throw new IllegalStateException(
          "未找到名为 \"" + KSESSION_NAME + "\" 的 KieSession，请检查 META-INF/kmodule.xml 是否已正确加载（需依赖 drools-xml-support）。");
    }
    try {
      session.setGlobal("hits", hits);
      AgendaFilter filter = match -> ruleId.equals(match.getRule().getName());
      for (Map<String, Object> point : series) {
        if (point != null) {
          session.insert(point);
        }
      }
      session.fireAllRules(filter);
    } finally {
      session.dispose();
    }

    return new EvaluateResponse(ruleId, hits.get(), series.size());
  }
}

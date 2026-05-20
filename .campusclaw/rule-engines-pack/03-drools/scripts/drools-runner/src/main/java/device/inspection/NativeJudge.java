package device.inspection;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.kie.api.io.ResourceType;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.rule.AgendaFilter;
import org.kie.internal.io.ResourceFactory;
import org.kie.internal.utils.KieHelper;

/**
 * 命令行子进程入口：从 stdin 读 JSON，加载磁盘上的 DRL，评估后输出 JSON。
 * 与 HTTP 服务共用同一套 DRL 语义（global hits、rule 名即 ruleId）。
 */
public final class NativeJudge {
  private static final ObjectMapper M = new ObjectMapper();

  public static void main(String[] args) throws Exception {
    String input = new String(System.in.readAllBytes(), StandardCharsets.UTF_8).trim();
    Map<String, Object> req = M.readValue(input, new TypeReference<Map<String, Object>>() {});

    String drlPath = String.valueOf(req.get("drlPath"));
    String ruleId = String.valueOf(req.get("ruleId"));
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> series = (List<Map<String, Object>>) req.get("series");

    if (drlPath == null || drlPath.isBlank()) {
      throw new IllegalArgumentException("缺少必填字段 drlPath（DRL 文件路径）");
    }
    if (ruleId == null || ruleId.isBlank()) {
      throw new IllegalArgumentException("缺少必填字段 ruleId（须与 DRL 中 rule 名称一致）");
    }
    if (series == null) {
      throw new IllegalArgumentException("缺少必填字段 series（时序点列表）");
    }

    AtomicInteger hits = new AtomicInteger(0);

    File drl = new File(drlPath);
    if (!drl.exists()) {
      throw new IllegalArgumentException("未找到 DRL 文件：" + drl.getAbsolutePath());
    }

    KieHelper helper = new KieHelper();
    helper.addResource(ResourceFactory.newFileResource(drl), ResourceType.DRL);
    KieSession ks = helper.build().newKieSession();
    try {
      ks.setGlobal("hits", hits);
      AgendaFilter filter = match -> ruleId.equals(match.getRule().getName());
      for (Map<String, Object> point : series) {
        if (point == null) {
          continue;
        }
        ks.insert(point);
      }
      ks.fireAllRules(filter);
    } finally {
      try {
        ks.dispose();
      } catch (Exception ignored) {
      }
    }

    Map<String, Object> resp = Map.of("ruleId", ruleId, "hits", hits.get(), "total", series.size());
    System.out.print(M.writeValueAsString(resp));
  }
}

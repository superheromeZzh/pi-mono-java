package device.inspection.api.config;

import org.kie.api.KieServices;
import org.kie.api.runtime.KieContainer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 从 classpath 加载 META-INF/kmodule.xml 与 rules 包下 DRL，构建进程内单例 KieContainer（规则只编译一次）。
 */
@Configuration
public class DroolsConfig {

  @Bean
  public KieContainer kieContainer() {
    KieServices ks = KieServices.Factory.get();
    return ks.getKieClasspathContainer();
  }
}

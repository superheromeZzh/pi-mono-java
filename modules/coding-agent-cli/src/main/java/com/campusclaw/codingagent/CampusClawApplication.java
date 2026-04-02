package com.campusclaw.codingagent;

import com.campusclaw.codingagent.cli.CampusClawCommand;
import com.campusclaw.codingagent.config.ToolExecutionProperties;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import picocli.CommandLine;
import picocli.CommandLine.IFactory;


/**
 * CampusClaw — Spring Boot CLI application.
 * Bridges Picocli with Spring Boot via the picocli-spring-boot-starter.
 */
@SpringBootApplication(scanBasePackages = "com.campusclaw")
@EnableConfigurationProperties(ToolExecutionProperties.class)
public class CampusClawApplication implements CommandLineRunner, ExitCodeGenerator {

    private final CampusClawCommand piCommand;
    private final IFactory factory;
    private int exitCode;

    public CampusClawApplication(CampusClawCommand piCommand, IFactory factory) {
        this.piCommand = piCommand;
        this.factory = factory;
    }

    public static void main(String[] args) {
        // Suppress JVM startup warnings that pollute terminal scrollback:
        // - Netty: avoid sun.misc.Unsafe (eliminates JVM module access warnings)
        // - JLine/Jansi: disable native library loading (eliminates restricted method warnings)
        System.setProperty("io.netty.noUnsafe", "true");
        System.setProperty("org.jline.terminal.disableDeprecatedProviderWarning", "true");
        System.setProperty("org.jline.terminal.jansi", "false");

        org.springframework.context.ConfigurableApplicationContext ctx =
            SpringApplication.run(CampusClawApplication.class, args);
        int exitCode = SpringApplication.exit(ctx);

        // Prevent NoClassDefFoundError on exit by stopping logback before the
        // Boot JarUrlClassLoader is torn down.
        try {
            ((ch.qos.logback.classic.LoggerContext)
                org.slf4j.LoggerFactory.getILoggerFactory()).stop();
        } catch (Exception ignored) {
        }

        System.exit(exitCode);
    }

    @Override
    public void run(String... args) {
        exitCode = new CommandLine(piCommand, factory).execute(args);
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }
}

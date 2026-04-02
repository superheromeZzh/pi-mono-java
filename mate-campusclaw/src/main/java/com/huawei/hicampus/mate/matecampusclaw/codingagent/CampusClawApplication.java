package com.huawei.hicampus.mate.matecampusclaw.codingagent;

import com.huawei.hicampus.mate.matecampusclaw.codingagent.cli.CampusClawCommand;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import picocli.CommandLine;
import picocli.CommandLine.IFactory;


/**
 * CampusClaw — Spring Boot CLI application.
 * Bridges Picocli with Spring Boot via the picocli-spring-boot-starter.
 */
@SpringBootApplication(
    scanBasePackages = "com.huawei.hicampus.mate.matecampusclaw",
    exclude = {
        org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration.class,
        org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration.class,
        org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration.class,
        org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration.class,
        org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration.class,
        org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration.class,
        org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration.class,
    }
)
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

        try {
            var context = SpringApplication.run(CampusClawApplication.class, args);
            System.err.println("[CampusClaw] Spring context loaded successfully");
            System.exit(SpringApplication.exit(context));
        } catch (Exception e) {
            System.err.println("[CampusClaw] Spring Boot startup FAILED:");
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }

    @Override
    public void run(String... args) {
        System.err.println("[CampusClaw] args: " + java.util.Arrays.toString(args));
        try {
            var cmd = new CommandLine(piCommand, factory);
            cmd.setErr(new java.io.PrintWriter(System.err, true));
            cmd.setOut(new java.io.PrintWriter(System.out, true));
            exitCode = cmd.execute(args);
            System.err.println("[CampusClaw] exitCode: " + exitCode);
        } catch (Exception e) {
            System.err.println("[CampusClaw] Exception in execute:");
            e.printStackTrace(System.err);
            exitCode = 1;
        }
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }
}

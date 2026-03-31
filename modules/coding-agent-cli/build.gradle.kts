plugins {
    id("org.springframework.boot")
}

dependencies {
    api(project(":modules:campusclaw-ai"))
    api(project(":modules:campusclaw-agent-core"))
    api(project(":modules:campusclaw-tui"))
    implementation(project(":modules:campusclaw-assistant"))

    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-webflux")

    // CLI framework with Spring Boot integration
    implementation("info.picocli:picocli-spring-boot-starter:4.7.6")
    annotationProcessor("info.picocli:picocli-codegen:4.7.6")

    // JSON processing
    implementation("com.fasterxml.jackson.core:jackson-databind")

    // YAML for configuration files
    implementation("org.yaml:snakeyaml")

    // Micrometer for metrics
    implementation("io.micrometer:micrometer-core")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}

springBoot {
    mainClass.set("com.campusclaw.codingagent.CampusClawApplication")
}

tasks.bootJar {
    archiveBaseName.set("campusclaw-agent")
}

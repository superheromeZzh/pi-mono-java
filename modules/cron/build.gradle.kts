dependencies {
    api(project(":modules:campusclaw-agent-core"))

    // JSON processing
    implementation("com.fasterxml.jackson.core:jackson-databind")

    // Spring context for DI and CronExpression
    implementation("org.springframework:spring-context")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

dependencies {
    api(project(":modules:campusclaw-ai"))

    // Reactive streams
    implementation("io.projectreactor:reactor-core")

    // JSON processing
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.networknt:json-schema-validator:1.5.2")

    // Spring context for DI
    implementation("org.springframework:spring-context")

    // Micrometer for metrics
    implementation("io.micrometer:micrometer-core")

    // Testing
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

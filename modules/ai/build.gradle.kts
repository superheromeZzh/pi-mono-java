dependencies {
    // LLM SDKs - official provider SDKs
    implementation("com.anthropic:anthropic-java:2.18.0")
    implementation("com.openai:openai-java:4.29.1")
    implementation("com.google.cloud:google-cloud-aiplatform:3.79.0")
    implementation("software.amazon.awssdk:bedrockruntime:2.41.34")

    // Nullability annotations
    implementation("jakarta.annotation:jakarta.annotation-api")

    // JSON processing
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.core:jackson-core")

    // Spring context - DI annotations (@Service, @Component, @Autowired)
    implementation("org.springframework:spring-context")

    // HTTP client - Spring WebClient for SSE streaming
    implementation("org.springframework:spring-webflux")
    implementation("io.projectreactor.netty:reactor-netty-http")
    implementation("io.projectreactor:reactor-core")

    // JSON Schema validation
    implementation("com.networknt:json-schema-validator:1.5.2")

    // Testing
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}

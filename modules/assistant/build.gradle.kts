plugins {
    id("org.springframework.boot")
}

tasks.bootJar {
    enabled = false
}

dependencies {
    api(project(":modules:campusclaw-ai"))
    api(project(":modules:campusclaw-agent-core"))

    implementation("org.springframework.boot:spring-boot-starter")

    // REST API
    implementation("org.springframework.boot:spring-boot-starter-web")

    // MyBatis + MySQL
    implementation("org.mybatis.spring.boot:mybatis-spring-boot-starter:3.0.4")
    runtimeOnly("com.mysql:mysql-connector-j")

    // JobRunr (Spring Boot 3.x)
    implementation("org.jobrunr:jobrunr-spring-boot-3-starter:7.4.0")
    runtimeOnly("com.h2database:h2")

    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

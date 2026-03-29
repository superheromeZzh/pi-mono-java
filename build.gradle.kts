plugins {
    java
    id("org.springframework.boot") version "3.4.1" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
    id("com.diffplug.spotless") version "7.0.4"
}

val javaVersion = 21

allprojects {
    group = "com.campusclaw"
    version = "1.0.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

spotless {
    java {
        target("modules/*/src/**/*.java")
        removeUnusedImports()
        importOrder("java", "javax", "com", "org", "")
    }
}

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "io.spring.dependency-management")

    the<io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension>().apply {
        imports {
            mavenBom("org.springframework.boot:spring-boot-dependencies:3.4.1")
        }
    }

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(javaVersion))
        }
        withSourcesJar()
    }

    tasks.compileJava {
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-parameters"))
        options.release.set(javaVersion)
    }

    tasks.compileTestJava {
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-parameters"))
        options.release.set(javaVersion)
    }

    tasks.test {
        useJUnitPlatform()
        jvmArgs("-XX:+EnableDynamicAgentLoading")
    }

    dependencies {
        // Logging (managed by Spring Boot BOM)
        implementation("org.slf4j:slf4j-api")
        implementation("ch.qos.logback:logback-classic")

        // Testing (managed by Spring Boot BOM)
        testImplementation("org.junit.jupiter:junit-jupiter")
        testImplementation("org.mockito:mockito-core")
        testImplementation("org.mockito:mockito-junit-jupiter")
    }
}

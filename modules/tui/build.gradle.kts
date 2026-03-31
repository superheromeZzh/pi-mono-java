dependencies {
    // Terminal I/O
    implementation("org.jline:jline-terminal:3.26.2")
    implementation("org.jline:jline-terminal-jansi:3.26.2")
    implementation("org.jline:jline-reader:3.26.2")
    implementation("org.jline:jline-console:3.26.2")

    // Terminal GUI components (optional)
    implementation("com.googlecode.lanterna:lanterna:3.1.2")

    // JSON processing for theme config
    implementation("com.fasterxml.jackson.core:jackson-databind")
}

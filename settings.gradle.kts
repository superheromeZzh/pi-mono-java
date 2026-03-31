rootProject.name = "campusclaw"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(
    ":modules:campusclaw-ai",
    ":modules:campusclaw-agent-core",
    ":modules:campusclaw-coding-agent",
    ":modules:campusclaw-tui",
    ":modules:campusclaw-assistant"
)

project(":modules:campusclaw-ai").projectDir = file("modules/ai")
project(":modules:campusclaw-agent-core").projectDir = file("modules/agent-core")
project(":modules:campusclaw-coding-agent").projectDir = file("modules/coding-agent-cli")
project(":modules:campusclaw-tui").projectDir = file("modules/tui")
project(":modules:campusclaw-assistant").projectDir = file("modules/assistant")

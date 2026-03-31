rootProject.name = "campusclaw"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(
    ":modules:campusclaw-ai",
    ":modules:campusclaw-agent-core",
    ":modules:campusclaw-cron",
    ":modules:campusclaw-coding-agent",
    ":modules:campusclaw-tui"
)

project(":modules:campusclaw-ai").projectDir = file("modules/ai")
project(":modules:campusclaw-agent-core").projectDir = file("modules/agent-core")
project(":modules:campusclaw-cron").projectDir = file("modules/cron")
project(":modules:campusclaw-coding-agent").projectDir = file("modules/coding-agent-cli")
project(":modules:campusclaw-tui").projectDir = file("modules/tui")

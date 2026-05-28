# Regenerate Python lexer/parser from grammar/Trigger.g4
$ErrorActionPreference = "Stop"
$SkillRoot = Split-Path -Parent $PSScriptRoot
$Out = Join-Path $SkillRoot "generated"
$Jar = Join-Path $env:USERPROFILE ".antlr/antlr-4.13.2-complete.jar"
if (-not (Test-Path $Jar)) {
    New-Item -ItemType Directory -Force -Path (Split-Path $Jar) | Out-Null
    Invoke-WebRequest -Uri "https://www.antlr.org/download/antlr-4.13.2-complete.jar" -OutFile $Jar
}
New-Item -ItemType Directory -Force -Path $Out | Out-Null
java -jar $Jar -Dlanguage=Python3 -visitor -o $Out (Join-Path $SkillRoot "grammar/Trigger.g4")
Write-Host "generated -> $Out"

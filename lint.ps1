# Kotlin 구문/스타일 검사 (ktlint). 사용법:
#   .\lint.ps1        검사만
#   .\lint.ps1 -Fix   자동 수정 후 검사
param([switch]$Fix)
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$jar = "$env:USERPROFILE\.ktlint\ktlint.jar"
$targets = @("app/src/**/*.kt", "*.kts", "app/*.kts")
if ($Fix) { java -jar $jar -F @targets }
java -jar $jar @targets
if ($LASTEXITCODE -eq 0) { Write-Host "ktlint: OK" -ForegroundColor Green } else { exit $LASTEXITCODE }

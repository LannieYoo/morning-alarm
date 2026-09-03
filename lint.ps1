# Kotlin 구문/스타일 검사 (ktlint). 사용법:
#   .\lint.ps1        검사만
#   .\lint.ps1 -Fix   자동 수정 후 검사
# CI(.github/workflows/build-apk.yml)의 lint job과 같은 ktlint 1.3.1 CLI·같은 대상을 사용한다.
param([switch]$Fix)

# JDK 17 자동 탐색 (PATH에 java가 없을 때)
if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    $jdk = Get-ChildItem "C:\Program Files\Eclipse Adoptium" -Directory -ErrorAction SilentlyContinue |
        Where-Object Name -like "jdk-17*" | Sort-Object Name -Descending | Select-Object -First 1
    if (-not $jdk) {
        Write-Host "JDK 17이 없습니다. 설치: winget install --id EclipseAdoptium.Temurin.17.JDK -e" -ForegroundColor Red
        exit 1
    }
    $env:JAVA_HOME = $jdk.FullName
    $env:Path = "$env:JAVA_HOME\bin;$env:Path"
}

$jar = "$env:USERPROFILE\.ktlint\ktlint.jar"
if (-not (Test-Path $jar)) {
    New-Item -ItemType Directory -Force (Split-Path $jar) | Out-Null
    Invoke-WebRequest -Uri "https://github.com/pinterest/ktlint/releases/download/1.3.1/ktlint" -OutFile $jar
}

$targets = @("app/src/**/*.kt", "*.kts", "app/*.kts")
if ($Fix) { java -jar $jar -F @targets }
java -jar $jar @targets
if ($LASTEXITCODE -eq 0) { Write-Host "ktlint: OK" -ForegroundColor Green } else { exit $LASTEXITCODE }

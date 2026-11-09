$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location -LiteralPath $projectRoot
try {
    $env:JAVA_HOME = 'F:\ayd\jdk-17'
    $env:Path = "$env:JAVA_HOME\bin;$env:Path"
    & .\mvnw.cmd --batch-mode --no-transfer-progress '-Dmaven.test.skip=true' compile dependency:build-classpath '-Dmdep.outputFile=target/runtime-classpath.txt'
    if ($LASTEXITCODE -ne 0) { throw 'Compilation/classpath resolution failed.' }
    $dependencyClasspath = (Get-Content -LiteralPath 'target/runtime-classpath.txt' -Raw).Trim()
    & "$env:JAVA_HOME\bin\java.exe" -cp "target/classes;$dependencyClasspath" com.yu.retailops.support.PasswordHashTool
    if ($LASTEXITCODE -ne 0) { throw 'Password hash generation failed.' }
} finally {
    Pop-Location
}

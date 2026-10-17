param([string]$Database = 'concert.db', [switch]$NoRecover)
$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    if (-not $env:JAVA_HOME) { throw 'Set JAVA_HOME to the installed JDK 25.' }
    $java = Join-Path $env:JAVA_HOME 'bin\java.exe'
    if (-not (Test-Path -LiteralPath $java)) { throw "JDK not found: $java" }
    & mvn -B clean verify
    if ($LASTEXITCODE -ne 0) { throw 'Maven build/tests failed.' }
    $cliArguments = @('--enable-native-access=ALL-UNNAMED', '-jar', 'target/concert-ticketing.jar', $Database)
    if ($NoRecover) { $cliArguments += '--no-recover' }
    & $java @cliArguments
    if ($LASTEXITCODE -ne 0) { throw 'CLI failed.' }
} finally { Pop-Location }

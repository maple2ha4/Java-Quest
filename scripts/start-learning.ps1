param(
    [ValidateSet('off', 'process', 'docker')][string]$Mode = 'off',
    [int]$ApiPort = 8080,
    [int]$JudgePort = 8091
)
# Run from any directory. This script starts only its own Java services; frontend is a second terminal.
$ErrorActionPreference = 'Stop'
$questRoot = Split-Path -Parent $PSScriptRoot
$apiJar = Join-Path $questRoot 'backend/target/java-quest-api-0.1.0.jar'
$judgeJar = Join-Path $questRoot 'backend/target/java-quest-api-0.1.0-judge.jar'
$java = (Get-Command java -ErrorAction Stop).Source
$Mode = $Mode.ToLowerInvariant()
if (-not (Test-Path $apiJar) -or ($Mode -ne 'off' -and -not (Test-Path $judgeJar))) {
    throw 'Build first from the project root: mvn -f backend/pom.xml verify'
}
function Assert-FreePort([int]$Port) {
    $client = New-Object System.Net.Sockets.TcpClient
    $occupied = $false
    try { $client.Connect('127.0.0.1', $Port); $occupied = $true } catch { } finally { $client.Dispose() }
    if ($occupied) { throw "Port $Port is occupied. Stop the previous instance before switching modes." }
}
function Wait-QuestService($Process, [string]$Url) {
    $deadline = (Get-Date).AddSeconds(35)
    while ((Get-Date) -lt $deadline) {
        if ($Process.HasExited) { throw "Owned Java service exited with code $($Process.ExitCode). Check its console output." }
        try { return Invoke-RestMethod -Uri $Url -TimeoutSec 2 } catch { Start-Sleep -Milliseconds 250 }
    }
    throw "Startup timed out: $Url"
}
Assert-FreePort $ApiPort
if ($Mode -ne 'off') { Assert-FreePort $JudgePort }
if ($Mode -eq 'docker') {
    $null = Get-Command docker -ErrorAction Stop
    & docker image inspect eclipse-temurin:21-jdk *> $null
    if ($LASTEXITCODE -ne 0) { throw 'Docker/image unavailable. Start Docker and pull eclipse-temurin:21-jdk. No process fallback will occur.' }
}
$keys = @('JUDGE_ENABLED', 'JUDGE_MODE', 'JUDGE_TOKEN', 'JUDGE_URL', 'JUDGE_PORT', 'JUDGE_IMAGE')
$previous = @{}
foreach ($key in $keys) { $previous[$key] = [Environment]::GetEnvironmentVariable($key, 'Process') }
$owned = @()
try {
    $env:JUDGE_ENABLED = if ($Mode -eq 'off') { 'false' } else { 'true' }
    $env:JUDGE_MODE = if ($Mode -eq 'off') { 'docker' } else { $Mode }
    $env:JUDGE_PORT = [string]$JudgePort
    $env:JUDGE_IMAGE = 'eclipse-temurin:21-jdk'
    $env:JUDGE_URL = "http://127.0.0.1:$JudgePort"
    # Shared token stays in these two service processes; it is not written to files or printed.
    $bytes = New-Object byte[] 32
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    $env:JUDGE_TOKEN = [Convert]::ToBase64String($bytes)
    if ($Mode -ne 'off') {
        $judgeArgs = @('-jar', ('"' + $judgeJar + '"'), "--mode=$Mode")
        if ($Mode -eq 'process') {
            Write-Host 'TRUSTED LOCAL CODE ONLY: local processes can access your files and network. This is not a security sandbox.' -ForegroundColor Yellow
            $judgeArgs += '--allow-local-process'
        }
        $judge = Start-Process -FilePath $java -ArgumentList $judgeArgs -NoNewWindow -PassThru -WorkingDirectory $questRoot
        $owned += $judge
        $health = Wait-QuestService $judge "http://127.0.0.1:$JudgePort/health"
        if ($health.mode -ne $Mode) { throw 'Judge mode mismatch; refusing to start API.' }
    }
    $api = Start-Process -FilePath $java -ArgumentList @('-jar', ('"' + $apiJar + '"'), '--spring.profiles.active=local', "--server.port=$ApiPort") -NoNewWindow -PassThru -WorkingDirectory $questRoot
    $owned += $api
    $null = Wait-QuestService $api "http://127.0.0.1:$ApiPort/api/health"
    Write-Host "Mode=$Mode. Start frontend in another terminal: npm run dev --prefix frontend"
    Write-Host 'Ctrl+C stops only the Java services launched by this script. Stop before switching modes.'
    while (-not $api.HasExited) {
        if ($Mode -ne 'off' -and $judge.HasExited) { throw 'Judge exited. Stopping API; no fallback will occur.' }
        Start-Sleep -Milliseconds 500
    }
} finally {
    foreach ($process in $owned) {
        if (-not $process.HasExited) {
            if (-not $process.WaitForExit(1500)) { Stop-Process -Id $process.Id -ErrorAction SilentlyContinue }
        }
    }
    foreach ($key in $keys) { [Environment]::SetEnvironmentVariable($key, $previous[$key], 'Process') }
}

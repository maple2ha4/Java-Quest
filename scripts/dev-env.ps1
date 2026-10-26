# Dot-source from any directory: . .\scripts\dev-env.ps1
$questRoot = Split-Path -Parent $PSScriptRoot
$questEnvPath = Join-Path $questRoot '.env'
if (-not (Test-Path $questEnvPath)) { throw 'Copy .env.example to .env and set your local credentials first.' }
Get-Content $questEnvPath | ForEach-Object {
    $line = $_.Trim()
    if ($line -and -not $line.StartsWith('#')) {
        $parts = $line.Split('=', 2)
        if ($parts.Length -ne 2 -or $parts[0] -notmatch '^[A-Za-z_][A-Za-z0-9_]*$') { throw 'Invalid .env entry' }
        $value = $parts[1].Trim()
        if (($value.StartsWith("'") -and $value.EndsWith("'")) -or ($value.StartsWith('"') -and $value.EndsWith('"'))) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        [Environment]::SetEnvironmentVariable($parts[0], $value, 'Process')
    }
}
Remove-Variable questRoot, questEnvPath

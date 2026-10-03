param([int] $DurationMs = 0)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding $false

try {
    Add-Type -Path (Join-Path $PSScriptRoot 'spotify-audio.cs')
    [NathanAudioBands]::Run($DurationMs)
} catch {
    [Console]::WriteLine('ERROR ' + $_.Exception.Message)
}

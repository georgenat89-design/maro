param(
    [ValidateSet('status', 'art', 'previous', 'toggle', 'next', 'seek')]
    [string] $Action = 'status',
    [string] $ArtworkPath = '',
    [long] $SeekMs = 0,
    [string] $ExpectedTrack = '',
    [switch] $Watch
)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding $false

function Await-WinRt($operation, [Type] $resultType) {
    $asTask = [System.WindowsRuntimeSystemExtensions].GetMethods() |
        Where-Object { $_.Name -eq 'AsTask' -and $_.IsGenericMethodDefinition -and $_.GetParameters().Count -eq 1 } |
        Select-Object -First 1

    $task = $asTask.MakeGenericMethod($resultType).Invoke($null, @($operation))
    if (-not $task.Wait(3500)) { throw 'Windows media session timed out.' }
    return $task.Result
}

function Result($value) {
    $value | ConvertTo-Json -Compress -Depth 4
}

function Read-Status($manager) {
    $sessions = @($manager.GetSessions())
    $spotify = @($sessions | Where-Object { $_.SourceAppUserModelId -match 'spotify' })
    $browser = @($sessions | Where-Object { $_.SourceAppUserModelId -match 'chrome|msedge|firefox|brave|opera|vivaldi' })
    $session = @($spotify | Where-Object { $_.GetPlaybackInfo().PlaybackStatus.ToString() -eq 'Playing' }) | Select-Object -First 1
    if ($null -eq $session) { $session = @($browser | Where-Object { $_.GetPlaybackInfo().PlaybackStatus.ToString() -eq 'Playing' }) | Select-Object -First 1 }
    if ($null -eq $session) { $session = $spotify | Select-Object -First 1 }
    if ($null -eq $session) { $session = $browser | Select-Object -First 1 }
    if ($null -eq $session) { return @{ ok = $true; available = $false } }
    $media = Await-WinRt ($session.TryGetMediaPropertiesAsync()) $mediaType
    $playback = $session.GetPlaybackInfo()
    $timeline = $session.GetTimelineProperties()
    $duration = [Math]::Max(0, [Math]::Round(($timeline.EndTime - $timeline.StartTime).TotalMilliseconds))
    $position = [Math]::Max(0, [Math]::Round(($timeline.Position - $timeline.StartTime).TotalMilliseconds))
    $playing = $playback.PlaybackStatus.ToString() -eq 'Playing'
    $sampled = [DateTimeOffset]::UtcNow
    if ($playing -and $duration -gt 0 -and $timeline.LastUpdatedTime.Year -gt 2000) {
        $age = [Math]::Max(0, ($sampled - [DateTimeOffset]$timeline.LastUpdatedTime).TotalMilliseconds)
        $rate = if ($null -ne $playback.PlaybackRate) { [double]$playback.PlaybackRate } else { 1.0 }
        $position = [Math]::Min($duration, [Math]::Round($position + $age * $rate))
    }
    return @{
        ok = $true; available = $true; title = [string]$media.Title; artist = [string]$media.Artist
        album = [string]$media.AlbumTitle; playing = $playing
        canSeek = [bool]$playback.Controls.IsPlaybackPositionEnabled -and $duration -gt 0
        positionMs = $position; durationMs = $duration; sampledAtMs = $sampled.ToUnixTimeMilliseconds()
        source = if ($session.SourceAppUserModelId -match 'spotify') { 'Spotify' } else { 'Browser' }
    }
}

try {
    Add-Type -AssemblyName System.Runtime.WindowsRuntime

    $managerType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType=WindowsRuntime]
    $mediaType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties, Windows.Media.Control, ContentType=WindowsRuntime]
    $manager = Await-WinRt ($managerType::RequestAsync()) $managerType

    if ($Watch -and $Action -eq 'status') {
        while ($true) {
            try { $value = Read-Status $manager }
            catch { $value = @{ ok = $false; available = $false; error = $_.Exception.Message } }
            [Console]::Out.WriteLine(($value | ConvertTo-Json -Compress -Depth 4))
            [Console]::Out.Flush()
            Start-Sleep -Milliseconds 250
        }
    }
    if ($Action -eq 'status') { Result (Read-Status $manager); exit 0 }

    $sessions = @($manager.GetSessions())
    $spotify = @($sessions | Where-Object { $_.SourceAppUserModelId -match 'spotify' })
    $browser = @($sessions | Where-Object { $_.SourceAppUserModelId -match 'chrome|msedge|firefox|brave|opera|vivaldi' })

    # Prefer a playing Spotify desktop session. If there is none, use the
    # playing browser media session; that covers Spotify's web player.
    $session = @($spotify | Where-Object { $_.GetPlaybackInfo().PlaybackStatus.ToString() -eq 'Playing' }) | Select-Object -First 1
    if ($null -eq $session) {
        $session = @($browser | Where-Object { $_.GetPlaybackInfo().PlaybackStatus.ToString() -eq 'Playing' }) | Select-Object -First 1
    }
    if ($null -eq $session) { $session = $spotify | Select-Object -First 1 }
    if ($null -eq $session) { $session = $browser | Select-Object -First 1 }

    if ($null -eq $session) {
        Result @{ ok = $true; available = $false }
        exit 0
    }

    if ($Action -eq 'seek') {
        $playback = $session.GetPlaybackInfo()
        $timeline = $session.GetTimelineProperties()
        $duration = [Math]::Max(0, [Math]::Round(($timeline.EndTime - $timeline.StartTime).TotalMilliseconds))
        if (-not $playback.Controls.IsPlaybackPositionEnabled -or $duration -le 0) {
            Result @{ ok = $false; available = $true; error = 'This player does not support seeking.' }
            exit 0
        }

        # A track may change while a scrub waits behind a status/artwork query.
        if (-not [string]::IsNullOrWhiteSpace($ExpectedTrack)) {
            $expected = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($ExpectedTrack)) | ConvertFrom-Json
            $currentMedia = Await-WinRt ($session.TryGetMediaPropertiesAsync()) $mediaType
            $source = if ($session.SourceAppUserModelId -match 'spotify') { 'Spotify' } else { 'Browser' }
            if ([string]$currentMedia.Title -cne [string]$expected.title -or
                [string]$currentMedia.Artist -cne [string]$expected.artist -or
                $source -cne [string]$expected.source -or $duration -ne [long]$expected.durationMs) {
                Result @{ ok = $false; available = $true; changed = $true; error = 'The track changed before seeking.' }
                exit 0
            }
        }

        $relative = [long][Math]::Max(0, [Math]::Min($duration, $SeekMs))
        $requestedTicks = $timeline.StartTime.Ticks + $relative * 10000L
        if ($timeline.MaxSeekTime -gt $timeline.MinSeekTime) {
            $requestedTicks = [Math]::Max($timeline.MinSeekTime.Ticks, [Math]::Min($timeline.MaxSeekTime.Ticks, $requestedTicks))
        }
        $worked = Await-WinRt ($session.TryChangePlaybackPositionAsync([long]$requestedTicks)) ([bool])
        Result @{
            ok = [bool]$worked
            available = $true
            positionMs = [long][Math]::Round(($requestedTicks - $timeline.StartTime.Ticks) / 10000.0)
            error = if ($worked) { '' } else { 'The player declined the seek request.' }
        }
        exit 0
    }
    if ($Action -in @('previous', 'toggle', 'next')) {
        $operation = switch ($Action) {
            'previous' { $session.TrySkipPreviousAsync() }
            'toggle'   { $session.TryTogglePlayPauseAsync() }
            'next'     { $session.TrySkipNextAsync() }
        }

        $worked = Await-WinRt $operation ([bool])
        Result @{ ok = [bool]$worked; available = $true }
        exit 0
    }

    $media = Await-WinRt ($session.TryGetMediaPropertiesAsync()) $mediaType

    if ($Action -eq 'art') {
        $artTimeline = $session.GetTimelineProperties()
        $artResult = @{
            ok = $true
            available = $true
            artwork = $false
            title = [string]$media.Title
            artist = [string]$media.Artist
            source = if ($session.SourceAppUserModelId -match 'spotify') { 'Spotify' } else { 'Browser' }
            durationMs = [Math]::Max(0, [Math]::Round(($artTimeline.EndTime - $artTimeline.StartTime).TotalMilliseconds))
        }
        if ($null -eq $media.Thumbnail -or [string]::IsNullOrWhiteSpace($ArtworkPath)) {
            Result $artResult
            exit 0
        }

        $streamType = [Windows.Storage.Streams.IRandomAccessStreamWithContentType, Windows.Storage.Streams, ContentType=WindowsRuntime]
        $stream = Await-WinRt ($media.Thumbnail.OpenReadAsync()) $streamType
        $asStream = [System.IO.WindowsRuntimeStreamExtensions].GetMethods() |
            Where-Object { $_.Name -eq 'AsStream' -and $_.GetParameters().Count -eq 1 } |
            Select-Object -First 1
        $managed = $asStream.Invoke($null, @($stream))

        try {
            if ($managed.Length -gt 5000000) { throw 'Album artwork is too large.' }
            $memory = New-Object System.IO.MemoryStream
            $managed.CopyTo($memory)
            [System.IO.File]::WriteAllBytes($ArtworkPath, $memory.ToArray())
            $memory.Dispose()
        } finally {
            $managed.Dispose()
        }

        $artResult.artwork = $true
        Result $artResult
        exit 0
    }

    $playback = $session.GetPlaybackInfo()
    $timeline = $session.GetTimelineProperties()

    $duration = [Math]::Max(0, [Math]::Round(($timeline.EndTime - $timeline.StartTime).TotalMilliseconds))
    $position = [Math]::Max(0, [Math]::Round(($timeline.Position - $timeline.StartTime).TotalMilliseconds))
    $playing = $playback.PlaybackStatus.ToString() -eq 'Playing'
    # Windows reports Position at LastUpdatedTime, rather than necessarily at this query.
    if ($playing -and $duration -gt 0 -and $timeline.LastUpdatedTime.Year -gt 2000) {
        $age = [Math]::Max(0, ([DateTimeOffset]::UtcNow - [DateTimeOffset]$timeline.LastUpdatedTime).TotalMilliseconds)
        $rate = if ($null -ne $playback.PlaybackRate) { [double]$playback.PlaybackRate } else { 1.0 }
        $position = [Math]::Min($duration, [Math]::Round($position + $age * $rate))
    }

    Result @{
        ok = $true
        available = $true
        title = [string]$media.Title
        artist = [string]$media.Artist
        album = [string]$media.AlbumTitle
        playing = $playing
        canSeek = [bool]$playback.Controls.IsPlaybackPositionEnabled -and $duration -gt 0
        positionMs = $position
        durationMs = $duration
        source = if ($session.SourceAppUserModelId -match 'spotify') { 'Spotify' } else { 'Browser' }
    }
} catch {
    Result @{ ok = $false; available = $false; error = $_.Exception.Message }
}

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Runtime.WindowsRuntime

function Wait-WinRt($operation, [Type]$resultType) {
    $method = [System.WindowsRuntimeSystemExtensions].GetMethods() |
        Where-Object { $_.Name -eq "AsTask" -and $_.IsGenericMethod -and $_.GetParameters().Length -eq 1 -and $_.GetParameters()[0].ParameterType.Name -like "IAsyncOperation*" } |
        Select-Object -First 1
    if ($null -eq $method) { throw "WinRT async bridge unavailable" }
    $task = $method.MakeGenericMethod($resultType).Invoke($null, @($operation))
    $task.GetAwaiter().GetResult()
}

$managerType = [Type]::GetType("Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType=WindowsRuntime")
$propsType = [Type]::GetType("Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties, Windows.Media.Control, ContentType=WindowsRuntime")
$streamType = [Type]::GetType("Windows.Storage.Streams.IRandomAccessStreamWithContentType, Windows.Storage, ContentType=WindowsRuntime")
$readerType = [Type]::GetType("Windows.Storage.Streams.DataReader, Windows.Storage, ContentType=WindowsRuntime")
if ($null -eq $managerType -or $null -eq $propsType) { throw "Windows media session API unavailable" }
$managerOp = $managerType.GetMethod("RequestAsync").Invoke($null, @())
$manager = Wait-WinRt $managerOp $managerType

$pendingCommand = [Console]::In.ReadLineAsync()
$lastCoverBase64 = $null
while ($true) {
    try {
        if ($pendingCommand.IsCompleted) {
            $command = $pendingCommand.GetAwaiter().GetResult()
            if ($null -eq $command) { break }
            $pendingCommand = [Console]::In.ReadLineAsync()
            if ($command -eq "PLAYPAUSE" -or $command -eq "NEXT" -or $command -eq "PREVIOUS") {
                $active = $manager.GetCurrentSession()
                if ($null -ne $active) {
                    $methodName = switch ($command) {
                        "PLAYPAUSE" { "TryTogglePlayPauseAsync" }
                        "NEXT" { "TrySkipNextAsync" }
                        "PREVIOUS" { "TrySkipPreviousAsync" }
                    }
                    $operation = $active.GetType().GetMethod($methodName).Invoke($active, @())
                    Wait-WinRt $operation ([bool]) | Out-Null
                }
            }
        }

        $session = $manager.GetCurrentSession()
        $coverBase64 = ""
        if ($null -eq $session) {
            $payload = @{ available=$false; title=""; artist=""; app=""; position=0; duration=0; playing=$false }
        } else {
            $operation = $session.GetType().GetMethod("TryGetMediaPropertiesAsync").Invoke($session, @())
            $props = Wait-WinRt $operation $propsType
            $timeline = $session.GetTimelineProperties()
            $playback = $session.GetPlaybackInfo()
            $position = [Math]::Max(0, [int64]$timeline.Position.TotalMilliseconds)
            $duration = [Math]::Max(0, [int64]$timeline.EndTime.TotalMilliseconds)

            # Windows exposes the actual album/track artwork as a WinRT thumbnail stream.
            try {
                if ($null -ne $props.Thumbnail -and $null -ne $streamType -and $null -ne $readerType) {
                    $thumbStream = Wait-WinRt ($props.Thumbnail.OpenReadAsync()) $streamType
                    $size = [uint32]$thumbStream.Size
                    if ($size -gt 0 -and $size -le 4194304) {
                        $reader = [Activator]::CreateInstance($readerType, @($thumbStream.GetInputStreamAt(0)))
                        Wait-WinRt ($reader.LoadAsync($size)) ([uint32]) | Out-Null
                        $buffer = New-Object byte[] ([int]$size)
                        $reader.ReadBytes($buffer)
                        $coverBase64 = [Convert]::ToBase64String($buffer)
                        $reader.DetachStream()
                        $reader.Close()
                    }
                }
            } catch {
                $coverBase64 = ""
            }

            $payload = @{
                available=$true
                title=[string]$props.Title
                artist=[string]$props.Artist
                app=""
                position=$position
                duration=$duration
                playing=($playback.PlaybackStatus.ToString() -eq "Playing")
            }
        }

        # Send artwork only when it changes, keeping the bridge output small.
        if ($coverBase64 -ne $lastCoverBase64) {
            $lastCoverBase64 = $coverBase64
            $payload.cover = $coverBase64
        }
        $payload | ConvertTo-Json -Compress
    } catch {
        $payload = @{ available=$false; title=""; artist=""; app=""; position=0; duration=0; playing=$false; cover="" }
        if ($lastCoverBase64 -ne "") { $lastCoverBase64 = ""; $payload.cover = "" }
        $payload | ConvertTo-Json -Compress
    }
    Start-Sleep -Milliseconds 900
}

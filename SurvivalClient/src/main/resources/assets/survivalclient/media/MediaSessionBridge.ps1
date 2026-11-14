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
if ($null -eq $managerType -or $null -eq $propsType) { throw "Windows media session API unavailable" }
$managerOp = $managerType.GetMethod("RequestAsync").Invoke($null, @())
$manager = Wait-WinRt $managerOp $managerType

$pendingCommand = [Console]::In.ReadLineAsync()
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
                    $boolType = [bool]
                    Wait-WinRt $operation $boolType | Out-Null
                }
            }
        }

        $session = $manager.GetCurrentSession()
        if ($null -eq $session) {
            @{ available=$false; title=""; artist=""; app=""; position=0; duration=0; playing=$false } | ConvertTo-Json -Compress
        } else {
            $operation = $session.GetType().GetMethod("TryGetMediaPropertiesAsync").Invoke($session, @())
            $props = Wait-WinRt $operation $propsType
            $timeline = $session.GetTimelineProperties()
            $playback = $session.GetPlaybackInfo()
            $position = [Math]::Max(0, [int64]$timeline.Position.TotalMilliseconds)
            $duration = [Math]::Max(0, [int64]$timeline.EndTime.TotalMilliseconds)
            @{
                available=$true
                title=[string]$props.Title
                artist=[string]$props.Artist
                app=""
                position=$position
                duration=$duration
                playing=($playback.PlaybackStatus.ToString() -eq "Playing")
            } | ConvertTo-Json -Compress
        }
    } catch {
        @{ available=$false; title=""; artist=""; app=""; position=0; duration=0; playing=$false; error=$_.Exception.Message } | ConvertTo-Json -Compress
    }
    Start-Sleep -Milliseconds 900
}

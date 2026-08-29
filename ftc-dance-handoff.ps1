param(
    [string]$FtcSsid = 'FTC-L3OE',
    [string]$RestoreSsid = 'NETGEAR94',
    [string]$FtcHost = '192.168.43.1',
    [int]$DurationSeconds = 22,
    [switch]$SkipInstall,
    [switch]$DeployOnly,
    [string]$OpMode = 'DanceBot Auto'
)

# ADB uses nonzero exits while its TCP transport is coming online. Keep those
# results non-terminating and explicitly decide which ones are fatal below.
$ErrorActionPreference = 'Continue'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$log = Join-Path $root 'reports\ftc-dance-handoff.log'
$apk = 'C:\Temp\FTC_demo_deploy\TeamCode\build\outputs\apk\debug\TeamCode-debug.apk'
$adb = 'C:\Users\josep\AppData\Local\Android\Sdk\platform-tools\adb.exe'

New-Item -ItemType Directory -Force -Path (Split-Path -Parent $log) | Out-Null
Start-Transcript -Path $log -Append | Out-Null

function Join-Wifi([string]$ssid) {
    netsh wlan connect name="$ssid" interface='Wi-Fi 3' | Out-Host
    for ($i = 0; $i -lt 20; $i++) {
        $state = netsh wlan show interfaces
        if ($state -match "SSID\s+:\s+$([regex]::Escape($ssid))") { return }
        Start-Sleep -Seconds 1
    }
    throw "Timed out joining Wi-Fi profile $ssid"
}

function Connect-Adb {
    # Clear any stale local ADB transport left by an interrupted Wi-Fi handoff.
    & $adb kill-server 2>&1 | Out-Host
    Start-Sleep -Seconds 2
    & $adb start-server 2>&1 | Out-Host
    for ($i = 1; $i -le 8; $i++) {
        & $adb disconnect "${FtcHost}:5555" 2>&1 | Out-Host
        & $adb connect "${FtcHost}:5555" 2>&1 | Out-Host
        $state = (& $adb -s "${FtcHost}:5555" get-state 2>$null | Select-Object -Last 1)
        if ($state -eq 'device') { return }
        Start-Sleep -Seconds 3
    }
    throw 'ADB did not reach device state'
}

function BE16([int]$n) { return [byte[]]@([byte](($n -shr 8) -band 255), [byte]($n -band 255)) }
function BE64([Int64]$n) {
    $b = [BitConverter]::GetBytes($n)
    [Array]::Reverse($b)
    return $b
}
function New-Command([string]$name, [string]$extra, [int]$seq, [bool]$ack, [Int64]$timestamp = [Int64]0) {
    if ($timestamp -eq 0) { $timestamp = [System.Diagnostics.Stopwatch]::GetTimestamp() }
    $nb = [Text.Encoding]::UTF8.GetBytes($name)
    $eb = [Text.Encoding]::UTF8.GetBytes($extra)
    $payload = [Collections.Generic.List[byte]]::new()
    foreach($b in (BE64 $timestamp)) { $payload.Add([byte]$b) }
    $payload.Add([byte]$(if ($ack) { 1 } else { 0 }))
    foreach($b in (BE16 $nb.Length)) { $payload.Add([byte]$b) }
    foreach($b in $nb) { $payload.Add([byte]$b) }
    if (-not $ack) { foreach($b in (BE16 $eb.Length)) { $payload.Add([byte]$b) }; foreach($b in $eb) { $payload.Add([byte]$b) } }
    $packet = [Collections.Generic.List[byte]]::new()
    $packet.Add(4); foreach($b in (BE16 $payload.Count)) { $packet.Add([byte]$b) }; foreach($b in (BE16 $seq)) { $packet.Add([byte]$b) }; foreach($b in $payload) { $packet.Add([byte]$b) }
    return $packet.ToArray()
}
function New-Peer([int]$seq) { return [byte[]](3,0,10,124,1,(($seq -shr 8)-band 255),($seq-band 255),1,7,234,11,1,0) }
function Read-BE16([byte[]]$bytes, [int]$at) { return (($bytes[$at] -shl 8) -bor $bytes[$at + 1]) }
function Read-BE64([byte[]]$bytes, [int]$at) {
    [Int64]$v=0; for($i=0;$i -lt 8;$i++) { $v=($v -shl 8) -bor $bytes[$at+$i] }; return $v
}

try {
    if (-not (Test-Path $apk)) { throw "Missing APK: $apk" }
    if (-not (Test-Path $adb)) { throw "Missing adb: $adb" }
    Join-Wifi $FtcSsid
    Connect-Adb
    if (-not $SkipInstall) {
        if (-not $DeployOnly) {
            # Approved fallback for a signature-incompatible installed controller.
            & $adb -s "${FtcHost}:5555" uninstall com.qualcomm.ftcrobotcontroller 2>&1 | Out-Host
            # It may already be absent after a prior handoff attempt.
        }
        # Older Control Hub ADB daemons can reject the modern streamed installer,
        # and may reset their TCP connection during a large transfer. Retry only
        # the idempotent install after rebuilding the transport.
        $installed = $false
        for ($attempt = 1; $attempt -le 5; $attempt++) {
            Write-Host "APK install attempt $attempt of 5"
            & $adb -s "${FtcHost}:5555" install --no-streaming -r -d $apk 2>&1 | Out-Host
            if ($LASTEXITCODE -eq 0) { $installed = $true; break }
            Start-Sleep -Seconds 5
            Connect-Adb
        }
        if (-not $installed) { throw 'APK installation failed after five transfer attempts' }
    }
    & $adb -s "${FtcHost}:5555" shell monkey -p com.qualcomm.ftcrobotcontroller -c android.intent.category.LAUNCHER 1 2>&1 | Out-Host
    Start-Sleep -Seconds 3
    if ($DeployOnly) { Write-Host 'Deployment complete; no OpMode was launched.'; return }

    $udp = [Net.Sockets.UdpClient]::new(20884)
    $target = [Net.IPEndPoint]::new([Net.IPAddress]::Parse($FtcHost),20884)
    [int]$seq = 0
    $send = { param([byte[]]$packet) [void]$udp.Send($packet,$packet.Length,$target) }
    $peer = { & $send (New-Peer $seq); $seq = ($seq + 1) -band 65535 }
    $command = { param([string]$name,[string]$extra,[bool]$ack,[Int64]$stamp=0) & $send (New-Command $name $extra $seq $ack $stamp); $seq = ($seq + 1) -band 65535 }
    $waitFor = {
        param([string]$expectedName,[string]$expectedExtra,[int]$seconds)
        $deadline=[DateTime]::UtcNow.AddSeconds($seconds); $nextPeer=[DateTime]::MinValue
        while([DateTime]::UtcNow -lt $deadline) {
            if([DateTime]::UtcNow -ge $nextPeer) { & $peer; $nextPeer=[DateTime]::UtcNow.AddMilliseconds(750) }
            if($udp.Available -gt 0) {
                $remote=[Net.IPEndPoint]::new([Net.IPAddress]::Any,0); $data=$udp.Receive([ref]$remote)
                if($data.Length -ge 16 -and $data[0] -eq 4) {
                    $stamp=Read-BE64 $data 5; $isAck=$data[13] -ne 0; $nameLen=Read-BE16 $data 14
                    if(16+$nameLen -le $data.Length) {
                        $name=[Text.Encoding]::UTF8.GetString($data,16,$nameLen); $offset=16+$nameLen; $extra=''
                        if(-not $isAck -and $offset+2 -le $data.Length) { $extraLen=Read-BE16 $data $offset; $offset+=2; if($offset+$extraLen -le $data.Length){$extra=[Text.Encoding]::UTF8.GetString($data,$offset,$extraLen)} }
                        if(-not $isAck) { & $command $name '' $true $stamp }
                        if($name -eq $expectedName -and ($expectedExtra -eq $null -or $extra -eq $expectedExtra)) { return }
                    }
                }
            }
            Start-Sleep -Milliseconds 50
        }
        throw "Timed out waiting for $expectedName $expectedExtra"
    }
    # Stop any prior OpMode, then select and run DanceBot Auto. The final stop is guaranteed.
    1..3 | ForEach-Object { & $command 'CMD_INIT_OP_MODE' '$Stop$Robot$' $false; Start-Sleep -Milliseconds 250 }
    & $command 'CMD_INIT_OP_MODE' $OpMode $false
    & $waitFor 'CMD_NOTIFY_INIT_OP_MODE' $OpMode 8
    & $command 'CMD_RUN_OP_MODE' $OpMode $false
    & $waitFor 'CMD_NOTIFY_RUN_OP_MODE' $OpMode 5
    $until=[DateTime]::UtcNow.AddSeconds($DurationSeconds)
    while([DateTime]::UtcNow -lt $until) { & $peer; Start-Sleep -Milliseconds 700 }
    1..3 | ForEach-Object { & $command 'CMD_INIT_OP_MODE' '$Stop$Robot$' $false; Start-Sleep -Milliseconds 250 }
    $udp.Dispose()
    Write-Host "$OpMode completed and stopped."
}
catch {
    Write-Error $_
    exit 1
}
finally {
    try { Join-Wifi $RestoreSsid } catch { Write-Error "Could not restore $RestoreSsid : $_" }
    Stop-Transcript | Out-Null
}

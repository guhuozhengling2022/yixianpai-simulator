param([ValidateRange(1024,65535)][int]$Port=8081,[switch]$NoBrowser)
$ErrorActionPreference='Stop'
$taskRoot=Split-Path -Parent $PSScriptRoot
$mutex=New-Object System.Threading.Mutex($false,('Local\YxpLauncher'+$Port))
$locked=$false
$portableJava=Join-Path $taskRoot "runtime/java/bin/java.exe"
$portablePython=Join-Path $taskRoot "runtime/python/python.exe"
if(Test-Path -LiteralPath $portablePython){$env:YXP_PYTHON=$portablePython}
function Test-Simulator {
    try {
        $data=Invoke-RestMethod -Uri "http://127.0.0.1:$Port/api/definitions" -TimeoutSec 2
        if ($data.singleUseCardLimit -ne $true) { return $false }
        if ($data.rulesetId -notlike 'local-snapshot-*' -or $null -eq $data.cards -or $data.damagePreview -ne $true -or $data.deckSearch -ne $true -or $null -eq $data.seasonTalents -or $data.cachedBattleImport -ne $true -or $data.balancePatch -ne '2026-11-12' -or $null -eq $data.plantRefinements -or $data.cardOutcomeInputs -ne $true -or $data.automaticInitiative -ne $true) { return $false }
        $tracker=Invoke-RestMethod -Uri "http://127.0.0.1:$Port/api/match-tracker" -TimeoutSec 2
        return ($tracker.status -in @('WAITING','FIRST_ROUND','READY','UPDATING','UNREADABLE','MISSING','EMPTY','UNRECOGNIZED'))
    } catch { return $false }
}
try {
    $locked=$mutex.WaitOne(30000)
    if(!$locked){throw '启动正在进行，请稍后再试。'}
    $url="http://127.0.0.1:$Port/"
    if(!(Test-Simulator)) {
        $jar=Join-Path $taskRoot 'backend/target/battle-simulator-0.1.0.jar'
        if(!(Test-Path -LiteralPath $jar) -or !(Test-Path -LiteralPath (Join-Path $taskRoot 'frontend/dist/index.html'))){throw '缺少构建文件，请先运行 scripts/build.ps1。'}
        $listener=[System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback,$Port)
        try {$listener.Start()} catch {throw "端口 $Port 已被占用。请关闭旧版模拟器或占用端口的程序后重试。"} finally {$listener.Stop()}
        $java=if(Test-Path -LiteralPath $portableJava){$portableJava}elseif($env:JAVA_HOME -and (Test-Path -LiteralPath "$env:JAVA_HOME/bin/java.exe")){"$env:JAVA_HOME/bin/java.exe"}else{(Get-Command java.exe -ErrorAction Stop).Source}
        $logRoot=Join-Path $taskRoot 'backend/target'
        $process=Start-Process -FilePath $java -ArgumentList @('-jar',('"'+$jar+'"'),$Port) -WorkingDirectory $taskRoot -WindowStyle Hidden -RedirectStandardOutput (Join-Path $logRoot "launcher-$Port.stdout.log") -RedirectStandardError (Join-Path $logRoot "launcher-$Port.stderr.log") -PassThru
        $ready=$false
        for($attempt=0;$attempt -lt 40;$attempt++){
            if(Test-Simulator){$ready=$true;break}
            if($process.HasExited){break}
            Start-Sleep -Milliseconds 500
        }
        if(!$ready){throw "启动失败，请查看 backend/target/launcher-$Port.stderr.log。"}
        Set-Content -LiteralPath (Join-Path $logRoot "launcher-$Port.pid") -Value $process.Id
    }
    . (Join-Path $PSScriptRoot 'ocr-support.ps1')
    $ocrStatus=Get-ChineseOcrStatus
    $ocrNotice=Get-ChineseOcrNotice $ocrStatus
    if($ocrNotice){
        if($NoBrowser){Write-Warning $ocrNotice}
        else {
            $ocrPopup=New-Object -ComObject WScript.Shell
            [void]$ocrPopup.Popup($ocrNotice,0,'弈仙牌模拟器：中文OCR检查',64)
        }
    }
    if(!$NoBrowser){Start-Process $url}
    Write-Output "模拟器已就绪：$url"
} catch {
    if($NoBrowser){throw}
    $popup=New-Object -ComObject WScript.Shell
    [void]$popup.Popup($_.Exception.Message,0,'弈仙牌模拟器启动失败',16)
} finally {
    if($locked){$mutex.ReleaseMutex()}
    $mutex.Dispose()
}

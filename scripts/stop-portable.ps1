param([int]$Port=8081,[switch]$Quiet)
$ErrorActionPreference='Stop'
try {
 $taskRoot=Split-Path -Parent $PSScriptRoot
 $pidFile=Join-Path $taskRoot "backend/target/launcher-$Port.pid"
 if(!(Test-Path -LiteralPath $pidFile)){throw '未找到本压缩包启动的服务记录。'}
 $serverId=[int](Get-Content -LiteralPath $pidFile -Raw).Trim()
 $server=Get-Process -Id $serverId -ErrorAction SilentlyContinue
 if($server){
  $expected=Join-Path $taskRoot 'runtime/java/bin/java.exe'
  if(![string]::Equals($server.Path,$expected,[StringComparison]::OrdinalIgnoreCase)){throw '进程与本压缩包不匹配，未关闭其他程序。'}
  Stop-Process -Id $serverId
 }
 Remove-Item -LiteralPath $pidFile
 if(!$Quiet){[void](New-Object -ComObject WScript.Shell).Popup('模拟器后台服务已关闭，可以关闭浏览器页面。',0,'弈仙牌模拟器',64)}
} catch {if($Quiet){throw};[void](New-Object -ComObject WScript.Shell).Popup($_.Exception.Message,0,'关闭提示',48)}

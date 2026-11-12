function Get-ChineseOcrStatus {
    try {
        $null=[Windows.Media.Ocr.OcrEngine,Windows.Foundation,ContentType=WindowsRuntime]
        $null=[Windows.Globalization.Language,Windows.Globalization,ContentType=WindowsRuntime]
        $language=[Windows.Globalization.Language]::new('zh-CN')
        $engine=[Windows.Media.Ocr.OcrEngine]::TryCreateFromLanguage($language)
        if($null -ne $engine){return 'Available'}
        return 'Missing'
    } catch {return 'Unknown'}
}
function Get-ChineseOcrNotice([string]$Status) {
    if($Status -eq 'Available'){return ''}
    $headline=if($Status -eq 'Missing'){'未检测到可用的简体中文OCR语言包。'}else{'无法确认简体中文OCR是否可用，请按以下步骤检查。'}
    return @"
$headline
截图导入可能无法识别文字；仍可手动选牌和使用战斗模拟。

安装方法（需要联网）：
1. 开始菜单搜索 Windows PowerShell，右键“以管理员身份运行”。
2. 复制并执行以下命令：
Add-WindowsCapability -Online -Name 'Language.OCR~~~zh-CN~0.0.1.0'
3. 完成后，如 RestartNeeded 为 True，请重启电脑；然后重新启动模拟器。

检查是否已安装：
Get-WindowsCapability -Online -Name 'Language.OCR~~~zh-CN~0.0.1.0'
State 为 Installed 表示已安装。

完整步骤也保存在项目目录的“中文OCR安装说明.txt”。程序不会自动安装或修改系统。
"@
}

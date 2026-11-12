import shutil,subprocess,os,datetime
from pathlib import Path
root=Path(__file__).resolve().parents[1]
release=root/'_local'/'release'
out=release/('yixianpai-simulator-windows-x64-'+datetime.datetime.now().strftime('%Y%m%d-%H%M%S'))
out.mkdir(parents=True)
def copy(src,dest):
 src=root/src if not isinstance(src,Path) else src;dest=out/dest
 dest.parent.mkdir(parents=True,exist_ok=True)
 if src.is_dir():shutil.copytree(src,dest,ignore=shutil.ignore_patterns('__pycache__'))
 else:shutil.copy2(src,dest)
copy('backend/target/battle-simulator-0.1.0.jar','backend/target/battle-simulator-0.1.0.jar')
copy('backend/src/main/resources/rules.json','backend/src/main/resources/rules.json')
copy('backend/src/main/resources/season-talents.json','backend/src/main/resources/season-talents.json')
copy('frontend/dist','frontend/dist')
for name in ['launch.ps1','stop-portable.ps1','ocr-support.ps1','recognize-screenshot.py','screenshot-ocr.ps1']:copy('scripts/'+name,'scripts/'+name)
copy('LICENSE','LICENSE')
copy('中文OCR安装说明.txt','中文OCR安装说明.txt')
subprocess.run(['C:/Program Files/Java/jdk-21.0.10/bin/jlink.exe','--add-modules','java.base,java.desktop,java.net.http,jdk.httpserver,jdk.crypto.ec','--strip-debug','--no-header-files','--no-man-pages','--output',str(out/'runtime/java')],check=True)
py=Path(os.environ['USERPROFILE'])/'.cache/codex-runtimes/codex-primary-runtime/dependencies/python'
(out/'runtime/python').mkdir(parents=True)
for p in py.iterdir():
 if p.is_file() and p.suffix in ['.exe','.dll','.txt']:copy(p,'runtime/python/'+p.name)
copy(py/'DLLs','runtime/python/DLLs')
shutil.copytree(py/'Lib',out/'runtime/python/Lib',ignore=shutil.ignore_patterns('site-packages','__pycache__','test','tests','idlelib','tkinter','ensurepip'))
for p in (py/'Lib/site-packages').iterdir():
 if p.name=='PIL' or p.name.lower().startswith(('numpy','pillow')):copy(p,'runtime/python/Lib/site-packages/'+p.name)
for name,script in [('启动模拟器','launch.ps1'),('关闭模拟器','stop-portable.ps1')]:
 command = f'@echo off\nstart "" powershell.exe -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File "%~dp0scripts\\{script}"\n'
 (out/(name+'.cmd')).write_text(command,encoding='ascii',newline='\r\n')
(out/'使用说明.txt').write_text(
    'Windows 10/11 x64 便携测试包\n\n'
    '请先完整解压，再双击“启动模拟器.cmd”。用完双击“关闭模拟器.cmd”。关闭浏览器后，后台仍会运行。\n'
    '附带 Java 和 Python。中文 OCR 依赖 Windows 的简体中文 OCR 语言包，缺少时会弹窗说明。\n'
    '8081 端口被占用时，请先关闭旧版模拟器。\n\n'
    '对局追踪\n'
    '通过 Steam 打开《弈仙牌》，在游戏里选择模式并开始匹配。追踪页会读取本机已写入的轮次。\n'
    '在“标记我方”选自己，再点对手记录下的“导入双方到模拟器”。\n'
    '日志缺少手牌、仙命和先手信息，推演前请补齐或核对。\n',
    encoding='utf-8-sig')
archive = shutil.make_archive(str(out), 'zip', root_dir=out.parent, base_dir=out.name)
(release/'latest-build.txt').write_text(str(out),encoding='utf-8')
print(archive,flush=True)

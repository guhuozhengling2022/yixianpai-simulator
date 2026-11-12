# 弈仙牌战斗模拟器

这是一个面向《弈仙牌》玩家的非官方本地工具。输入双方角色、属性、仙命、卡组和手牌，就可以推演一场双人斗法，查看胜负、最终生命和逐次结算日志。可以用它复盘对局、比较摆牌顺序，或从已有卡牌中寻找可能击败固定对手的组合。

项目主要通过 AI 辅助开发（vibe coding），目前处于测试阶段。卡牌和仙命的支持范围会随游戏版本变化，部分机制尚未经过实战校准。欢迎用真实对局结果帮助定位偏差。

## 功能

| 功能 | 说明 |
| --- | --- |
| 双人斗法 | 双方可分别设置 3–8 张卡槽、手牌、仙命和持续／消耗牌合计上限。支持自动判断先后手，也可手动指定。 |
| 战斗日志 | 查看逐次出牌、攻击、治疗、资源与状态变化，以及最终胜负和生命。 |
| 伤害速算与卡组求解 | 伤害速算展示最多 8 回合的累计伤害，开启后随摆牌更新。卡组求解从己方场上牌和手牌中尝试不同组合与顺序。 |
| 截图导入 | 战绩截图可填写双方，游戏内截图可导入甲方或乙方。可选择是否导入手牌。公开版使用文字 OCR，角色、图标仙命和牌等级需手动核对。 |
| 对局跟踪与战绩码 | 读取游戏写入本机的对局日志或战绩缓存，查看已记录轮次，并将双方配置导入模拟器。战绩码需先在游戏内打开并产生本机可读取的缓存。 |
| 卡组收藏与测试反馈 | 按名称保存并快速载入常用卡组。推演和实战有出入时，可填入双方真实最终生命，导出本地 JSON 供复现。 |

模拟范围是一场双人斗法。涉及局外成长、换牌或整局淘汰的效果以开场配置和现有记录为准。求解结果受搜索时间和随机结果影响。

## 运行

### Windows 便携包

[下载 Windows x64 便携包（v0.1.0-beta.1）](https://github.com/guhuozhengling2022/yixianpai-simulator/releases/download/v0.1.0-beta.1/yixianpai-simulator-v0.1.0-beta.1-windows-x64.zip)

完整解压后，双击 `启动模拟器.cmd`，用完双击 `关闭模拟器.cmd`。便携包附带 Java 和 Python，截图导入需要 Windows 简体中文 OCR 语言包。安装方式见 [中文 OCR 安装说明](中文OCR安装说明.txt)。对局跟踪和战绩码导入需要同一台机器上有游戏生成的日志或缓存。

### 从源码运行

准备 JDK 21、Maven 和 Node.js/npm，并将它们加入 PATH。在仓库根目录执行：

```powershell
mvn.cmd -f backend/pom.xml -DskipTests package
npm.cmd --prefix frontend ci
npm.cmd --prefix frontend run build
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/start.ps1
```

打开 [http://127.0.0.1:8080](http://127.0.0.1:8080)，在启动终端按 Ctrl+C 可停止服务。上述命令跳过 Java 测试，便于启动当前测试版。要运行战斗回归，执行 `mvn.cmd -f backend/pom.xml test`，当前仍有已知失败。

源码模式下，如需使用截图导入，请将 `YXP_PYTHON` 指向已安装 Pillow 和 NumPy 的 Python，再启动服务。当前 Windows 打包入口为 [打包模拟器.cmd](打包模拟器.cmd)，它依赖开发机上配置的 JDK 和 Python 路径，输出到 `_local/release`。

## 技术架构

| 位置 | 职责 |
| --- | --- |
| [`backend/`](backend/) | Java 21、Maven、Gson 和 JDK HTTP Server。战斗引擎校验输入、推进回合、结算卡牌与仙命，并生成事件日志。后端也提供规则目录、伤害速算、卡组求解和本地记录读取接口。 |
| [`frontend/`](frontend/) | Vue 3、TypeScript 和 Vite。负责双方配置、卡牌图鉴、收藏、导入和日志展示。生产构建由 Java 服务统一提供。 |
| [`scripts/`](scripts/) | 构建、启动和便携打包脚本，以及本机截图识别。Python 使用 Pillow 和 NumPy 处理图像，再通过 PowerShell 调用 Windows OCR。 |

应用默认只监听本机。截图识别和战绩读取在本地完成，反馈文件由用户自行导出和分享。

## 反馈与公开发布

发现推演和实战不一致时，请在「斗法记录」中导出反馈 JSON，填写双方实际最终生命。如果能定位到首次出现偏差的回合、卡槽或仙命，也请一并说明。分享前可检查并删除个人信息。

## 支持项目

如果这个工具对你有帮助，欢迎自愿支持后续开发和维护。项目继续免费开源。 [爱发电主页](https://afdian.com/a/yxp-simulator)

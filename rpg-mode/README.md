# `:rpg-mode` — YGOMobile OCG 时间轴 RPG 编年史模块

本模块以**最小侵入、零上游冲突**的方式为 `YGOMobile-cn-ko-en` 扩展日版 OCG 编年史 RPG 模式（独立共存包名 `cn.garymb.ygomobile.CN.rpg`）。

## 核心特性（当前已完成：1999 第 1 期全编年史 `1999-02-04 ~ 1999-12-31`）
1. **完整 1999 OCG 实卡池与商品体系**：
   - 收录 1999 年全部日版 OCG 常规卡包、贩卖机 Booster、Starter Box（通常版）、EX、Premium Pack / Limited Edition 与游戏/杂志/东京巨蛋大会特典活动，共 **591 张**卡片的 8 位卡密（与 `cards.cdb` 同源）、中日英卡名、日元原价 1:1 映射代币价与真实稀罕度。
2. **时间轴驱动的规则、禁限表与历史原版效果（Pre-Errata Lua）**：
   - 玩家可自由推进/回退时间点，自动联动已发售卡池、OCG 规则演变（`1999-02-04` 公式规则免祭品/先攻不抽/无6卡手牌上限 → `1999-05-05` 专家规则祭品召唤）与历史 OCG 禁限表（`1999-08-26` 东京巨蛋首份限制表等）。
   - 通过 [`RpgHistoricalLuaBuilder`](src/main/java/cn/garymb/ygomobile/rpg/engine/RpgHistoricalLuaBuilder.java) 在每局 RPG 加载卡组前注入独立 `lua_State` 覆盖，**精确还原 1999 原版卡效**（如 1999 原版 3 回合抽卡破坏《死之卡组破坏病毒》、1999-12-20 前任意区域送墓触发《三眼怪》《黑森林的魔女》、无一回合一次《弹射龟》），对普通 YGOMobile 对战零污染。
3. **13 位多时代进化 AI 对手 + 14 场原著「🎬 对决还原」**：
   - 涵盖 3 位循序渐进的休闲/中坚对手 + 10 位源自 `yugioh-history.com` 第 1 期环境史的主流流派对手（暗游戏、海马、火焰直伤、装备 Beat、反转控制、死毒暗控、东京巨蛋 Good Stuff、艾克佐迪亚、变形壶卡组破坏、融合科学怪人），随时间轴自动切换卡组形态，并全部配备游戏王画风头像。
   - 「🎬 对决还原」收录 14 场 1999 年前后的原作漫画与东映动画经典对决，按原著日期升序展示、按核心卡 OCG 首发日定档解锁、100% 保留原著用卡并支持 4 模式挑战与通关标记。
4. **纯 Java 专属战术 AI、全环境瑞士轮天梯与实测 Elo 奖金校准**：
   - 彻底脱离 WindBot 随机脚本与近似模拟器，由真实 `ocgcore` + [`RpgAiStrategy`](src/main/java/cn/garymb/ygomobile/rpg/ai/RpgAiStrategy.java) 驱动人机对战、AI 托管快战、锦标赛、3D 观战及全环境天梯赛。
   - 内置 [`RoundRobinLeagueEngine`](src/main/java/cn/garymb/ygomobile/rpg/engine/RoundRobinLeagueEngine.java)（$O(N \log N)$ 6 局均衡先后手瑞士轮 + 全局贝叶斯 Bradley-Terry Elo / `±SE` / 对手均分 SOS，支持随时追加轮次与全量循环赛）。
   - [`EraBountyCalibrator`](src/main/java/cn/garymb/ygomobile/rpg/engine/EraBountyCalibrator.java) 基于真实 `ocgcore` 全年代基准实测 Elo 校准每个时期的 AI 对战奖金，保证强弱与收益严格单调一致。
5. **开包、分解合成、严格组卡与多套卡组管理**：
   - 按 OCG 第 1 期规则开包与保底，支持一键分解多余卡、按时间轴合法范围合成单卡、多套卡组保存/导入导出与严格拥有数/发售日/禁限表校验。

## 对上游 `YGOMobile-cn-ko-en` 的极薄接入点
- `settings.gradle`：加入 `':rpg-mode'`
- `mobile/build.gradle`：末尾追加 `apply from: '../rpg-custom.gradle'`
- `MainMenuDialog.java`：1 行 `RpgIntegration.attachMainMenuButton(activity, root);`
- `EngineCallbackDelegate.java`：1 行 `RpgIntegration.onDuelResult(activity, winner, selfWon);`
- `ServerDuelBase.java` / `ServerDuel.java`：在 `loadDeckToEngine` 前 1 行 `RpgIntegration.applyActiveRpgRulesToDuel(pduel);`

## 扩展新年代（2000 第 2 期及以后）强制规范
请务必先阅读 [`docs/OCG_TIMELINE_DATA_SOURCES.md`](docs/OCG_TIMELINE_DATA_SOURCES.md)（含权威 OCG 数据源、新流派 AI & 头像生成、原著对决还原 Backlog [`docs/CANON_DUELS_FUTURE_BACKLOG.md`](docs/CANON_DUELS_FUTURE_BACKLOG.md)、Pre-Errata Lua 还原、全 AI 零废牌守卫与实测 Elo 奖金校准全流程）。

```bash
# 运行全部 RPG 单元测试与自动化守卫校验
./gradlew :rpg-mode:testDebugUnitTest
```

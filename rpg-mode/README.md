# `:rpg-mode` — YGOMobile OCG RPG 编年史模块

本模块以**最小侵入、零上游冲突**的方式为 `YGOMobile-cn-ko-en` 扩展 OCG 编年史 RPG 模式：

## 核心特性（当前 Demo：Vol.1 & Vol.2）
1. **OCG 真实卡包数据**：内置 OCG 最早发售的两弹基本补充包——`Vol.1`（`1999-02-04`，40 种）与 `Vol.2`（`1999-03-27`，40 种），共 80 张卡片的 8 位卡密（与 `cards.cdb` 同源）、中文卡名、稀罕度与攻守效果。
2. **时间轴维度**：玩家可自由移动当前时间点：
   - 仅可购买、合成、使用发售日 `<= 当前时间点` 的卡包与卡片；
   - 自动匹配 `生效日期 <= 当前时间点` 的最新 OCG 历史禁限卡表（如 `1999-02-04` 无禁限 → `1999-08-26` 东京巨蛋首届限制表：黑洞/落穴限 1 → `2000-04-01` 正式制限改订：黑洞限 1、死者苏生准限 2、落穴解除）；
   - AI 对手同样严格遵守当前时间轴的已发售卡池与最新 OCG 禁限表。
3. **开包与保底**：按 OCG 第一期 5 张/包规则（4 张 N + 第 5 张按稀罕度权重抽取，每 5 包保底 R 以上）。
4. **分解与合成**：支持单卡分解、一键分解多余卡（>3 张）换取尘埃，消耗尘埃合成当前时间轴已发售卡片。
5. **严格组卡与 AI 对战**：RPG 卡组同时校验「已拥有数量」「已发售时间」「当前禁限表」，导出标准 `.ydk` 并通过 WindBot `Deck=Lucky DeckFile=...` 发起对战、通过 `EngineCallbackDelegate.onDuelResult` 自动结算代币奖励。

## 对上游 `YGOMobile-cn-ko-en` 的极薄接入点（仅 4 处）
- `settings.gradle`：加入 `':rpg-mode'`
- `mobile/build.gradle`：末尾追加 `apply from: '../rpg-custom.gradle'`
- `MainMenuDialog.java`：1 行 `RpgIntegration.attachMainMenuButton(activity, root);`
- `EngineCallbackDelegate.java`：1 行 `RpgIntegration.onDuelResult(activity, winner, selfWon);`

## 如何同步上游最新改动
```bash
git fetch upstream master
git merge upstream/master
./gradlew :rpg-mode:testDebugUnitTest --configure-on-demand
```
或者直接在 GitHub Actions 手动触发 `.github/workflows/sync-upstream.yml`。

## 扩展更多 OCG 卡包与禁限表
只需向 `rpg-mode/src/main/assets/rpg/ocg_demo_catalog.json` 的 `packs` 与 `banlists` 数组追加新条目（无需修改业务代码）。

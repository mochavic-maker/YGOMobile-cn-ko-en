# OCG 时间轴 RPG 扩展参考数据源手册

本手册记录了构建 **YGOMobile OCG 时间轴 RPG 模式**（1999 第 1 期）过程中检索、核对并使用的全部权威网站与 API 端点，便于日后将时间轴扩展至第 2 期（2000 年）及以后版本时直接查阅与自动化抓取。

> 同步已保存为 Agent Skill：**`ocg-timeline-expansion`**（后续任何对话均可直接调用该技能获取相同清单与扩展流程）。

---

## 1. 卡片基础数据、密码与中英日文名（API）

| 资源名称 | 网址 / 端点 | 用途说明 |
| :--- | :--- | :--- |
| **百鸽 (YGOCDB) API 文档** | https://ygocdb.com/api | 汇总 KONAMI 官方库与 YGOPro 数据库，提供免鉴权 JSON API |
| 百鸽全量卡片数据 | https://ygocdb.com/api/v0/cards.zip | 获取所有卡片的 YGOPro 密码 (`id`)、官方 `cid`、中/日/英卡名、属性种族攻守与效果文本 |
| 百鸽数据 MD5 校验 | https://ygocdb.com/api/v0/cards.zip.md5 | 判断 `cards.zip` 是否有更新 |
| 百鸽全卡发售日期表 | https://ygocdb.com/api/v0/releaseDates.json | 查询卡片各语种首次发售信息 |
| 百鸽禁限卡表（含历史） | https://ygocdb.com/api/v0/limits.json | 获取当前及历史禁限卡表 JSON |
| **KONAMI 官方 OCG 卡片数据库** | https://www.db.yugioh-card.com/yugiohdb/?request_locale=ja | 最终核对卡包正式收录列表、商品 `pid` 与官方最新文本 |

---

## 2. OCG 卡包发售日、日元原价、卡池清单与抽包机制

| 资源名称 | 网址 / 端点 | 用途说明 |
| :--- | :--- | :--- |
| **Yu-Gi-Oh! Wiki (Fandom) Parse API** | `https://yugioh.fandom.com/api.php?action=parse&page=<Page_Name>&prop=text\|wikitext&format=json` | 批量抓取卡包发售日、日元原价、每包张数、完整卡表与稀罕度 |
| Fandom 第 1 期商品总览 | https://yugioh.fandom.com/wiki/Template:Series_1_sets | 含 Vol.1~7、Booster 1~7、Starter Box、EX、Premium Pack、Limited Edition、特典卡（第 2 期可查 `Template:Series_2_sets` 以此类推） |
| **Yugipedia** | https://yugipedia.com/ | 交叉核对 OCG 各类商品（含 Carddass 贩卖机、杂志/游戏/大会特典卡）的准确日期与稀罕度 |
| **YGOPRODeck Pack Simulator** | https://ygoprodeck.com/pack-sim/ | 抽包交互与槽位出率权重机制参考 |
| YGOPRODeck API 指南 | https://ygoprodeck.com/api-guide/ | 卡组/卡包结构参考（注：其发售日以 TCG 为主，OCG 数据仍以 Fandom/Yugipedia/官方库为准） |
| YGOPRODeck Card Sets API | https://db.ygoprodeck.com/api/v7/cardsets.php | 批量检索卡包列表元数据 |

---

## 3. OCG 历代规则演变（LP、先攻抽卡、祭品召唤、抽干判胜等）

来源：**遊戯王カードWiki**（https://yugioh-wiki.net/）

| 时期与规则名称 | 生效时间 | 考据页面 |
| :--- | :--- | :--- |
| **第 1 期 公式ルール** | `1999-02-04` | https://yugioh-wiki.net/?%B8%F8%BC%B0%A5%EB%A1%BC%A5%EB |
| **第 1 期 エキスパートルール** | `1999-05-05` | https://yugioh-wiki.net/?%A5%A8%A5%AD%A5%B9%A5%D1%A1%BC%A5%C8%A5%EB%A1%BC%A5%EB |
| **第 2~5 期 新エキスパートルール** | `2000-04-20` | https://yugioh-wiki.net/?%BF%B7%A5%A8%A5%AD%A5%B9%A5%D1%A1%BC%A5%C8%A5%EB%A1%BC%A5%EB |
| **第 6~7 期 マスタールール** | `2008-03-15` | https://yugioh-wiki.net/?%A5%DE%A5%B9%A5%BF%A1%BC%A5%EB%A1%BC%A5%EB |
| **第 7~8 期 マスタールール2** | `2011-03-19` | https://yugioh-wiki.net/?%A5%DE%A5%B9%A5%BF%A1%BC%A5%EB%A1%BC%A5%EB%A3%B2 |
| **第 9 期 マスタールール3** | `2014-03-21` | https://yugioh-wiki.net/?%A5%DE%A5%B9%A5%BF%A1%BC%A5%EB%A1%BC%A5%EB%A3%B3 |
| **第 10 期 新マスタールール** | `2017-03-25` | https://yugioh-wiki.net/?%BF%B7%A5%DE%A5%B9%A5%BF%A1%BC%A5%EB%A1%BC%A5%EB |
| **第 11 期~现行 マスタールール(11期)** | `2020-04-01` | https://yugioh-wiki.net/?%A5%DE%A5%B9%A5%BF%A1%BC%A5%EB%A1%BC%A5%EB%2811%B4%FC%29 |

---

## 4. OCG 历次禁限卡表（禁止・制限カード / リミットレギュレーション）

| 资源名称 | 网址 | 用途说明 |
| :--- | :--- | :--- |
| **禁止・制限カード 总索引（1999 ~ 2013.08）** | https://yugioh-wiki.net/?%B6%D8%BB%DF%A1%A6%C0%A9%B8%C2%A5%AB%A1%BC%A5%C9 | 涵盖第 1 期至第 8 期全部历史禁限卡表 |
| 1999 年首次限制表 | https://yugioh-wiki.net/?%B6%D8%BB%DF%A1%A6%C0%A9%B8%C2%A5%AB%A1%BC%A5%C9%2F1999 | 1999 年东京巨蛋大会起生效的 OCG 首次限制卡表 |
| 2000 年 4 月 1 日限制表（第 2 期起点） | https://yugioh-wiki.net/?%B6%D8%BB%DF%A1%A6%C0%A9%B8%C2%A5%AB%A1%BC%A5%C9%2F2000%C7%AF4%B7%EE1%C6%FC | 扩展至 2000 年时的下一份核心限表 |
| 2004 年 3 月 1 日禁止制限表 | https://yugioh-wiki.net/?%B6%D8%BB%DF%A1%A6%C0%A9%B8%C2%A5%AB%A1%BC%A5%C9%2F2004%C7%AF3%B7%EE1%C6%FC | OCG 史上首次设立「禁止卡」 |
| **リミットレギュレーション 总索引（2013.09 ~ 现行）** | https://yugioh-wiki.net/?%A5%EA%A5%DF%A5%C3%A5%C8%A5%EC%A5%AE%A5%E5%A5%EC%A1%BC%A5%B7%A5%E7%A5%F3 | 第 8 期后期至今的季更禁限卡表 |

---

## 5. 官方卡片效果历次勘误（エラッタ / Errata）

| 资源名称 | 网址 | 用途说明 |
| :--- | :--- | :--- |
| **遊戯王カードWiki エラッタ总表** | https://yugioh-wiki.net/?%A5%A8%A5%E9%A5%C3%A5%BF | 最全的 OCG 官方效果变更与禁止解除勘误清单（注意页面编码为 `EUC-JP`） |
| **KONAMI 官方文本变更公告** | http://www.yugioh-card.com/japan/notice/revision/ | 官方历次大型勘误/裁定变更原文公告 |

---

## 6. 各时期 OCG 环境史与 AI 对手卡组构建参考

来源：**遊戯王史 初期から遡る環境の歴史**（https://yugioh-history.com/）

| 资源名称 | 网址 | 用途说明 |
| :--- | :--- | :--- |
| 站点首页与全期目录 | https://yugioh-history.com/ | 第 1 期至第 10 期 OCG 竞技环境编年史 |
| 各期环境历史文章分类 | https://yugioh-history.com/category/environment | 按发售节点详述每次卡包/限表对环境的冲击 |
| 各期环境卡组年表总目录 | https://yugioh-history.com/category/deck-list | 查看各时代主流卡组势力分布与演变 |
| 第 1 期（1999）环境卡组年表 | https://yugioh-history.com/deck-list/generation-one | 本项目 1999 年 10 位 AI / 35 套形态的核心蓝本 |
| 第 2 期（2000~2002）环境卡组年表 | https://yugioh-history.com/deck-list/generation-two | 扩展 2000 年（八汰乌前夕、卡组破坏、手控三神器等）直接参考 |
| 第 5~8 期环境卡组年表 | `https://yugioh-history.com/deck-list/generation-<five\|six\|seven\|eight>` | 后续扩展 GX / 5D's / ZEXAL 时代的卡组演变蓝本 |

---

## 7. 日后扩展时间轴的标准接入清单

1. **商品与特典补齐（同商品变体不拆包）**：从 Fandom `Template:Series_N_sets` / Yugipedia 查清目标时间段内全部常规包、贩卖机 Booster、预组盒、限定包与特典活动，按当年日元原价 1:1 映射为代币价。**同一商品的剧场版/限定版/复刻版变体不再拆分成独立卡包，商店仅保留通常版**（如 `STARTER BOX` 仅保留通常版，其剧场版少量独占卡改为按发售日以 `events` 特典活动收录）；商店、特典活动与效果勘误均按发售日降序展示。
2. **卡片数据对齐**：拉取百鸽 `cards.zip` 匹配密码与中英日名，重算每张卡的最早发售日，确保时间轴前的卡池不泄漏未来卡。
3. **规则与限表追加**：按 `yugioh-wiki.net` 规则页与禁限卡表页，在 `ocg_demo_catalog.json` 中追加新 `RuleSetVersion` 与 `BanlistVersion`。
4. **勘误时间点核对**：对照 `yugioh-wiki.net/?%A5%A8%A5%E9%A5%C3%A5%BF`，更新 `ocg_errata_history.json` 中落入新时间段的勘误生效日（如 `2000-04-20` 黑森林魔女首次改版、`2000-07-13` 三魔神改版等）。
5. **【强制】参考环境史创建新流派 AI 对手与卡组进化线**：对照 `yugioh-history.com` 对应世代环境年表与环境史文章：
   - 为既有 AI 追加新时代 `EraDeckProfile` 进化形态；
   - 每当新时期环境史出现新的代表性主流/特色流派（如卡组破坏/变形壶、削手三神器、混沌、墓地特召、仪式、同调、超量、灵摆、连接等），**必须新建对应的独立 AI 对手**（而非只塞进通用卡组），并运行 `:rpg-mode:testDebugUnitTest` 校验全时间节点的 40 张合法构筑。
6. **【强制】为每位新 AI 对手生成游戏王风格头像并展示**：根据对手名与卡组流派为其生成一张游戏王画风头像（流派吻合时可直接使用原版游戏王角色，否则生成契合流派气质的游戏王动画风原创决斗者），保存为 `256×256` WebP 至 `rpg-mode/src/main/assets/rpg/avatars/<opponent_id>.webp`（由 `RpgAiStrategyTest#everyAiOpponentHasBundledAvatarAsset` 守卫测试强制校验），自动在 AI 对战、AI 对决与当期动态中展示。
7. **【强制】参考环境史优化 AI 博弈细节，并为新卡池诞生的新流派提供专属 AI 系统**：每当延长时间线引入新卡包/新规则/新限表时，**必须参考对应时期的游戏王环境史**（如 `yugioh-history.com` 环境编年史与卡组年表）：
   - 对照该时期环境史中的经典打法、关键单卡交互与心理博弈（例如第 1 期的落穴留牌/踩坑试探、地割/黑洞/雷击克制留牌、装备防 1 换 2、虚张声势盖魔、锁链飞镖/圣防/死毒/反转怪兽交互等；第 2 期后的削手顺序、旋风盖伏时机、墓地资源管理、大岚/激流葬防踩坑等），同步迭代 AI 的全局战术与单卡逻辑；
   - 若新卡池诞生了新的主流/特色卡组流派，**必须为其补齐一整套对应的纯 Java 专属 AI 系统**——包括单卡知识登记、专属流派战术大脑、新规则/新盘面状态评估、玩家自选卡组自动流派识别（`inferStyleFromDeck`）以及战术单元测试，严禁复用通用强攻或盲出 AI（详见第 8 节）。

---

## 8. 新增 AI 对手 / 新流派卡组 / 环境史 AI 优化强制规范

本项目 RPG 人机对战、AI 托管快战、锦标赛与 3D 观战均已彻底移除 WindBot 通用随机脚本（`Deck=Lucky`）和近似模拟器，全部由真实 `ocgcore` 引擎 + 纯 Java 战术大脑 [`RpgAiStrategy`](../src/main/java/cn/garymb/ygomobile/rpg/ai/RpgAiStrategy.java) 驱动。

**每次延长时间线、参考环境史推进新时期、在 `AiDeckGenerator.java` 中新增 AI 对手，或给已有对手添加新卡组/新核心卡时，必须按以下 7 步同步落实：**

1. **考据环境史：补齐新流派 AI 对手 + 提炼核心打法与博弈（`yugioh-history.com` / 遊戯王環境史）**
   - 核对该时间段环境年表中的全部主流/特色流派，若存在尚未建档的新流派，立即在 `AiDeckGenerator.java` 中创建对应新 AI 对手及多阶段进化线；
   - 提炼该时代特有的战术共识、核心单卡克制关系、资源交换纪律与防踩坑博弈（如防清场、防反击、踩坑试探、虚张声势盖牌、先战破小怪再解大怪、墓地防苏生等），转化为规则化启发式逻辑。
2. **生成游戏王风格 AI 对手头像（`assets/rpg/avatars/<opponent_id>.webp`）**
   - 为每位新增 AI 对手根据名字与流派生成 1:1 游戏王动画风格头像（流派吻合时可用原版游戏王角色，否则绘制契合流派的游戏王画风原创决斗者），压缩为 `256×256` WebP 放入 `rpg-mode/src/main/assets/rpg/avatars/<opponent_id>.webp`，自动由 `RpgCardArtLoader.loadOpponentAvatarInto` 在 AI 对战、AI 对决与当期动态中展示。
3. **登记新卡知识（`RpgAiCardKnowledge.java`）**
   - 将新卡组/新流派及该时代泛用强卡的关键魔法、陷阱、反转/诱发/检索/斩杀/终端怪兽、装备适用条件、直伤数值、高威胁反击对象、安全虚张声势盖牌列表与墓地回收优先级登记到 `RpgAiCardKnowledge`。
4. **构建新流派专属策略与规则/卡池基准评估（`RpgAiStrategy.java` + `RpgAiDuelState.java`）**
   - 若新卡池诞生新流派（`fillStyle`），必须在 `RpgAiStrategy.SUPPORTED_STYLES` 中显式注册并补充中文展示名 `getStyleDisplayName`（否则单元测试守卫会自动报错）。
   - 为新流派/新卡与该时代环境博弈编写专属决策逻辑：
     - 先后攻偏好（`chooseGoFirst`，结合当前规则的先攻抽卡设定）；
     - 主阶段出牌先后序、核心 Combo 展开链、资源克制与防自爆逻辑（例如清场/削手/抽滤先于通召、不乱交地割/黑洞/雷击/装备卡、先用下级试探对手盖牌、虚张声势盖魔、绝不先招主力再黑洞/大岚炸自己）；
     - 选目标/弃牌/检索/苏生/解放/表示形式优先级（`chooseSelectCard` / `chooseSelectTribute` / `choosePosition`）；
     - 陷阱与速攻连锁时机（`chooseChain` / `chooseEffectYn`，如防同效果陷阱重复叠连锁、按目标威胁与己方解场能力判断落穴/神宣/角笛交留、战斗陷阱不在主阶乱开）；
     - 校验 `RpgAiDuelState` 的动态时代卡池基准（`computeEraBenchmarks`）与新规则/新召唤/新胜利机制评估（如抽干直接判负、仪式/同调/超量/灵摆/连接、手控削手等）。
5. **同步扩展玩家自选卡组流派自动识别（`RpgAiStrategy.inferStyleFromDeck`）**
   - 确保玩家自己在 RPG 组卡中构筑同类新流派卡组时，AI 托管快战、AI 对决与 3D 观战也能自动识别并调用这套新流派专属 AI。
6. **补充战术单元测试（`RpgAiStrategyTest.java`）**
   - 为该时期环境史的关键博弈场景、新流派核心展开 Combo、防低级失误场景及 `inferStyleFromDeck` 自动分类补充单元测试。
7. **执行自动化守卫校验**
   - 运行 `./gradlew :rpg-mode:testDebugUnitTest`，确认全部卡组构筑校验、战术单测、`allAiOpponentDeckStylesHaveDedicatedStrategySupport` 以及 `everyAiOpponentHasBundledAvatarAsset` 守卫测试全部通过。


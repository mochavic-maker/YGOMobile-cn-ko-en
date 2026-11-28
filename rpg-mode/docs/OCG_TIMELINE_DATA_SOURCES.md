# OCG 时间轴 RPG 扩展参考数据源手册

本手册记录了构建 **YGOMobile OCG 时间轴 RPG 模式**（1999 第 1 期）过程中检索、核对并使用的全部权威网站与 API 端点，便于日后将时间轴扩展至第 2 期（2000 年）及以后版本时直接查阅与自动化抓取。

> **【最高原则 · 仅限 OCG 环境】** 本模式**严格仅还原日版 OCG 环境**（仅包含 OCG 正式发售的卡包、预组、限定包、特典活动、OCG 禁限卡表、OCG 历代规则、OCG 勘误史与 OCG 竞技环境史）。**完全不考虑、不收录任何 TCG 专属卡包、TCG 先行/独占卡、TCG 特典、TCG 发售日或 TCG 限表**（例如 `LOB / MRD / SDK` 等 TCG 商品一律不引入）。
>
> 同步已保存为 Agent Skill：**`ocg-timeline-expansion`**（后续任何对话均可直接调用该技能获取相同清单与扩展流程）。

---

## 1. 卡片基础数据、密码与中英日文名（API）

| 资源名称 | 网址 / 端点 | 用途说明 |
| :--- | :--- | :--- |
| **百鸽 (YGOCDB) API 文档** | https://ygocdb.com/api | 汇总 KONAMI 官方库与 YGOPro 数据库，提供免鉴权 JSON API |
| 百鸽全量卡片数据 | https://ygocdb.com/api/v0/cards.zip | 获取所有卡片的 YGOPro 密码 (`id`)、官方 `cid`、中/日/英卡名、属性种族攻守与效果文本 |
| 百鸽数据 MD5 校验 | https://ygocdb.com/api/v0/cards.zip.md5 | 判断 `cards.zip` 是否有更新 |
| 百鸽全卡发售日期表 | https://ygocdb.com/api/v0/releaseDates.json | 查询卡片各语种首次发售信息（仅取日版 OCG 发售日） |
| 百鸽禁限卡表（含历史） | https://ygocdb.com/api/v0/limits.json | 获取当前及历史 OCG 禁限卡表 JSON |
| **KONAMI 官方 OCG 卡片数据库** | https://www.db.yugioh-card.com/yugiohdb/?request_locale=ja | 最终核对日版 OCG 卡包正式收录列表、商品 `pid` 与官方最新文本 |

---

## 2. OCG 卡包发售日、日元原价、卡池清单与抽包机制

| 资源名称 | 网址 / 端点 | 用途说明 |
| :--- | :--- | :--- |
| **Yu-Gi-Oh! Wiki (Fandom) Parse API** | `https://yugioh.fandom.com/api.php?action=parse&page=<Page_Name>&prop=text\|wikitext&format=json` | 批量抓取日版 OCG 卡包发售日、日元原价、每包张数、完整卡表与稀罕度 |
| Fandom 第 1 期商品总览 | https://yugioh.fandom.com/wiki/Template:Series_1_sets | 仅取日版 OCG 商品：Vol.1~7、Booster 1~7、Starter Box、EX、Premium Pack、Limited Edition、OCG 特典卡（第 2 期查 `Template:Series_2_sets` 以此类推） |
| **Yugipedia** | https://yugipedia.com/ | 交叉核对日版 OCG 各类商品（含 Carddass 贩卖机、杂志/游戏/大会特典卡）的准确日期与稀罕度 |
| **YGOPRODeck Pack Simulator** | https://ygoprodeck.com/pack-sim/ | 仅参考其前端抽包动画/槽位交互体验；**切勿直接照搬其 TCG 卡包或 TCG 发售日** |

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
8. **【强制】补齐对应时间段的原著漫画/动画/电影「🎬 对决还原」**：检索新扩展时间段内的全部游戏王原著漫画（周刊少年 Jump 连载发售日）、TV 动画（如 2000-04-18 起播出的《遊☆戯☆王 デュエルモンスターズ》DM 动画日本首播日）与剧场版电影中的经典对决，整理双方用卡、构建 40 张还原卡组、为新角色绘制游戏王画风头像并登记为日期事项（详见第 9 节）。

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
     - **格子留空纪律（后场优先防卡死）**：魔法与陷阱区域尽量保留最后 1 格（4/5 占满时，除紧急高价值陷阱如《圣防》《神宣》《角笛》《死毒》等外禁止继续盖放低优先魔陷/虚张声势卡，避免卡死手牌通常/速攻魔法发动空间）；怪兽区已占 4 格且手牌有更高价值的反转/功能怪时，避免用冗余通常怪占满第 5 格；
     - 选目标/弃牌/检索/苏生/解放/表示形式优先级（`chooseSelectCard` / `chooseSelectTribute` / `choosePosition`）；
     - 陷阱与速攻连锁时机（`chooseChain` / `chooseEffectYn`，如防同效果陷阱重复叠连锁、按目标威胁与己方解场能力判断落穴/神宣/角笛交留、战斗陷阱不在主阶乱开）；
     - 校验 `RpgAiDuelState` 的动态时代卡池基准（`computeEraBenchmarks`）与新规则/新召唤/新胜利机制评估（如抽干直接判负、仪式/同调/超量/灵摆/连接、手控削手等）。
5. **同步扩展玩家自选卡组流派自动识别（`RpgAiStrategy.inferStyleFromDeck`）与手动策略覆盖**
   - 确保玩家自己在 RPG 组卡中构筑同类新流派卡组时，AI 托管快战、AI 对决与 3D 观战能准确自动识别流派（例如避免 Good Stuff 卡组因混入少量泛用反转怪而被误判为纯 `FLIP_CONTROL`），并同步支持玩家在组卡页为每套卡组**手动指定 AI 策略**（`SavedDeck.aiStyleOverride` / `activeDeckAiStyleOverride`，留空则自动识别）。
6. **补充战术单元测试（`RpgAiStrategyTest.java`）**
   - 为该时期环境史的关键博弈场景、新流派核心展开 Combo、防低级失误场景、格子留空纪律及 `inferStyleFromDeck` 自动分类补充单元测试。
7. **执行自动化守卫校验**
   - 运行 `./gradlew :rpg-mode:testDebugUnitTest`，确认全部卡组构筑校验、战术单测、`allAiOpponentDeckStylesHaveDedicatedStrategySupport` 以及 `everyAiOpponentHasBundledAvatarAsset` 守卫测试全部通过。

---

## 9. 原著漫画 / 动画 / 电影「🎬 对决还原」扩展强制规范

每次将时间线扩展至新时期（例如 2000 年起的 Series 2、决斗都市漫画连载、2000-04-18 起播出的《遊☆戯☆王 DM》TV 动画、后续剧场版/GX/5D's 等）时，**必须同步核查 `rpg-mode/docs/CANON_DUELS_FUTURE_BACKLOG.md` 并扩展 `rpg-mode/src/main/assets/rpg/ocg_canon_duels.json`**：

1. **原著日期考据 + 核心卡 OCG 首次发售日定档（严禁跨时代超模卡提前解锁）**：
   - **原著日期 (`originalDate`)**：
     - **原作漫画**：按《周刊少年 Jump》日本实际发售日登记（2003 年 5 月前为每周二，2003 年 5 月起为每周一，参考 `jajanken.net` 与日文维基）；
     - **TV 动画 / 剧场版**：按日本东京电视台首播日 / 日本院线上映日登记；
     - **跨越多集/多话的单局对决**：严格**仅取该对决最后一集/最后一话的日期**作为 `originalDate`。
   - **核心卡定档解锁日 (`timelineDate` + `coreCardIds`)**：
     - 从双方原著用卡中挑选若干最能代表该对决灵魂的**核心卡**写入 `coreCardIds`（如 `青眼白龙` / `恶魔召唤` / `艾克佐迪亚` / `暗黑魔龙`＆`三魔神` / `混沌战士` / `卡通世界` / `左轮手枪龙` / `千眼纳祭神` 等）；
     - 核查所有 `coreCardIds` 在**日本 OCG 的首次正式发售日期**，取其中**最晚日期**作为该对决还原事件的时间轴解锁日期 `timelineDate`（并在 `coreUnlockNote` 中写明依据）；
     - **未来待办文档同步 (`rpg-mode/docs/CANON_DUELS_FUTURE_BACKLOG.md`)**：若某场原著对决的核心卡首次 OCG 发售日超出了当前版本的时间轴上限（例如 1999 版本中核心卡在 2000+ 才发售的卡通世界/左轮手枪龙/千眼纳祭神等），**必须将其完整登记至 `rpg-mode/docs/CANON_DUELS_FUTURE_BACKLOG.md`**（含原著日期、核心卡及日本 OCG 首发日、目标解锁日、双方完整用卡与头像），切勿塞入当前早期环境；每次延长时间轴时，**第一步即检查该待办文档**，将所有目标解锁日落入新时间区间的对决迁入 `ocg_canon_duels.json`，保证永不遗漏。
2. **双方卡组还原规则（100% 保留原著用卡、不剔除未来卡 + 新时间点 OCG 卡池补足至 40 张）**：
   - 从 Fandom Wiki / Yugipedia 对应集数/话数页面检索双方使用过的全部卡片，核对 `mobile/assets/data/cards.cdb` 密码（凡在 OCG 中属于融合/同调/超量/连接怪兽的卡必须放入 `canonExtraIds`，不可放入主卡组 `canonCardIds`）；
   - **尽量完整保留原著用卡（禁止剔除非核心未来卡）**：原著单局中提及的卡牌数量本来就不多，因此在通过 `coreCardIds` 确定合理的 `timelineDate` 后，**不要剔除该对决中出现的非核心未来卡**（允许保留少量晚于 `timelineDate` 甚至跨年的非核心原著用卡，以最大程度忠实还原原著对决风味）；
   - 对于晚于当前时间轴上限的非核心原著用卡，将其中/日/英文元信息收录进 `ocg_canon_duels.json` 的 `canonCardCatalog`（通过 `RpgCatalog.findCardOrCanon` 仅用于还原卡组展示与 AI 识别，**严禁泄漏进商店/合成/玩家组卡池**；卡图在联网时由 `RpgIntegration.loadCardImageBytes` 自动从萌卡图床拉取并缓存）；
    - **《游戏王：决斗者遗产（链接进化）》Story Deck 蓝本参考、事件日期适配与主题纯净度原则（`CanonCharacterCardRegistry` + `CanonDuelDeckBuilder`）**：
      - **官方剧情卡组蓝本（`lotdDeckName` + `lotdEraPaddingIds`）**：构建原著对决卡组时，优先检索官方游戏《游戏王：决斗者遗产（Legacy of the Duelist / Link Evolution）》的 **Story Campaign（剧情模式 Story Deck）** 作为战术结构蓝本（如 `Exodia Obliterate`、`Legendary Blue-Eyes`、`Moth Evolution`、`Harpie Lady`、`Legendary Ocean`、`Red-Eyed Dinosaurs`、`Kuriboh Power`、`The Ultimate Dragon`、`Black Luster Soldier`、`Cheating Exodia` 等）；
      - **事件日期 OCG 卡池同定位适配**：因《决斗者遗产》剧情模式常为现代大师规则混入 2003~2017 年补强卡（如 2003《凡骨的意地》、2007《古之规则》、2017《超进化之茧》、2012《鹰身通灵师》），**不得为了照搬现代补强而将 1999/早期原著对决推迟至 2007~2017 年，也不得将现代卡直接泄漏入当期补位**；应将蓝本中的未来补强卡替换为事件日期（`timelineDate`）OCG 卡池内的**同定位强力卡**（例如 `Exodia Obliterate` 中用当期《骸骨天使》《圣魔术师》《三眼怪》《黑森林的魔女》《岩石巨兵》《圣精灵》《强欲之壶》《天使的施舍》《光之护封剑》替代 2003《凡骨的意地》）；
      - **战术主题纯净度硬守卫（`AiDeckGenerator.isUnsafeFillerCard`）**：例如 `EXODIA_DRAW`（黑暗大法师主题）补位池**严禁塞入场地魔法、装备魔法以及会丢弃手牌部件的自毁卡**（《手牌抹杀》《变形壶》《对死者的供奉》），`BURN_WALL`/`MILL_MORPHING` 严禁塞入装备魔法；
      - 其余不足 40 张的**补位卡**严格参考该对决事件日期（`timelineDate`）的当期 OCG 实卡池，按严格 3 级优先级挑选：
        1. **第一优先（角色原著用卡）**：优先挑选当期 OCG 卡池内**该人物在原著漫画/动画中真实使用过的卡牌**（由 [`CanonCharacterCardRegistry`](../src/main/java/cn/garymb/ygomobile/rpg/engine/CanonCharacterCardRegistry.java) 统一维护并动态汇总该角色跨集数原著卡池；**严禁将角色未在原著用过的 Vol.1/Starter 弱势凑数白板混入角色卡池**；在 1800+ 打点的后期事件日期中，若角色早期原著白板已严重落后环境，优先选用该角色符合当期强度的原著用卡与效果怪）；
        2. **第二优先（核心卡高度联动）**：优先挑选与该局 `canonCardIds` / `canonExtraIds` 核心卡高度联动的配套卡（例如：羽蛾带《进化之茧》必优先补《飞蛾宝宝》《森》与昆虫装备；额外含融合怪必优先补《融合》及缺失的正规融合素材如迷宫兄弟的《加农炮兵》《高科技狼》、城之内的《传说的剑豪 正树》《火焰操纵者》；渔太补《海》与水属性鱼/水/海龙族及《波塞冬之力》；龙崎补《荒野》与《体温上升》；孔雀舞补《万华镜》《电子紧身衣》《随风翼》与风属性鸟兽族；《死毒》祭品仅加权暗属性功能效果怪或高防盾墙，不加权低攻防白板）；
        3. **第三优先（符合事件日期卡池强度的主题与当期泛用卡）**：由 `CanonDuelDeckBuilder` 动态计算 `timelineDate` 当期 OCG 卡池的下级最高攻防（`eraTopLowerAtk` / `eraTopLowerDef`），自动导出当期强度基线（`eraAtkFloor = max(800, eraTopLowerAtk - 400)`、`eraDefFloor = max(1000, eraTopLowerDef - 300)`、高星 `eraTributeAtkFloor = eraTopLowerAtk + 400`）：**所有非原著、非核心联动的其他补位卡必须符合事件日期的卡池强度，严禁塞入落后环境的低攻防通常怪（例如在 1999-08-26 1800~1900 攻环境中塞入攻防 <1000~1400 的凑数白板）或非烧血卡组中的低效烧血/回血凑数魔陷**；种族/属性主题加分仅作用于符合当期强度或具实际效果/联动的卡牌，防止低攻匹配种族白板反超当期强力下级；
      - **严禁敌对/其他角色专属王牌串场**：补位池强制过滤其他原著人物的专属招牌卡（如暗游戏绝不补位《青眼白龙》《灯之魔精》《机械猎手》《死毒》，海马绝不补位《黑魔术师》《暗黑骑士 盖亚》《诅咒之龙》《恶魔召唤》《真红眼黑龙》，城之内/羽蛾/孔雀舞/渔太/龙崎/迷宫兄弟亦严禁混入其他角色的专属王牌；单局显式登记的 `canonCardIds` 仍 100% 忠实保留）。
      - 先补足怪兽、再做魔法/陷阱合理性判定与补位，保证种族/属性装备魔法能准确统计全卡组目标数，既不漏掉原著招牌装备、也不塞入废装备。
3. **仿《决斗者遗产》战前/战后双结局分支 AVG 对话场景（`CanonStoryDialog` + `introDialogue` / `canonWinnerSide` / `outroSideAWin` / `outroSideBWin`）**：
   - 每场原著对决必须在 `ocg_canon_duels.json` 中配置忠实于原作漫画/动画名台词的**战前开场对话（`introDialogue`，≥3 句）**、**原著正史胜方标记（`canonWinnerSide`: `"A"` 或 `"B"`）**以及**双分支战后尾声（`outroSideAWin` 与 `outroSideBWin`，均 ≥2 句且各自包含 A、B 双方台词，内容必须彼此独立）**，每句标注 `speakerSide`（`A`/`B`/`NARRATOR`）、`speakerName`、`emotion`（`normal`/`fierce`/`shock`/`smirk`/`determined`/`victory`）与中文台词 `text`；
   - 演出形式对标《决斗者遗产》：左右角色动画立绘聚焦高亮（说话方放大亮框、倾听方柔和变暗）、情绪边框色调、点击推进 / 上一句 / 一键跳过；战前开打前自动演出开场，**战后无论胜、负还是平局**均经 `CanonStoryDialog.showOutcomeOutro` 自动按实际胜方路由对应结局台词（区分 `🏆 正史胜利`、`⚡ IF 逆转`、`💔 战败结局`、`🏆 自组胜出`、`💔 挑战落败`、`⚖ 平局终场`），且对决卡片提供 **`📖 剧情`** 按钮自由挑选回看开场、A 胜结局、B 胜结局或完整正史连播（由 `RpgEnginesTest#canonDuelsHaveLotdBlueprintsThemePureExodiaAndAvgDialogues` 自动守卫校验）。
4. **当日限定展示、日历 🎬 特殊标记与四模式通关记录**：
   - 「🎬 对决还原」页**仅显示当前选定日期当日的对决还原**（`TimelineEngine.getCanonDuelsOnDate`，当日多场按原著日期 `originalDate` 升序排列），既避免后期列表冗长难查，也强制保证玩家自组挑战时**只能使用当时环境的卡池与规则/禁限表，无法携带未来卡**；当日无对决还原时提供一键打开日历与全对决日期快捷跳转按钮；
   - 每场新对决的 `timelineDate` 自动进入 `TimelineEngine.getTimelineEventDates`、`TimelineEngine.getCanonDuelDates` 与 `TimelineDigest.newCanonDuels`：**时间轴日历弹窗中含对决还原的日期采用专属粉框 + `🎬` 图标背景高亮**（月份下拉菜单同样附带 `🎬` 标识），与普通浅绿圆点 OCG 事件一眼区分；
   - 每场对决提供 4 种挑战模式（`ROLE_A` 扮演 A、`ROLE_B` 扮演 B、`OWN_VS_A` 自组挑战 A、`OWN_VS_B` 自组挑战 B）、`📖 剧情` 回看及与 AI 对战一致的分屏可视化查看卡组弹窗（带 `原著` 筛选徽章与决斗者遗产蓝本标注）；玩家在任意模式下获胜后，通过 `RpgProfile.clearedCanonDuelModes` 持久化记录，并在对应挑战按钮右上角显示绿色 **`✔ 成功`** 标签。
5. **角色头像复用与新角色补绘**：
   - 同一角色在不同集数的对决中复用同一头像 ID（如 `ai_vol1_yugi`、`ai_starter_kaiba`、`ai_weekend_challenger`、`char_pegasus` 等）；
   - 凡新登场的原著角色，必须为其绘制 1:1 游戏王动画风格头像并压缩为 `256×256` WebP 放入 `rpg-mode/src/main/assets/rpg/avatars/char_<id>.webp`（由 `RpgEnginesTest#canonDuels1999LoadReconstruct40CardsHaveAvatarsAndRegisterTimelineDates` 自动守卫校验）。
6. **专属战术 AI 补充**：
   - 为原著卡组中引入的新标志性单卡（如仪式、卡通、纳祭之魔、银幕镜壁、左轮手枪龙、神之卡等）在 `RpgAiCardKnowledge.java` 和 `RpgAiStrategy.java` 中补充单卡知识与战术逻辑，确保 AI 能流畅发挥还原卡组的特色打法。

---

## 10. 历史原版 Lua 效果还原、AI 卡组合理性与角色还原守卫、天梯动态分档与奖金校准（强制规范）

基于 1999 第 1 期实机测试与 8,000+ 局真实 `ocgcore` 对局复盘，以下 5 条规范在未来扩展至第 2 期（2000 年）及后续环境时**同样强制适用**：

1. **【强制】历史原版效果（Pre-Errata）每局独立 Lua 预加载（`RpgHistoricalLuaBuilder.java`）**
   - **原理**：`ocgcore` 的 `interpreter::load_card_script(code)` 在 `_G["c<code>"]` 已定义时会直接复用该表而跳过加载 `scripts.zip` 中的现代改版脚本。只要在 `newCard` / 卡组装载**之前**预加载 `rpg_rule_override.lua`（覆盖 `ServerDuelBase`、`ServerDuel` 悔棋重建与 `RpgOcgHeadlessDuelRunner`），即可在 RPG 单局 `lua_State` 内精确还原历史原版卡效，**零污染 `expansions/script/` 与普通 YGOMobile 对战**。
   - **1999 已还原**：《死之卡组破坏病毒》（`57728570`，2015-01-01 前还原 3 回合抽卡确认破坏、无 0 伤负面效果）、《三眼怪》（`26202165`）与《黑森林的魔女》（`78010363`，1999-12-20 前任意区域送墓触发，2016/2017 前无一回合一次与检索封锁）、《弹射龟》（`95727991`，2014-03-21 前无一回合一次）。
   - **2000+ 扩展必做**：每次引入新卡包时，对照第 5 节 `yugioh-wiki.net` 勘误总表排查所有曾被现代削弱/大改的卡（如 2000 年《杀手蛇》《王宫的敕命》《破坏轮》《王家的神殿》《现世与冥界的逆转》《处刑人-摩休罗》等），按精确生效日补入 `RpgHistoricalLuaBuilder` 并写单测验证。
2. **【强制】全 AI 与原著还原补位卡组的「零废牌」合理性守卫（`AiDeckGenerator.isUnsafeFillerCard`）**
   - `AiDeckGenerator` 与 `CanonDuelDeckBuilder` 的自动补位池**严禁**出现以下不合理单卡：
     - **特殊召唤限定怪（Nomi 怪）与仪式怪**：如《究极完全态大飞蛾》《大飞蛾》《飞蛾幼虫》《鹰身女郎三姐妹》《门之守护神》《钢铁恶魔兽灵》《真红眼黑钢龙》及一切含「不能通常召唤」文本的新卡；
     - **无配套怪兽的仪式魔法**；
     - **非融合卡组中的融合依赖卡**：无额外融合怪时禁止补入《融合》《融合贤者》，非 `FUSION_COMBO` 时禁止补入《恶魔科学怪人》《眼球怪》与融合素材代用怪；
     - **缺少指定联动对象的单卡**：如无《鹰身女郎》时的《万华镜》《电子紧身衣》、无《飞蛾宝宝》时的《进化之茧》、无《龙之支配者》时的《呼龙笛》以及窄针对废牌（《白洞》《避雷针》等）；
     - **非艾克佐迪亚卡组的部件泄漏**、**暗属性 ≤1000 祭品不足 6 张时的《死之卡组破坏病毒》**；
     - **卡组内合规目标 < 4 张的种族/属性限定装备魔法**。
   - **自动化守卫**：`RpgEnginesTest#allAiAndCanonDecksHaveZeroUnsummonableNomiOrDeadDependencyFillers` 对全部里程碑日期的所有 AI 与原著对决补位卡做全量扫描，任何违规都会在单测直接失败。
3. **【强制】原著人物 AI 与对决还原的「角色原著还原度、主题联动与事件日期环境强度」守卫（`CanonCharacterCardRegistry` + `CanonDuelDeckBuilder`）**
   - 凡对应漫画/动画原著人物的 AI 对手（如 `ai_vol1_yugi` 初代暗游戏、`ai_starter_kaiba` 社长·海马濑人、`ai_weekend_challenger` 热血城之内风，以及未来新增的贝卡斯、马利克、盗贼基斯、孔雀舞等）与全部「🎬 对决还原」卡组：
     - 核心配方（`coreMain`/`coreExtra`）与自动补位池**必须参考事件日期（`timelineDate`）的 OCG 卡池，优先选用该角色原著用过的卡牌或与所选原著卡高度联动的配套卡，其余补位卡严格匹配事件日期的卡池强度**（动态参照当期下级最高攻防，禁止塞入落后环境的低攻防非原著凑数白板，例如 1800~1900 环境中的 <1000~1400 白板）；
     - **严禁出现角色王牌串场**（如暗游戏绝不携带青眼白龙/灯之魔精/机械猎手/死毒，海马绝不携带黑魔术师/暗黑骑士盖亚/诅咒之龙/恶魔召唤/圣精灵/真红眼黑龙，城之内绝不携带黑魔术师/青眼白龙/恶魔召唤等）；
     - 非原著角色的纯环境流派 AI（如死毒暗控、装备Beat、反转控制、融合科学怪人、卡组破坏等）在自动补位时同样由 `CanonCharacterCardRegistry.getCoreSynergyBonus` 优先挑选与卡组核心主题高度联动的卡牌。
   - **自动化守卫**：`RpgEnginesTest#canonCharacterFidelityAndCoreSynergyGuard` 与 `RpgEnginesTest#canonDuelPaddingMatchesEventDateCardPoolStrength` 覆盖全部里程碑日期的角色 AI 与全部原著对决，自动拦截任何串场王牌、缺失的核心联动件以及落后于事件日期卡池强度的非原著通常怪补位。
4. **【强制】全环境 AI 天梯（瑞士轮 + 全局最小权完美匹配 + Bradley-Terry Elo `±SE` + Fisher-Jenks 动态分档）与实测奖金校准（`EraBountyCalibrator`）**
   - `RoundRobinLeagueEngine` 提供 $O(N \log N)$ **瑞士轮天梯**（默认每组 **2 局 = 1 先 1 后**，消除先后手偏差且以同等总耗时提供 **3 倍轮数与对手覆盖面**，例如 10~14 人默认 18 轮可实现 100% 全员交手 + 相近名次决胜加赛；采用**全局最小权完美匹配** `pairRoundGloballyOptimal` 同步压低重赛与实际 `|Elo_i - Elo_j|` 差，消除贪心尾效应；同日期固定 AI 卡组种子；全局贝叶斯 Bradley-Terry MLE 解算 Elo、对手均分 SOS 与 Fisher 信息量标准误差 `±SE`；支持随时增量追加 +4 轮）以及全量循环赛，并支持玩家勾选**当前卡组与多套已保存卡组**共同参与当期天梯排位。
   - **动态 S/A/B/C/D 分档（Fisher-Jenks 一维自然断点 DP）**：天梯与 AI 对战均通过 `RoundRobinLeagueEngine.computeDynamicTiers` 对当期 Elo 分布求一维最优聚类切分（最小化档内方差之和 SSE），而非写死固定 Elo 阈值或仅取最大相邻差（避免底部长尾把前排全部挤进 S 档）；同时强制**同分锁档**、极端长尾（`<950`）归 D、小样本相近前排（差值 `<60` Elo）合并保档。
   - **实测驱动奖金校准与经济区间约束**：AI 对战奖金不再靠主观估算——每当扩展新年代、增补或修改 AI 卡组、或大幅迭代 AI 策略后，用同源 `ocgcore` 引擎跑一次各里程碑节点的高精度循环赛，将实测 Elo 写入 `EraBountyCalibrator`，**单场胜利奖金严格控制在 `120 ~ 300` 代币之间**（前期如 `120~220`，随年代平滑递增至年末 `120~300`，以 5 代币为步长，平局约 `43%`、败北约 `25%`），确保同一时间点内**AI 实测越强奖金越高**，且 `RpgEnginesTest` 强制校验全里程碑 Elo-奖金严格单调与 `[120, 300]` 区间边界。
5. **产品体验、日志调试与打包约束**
   - 组卡页不提供劣质「智能一键组卡」，坚持玩家自主构筑 + 严格时间轴/拥有数/禁限表校验；保存卡组支持新建命名或直接选取已有保存卡组覆盖（自动记录上次读取的卡组并默认选中），且支持为每套卡组单独覆盖 AI 策略（自动/手动指定）。
   - **全对局回放与一键复制日志 + 横向结算栏**：战后结算弹窗采用紧凑横向按钮栏（`回放` / `日志` / `完成`），**无论 AI 对决还是玩家手打实战**均自动保存回放与完整元数据，在结算页与「🎬 对战回放」列表均支持一键复制双方 AI 策略（或手动）、完整主/额外卡组及逐回合操作日志，便于持续打磨 AI 策略。
   - RPG 独立共存包名固定为 `cn.garymb.ygomobile.CN.rpg`，并持续沿用同一 Release 签名（SHA-256 `81:CC:69:4D:93:A2:47:98:A5:89:BF:D1:40:F0:9F:0C:EB:18:2E:47:FD:4A:93:9B:BE:32:86:59:14:29:C9:7C`），保证每次更新可直接覆盖安装且存档无损。

---

## 11. 添加或升级 AI 对手与卡组的完整标准流程（SOP）

无论是扩展新年份（2000+），还是在既有年代新增/修改一套 AI 卡组，均按以下 **8 步闭环流程** 执行，缺一不可：

1. **步骤 1 · 环境史考据与流派定位**
   - 查阅 `yugioh-history.com` 对应时期环境年表，明确该 AI 对手的定位（入门/休闲/中坚/主流竞技/特色自闭/原著角色等）、首次解锁日期 `unlockDate` 及随发售节点演变的多套 `EraDeckProfile`。
2. **步骤 2 · 制定核心卡组配方、角色还原登记与头像生成**
   - 在 `AiDeckGenerator.java` 中编写各阶段的 `coreMain` / `coreExtra` 与 `fillStyle`，确保每张卡满足 `发售日 <= effectiveDate`、严守当期 OCG 禁限表、核心配方中不含融合怪误写进 `coreMain`，且配齐核心联动件（例如带 `死毒` 必配 ≥6 张暗属性 ≤1000 祭品、带装备必配充足合规目标怪、带融合必配额外怪与融合手段）。
   - **若该 AI 为漫画/动画原著人物**：必须在 [`CanonCharacterCardRegistry.java`](../src/main/java/cn/garymb/ygomobile/rpg/engine/CanonCharacterCardRegistry.java) 中映射其 `characterId`、维护其当期原著卡池与专属王牌表，核心配方与自动补位**均优先挑选当期环境内原著用过的卡牌或与主题高度联动的卡牌，严禁混入其他角色的专属王牌**（如游戏用青眼白龙、海马用黑魔导）。
   - 为新增 AI 对手生成 1:1 游戏王动画风格头像并保存至 `rpg-mode/src/main/assets/rpg/avatars/<opponent_id>.webp`（`256×256` WebP）。
3. **步骤 3 · 登记单卡知识与制定专属 AI 策略**
   - 在 `RpgAiCardKnowledge.java` 中补齐新卡角色、装备兼容表、检索/苏生/回收/康牌优先级；
   - 在 `RpgAiStrategy.java` 中注册新 `fillStyle`（`SUPPORTED_STYLES`、`getStyleDisplayName`、`inferStyleFromDeck`），实现先后手偏好、主阶出牌序、资源保留（如单表侧怪优先地割保雷击、单后场优先魔除/陷除保羽毛扫、不炸自己优势盘面）、盖牌/献祭/连锁/弃牌逻辑，并在 `RpgAiStrategyTest.java` 补充战术单测。
4. **步骤 4 · 核查历史原版效果（Pre-Errata Lua）**
   - 对照 `yugioh-wiki.net` 勘误总表与 `mobile/assets/data/scripts.zip`，确认该卡组用到的关键卡在当前时间线是否尚未被现代 Errata 削弱；若 `scripts.zip` 为现代改版，在 `RpgHistoricalLuaBuilder.java` 补充按日期生效的原版 Lua 覆盖并写单测。
5. **步骤 5 · 全年代卡组合理性、「零废牌」与「角色还原度」检查**
   - 检查新卡组是否引入新的特召限定 Nomi 怪、仪式魔法、融合依赖卡、指定卡名联动卡或种族/属性装备魔法，同步更新 `AiDeckGenerator.isUnsafeFillerCard` 与 `CanonCharacterCardRegistry`；
   - 运行 `RpgEnginesTest#allAiAndCanonDecksHaveZeroUnsummonableNomiOrDeadDependencyFillers` 与 `RpgEnginesTest#canonCharacterFidelityAndCoreSynergyGuard`，确认全部里程碑日期下生成出的 40 张实装卡组（核心+补位）**零不可召唤怪、零死卡、零角色王牌串场、零禁限表/时间轴越界**。
6. **步骤 6 · 跑真实 `ocgcore` 全环境天梯基准**
   - 使用与 App 同源的 `ocgcore` + `RpgAiStrategy` + `RpgHistoricalLuaBuilder`（主机侧或 App 内瑞士轮/全量循环赛），在受影响的每个里程碑日期跑均衡先后手对局（建议每对 10~16 局，压低误差 `±SE`），产出每位 AI 的实测 Bradley-Terry Elo、胜率与 Fisher-Jenks 动态 S/A/B/C/D 分档。
7. **步骤 7 · 按实测 Elo 校准奖金（`120 ~ 300` 代币区间）**
   - 将最新实测 Elo 快照写入 `EraBountyCalibrator.java`，按年代阶梯（如 `120~220` 递进至 `120~300`，单场胜场奖金严格限定在 **`120 ~ 300`** 代币内，步长 5，平局 43%，败北 25%）自动映射，通过 Fisher-Jenks 自动给出当期动态档位，并保证同时间点内 **Elo 越高奖金越高**。
8. **步骤 8 · 全量自动化守卫验收与打包发布**
   - 运行 `./gradlew :rpg-mode:testDebugUnitTest :mobile:assembleCnRelease`，确认全部战术单测、零废牌审计、角色还原守卫、17 年代 Elo-奖金单调与 `[120, 300]` 边界校验、动态分档与头像守卫 100% 通过，核验 Release 签名 SHA-256 无误后推送并更新安装包。





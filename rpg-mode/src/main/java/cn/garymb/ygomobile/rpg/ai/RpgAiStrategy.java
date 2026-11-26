package cn.garymb.ygomobile.rpg.ai;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import cn.garymb.ygomobile.rpg.model.AiOpponent;
import cn.garymb.ygomobile.rpg.model.RpgCard;

/**
 * Pure-Java 1999 OCG tactical & archetype-aware decision engine.
 * Replaces WindBot's generic random "Deck=Lucky" with genuine card-aware and
 * per-archetype decision making across all 1999 RPG AI opponents.
 */
public class RpgAiStrategy {

    /**
     * Authoritative registry of AI deck fillStyles with dedicated tactics implemented in this class.
     * Enforced by RpgAiStrategyTest#allAiOpponentDeckStylesHaveDedicatedStrategySupport():
     * whenever adding a new AI opponent or new EraDeckProfile in AiDeckGenerator, register its
     * signature cards in RpgAiCardKnowledge, implement its archetype logic here, and add its style below.
     */
    public static final Set<String> SUPPORTED_STYLES = Collections.unmodifiableSet(new HashSet<>(java.util.Arrays.asList(
            "NOVICE_NR",
            "CASUAL_MID",
            "MID_TIER_THEME",
            "NO_TRIBUTE_BEAT",
            "LOWER_BEAT",
            "GOOD_STUFF",
            "BURN_WALL",
            "EQUIP_LIGHT_EARTH",
            "FLIP_CONTROL",
            "MILL_MORPHING",
            "DARK_VIRUS",
            "EXODIA_DRAW",
            "FUSION_COMBO"
    )));

    // ===== Parsed Command DTOs =====

    public static class CmdEntry {
        public final int code;
        public final int protoCtrl;
        public final int localCtrl;
        public final int location;
        public final int sequence;
        public final int desc;
        public final int flag;
        public final int index;

        public CmdEntry(int code, int protoCtrl, int localCtrl, int location, int sequence,
                        int desc, int flag, int index) {
            this.code = code;
            this.protoCtrl = protoCtrl;
            this.localCtrl = localCtrl;
            this.location = location;
            this.sequence = sequence;
            this.desc = desc;
            this.flag = flag;
            this.index = index;
        }
    }

    public static class IdleCmdPrompt {
        public final List<CmdEntry> summonable = new ArrayList<>();
        public final List<CmdEntry> spSummonable = new ArrayList<>();
        public final List<CmdEntry> reposable = new ArrayList<>();
        public final List<CmdEntry> msetable = new ArrayList<>();
        public final List<CmdEntry> ssetable = new ArrayList<>();
        public final List<CmdEntry> activatable = new ArrayList<>();
        public boolean canBattle;
        public boolean canEnd;
        public boolean canShuffle;
    }

    public static class BattleCmdPrompt {
        public final List<CmdEntry> activatable = new ArrayList<>();
        public final List<CmdEntry> attackable = new ArrayList<>();
        public boolean canMain2;
        public boolean canEnd;
    }

    public static class SelectCardItem {
        public final int code;
        public final int protoCtrl;
        public final int localCtrl;
        public final int location;
        public final int sequence;
        public final int subSeq;
        public final int index;
        public final int opParam;

        public SelectCardItem(int code, int protoCtrl, int localCtrl, int location, int sequence,
                              int subSeq, int index, int opParam) {
            this.code = code;
            this.protoCtrl = protoCtrl;
            this.localCtrl = localCtrl;
            this.location = location;
            this.sequence = sequence;
            this.subSeq = subSeq;
            this.index = index;
            this.opParam = opParam;
        }
    }

    public static class ChainItem {
        public final int flag;
        public final boolean forced;
        public final int code;
        public final int protoCtrl;
        public final int localCtrl;
        public final int location;
        public final int sequence;
        public final int subSeq;
        public final int desc;
        public final int index;

        public ChainItem(int flag, boolean forced, int code, int protoCtrl, int localCtrl,
                         int location, int sequence, int subSeq, int desc, int index) {
            this.flag = flag;
            this.forced = forced;
            this.code = code;
            this.protoCtrl = protoCtrl;
            this.localCtrl = localCtrl;
            this.location = location;
            this.sequence = sequence;
            this.subSeq = subSeq;
            this.desc = desc;
            this.index = index;
        }
    }

    private final RpgAiDuelState state;
    private final AiOpponent opponent;
    private final String style;

    private int lastDecisionTurn = -1;
    private int idleActionsThisTurn = 0;
    private int pendingAttackerSeq = -1;
    private int pendingActivatingCode = 0;

    public RpgAiStrategy(RpgAiDuelState state, AiOpponent opponent, String fillStyle) {
        this.state = state;
        this.opponent = opponent;
        this.style = (fillStyle != null && !fillStyle.isEmpty()) ? fillStyle : "GOOD_STUFF";
    }

    public String getStyle() {
        return style;
    }

    /**
     * Automatically infers the best-matching dedicated AI strategy style for an arbitrary/player deck
     * based on its card composition and active OCG rule set.
     */
    public static String inferStyleFromDeck(List<Integer> main, List<Integer> extra,
                                            cn.garymb.ygomobile.rpg.model.OcgRuleSet ruleSet) {
        if (main == null || main.isEmpty()) {
            return (ruleSet != null && !ruleSet.tributeSummonEnabled) ? "NO_TRIBUTE_BEAT" : "GOOD_STUFF";
        }
        Set<Integer> exodiaDistinct = new HashSet<>();
        int ccvCount = 0;
        int fusionCore = 0;
        int cannonCount = 0;
        int burnSpellCount = 0;
        int millCore = 0;
        int flipCount = 0;
        int equipCount = 0;

        for (Integer idObj : main) {
            if (idObj == null) continue;
            int id = idObj;
            if (RpgAiCardKnowledge.isExodiaPiece(id)) exodiaDistinct.add(id);
            if (id == RpgAiCardKnowledge.CRUSH_CARD_VIRUS) ccvCount++;
            if (id == RpgAiCardKnowledge.CYBER_STEIN || id == RpgAiCardKnowledge.POLYMERIZATION) fusionCore++;
            if (id == RpgAiCardKnowledge.CANNON_SOLDIER) cannonCount++;
            if (RpgAiCardKnowledge.getBurnDamage(id) > 0) burnSpellCount++;
            if (id == RpgAiCardKnowledge.NEEDLE_WORM || id == RpgAiCardKnowledge.MORPHING_JAR) millCore++;
            if (RpgAiCardKnowledge.isFlipMonster(id)) flipCount++;
            if (RpgAiCardKnowledge.isEquipSpell(id) || id == RpgAiCardKnowledge.METALMORPH) equipCount++;
        }

        if (exodiaDistinct.size() >= 4) return "EXODIA_DRAW";
        if (ccvCount >= 1) return "DARK_VIRUS";
        if (fusionCore >= 2 && extra != null && !extra.isEmpty()) return "FUSION_COMBO";
        if (cannonCount >= 1 || burnSpellCount >= 5) return "BURN_WALL";
        if (millCore >= 4) return "MILL_MORPHING";
        if (flipCount >= 6) return "FLIP_CONTROL";
        if (equipCount >= 4) return "EQUIP_LIGHT_EARTH";
        if (ruleSet != null && !ruleSet.tributeSummonEnabled) return "NO_TRIBUTE_BEAT";
        return "GOOD_STUFF";
    }

    public static String getStyleDisplayName(String fillStyle) {
        if (fillStyle == null) return "Good Stuff 竞技强攻";
        switch (fillStyle) {
            case "EXODIA_DRAW":       return "艾克佐迪亚抽滤";
            case "DARK_VIRUS":        return "死毒暗控";
            case "FUSION_COMBO":      return "融合＆科学怪人";
            case "BURN_WALL":         return "烧血射出壁垒";
            case "MILL_MORPHING":     return "变形壶＆卡组破坏";
            case "FLIP_CONTROL":      return "反转控制";
            case "EQUIP_LIGHT_EARTH": return "装备重击流";
            case "NO_TRIBUTE_BEAT":   return "免祭品高攻强攻";
            case "LOWER_BEAT":        return "下级重击";
            case "NOVICE_NR":         return "新手凡骨";
            case "CASUAL_MID":        return "休闲中游";
            case "MID_TIER_THEME":    return "主题中坚";
            case "GOOD_STUFF":
            default:                  return "Good Stuff 竞技强攻";
        }
    }

    private boolean isExodiaStyle() {
        return "EXODIA_DRAW".equals(style);
    }

    private boolean isBurnStyle() {
        return "BURN_WALL".equals(style);
    }

    private boolean isMillStyle() {
        return "MILL_MORPHING".equals(style);
    }

    private boolean isFlipStyle() {
        return "FLIP_CONTROL".equals(style);
    }

    private boolean isDarkVirusStyle() {
        return "DARK_VIRUS".equals(style);
    }

    private boolean isFusionStyle() {
        return "FUSION_COMBO".equals(style);
    }

    private boolean isEquipStyle() {
        return "EQUIP_LIGHT_EARTH".equals(style);
    }

    private boolean isNoviceStyle() {
        return "NOVICE_NR".equals(style) || "CASUAL_MID".equals(style);
    }

    /**
     * Decides whether to go first (true) or second (false) after winning RPS.
     * Under 1999 rules (Turn 1 does NOT draw), aggressive beatdown decks benefit strongly
     * from going second (extra draw + immediate Battle Phase), while Exodia/Burn/Mill/Virus/Flip
     * decks prefer going first to set traps/flip walls and draw/filter undisturbed.
     */
    public boolean chooseGoFirst() {
        if (isExodiaStyle() || isBurnStyle() || isMillStyle() || isFlipStyle() || isDarkVirusStyle()) {
            return true;
        }
        return state.ruleSet != null && state.ruleSet.firstTurnDrawEnabled;
    }

    // =========================================================================
    // MSG_SELECT_IDLECMD (Main Phase 1 / Main Phase 2)
    // =========================================================================

    public IdleCmdPrompt parseIdleCmd(ByteBuffer data) {
        IdleCmdPrompt p = new IdleCmdPrompt();
        if (data.remaining() < 1) return p;
        data.get(); // selecting_player
        readSimpleCmdList(data, p.summonable);
        readSimpleCmdList(data, p.spSummonable);
        readSimpleCmdList(data, p.reposable);
        readSimpleCmdList(data, p.msetable);
        readSimpleCmdList(data, p.ssetable);
        int actCount = data.remaining() >= 1 ? (data.get() & 0xFF) : 0;
        for (int i = 0; i < actCount && data.remaining() >= 11; i++) {
            int rawCode = data.getInt();
            int flag = (rawCode & 0x80000000) != 0 ? 1 : 0;
            int code = rawCode & 0x7FFFFFFF;
            int con = data.get() & 0xFF;
            int loc = data.get() & 0xFF;
            int seq = data.get() & 0xFF;
            int desc = data.getInt();
            p.activatable.add(new CmdEntry(code, con, state.localPlayer(con & 1), loc, seq, desc, flag, i));
        }
        p.canBattle = data.remaining() >= 1 && (data.get() & 0xFF) != 0;
        p.canEnd = data.remaining() >= 1 && (data.get() & 0xFF) != 0;
        p.canShuffle = data.remaining() >= 1 && (data.get() & 0xFF) != 0;
        return p;
    }

    private void readSimpleCmdList(ByteBuffer data, List<CmdEntry> out) {
        int count = data.remaining() >= 1 ? (data.get() & 0xFF) : 0;
        for (int i = 0; i < count && data.remaining() >= 7; i++) {
            int code = data.getInt();
            int con = data.get() & 0xFF;
            int loc = data.get() & 0xFF;
            int seq = data.get() & 0xFF;
            out.add(new CmdEntry(code, con, state.localPlayer(con & 1), loc, seq, 0, 0, i));
        }
    }

    /**
     * Core Main Phase decision waterfall:
     * 0. Burn / Cannon Soldier immediate lethal
     * 1. Draw & search spells (Pot of Greed, Graceful Charity, Fusion Sage, Last Will)
     * 2. Backrow wipe (Harpie's Feather Duster / Heavy Storm)
     * 3. Board wipes & removal BEFORE committing Normal/Special Summons (NEVER summon then Dark Hole!)
     * 4. Beneficial Flip Summons (Magician of Faith, Mask of Darkness, Man-Eater Bug w/ target, etc.)
     * 5. Archetype Special Summons (Cyber-Stein, Polymerization, Monster Reborn)
     * 6. Normal Summon / Tribute Summon / Monster Set
     * 7. Equip Spells AFTER summoning our monster (NEVER equip opponent's monster!)
     * 8. Position changes (DEF->ATK in M1 for attackers; weak ATK->DEF in M2/threatened)
     * 9. Burn / Heal / Swords of Revealing Light / Cannon Soldier utility
     * 10. Set Traps (never clutter S/T zone by blindly setting normal spells)
     * 11. Battle Phase (if profitable attack exists) or End Phase
     */
    public int chooseIdleCmd(IdleCmdPrompt p) {
        if (state.turnCount != lastDecisionTurn) {
            lastDecisionTurn = state.turnCount;
            idleActionsThisTurn = 0;
        }
        pendingAttackerSeq = -1;
        pendingActivatingCode = 0;

        if (idleActionsThisTurn++ >= 35) {
            return finishMainPhase(p);
        }

        // 0. Check immediate burn / Cannon Soldier lethal
        Integer lethalResp = tryImmediateLethal(p);
        if (lethalResp != null) return lethalResp;

        // 1. Draw / Search / Combo Setup Spells FIRST
        Integer drawResp = tryDrawAndSearchSpells(p);
        if (drawResp != null) return drawResp;

        // 2. Backrow Wipe before committing monsters/attacks
        Integer backrowResp = tryBackrowWipe(p);
        if (backrowResp != null) return backrowResp;

        // 3. Board Wipes & Monster Removal BEFORE Normal/Special Summon!
        Integer removalResp = tryMonsterRemoval(p);
        if (removalResp != null) return removalResp;

        // 4. Flip Summon our face-down utility/flip monsters when beneficial
        Integer flipResp = tryFlipSummon(p);
        if (flipResp != null) return flipResp;

        // 5. Special Summons & Archetype Combos (Cyber-Stein, Polymerization, Monster Reborn)
        Integer spResp = trySpecialSummonsAndCombos(p);
        if (spResp != null) return spResp;

        // 6. Normal Summon / Tribute Summon / Monster Set
        Integer summonResp = tryNormalSummonOrSet(p);
        if (summonResp != null) return summonResp;

        // 7. Equip Spells AFTER summoning our best monster
        Integer equipResp = tryEquipSpells(p);
        if (equipResp != null) return equipResp;

        // 8. Position Changes (DEF -> ATK for attack in M1, or ATK -> DEF for defense in M2/behind)
        Integer reposResp = tryReposition(p);
        if (reposResp != null) return reposResp;

        // 9. Burn / Heal / Swords of Revealing Light / Cannon Soldier utility
        Integer utilResp = tryUtilitySpellsAndEffects(p);
        if (utilResp != null) return utilResp;

        // 10. Set Traps
        Integer setTrapResp = trySetTraps(p);
        if (setTrapResp != null) return setTrapResp;

        // 11. Enter Battle Phase if profitable, else End Phase
        return finishMainPhase(p);
    }

    private Integer tryImmediateLethal(IdleCmdPrompt p) {
        int oppLp = state.lp[1];
        // Direct burn spell lethal
        int totalBurnInHand = 0;
        CmdEntry firstBurn = null;
        for (CmdEntry e : p.activatable) {
            int dmg = RpgAiCardKnowledge.getBurnDamage(e.code);
            if (dmg > 0) {
                totalBurnInHand += dmg;
                if (firstBurn == null) firstBurn = e;
                if (dmg >= oppLp) {
                    return activateIdle(e);
                }
            }
        }
        int myMonsters = state.getMonsters(0).size();
        CmdEntry cannon = findActivatable(p, RpgAiCardKnowledge.CANNON_SOLDIER);
        int cannonMax = cannon != null ? myMonsters * 500 : 0;
        if (firstBurn != null && totalBurnInHand + cannonMax >= oppLp) {
            return activateIdle(firstBurn);
        }
        if (cannon != null && cannonMax >= oppLp) {
            return activateIdle(cannon);
        }
        return null;
    }

    private Integer tryDrawAndSearchSpells(IdleCmdPrompt p) {
        CmdEntry pot = findActivatable(p, RpgAiCardKnowledge.POT_OF_GREED);
        if (pot != null && state.deckCount[0] >= 2) return activateIdle(pot);

        CmdEntry grace = findActivatable(p, RpgAiCardKnowledge.GRACEFUL_CHARITY);
        if (grace != null && state.deckCount[0] >= 3) return activateIdle(grace);

        CmdEntry sage = findActivatable(p, RpgAiCardKnowledge.FUSION_SAGE);
        if (sage != null) return activateIdle(sage);

        // Last Will: activate BEFORE tributing/sending monsters (Cannon Soldier, Tribute Summon, Dark Hole)
        CmdEntry lastWill = findActivatable(p, RpgAiCardKnowledge.LAST_WILL);
        if (lastWill != null && !state.lastWillActivatedThisTurn && shouldActivateLastWill(p)) {
            return activateIdle(lastWill);
        }
        return null;
    }

    private boolean shouldActivateLastWill(IdleCmdPrompt p) {
        if (state.getMonsters(0).size() > 0) {
            // We have a monster that can be tributed / fired by Cannon Soldier / dark-holed / crashed
            if (hasMonsterOnField(0, RpgAiCardKnowledge.CANNON_SOLDIER)
                    || findActivatable(p, RpgAiCardKnowledge.DARK_HOLE) != null
                    || hasTributeSummonCandidate(p)) {
                return true;
            }
            if (!state.isMain2() && !state.getMonsters(1).isEmpty()) {
                return true;
            }
        }
        // Or if we have a monster to summon & Cannon Soldier/Change of Heart in hand
        return (isExodiaStyle() || isFusionStyle() || isBurnStyle()) && !p.summonable.isEmpty();
    }

    private Integer tryBackrowWipe(IdleCmdPrompt p) {
        int oppBackrow = state.getSpellTraps(1).size();
        int myBackrow = state.getSpellTraps(0).size();

        CmdEntry hfd = findActivatable(p, RpgAiCardKnowledge.HARPIES_FEATHER_DUSTER);
        if (hfd != null && oppBackrow >= 1) {
            return activateIdle(hfd);
        }
        CmdEntry storm = findActivatable(p, RpgAiCardKnowledge.HEAVY_STORM);
        if (storm != null && oppBackrow > myBackrow && oppBackrow >= 1) {
            return activateIdle(storm);
        }
        // De-Spell (Vol.2): snipe opponent's face-up Spell (e.g. Swords of Revealing Light / Equip)
        CmdEntry deSpell = findActivatable(p, RpgAiCardKnowledge.DE_SPELL);
        if (deSpell != null && hasFaceUpSpellOnField(1)) {
            return activateIdle(deSpell);
        }
        // Remove Trap (Starter Box): snipe opponent's face-up Trap (e.g. Royal Decree / Dragon Capture Jar)
        CmdEntry removeTrap = findActivatable(p, RpgAiCardKnowledge.REMOVE_TRAP);
        if (removeTrap != null && hasFaceUpTrapOnField(1)) {
            return activateIdle(removeTrap);
        }
        return null;
    }

    private boolean hasFaceUpSpellOnField(int localPlayer) {
        for (RpgAiDuelState.CardSlot s : state.getSpellTraps(localPlayer)) {
            if (s.isFaceUp() && (s.isSpell() || (state.lookupCard(s.code) != null && "spell".equalsIgnoreCase(state.lookupCard(s.code).frameType)))) {
                return true;
            }
        }
        return false;
    }

    private boolean hasFaceUpTrapOnField(int localPlayer) {
        for (RpgAiDuelState.CardSlot s : state.getSpellTraps(localPlayer)) {
            if (s.isFaceUp() && (s.isTrap() || isLikelyTrapCode(s.code))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Evaluates Raigeki, Dark Hole, Change of Heart, Fissure, Tribute to the Doomed, Stop Defense,
     * and Time Wizard BEFORE Normal Summoning so the AI never commits a monster then blows it up!
     */
    private Integer tryMonsterRemoval(IdleCmdPrompt p) {
        List<RpgAiDuelState.CardSlot> myMons = state.getMonsters(0);
        List<RpgAiDuelState.CardSlot> oppMons = state.getMonsters(1);
        if (oppMons.isEmpty()) {
            return null;
        }

        int myBestOnField = state.getStrongestAtk(0);
        int myBestInHand = getBestPlayableSummonAtk(p);
        int myEffectiveBest = Math.max(myBestOnField, myBestInHand);
        int oppStrongestAtk = state.getStrongestAtk(1);
        int oppStrongestThreat = state.getStrongestThreatValue(1);
        int oppWeakestFaceUpAtk = getWeakestFaceUpMonsterAtk(1);
        boolean oppHasFaceDown = hasFaceDownMonster(1);
        boolean oppHasFaceUp = hasFaceUpMonster(1);
        // First Flip monsters (Man-Eater Bug / Hane-Hane / Needle Worm) debuted in Vol.3 (1999-05-27)
        boolean flipMonstersExist = state.currentTimelineDate.compareTo("1999-05-27") >= 0;
        boolean clearForLethal = !state.isMain2() && p.canBattle
                && (state.getTotalFaceUpAtk(0) + myBestInHand >= state.lp[1]);

        // 1) Change of Heart / Brain Control: huge tempo play in Main 1 (steal to tribute or beat down!)
        CmdEntry coh = findActivatable(p, RpgAiCardKnowledge.CHANGE_OF_HEART);
        if (coh == null && state.lp[0] > 1200) {
            coh = findActivatable(p, RpgAiCardKnowledge.BRAIN_CONTROL);
        }
        if (coh != null && state.getFreeMainMzoneCount(0) > 0) {
            boolean wantForTribute = hasTributeSummonCandidate(p);
            boolean wantForBattle = !state.isMain2() && p.canBattle;
            boolean wantForCannon = hasMonsterOnField(0, RpgAiCardKnowledge.CANNON_SOLDIER)
                    || hasMonsterOnField(0, RpgAiCardKnowledge.CATAPULT_TURTLE);
            if (wantForTribute || wantForBattle || wantForCannon) {
                return activateIdle(coh);
            }
        }

        // 1b) Canon boss monster ignition effects (Relinquished, Thousand-Eyes Restrict, Barrel Dragon)
        CmdEntry relinq = findActivatable(p, RpgAiCardKnowledge.RELINQUISHED);
        if (relinq == null) relinq = findActivatable(p, RpgAiCardKnowledge.THOUSAND_EYES_RESTRICT);
        if (relinq != null && !oppMons.isEmpty()) {
            return activateIdle(relinq);
        }
        CmdEntry barrel = findActivatable(p, RpgAiCardKnowledge.BARREL_DRAGON);
        if (barrel != null && !oppMons.isEmpty()) {
            return activateIdle(barrel);
        }

        // 1c) Canon theme enablers: Ritual Spells, Elegant Egotist, Toon World
        for (int ritualId : RpgAiCardKnowledge.RITUAL_SPELLS) {
            CmdEntry ritual = findActivatable(p, ritualId);
            if (ritual != null && state.getFreeMainMzoneCount(0) > 0) {
                return activateIdle(ritual);
            }
        }
        CmdEntry egotist = findActivatable(p, RpgAiCardKnowledge.ELEGANT_EGOTIST);
        if (egotist != null && state.getFreeMainMzoneCount(0) > 0) {
            return activateIdle(egotist);
        }
        CmdEntry toonWorld = findActivatable(p, RpgAiCardKnowledge.TOON_WORLD);
        if (toonWorld != null && state.lp[0] > 1500) {
            return activateIdle(toonWorld);
        }

        // 2) Raigeki: one-sided wipe — preserve unless it earns real value (multi-kill, answers threat we
        //    can't beat in battle, protects a Boss from post-Vol.3 Flip monsters, or secures immediate lethal!)
        CmdEntry raigeki = findActivatable(p, RpgAiCardKnowledge.RAIGEKI);
        if (raigeki != null) {
            boolean worthIt = oppMons.size() >= 2
                    || oppStrongestThreat >= myEffectiveBest
                    || (flipMonstersExist && oppHasFaceDown && myEffectiveBest >= state.eraBossAtk)
                    || clearForLethal
                    || ((isExodiaStyle() || isBurnStyle()) && oppStrongestThreat >= state.eraTypicalLowerAtk);
            if (worthIt) {
                return activateIdle(raigeki);
            }
        }

        // 3) Dark Hole: STRICT anti-self-sabotage + resource conservation check!
        //    - NEVER blow up our own advantage!
        //    - Don't waste Dark Hole on a single weak monster our hand can already beat over in battle!
        CmdEntry darkHole = findActivatable(p, RpgAiCardKnowledge.DARK_HOLE);
        if (darkHole != null && shouldActivateDarkHole(myMons, oppMons, myBestOnField, myBestInHand,
                oppStrongestThreat, flipMonstersExist, clearForLethal)) {
            return activateIdle(darkHole);
        }

        // 4) Fissure: destroys opponent's LOWEST-ATK face-up monster!
        //    Hold Fissure if our field/hand already beats over that target in battle (save Fissure for
        //    Dark Magician / Gaia / Blue-Eyes / Summoned Skull, or use in M2 after clearing smaller monsters!).
        CmdEntry fissure = findActivatable(p, RpgAiCardKnowledge.FISSURE);
        if (fissure != null && oppHasFaceUp) {
            boolean targetBeatsOrTiesUs = oppWeakestFaceUpAtk >= myEffectiveBest;
            boolean singleBlockerLethal = oppMons.size() == 1 && clearForLethal;
            boolean stallClear = (isExodiaStyle() || isBurnStyle()) && oppWeakestFaceUpAtk >= state.eraTypicalLowerAtk;
            if (targetBeatsOrTiesUs || singleBlockerLethal || stallClear) {
                return activateIdle(fissure);
            }
        }

        // 5) Tribute to the Doomed: costs a discard (-1), so ONLY use against threats we can't beat over
        //    in battle (or for immediate lethal / pre-errata Exodia Sangan discard combo)!
        CmdEntry ttd = findActivatable(p, RpgAiCardKnowledge.TRIBUTE_TO_THE_DOOMED);
        if (ttd != null && canSafelyDiscard(1)) {
            boolean bigThreat = oppStrongestThreat >= myEffectiveBest
                    || (oppMons.size() == 1 && clearForLethal)
                    || (isExodiaStyle() && state.isPreErrataSanganEra() && hasSearcherInHand());
            if (bigThreat) {
                return activateIdle(ttd);
            }
        }

        // 6) Stop Defense: only in Main 1 when opponent has a DEF monster we can beat in ATK
        CmdEntry stopDef = findActivatable(p, RpgAiCardKnowledge.STOP_DEFENSE);
        if (stopDef != null && !state.isMain2() && p.canBattle && hasDefenseMonster(1) && myEffectiveBest >= state.eraTypicalLowerAtk) {
            return activateIdle(stopDef);
        }

        // 7) Time Wizard: only gamble when we are behind on board!
        CmdEntry timeWiz = findActivatable(p, RpgAiCardKnowledge.TIME_WIZARD);
        if (timeWiz != null) {
            if (oppStrongestThreat > myBestOnField || oppMons.size() >= myMons.size() + 1) {
                return activateIdle(timeWiz);
            }
        }

        return null;
    }

    private int getWeakestFaceUpMonsterAtk(int localPlayer) {
        int weakest = Integer.MAX_VALUE;
        for (RpgAiDuelState.CardSlot m : state.getMonsters(localPlayer)) {
            if (m.isFaceUp()) {
                weakest = Math.min(weakest, Math.max(0, m.atk));
            }
        }
        return weakest == Integer.MAX_VALUE ? 0 : weakest;
    }

    /**
     * Decides whether Dark Hole is genuinely advantageous (never self-sabotaging, and conserving
     * Dark Hole when our monster in hand cleanly beats over a lone weak opponent monster).
     */
    boolean shouldActivateDarkHole(List<RpgAiDuelState.CardSlot> myMons,
                                   List<RpgAiDuelState.CardSlot> oppMons,
                                   int myBestOnField,
                                   int myBestInHand,
                                   int oppStrongestThreat,
                                   boolean flipMonstersExist,
                                   boolean clearForLethal) {
        if (oppMons.isEmpty()) return false;

        // If we already have monsters on field, NEVER blow up our own advantage!
        if (!myMons.isEmpty()) {
            // Check if all our monsters are expendable (e.g., Sangan/Witch or weak < eraTypicalLowerAtk)
            int myValuableCount = 0;
            for (RpgAiDuelState.CardSlot m : myMons) {
                if (!RpgAiCardKnowledge.isSearcherMonster(m.code)
                        && m.effectiveCombatValue(state.eraEstimatedFaceDownDef) >= state.eraTypicalLowerAtk) {
                    myValuableCount++;
                }
            }
            // If we have valuable monsters and are NOT strictly outclassed, hold Dark Hole!
            if (myValuableCount > 0) {
                boolean strictlyOutclassed = oppStrongestThreat > myBestOnField;
                boolean heavilyOutnumbered = oppMons.size() >= myMons.size() + 2;
                if (!strictlyOutclassed && !heavilyOutnumbered) {
                    return false;
                }
            }
            // Even with weak monsters, don't blow up our own board unless opponent has a real threat
            if (oppStrongestThreat < Math.max(state.eraTopLowerAtk, myBestInHand) && oppMons.size() <= myMons.size()) {
                return false;
            }
            return true;
        }

        // Our monster zone is EMPTY -> Dark Hole is effectively a one-sided wipe.
        // Still conserve it unless it hits 2+ monsters, answers a monster our hand can't beat,
        // clears a post-Vol.3 Flip threat before our Boss attacks, or secures immediate lethal!
        if (oppMons.size() >= 2) return true;
        if (oppStrongestThreat >= myBestInHand) return true;
        if (clearForLethal) return true;
        if (flipMonstersExist && hasFaceDownMonster(1) && myBestInHand >= state.eraBossAtk) return true;
        if ((isExodiaStyle() || isBurnStyle()) && oppStrongestThreat >= state.eraTypicalLowerAtk) return true;
        return false;
    }

    private Integer tryFlipSummon(IdleCmdPrompt p) {
        if (p.reposable.isEmpty()) return null;
        int oppMons = state.getMonsters(1).size();
        int oppBackrow = state.getSpellTraps(1).size();

        for (CmdEntry e : p.reposable) {
            RpgAiDuelState.CardSlot slot = state.getCard(0, e.location, e.sequence);
            if (slot == null || !slot.isFaceDown()) continue;
            int code = slot.code != 0 ? slot.code : e.code;

            if (code == RpgAiCardKnowledge.MAGICIAN_OF_FAITH) {
                if (state.hasGoodSpellInMyGrave()) return reposIdle(e);
                continue;
            }
            if (code == RpgAiCardKnowledge.MASK_OF_DARKNESS) {
                if (state.hasGoodTrapInMyGrave()) return reposIdle(e);
                continue;
            }
            if (code == RpgAiCardKnowledge.MAN_EATER_BUG
                    || code == RpgAiCardKnowledge.HANE_HANE
                    || code == RpgAiCardKnowledge.PENGUIN_SOLDIER) {
                // CRITICAL: NEVER flip Man-Eater Bug on an empty opponent board!
                if (oppMons >= 1) return reposIdle(e);
                continue;
            }
            if (code == RpgAiCardKnowledge.ARMED_NINJA
                    || code == RpgAiCardKnowledge.REAPER_OF_THE_CARDS
                    || code == RpgAiCardKnowledge.TRAP_MASTER) {
                if (oppBackrow >= 1) return reposIdle(e);
                continue;
            }
            if (code == RpgAiCardKnowledge.SKELENGEL || code == RpgAiCardKnowledge.NEEDLE_WORM) {
                return reposIdle(e);
            }
            if (code == RpgAiCardKnowledge.MORPHING_JAR) {
                int myHand = state.hand[0].size();
                int oppHand = state.hand[1].size();
                if (isExodiaStyle() && state.getUniqueExodiaPiecesInHand().size() >= 2) {
                    continue; // NEVER discard our Exodia pieces!
                }
                if (isMillStyle()) {
                    // Mill / Anti-Exodia Morphing Jar: flip whenever opponent holds cards or our hand is small!
                    if (oppHand >= 2 || myHand <= 3) return reposIdle(e);
                    continue;
                }
                if (myHand <= 2 || myHand < oppHand) {
                    return reposIdle(e);
                }
                continue;
            }
            // Normal face-down monster: flip to ATK in Main 1 if it can profitably attack
            int atk = state.getCardBaseAtk(code);
            if (!state.isMain2() && p.canBattle && atk >= state.eraTypicalLowerAtk && canMonsterAttackProfitably(atk)) {
                return reposIdle(e);
            }
        }
        return null;
    }

    private Integer trySpecialSummonsAndCombos(IdleCmdPrompt p) {
        // 1) Cyber-Stein (恶魔科学怪人): pay 5000 LP for Blue-Eyes Ultimate (4500) / Black Skull (3200)!
        CmdEntry stein = findActivatable(p, RpgAiCardKnowledge.CYBER_STEIN);
        if (stein != null && state.lp[0] > 5000 && state.getFreeMainMzoneCount(0) > 0) {
            if (!state.isMain2() || state.lp[0] >= 6500) {
                return activateIdle(stein);
            }
        }

        // 2) Flute of Summoning Dragon (呼龙笛, EX): drop Blue-Eyes / Dragons from hand when Lord of D. is out!
        CmdEntry flute = findActivatable(p, RpgAiCardKnowledge.FLUTE_OF_SUMMONING_DRAGON);
        if (flute != null && state.getFreeMainMzoneCount(0) > 0 && hasDragonInHand()) {
            return activateIdle(flute);
        }

        // 3) Polymerization (融合)
        CmdEntry poly = findActivatable(p, RpgAiCardKnowledge.POLYMERIZATION);
        if (poly != null && state.getFreeMainMzoneCount(0) > 0) {
            return activateIdle(poly);
        }

        // 4) Any intrinsic Special Summon (spSummonable)
        if (!p.spSummonable.isEmpty()) {
            CmdEntry bestSp = pickHighestAtkEntry(p.spSummonable);
            if (bestSp != null) return (bestSp.index << 16) + 1;
        }

        // 5) Monster Reborn (死者苏生)
        CmdEntry reborn = findActivatable(p, RpgAiCardKnowledge.MONSTER_REBORN);
        if (reborn != null && state.getFreeMainMzoneCount(0) > 0) {
            int bestGraveAtk = state.getBestGraveMonsterAtk();
            boolean hasSearcherInGrave = hasCodeInMyGrave(RpgAiCardKnowledge.SANGAN)
                    || hasCodeInMyGrave(RpgAiCardKnowledge.WITCH_OF_BLACK_FOREST);
            boolean hasSteinInGrave = isFusionStyle() && state.lp[0] > 5000
                    && hasCodeInMyGrave(RpgAiCardKnowledge.CYBER_STEIN);
            boolean worthIt = bestGraveAtk >= state.eraTopLowerAtk
                    || (bestGraveAtk >= state.eraTypicalLowerAtk && state.getMonsters(0).isEmpty())
                    || hasSteinInGrave
                    || ((isExodiaStyle() || isDarkVirusStyle() || isBurnStyle()) && hasSearcherInGrave);
            if (worthIt) {
                return activateIdle(reborn);
            }
        }
        return null;
    }

    private boolean hasDragonInHand() {
        for (RpgAiDuelState.CardSlot c : state.hand[0]) {
            if (c == null) continue;
            int race = c.race != 0 ? c.race : state.getCardRaceMask(c.code);
            if ((race & 0x2000) != 0) return true; // Dragon
        }
        return false;
    }

    private Integer tryNormalSummonOrSet(IdleCmdPrompt p) {
        if (p.summonable.isEmpty() && p.msetable.isEmpty()) return null;

        int oppStrongest = state.getStrongestThreatValue(1);
        int oppStrongestFaceUpAtk = state.getStrongestAtk(1);
        int myStrongestOnField = state.getStrongestAtk(0);
        boolean myBoardEmpty = state.getMonsters(0).isEmpty();
        boolean oppBoardEmpty = state.getMonsters(1).isEmpty();
        boolean hasBoardAdvantage = oppBoardEmpty || myStrongestOnField >= oppStrongest;
        boolean hasStolen = state.hasStolenMonsterOnMyField();

        // Special case: Fusion deck with Cyber-Stein in hand & LP > 5000 & Main 1 -> summon Cyber-Stein!
        if (isFusionStyle() && state.lp[0] > 5000 && !state.isMain2() && state.getFreeMainMzoneCount(0) >= 2) {
            for (CmdEntry e : p.summonable) {
                if (e.code == RpgAiCardKnowledge.CYBER_STEIN) {
                    return e.index << 16;
                }
            }
        }

        // Special case: EX Kaiba combo — Lord of D. + Flute of Summoning Dragon + Dragon in hand!
        if (state.hasCardInHand(RpgAiCardKnowledge.FLUTE_OF_SUMMONING_DRAGON)
                && hasDragonInHand()
                && state.getFreeMainMzoneCount(0) >= 2) {
            for (CmdEntry e : p.summonable) {
                if (e.code == RpgAiCardKnowledge.LORD_OF_D) {
                    return e.index << 16;
                }
            }
        }

        // Classic 1999 OCG tactic ("落穴踏み" / Trap Hole Baiting):
        // When opponent has face-down Spell/Trap(s) and we hold BOTH a Boss/Tribute monster AND a
        // 1000+ ATK non-tribute beater, probe the set backrow with the lower beater first instead of
        // walking Dark Magician / Gaia / Blue-Eyes / Summoned Skull straight into Trap Hole!
        boolean oppHasSetBackrow = countFaceDownBackrow(1) > 0;
        int bestBossAtk = 0;
        int bestBaitAtk = 0;
        boolean hasBossOrTribute = false;
        if (oppHasSetBackrow && !hasStolen && !isExodiaStyle() && !isBurnStyle()) {
            for (CmdEntry e : p.summonable) {
                int atk = state.getCardBaseAtk(e.code);
                int def = state.getCardBaseDef(e.code);
                int lv = state.getCardLevel(e.code);
                int tributes = state.tributesNeededForLevel(lv);
                if (atk >= state.eraBossAtk || tributes > 0) {
                    hasBossOrTribute = true;
                    bestBossAtk = Math.max(bestBossAtk, atk);
                } else if (tributes == 0
                        && !RpgAiCardKnowledge.isFlipMonster(e.code)
                        && !RpgAiCardKnowledge.isExodiaPiece(e.code)
                        && !RpgAiCardKnowledge.isSearcherMonster(e.code)
                        && atk >= 1000 && atk < state.eraBossAtk && atk >= def) {
                    bestBaitAtk = Math.max(bestBaitAtk, atk);
                }
            }
        }
        boolean bossWouldBeLethal = oppBoardEmpty && !state.isMain2() && p.canBattle
                && (state.getTotalFaceUpAtk(0) + bestBossAtk >= state.lp[1]);
        boolean preferTrapHoleBait = oppHasSetBackrow
                && hasBossOrTribute
                && bestBaitAtk >= 1000
                && !bossWouldBeLethal
                && oppStrongestFaceUpAtk <= Math.max(bestBaitAtk, myStrongestOnField)
                && (state.lp[0] > 2500 || oppStrongestFaceUpAtk <= bestBaitAtk);

        // Evaluate best Normal Summon candidate
        CmdEntry bestSummon = null;
        int bestSummonScore = Integer.MIN_VALUE;
        for (CmdEntry e : p.summonable) {
            int score = scoreNormalSummon(e, oppStrongest, myStrongestOnField, oppBoardEmpty, hasBoardAdvantage, hasStolen);
            if (preferTrapHoleBait && score > 0) {
                int atk = state.getCardBaseAtk(e.code);
                int def = state.getCardBaseDef(e.code);
                int tributes = state.tributesNeededForLevel(state.getCardLevel(e.code));
                if (atk >= state.eraBossAtk || tributes > 0) {
                    // Hold Boss/Tribute behind the bait beater when probing opponent's set backrow
                    score -= 1900;
                } else if (tributes == 0 && atk >= 1000 && atk < state.eraBossAtk && atk >= def
                        && !RpgAiCardKnowledge.isFlipMonster(e.code)
                        && !RpgAiCardKnowledge.isSearcherMonster(e.code)) {
                    score += 450;
                }
            }
            if (score > bestSummonScore) {
                bestSummonScore = score;
                bestSummon = e;
            }
        }

        // Evaluate best Monster Set candidate
        CmdEntry bestSet = null;
        int bestSetScore = Integer.MIN_VALUE;
        for (CmdEntry e : p.msetable) {
            int score = scoreMonsterSet(e, oppStrongest, myStrongestOnField, myBoardEmpty, oppBoardEmpty, hasBoardAdvantage);
            if (score > bestSetScore) {
                bestSetScore = score;
                bestSet = e;
            }
        }

        if (bestSummon == null && bestSet == null) return null;
        if (bestSummonScore < 0 && bestSetScore < 0) return null;

        if (bestSummon != null && bestSummonScore >= bestSetScore) {
            return bestSummon.index << 16;
        }
        if (bestSet != null) {
            return (bestSet.index << 16) + 3;
        }
        return null;
    }

    private int scoreNormalSummon(CmdEntry e, int oppStrongest, int myStrongestOnField,
                                  boolean oppBoardEmpty, boolean hasBoardAdvantage, boolean hasStolen) {
        int code = e.code;
        int atk = state.getCardBaseAtk(code);
        int def = state.getCardBaseDef(code);
        int lv = state.getCardLevel(code);
        int tributes = state.tributesNeededForLevel(lv);

        // NEVER summon Exodia pieces unless empty board and about to die!
        if (RpgAiCardKnowledge.isExodiaPiece(code)) {
            return -10000;
        }

        // Flip monsters strongly prefer Set, NEVER Attack Position summon (unless exact lethal!)
        if (RpgAiCardKnowledge.isFlipMonster(code)) {
            if (oppBoardEmpty && !state.isMain2() && state.getTotalFaceUpAtk(0) + atk >= state.lp[1]) {
                return 5000; // exact lethal!
            }
            return -500;
        }

        // Check if tribute summon is actually profitable!
        if (tributes > 0) {
            int tributeCost = estimateTributeFodderValue(tributes);
            // If we stole opponent's monster via Change of Heart, tributing it is AMAZING (+900)!
            int stolenBonus = hasStolen ? 900 : 0;
            // Don't tribute a stronger monster (or 2 good monsters) for a weaker one!
            if (atk + stolenBonus <= tributeCost + 100) {
                return -800;
            }
        }

        // Calculate potential equip boost from hand
        int equipBonus = getBestHandEquipBonusFor(code);
        int effectiveAtk = atk + equipBonus;

        // Defensive/wall monsters (era-calibrated, e.g. Giant Soldier 1300/2000, Mystical Elf 800/2000)
        if (def >= state.eraWallDefThreshold && def > atk && atk <= state.eraTypicalLowerAtk) {
            if (isExodiaStyle() || isBurnStyle() || isFlipStyle()) return -200;
            if (!hasBoardAdvantage && oppStrongest > effectiveAtk) return -200;
            if (state.isMain2() || (state.turnCount == 1)) return -200;
        }

        // Sangan / Witch: prefer Set unless we have Cannon Soldier / Crush Card Virus / open board
        if (RpgAiCardKnowledge.isSearcherMonster(code)) {
            if (isExodiaStyle() && !hasMonsterOnField(0, RpgAiCardKnowledge.CANNON_SOLDIER)) {
                return 100; // Set scores higher
            }
            if (!oppBoardEmpty && oppStrongest > atk) {
                return 150;
            }
        }

        // Cannon Soldier: great in Burn/Exodia if we have ammo or lethal
        if (code == RpgAiCardKnowledge.CANNON_SOLDIER && (isBurnStyle() || isExodiaStyle())) {
            return effectiveAtk + 400;
        }

        int score = effectiveAtk;
        // Era top beaters (e.g. 1200 ATK in Vol.1, 1600+ in Vol.4, 1800+ in EX) strongly prefer Attack Summon!
        if (effectiveAtk >= state.eraTopLowerAtk && effectiveAtk >= def) {
            score += 250;
        }
        // Under 1999-02-04 no-tribute rule, Lv5+ beaters cost 0 tributes -> pure ATK wins!
        if (tributes == 0 && lv >= 5 && effectiveAtk >= state.eraHighThreatAtk) {
            score += 350;
        }
        if (tributes > 0 && hasStolen) {
            score += 700;
        }
        // In Main 2 or Turn 1, only avoid leaving a weak DEF-leaning monster exposed in Attack Position
        if ((state.isMain2() || state.turnCount == 1)
                && effectiveAtk < state.eraTypicalLowerAtk && def > atk) {
            score -= 400;
        }
        // Only penalize Attack Summon when opponent strictly outclasses BOTH our field AND this monster
        if (!hasBoardAdvantage && oppStrongest > Math.max(effectiveAtk, myStrongestOnField) && def >= atk) {
            score -= 600;
        }
        return score;
    }

    private int scoreMonsterSet(CmdEntry e, int oppStrongest, int myStrongestOnField,
                                boolean myBoardEmpty, boolean oppBoardEmpty, boolean hasBoardAdvantage) {
        int code = e.code;
        int atk = state.getCardBaseAtk(code);
        int def = state.getCardBaseDef(code);
        int lv = state.getCardLevel(code);
        int tributes = state.tributesNeededForLevel(lv);

        // Don't tribute summon face-down
        if (tributes > 0) return -900;

        // NEVER set Exodia pieces unless board is empty and we're facing lethal
        if (RpgAiCardKnowledge.isExodiaPiece(code)) {
            boolean facingLethal = myBoardEmpty && state.getTotalFaceUpAtk(1) >= state.lp[0];
            return facingLethal ? 50 : -10000;
        }

        // Flip monsters LOVE being Set!
        if (RpgAiCardKnowledge.isFlipMonster(code)) {
            int base = 1750;
            if (isFlipStyle() || isMillStyle() || isExodiaStyle() || isBurnStyle()) base += 450;
            if (isMillStyle() && (code == RpgAiCardKnowledge.NEEDLE_WORM || code == RpgAiCardKnowledge.MORPHING_JAR)) {
                base += 450;
            }
            if (code == RpgAiCardKnowledge.MAN_EATER_BUG && !oppBoardEmpty) base += 350;
            if (code == RpgAiCardKnowledge.MAGICIAN_OF_FAITH && state.hasGoodSpellInMyGrave()) base += 300;
            if (code == RpgAiCardKnowledge.MASK_OF_DARKNESS && isDarkVirusStyle()) base += 350;
            if (code == RpgAiCardKnowledge.MORPHING_JAR && isExodiaStyle() && state.getUniqueExodiaPiecesInHand().size() >= 2) {
                base -= 1000;
            }
            if (code == RpgAiCardKnowledge.SKELENGEL && isExodiaStyle()) base += 400;
            return base;
        }

        // Sangan / Witch: love being Set defensively (and serve as Crush Card Virus / search fodder)
        if (RpgAiCardKnowledge.isSearcherMonster(code)) {
            int base = 1550;
            if (isExodiaStyle() || isDarkVirusStyle() || isFusionStyle() || isMillStyle()) base += 500;
            return base;
        }

        // Wall of Illusion (1000/1850) & era-high-DEF walls
        if (code == RpgAiCardKnowledge.WALL_OF_ILLUSION) {
            return (oppBoardEmpty && !isExodiaStyle() && !isBurnStyle() && !isMillStyle()) ? 1400 : 1950;
        }
        if (def >= state.eraWallDefThreshold && def > atk) {
            int base = def - 100;
            if (isExodiaStyle() || isBurnStyle() || isMillStyle() || state.turnCount == 1 || (!hasBoardAdvantage && oppStrongest > atk)) {
                base += 400;
            }
            return base;
        }

        // When opponent outclasses our board and this monster can't beat over them even with an equip,
        // Setting a defensive blocker saves LP (NEVER defensively set an offensive beater when we have
        // board advantage or opponent board is empty!)
        int effectiveAtk = atk + getBestHandEquipBonusFor(code);
        if (!hasBoardAdvantage && !oppBoardEmpty && oppStrongest > Math.max(effectiveAtk, myStrongestOnField)) {
            if (def >= atk || def >= state.eraWallDefThreshold) {
                return Math.max(def, state.eraTypicalLowerAtk) + 450;
            }
            // Even for low-DEF monsters when completely outclassed with an empty board, chump-blocking saves LP
            if (myBoardEmpty && oppStrongest - effectiveAtk >= 500) {
                return effectiveAtk + 400;
            }
        }

        // Otherwise normal beatsticks strongly prefer Attack Position summon
        return def - 300;
    }

    private Integer tryEquipSpells(IdleCmdPrompt p) {
        // Anti-2-for-1 discipline: NEVER equip in Main 2 or Turn 1 when we cannot attack with the boost!
        if (state.isMain2() || !p.canBattle || state.turnCount == 1) {
            return null;
        }
        List<RpgAiDuelState.CardSlot> oppMons = state.getMonsters(1);
        int oppBackrow = countFaceDownBackrow(1);

        for (CmdEntry e : p.activatable) {
            if (!RpgAiCardKnowledge.isEquipSpell(e.code)) continue;
            RpgAiDuelState.CardSlot target = findBestFriendlyAttackEquipTarget(e.code);
            if (target == null) continue;

            int bonus = Math.max(300, RpgAiCardKnowledge.getEquipAtkBonus(e.code));
            // 1) Secures immediate lethal on an open board
            if (oppMons.isEmpty() && state.getTotalFaceUpAtk(0) + bonus >= state.lp[1]) {
                return activateIdle(e);
            }
            // 2) Flips a losing/tied combat matchup into a clean win (or crosses face-down poke threshold)
            boolean flipsCombat = false;
            for (RpgAiDuelState.CardSlot opp : oppMons) {
                int oppVal = opp.effectiveCombatValue(state.eraEstimatedFaceDownDef);
                if (opp.isFaceUp()) {
                    if (target.atk <= oppVal && target.atk + bonus > oppVal) {
                        flipsCombat = true;
                        break;
                    }
                } else {
                    int pokeThreshold = Math.max(900, state.eraEstimatedFaceDownDef + 100);
                    if (target.atk < pokeThreshold && target.atk + bonus >= pokeThreshold) {
                        flipsCombat = true;
                        break;
                    }
                }
            }
            if (flipsCombat) {
                return activateIdle(e);
            }
            // 3) Dedicated Equip Beatdown style pushing tempo when opponent has no set backrow
            if (isEquipStyle() && oppBackrow == 0 && target.atk < state.eraBossAtk) {
                return activateIdle(e);
            }
        }
        return null;
    }

    private RpgAiDuelState.CardSlot findBestFriendlyAttackEquipTarget(int equipCode) {
        RpgAiDuelState.CardSlot best = null;
        for (RpgAiDuelState.CardSlot m : state.getMonsters(0)) {
            if (!m.isFaceUp() || !m.isAttackPos()) continue;
            int attr = m.attribute != 0 ? m.attribute : state.getCardAttrMask(m.code);
            int race = m.race != 0 ? m.race : state.getCardRaceMask(m.code);
            if (!RpgAiCardKnowledge.canEquipTarget(equipCode, attr, race)) continue;
            if (best == null || m.atk > best.atk) {
                best = m;
            }
        }
        return best;
    }

    private Integer tryReposition(IdleCmdPrompt p) {
        if (p.reposable.isEmpty()) return null;
        int oppStrongest = state.getStrongestThreatValue(1);
        int attackFloor = Math.min(1000, state.eraTypicalLowerAtk);
        for (CmdEntry e : p.reposable) {
            RpgAiDuelState.CardSlot slot = state.getCard(0, e.location, e.sequence);
            if (slot == null || slot.isFaceDown()) continue; // face-downs already handled in tryFlipSummon

            if (slot.isDefensePos()) {
                // Switch DEF -> ATK in Main 1 only if it can profitably attack!
                if (!state.isMain2() && p.canBattle && slot.atk >= attackFloor && canMonsterAttackProfitably(slot.atk)) {
                    return reposIdle(e);
                }
            } else if (slot.isAttackPos()) {
                // Switch weak/flipped monsters (Magician of Faith, Man-Eater Bug, Sangan, low ATK wall) to DEF
                boolean isWeakUtility = slot.atk < state.eraTypicalLowerAtk && slot.def > slot.atk;
                boolean outclassedWall = slot.def > slot.atk && oppStrongest > slot.atk;
                if ((state.isMain2() || !canMonsterAttackProfitably(slot.atk)) && (isWeakUtility || outclassedWall)) {
                    return reposIdle(e);
                }
            }
        }
        return null;
    }

    private Integer tryUtilitySpellsAndEffects(IdleCmdPrompt p) {
        // Burn spells (hold low-impact Sparks for a 1-card Trap Hole bluff when we have 0 backrow & no real traps;
        // note lethal Sparks already fired in tryImmediateLethal!)
        boolean wantBluffSparks = !isBurnStyle() && !isExodiaStyle()
                && countFaceDownBackrow(0) == 0 && !hasRealTrapInSsetable(p);
        for (CmdEntry e : p.activatable) {
            if (RpgAiCardKnowledge.getBurnDamage(e.code) > 0) {
                if (e.code == RpgAiCardKnowledge.SPARKS && wantBluffSparks) continue;
                return activateIdle(e);
            }
        }
        // Heal spells (when damaged, in burn decks, or in MILL_MORPHING to lock in 1999 deck-out LP lead!)
        for (CmdEntry e : p.activatable) {
            if (RpgAiCardKnowledge.isHealSpell(e.code)) {
                if (state.lp[0] < 8000 || isBurnStyle() || isMillStyle()) {
                    return activateIdle(e);
                }
            }
        }
        // Swords of Revealing Light: activate when opponent has threats/face-downs or in defensive decks
        CmdEntry swords = findActivatable(p, RpgAiCardKnowledge.SWORDS_OF_REVEALING_LIGHT);
        if (swords != null) {
            boolean oppHasMonsters = !state.getMonsters(1).isEmpty();
            boolean behind = state.getStrongestThreatValue(1) >= state.getStrongestAtk(0);
            boolean needStall = isExodiaStyle() || isBurnStyle() || isMillStyle() || state.lp[0] <= 3000;
            if ((oppHasMonsters && (behind || hasFaceDownMonster(1) || needStall))
                    || (needStall && state.getMonsters(0).isEmpty())) {
                return activateIdle(swords);
            }
        }

        // Cannon Soldier non-lethal utility: tribute stolen monster in M2, or tribute Sangan/Witch in Exodia/Burn!
        CmdEntry cannon = findActivatable(p, RpgAiCardKnowledge.CANNON_SOLDIER);
        if (cannon != null) {
            if (state.isMain2() && state.hasStolenMonsterOnMyField()) {
                return activateIdle(cannon);
            }
            if ((isExodiaStyle() || isBurnStyle())
                    && (hasMonsterOnField(0, RpgAiCardKnowledge.SANGAN)
                    || hasMonsterOnField(0, RpgAiCardKnowledge.WITCH_OF_BLACK_FOREST))) {
                return activateIdle(cannon);
            }
        }

        // Monster Eye: pay 1000 LP to recover Polymerization if we have LP > 2500
        CmdEntry monEye = findActivatable(p, RpgAiCardKnowledge.MONSTER_EYE);
        if (monEye != null && state.lp[0] > 2500) {
            return activateIdle(monEye);
        }

        return null;
    }

    private Integer trySetTraps(IdleCmdPrompt p) {
        if (p.ssetable.isEmpty() || state.getFreeMainSzoneCount(0) <= 0) return null;
        // 1) Always prioritize real Traps first
        for (CmdEntry e : p.ssetable) {
            RpgCard card = state.lookupCard(e.code);
            boolean isTrap = (card != null && "trap".equalsIgnoreCase(card.frameType))
                    || isKnownTrap(e.code);
            if (!isTrap) continue;
            // Don't set Royal Decree if we're playing Crush Card Virus trap-heavy deck
            if (e.code == RpgAiCardKnowledge.ROYAL_DECREE && isDarkVirusStyle()) {
                continue;
            }
            return (e.index << 16) + 4;
        }

        // 2) Classic 1999 OCG Bluff Setting ("ブラフ伏せ"):
        //    If we have 0 face-down backrow and no real Traps, set 1 low-opportunity-cost Spell
        //    (e.g. unused Equip Spell, Stop Defense, full-LP Heal, or Sparks) to threaten Trap Hole
        //    and deter opponent from slamming Dark Magician / Gaia / Blue-Eyes!
        //    NEVER bluff-set core power Spells (Dark Hole, Raigeki, Fissure, Monster Reborn, etc.).
        if (countFaceDownBackrow(0) == 0
                && !isExodiaStyle()
                && !state.hasCardInHand(RpgAiCardKnowledge.HEAVY_STORM)) {
            CmdEntry bestBluff = null;
            int bestBluffScore = 0;
            for (CmdEntry e : p.ssetable) {
                if (!RpgAiCardKnowledge.isSafeBluffSpell(e.code)) continue;
                int score = 100;
                if (RpgAiCardKnowledge.isEquipSpell(e.code)) {
                    // Dead/unmatched Equip Spell is the ideal zero-cost bluff!
                    score = state.hasEligibleFriendlyEquipTarget(e.code) ? 120 : 200;
                } else if (e.code == RpgAiCardKnowledge.SPARKS) {
                    score = 180;
                } else if (e.code == RpgAiCardKnowledge.STOP_DEFENSE) {
                    score = 160;
                } else if (RpgAiCardKnowledge.isHealSpell(e.code) && state.lp[0] >= 8000) {
                    score = 150;
                }
                if (score > bestBluffScore) {
                    bestBluffScore = score;
                    bestBluff = e;
                }
            }
            if (bestBluff != null) {
                return (bestBluff.index << 16) + 4;
            }
        }
        return null;
    }

    private boolean hasRealTrapInSsetable(IdleCmdPrompt p) {
        for (CmdEntry e : p.ssetable) {
            RpgCard card = state.lookupCard(e.code);
            boolean isTrap = (card != null && "trap".equalsIgnoreCase(card.frameType))
                    || isKnownTrap(e.code);
            if (isTrap) return true;
        }
        return false;
    }

    private int countFaceDownBackrow(int localPlayer) {
        int count = 0;
        for (int i = 0; i < 5; i++) {
            RpgAiDuelState.CardSlot s = state.szone[localPlayer][i];
            if (s != null && (s.isFaceDown() || !s.isFaceUp())) {
                count++;
            }
        }
        return count;
    }

    private int finishMainPhase(IdleCmdPrompt p) {
        if (p.canBattle && hasProfitableAttackAvailable()) {
            return 6; // Enter Battle Phase
        }
        return p.canEnd ? 7 : (p.canBattle ? 6 : 7);
    }

    // =========================================================================
    // MSG_SELECT_BATTLECMD (Battle Phase)
    // =========================================================================

    public BattleCmdPrompt parseBattleCmd(ByteBuffer data) {
        BattleCmdPrompt p = new BattleCmdPrompt();
        if (data.remaining() < 1) return p;
        data.get(); // selecting_player
        int actCount = data.remaining() >= 1 ? (data.get() & 0xFF) : 0;
        for (int i = 0; i < actCount && data.remaining() >= 11; i++) {
            int rawCode = data.getInt();
            int flag = (rawCode & 0x80000000) != 0 ? 1 : 0;
            int code = rawCode & 0x7FFFFFFF;
            int con = data.get() & 0xFF;
            int loc = data.get() & 0xFF;
            int seq = data.get() & 0xFF;
            int desc = data.getInt();
            p.activatable.add(new CmdEntry(code, con, state.localPlayer(con & 1), loc, seq, desc, flag, i));
        }
        int atkCount = data.remaining() >= 1 ? (data.get() & 0xFF) : 0;
        for (int i = 0; i < atkCount && data.remaining() >= 8; i++) {
            int code = data.getInt();
            int con = data.get() & 0xFF;
            int loc = data.get() & 0xFF;
            int seq = data.get() & 0xFF;
            int diratt = data.get() & 0xFF;
            p.attackable.add(new CmdEntry(code, con, state.localPlayer(con & 1), loc, seq, diratt, 0, i));
        }
        p.canMain2 = data.remaining() >= 1 && (data.get() & 0xFF) != 0;
        p.canEnd = data.remaining() >= 1 && (data.get() & 0xFF) != 0;
        return p;
    }

    public int chooseBattleCmd(BattleCmdPrompt p) {
        // Optional battle-phase trap activations (Metalmorph / Reinforcements) before attacking if needed
        for (CmdEntry e : p.activatable) {
            if (e.code == RpgAiCardKnowledge.METALMORPH || e.code == RpgAiCardKnowledge.REINFORCEMENTS) {
                if (!p.attackable.isEmpty()) {
                    pendingActivatingCode = e.code;
                    return e.index << 16; // BATTLECMD activate opcode = 0
                }
            }
        }

        if (!p.attackable.isEmpty()) {
            List<RpgAiDuelState.CardSlot> oppMons = state.getMonsters(1);
            if (oppMons.isEmpty()) {
                // Direct attack! Attack with smallest ATK first (classic habit)
                CmdEntry smallest = null;
                int minAtk = Integer.MAX_VALUE;
                for (CmdEntry a : p.attackable) {
                    int atk = getFieldMonsterAtk(0, a.sequence, a.code);
                    if (atk < minAtk) {
                        minAtk = atk;
                        smallest = a;
                    }
                }
                if (smallest != null) {
                    pendingAttackerSeq = smallest.sequence;
                    return (smallest.index << 16) + 1;
                }
            } else {
                // Find best profitable attack among attackable monsters
                CmdEntry bestAttacker = null;
                int bestPriority = Integer.MIN_VALUE;
                for (CmdEntry a : p.attackable) {
                    int myAtk = getFieldMonsterAtk(0, a.sequence, a.code);
                    int prio = evaluateBestAttackForMonster(a, myAtk, oppMons);
                    if (prio > bestPriority) {
                        bestPriority = prio;
                        bestAttacker = a;
                    }
                }
                if (bestAttacker != null && bestPriority > 0) {
                    pendingAttackerSeq = bestAttacker.sequence;
                    return (bestAttacker.index << 16) + 1;
                }
            }
        }

        pendingAttackerSeq = -1;
        // Go to Main Phase 2 if we have traps to set, stolen monster to Cannon, or weak monsters to flip to DEF
        if (p.canMain2 && shouldEnterMain2()) {
            return 2;
        }
        return p.canEnd ? 3 : (p.canMain2 ? 2 : 3);
    }

    private int evaluateBestAttackForMonster(CmdEntry attacker, int myAtk, List<RpgAiDuelState.CardSlot> oppMons) {
        int bestTargetScore = -1;
        boolean isSearcher = RpgAiCardKnowledge.isSearcherMonster(attacker.code);
        boolean isStolen = isStolenSlot(attacker.sequence);

        for (RpgAiDuelState.CardSlot opp : oppMons) {
            if (opp.isFaceUp() && opp.isAttackPos()) {
                if (myAtk > opp.atk) {
                    // Prefer using the smallest attacker that beats this target, saving big beaters for direct hits
                    int score = 2000 + opp.atk - (myAtk - opp.atk) / 4;
                    if (opp.atk <= 1000 && opp.code == RpgAiCardKnowledge.WALL_OF_ILLUSION) score -= 400;
                    bestTargetScore = Math.max(bestTargetScore, score);
                } else if (myAtk == opp.atk) {
                    // Crash trade if stolen, searcher, or we have more monsters
                    if (isStolen || isSearcher || state.getMonsters(0).size() > oppMons.size()) {
                        bestTargetScore = Math.max(bestTargetScore, 800 + opp.atk / 2);
                    }
                } else {
                    // Suicide ONLY for Exodia/Virus Sangan/Witch search if LP is safe, or stolen monster crash
                    if (isStolen && (opp.atk - myAtk) < state.lp[0] - 1500) {
                        bestTargetScore = Math.max(bestTargetScore, 300);
                    }
                }
            } else if (opp.isFaceUp() && opp.isDefensePos()) {
                if (myAtk > opp.def) {
                    int score = 1500 + opp.def / 2;
                    if (opp.code == RpgAiCardKnowledge.WALL_OF_ILLUSION) score -= 500;
                    bestTargetScore = Math.max(bestTargetScore, score);
                }
            } else {
                // Face-down defense monster: poke if our ATK beats the era's typical Set DEF
                int threshold = Math.max(900, state.eraEstimatedFaceDownDef + 100);
                if (myAtk >= threshold || isStolen) {
                    int score = 900 + Math.min(myAtk, 2500) / 4;
                    bestTargetScore = Math.max(bestTargetScore, score);
                }
            }
        }
        return bestTargetScore;
    }

    private boolean shouldEnterMain2() {
        // Check if we have traps in hand to set
        for (RpgAiDuelState.CardSlot c : state.hand[0]) {
            if (c != null && (c.isTrap() || isKnownTrap(c.code)) && state.getFreeMainSzoneCount(0) > 0) {
                return true;
            }
        }
        // Check if opponent still has a monster after Battle Phase and we hold removal (e.g. kill small monster in BP -> Fissure isolated Boss in M2!)
        if (!state.getMonsters(1).isEmpty()) {
            for (RpgAiDuelState.CardSlot c : state.hand[0]) {
                if (c == null) continue;
                if (c.code == RpgAiCardKnowledge.FISSURE
                        || c.code == RpgAiCardKnowledge.RAIGEKI
                        || c.code == RpgAiCardKnowledge.DARK_HOLE
                        || c.code == RpgAiCardKnowledge.TRIBUTE_TO_THE_DOOMED) {
                    return true;
                }
            }
        }
        // Check if we have a stolen monster & Cannon Soldier or tribute monster
        if (state.hasStolenMonsterOnMyField()) return true;
        // Check if we have a monster we haven't summoned yet
        if (!state.normalSummonedThisTurn) return true;
        return false;
    }

    // =========================================================================
    // MSG_SELECT_CHAIN (Traps, Quick-Plays, Triggers)
    // =========================================================================

    public int chooseChain(ByteBuffer data) {
        if (data.remaining() < 11) return -1;
        data.get(); // selecting_player
        int count = data.get() & 0xFF;
        data.get(); // specount
        data.getInt(); // hint0
        data.getInt(); // hint1

        List<ChainItem> items = new ArrayList<>();
        boolean anyForced = false;
        for (int i = 0; i < count && data.remaining() >= 14; i++) {
            int flag = data.get() & 0xFF;
            boolean forced = (data.get() & 0xFF) != 0;
            if (forced) anyForced = true;
            int code = data.getInt();
            int ctrl = data.get() & 0xFF;
            int loc = data.get() & 0xFF;
            int seq = data.get() & 0xFF;
            int subSeq = data.get() & 0xFF;
            int desc = data.getInt();
            items.add(new ChainItem(flag, forced, code, ctrl, state.localPlayer(ctrl & 1), loc, seq, subSeq, desc, i));
        }

        if (items.isEmpty()) return -1;

        // First: if any forced trigger exists, pick the first forced one
        if (anyForced) {
            for (ChainItem it : items) {
                if (it.forced) {
                    pendingActivatingCode = it.code;
                    return it.index;
                }
            }
            pendingActivatingCode = items.get(0).code;
            return items.get(0).index;
        }

        // Evaluate optional chains tactically
        ChainItem best = null;
        int bestScore = 0;
        for (ChainItem it : items) {
            int score = scoreChainItem(it);
            if (score > bestScore) {
                bestScore = score;
                best = it;
            }
        }
        if (best != null) {
            pendingActivatingCode = best.code;
            return best.index;
        }
        return -1; // Pass
    }

    /**
     * Returns true if we already have an activation of one of {@code codes} on the current chain
     * that has NOT been threatened with negation by a later opponent chain link (e.g. Seven Tools,
     * Solemn Judgment, Royal Decree, Magic Jammer). Prevents double-firing 2 Trap Holes, 2 Mirror
     * Forces, Mirror Force + Waboku, etc. in the same chain!
     */
    private boolean hasActiveUnnegatedMyChain(int... codes) {
        int n = state.currentChain.size();
        for (int i = 0; i < n; i++) {
            RpgAiDuelState.ChainLink link = state.currentChain.get(i);
            if (link.controller != 0) continue;
            boolean match = false;
            for (int c : codes) {
                if (link.code == c) {
                    match = true;
                    break;
                }
            }
            if (!match) continue;

            boolean negatedByOpponent = false;
            for (int j = i + 1; j < n; j++) {
                RpgAiDuelState.ChainLink later = state.currentChain.get(j);
                if (later.controller == 1 && isOpponentNegateTrapOrCounter(later.code)) {
                    negatedByOpponent = true;
                    break;
                }
            }
            if (!negatedByOpponent) {
                return true;
            }
        }
        return false;
    }

    private boolean isOpponentNegateTrapOrCounter(int code) {
        return code == RpgAiCardKnowledge.SEVEN_TOOLS
                || code == RpgAiCardKnowledge.SOLEMN_JUDGMENT
                || code == RpgAiCardKnowledge.ROYAL_DECREE
                || code == RpgAiCardKnowledge.MAGIC_JAMMER;
    }

    private boolean isLikelyTrapCode(int code) {
        if (isKnownTrap(code)) return true;
        RpgCard c = state.lookupCard(code);
        return c != null && "trap".equalsIgnoreCase(c.frameType);
    }

    private boolean isMustTrapHoleEffectMonster(int code) {
        return code == RpgAiCardKnowledge.CANNON_SOLDIER
                || code == RpgAiCardKnowledge.CYBER_STEIN
                || code == RpgAiCardKnowledge.TIME_WIZARD
                || code == RpgAiCardKnowledge.WALL_OF_ILLUSION;
    }

    /**
     * Tactically evaluates whether to fire Trap Hole immediately on the opponent's just-summoned
     * monster or hold it for a bigger future boss (e.g. Dark Magician / Gaia / Blue-Eyes / Summoned Skull).
     */
    private int scoreTrapHoleActivation() {
        int targetCode = state.lastOppSummonedCode;
        // Fallback when summon event wasn't observed (e.g. synthetic unit prompt)
        if (targetCode == 0) return 850;

        int targetAtk = state.lastOppSummonedAtk > 0
                ? state.lastOppSummonedAtk
                : state.getCardBaseAtk(targetCode);
        int targetLv = state.lastOppSummonedLevel > 0
                ? state.lastOppSummonedLevel
                : state.getCardLevel(targetCode);

        // Compute opponent's total attacking power including the newly summoned monster
        int oppTotalAtk = state.getTotalFaceUpAtk(1);
        RpgAiDuelState.CardSlot summonedSlot = (state.lastOppSummonedSeq >= 0 && state.lastOppSummonedSeq < 7)
                ? state.mzone[1][state.lastOppSummonedSeq] : null;
        if (summonedSlot == null || summonedSlot.code != targetCode || !summonedSlot.isAttackPos()) {
            oppTotalAtk += targetAtk;
        }

        // 1. Always stop immediate lethal or dangerous field/ignition effect monsters!
        if (oppTotalAtk >= state.lp[0]) return 920;
        if (isMustTrapHoleEffectMonster(targetCode)) return 900;

        // 2. Avoid wasting Trap Hole on Sangan / Witch (triggers their free deck search) unless LP is critical
        if (RpgAiCardKnowledge.isSearcherMonster(targetCode) && state.lp[0] > targetAtk + 1500) {
            return 0;
        }

        // 3. Always fire on era boss-tier monsters (e.g. Dark Magician 2500, Gaia 2300, Blue-Eyes 3000, Summoned Skull 2500)
        //    or any Tribute-Summoned Lv5+ monster (massive 2-for-1 / 3-for-1 tempo swing!)
        if (targetAtk >= state.eraBossAtk
                || (state.isTributeSummonEnabled() && targetLv >= 5)) {
            return 900;
        }

        int myBestFieldAtk = state.getStrongestAtk(0);
        int myBestFieldDef = state.getStrongestFaceUpDef(0);
        int myBestHandAtk = state.getBestPlayableHandMonsterAtk();
        boolean myBoardEmpty = state.getMonsters(0).isEmpty();
        int oppOtherMaxAtk = 0;
        for (RpgAiDuelState.CardSlot m : state.getMonsters(1)) {
            if (m.sequence == state.lastOppSummonedSeq && m.code == targetCode) continue;
            if (m.isFaceUp() && m.isAttackPos()) {
                oppOtherMaxAtk = Math.max(oppOtherMaxAtk, m.atk);
            }
        }

        // 4. If we ALREADY control a strictly stronger face-up monster on field (e.g. Dark Magician 2500 vs Mammoth 1200)
        //    and opponent can't break our field monster with another attacker -> HOLD Trap Hole for their boss!
        if (myBestFieldAtk > targetAtk && oppOtherMaxAtk <= myBestFieldAtk) {
            return 0;
        }

        // 5. If we control a solid face-up Defense Wall that blocks this monster and opponent has no bigger attacker,
        //    HOLD Trap Hole (if they tribute it later, Trap Hole punishes their boss 2-for-1!)
        if (myBestFieldDef > targetAtk && oppOtherMaxAtk <= myBestFieldDef && state.lp[0] > 2000) {
            return 0;
        }

        // 6. Stall/combo styles (Exodia / Burn) don't rely on battle beatdowns: clear incoming attackers!
        if (isExodiaStyle() || isBurnStyle()) {
            return 850;
        }

        // 7. If opponent only has this single lower monster, our LP is healthy, and we hold a clearly stronger
        //    playable monster in hand (e.g. Dark Magician 2500 / Gaia 2300 in Vol.1 vs Mammoth 1200, or an 1800
        //    beater vs a 1200 monster in late 1999) that will beat over it next turn for free +1 card advantage,
        //    HOLD Trap Hole to snipe their real boss!
        int potentialDamage = myBoardEmpty ? oppTotalAtk : Math.max(0, oppTotalAtk - myBestFieldAtk);
        boolean lpSafe = (state.lp[0] - potentialDamage >= 3000) && (potentialDamage <= state.lp[0] / 3);
        if (lpSafe && oppOtherMaxAtk == 0
                && myBestHandAtk >= targetAtk + 400
                && targetAtk <= state.eraTopLowerAtk) {
            return 0;
        }

        return 850;
    }

    private int scoreChainItem(ChainItem it) {
        int code = it.code;
        RpgAiDuelState.ChainLink top = state.currentChain.isEmpty()
                ? null : state.currentChain.get(state.currentChain.size() - 1);
        boolean oppTopChain = top != null && top.controller == 1;

        switch (code) {
            case RpgAiCardKnowledge.CRUSH_CARD_VIRUS:
                // Never double-chain two Crush Card Viruses on the same chain
                if (hasActiveUnnegatedMyChain(RpgAiCardKnowledge.CRUSH_CARD_VIRUS)) return 0;
                // Always fire Crush Card Virus! Especially devastating in 1999!
                return 1000;

            case RpgAiCardKnowledge.MIRROR_FORCE:
                // Never stack Mirror Force with another Mirror Force, Mirror Wall, Negate Attack, or Waboku
                if (hasActiveUnnegatedMyChain(
                        RpgAiCardKnowledge.MIRROR_FORCE,
                        RpgAiCardKnowledge.MIRROR_WALL,
                        RpgAiCardKnowledge.NEGATE_ATTACK,
                        RpgAiCardKnowledge.WABOKU)) {
                    return 0;
                }
                return 950;

            case RpgAiCardKnowledge.MIRROR_WALL:
                if (hasActiveUnnegatedMyChain(
                        RpgAiCardKnowledge.MIRROR_FORCE,
                        RpgAiCardKnowledge.MIRROR_WALL,
                        RpgAiCardKnowledge.NEGATE_ATTACK,
                        RpgAiCardKnowledge.WABOKU)) {
                    return 0;
                }
                if (!state.isMyTurn() && state.isBattlePhase() && state.lastAttackerLocalCtrl == 1) {
                    return 910;
                }
                return 0;

            case RpgAiCardKnowledge.NEGATE_ATTACK:
            case RpgAiCardKnowledge.MAGICAL_HATS:
                if (hasActiveUnnegatedMyChain(
                        RpgAiCardKnowledge.MIRROR_FORCE,
                        RpgAiCardKnowledge.MIRROR_WALL,
                        RpgAiCardKnowledge.NEGATE_ATTACK,
                        RpgAiCardKnowledge.MAGICAL_HATS,
                        RpgAiCardKnowledge.WABOKU)) {
                    return 0;
                }
                if (!state.isMyTurn() && state.isBattlePhase() && state.lastAttackerLocalCtrl == 1) {
                    return 840;
                }
                return 0;

            case RpgAiCardKnowledge.SHADOW_SPELL:
            case RpgAiCardKnowledge.SPELLBINDING_CIRCLE:
                if (hasActiveUnnegatedMyChain(
                        RpgAiCardKnowledge.SHADOW_SPELL,
                        RpgAiCardKnowledge.SPELLBINDING_CIRCLE,
                        RpgAiCardKnowledge.MIRROR_FORCE)) {
                    return 0;
                }
                if (!state.isMyTurn() && state.getStrongestAtk(1) >= state.eraTypicalLowerAtk) {
                    return 810;
                }
                return 0;

            case RpgAiCardKnowledge.CALL_OF_THE_HAUNTED:
                if (hasActiveUnnegatedMyChain(RpgAiCardKnowledge.CALL_OF_THE_HAUNTED)) return 0;
                if (state.getFreeMainMzoneCount(0) > 0 && !state.grave[0].isEmpty()) {
                    if (!state.isMyTurn() && state.isBattlePhase()) return 860;
                    if (state.isMyTurn()) return 780;
                }
                return 0;

            case RpgAiCardKnowledge.TRAP_HOLE:
                // Never waste a second Trap Hole on the same summon when ours is already resolving!
                if (hasActiveUnnegatedMyChain(
                        RpgAiCardKnowledge.TRAP_HOLE,
                        RpgAiCardKnowledge.ACID_TRAP_HOLE,
                        RpgAiCardKnowledge.CRUSH_CARD_VIRUS)) {
                    return 0;
                }
                return scoreTrapHoleActivation();

            case RpgAiCardKnowledge.ACID_TRAP_HOLE:
                if (hasActiveUnnegatedMyChain(
                        RpgAiCardKnowledge.TRAP_HOLE,
                        RpgAiCardKnowledge.ACID_TRAP_HOLE,
                        RpgAiCardKnowledge.CRUSH_CARD_VIRUS)) {
                    return 0;
                }
                return 850;

            case RpgAiCardKnowledge.SOLEMN_JUDGMENT:
                // Only negate high-impact opponent cards/summons or lethal threats!
                if (oppTopChain && RpgAiCardKnowledge.HIGH_THREAT_SPELLS_TO_NEGATE.contains(top.code)) {
                    return state.lp[0] >= 1000 ? 900 : 0;
                }
                if (!state.isMyTurn() && state.currentChain.isEmpty()) {
                    // Negating a big monster summon (calibrated to boss/high-threat threshold)
                    int summonCode = state.lastOppSummonedCode;
                    int summonAtk = summonCode != 0 ? state.lastOppSummonedAtk : state.getStrongestAtk(1);
                    if (summonAtk >= state.eraBossAtk
                            || isMustTrapHoleEffectMonster(summonCode)
                            || (summonAtk >= state.eraHighThreatAtk && summonAtk > state.getStrongestAtk(0) && state.lp[0] <= 4000)
                            || state.lp[0] <= 2500) {
                        return 750;
                    }
                }
                return 0;

            case RpgAiCardKnowledge.SEVEN_TOOLS:
                if (oppTopChain && state.lp[0] > 1500) {
                    return 880;
                }
                return 0;

            case RpgAiCardKnowledge.MAGIC_JAMMER:
                if (oppTopChain && RpgAiCardKnowledge.HIGH_THREAT_SPELLS_TO_NEGATE.contains(top.code)
                        && !state.hand[0].isEmpty()) {
                    return 870;
                }
                return 0;

            case RpgAiCardKnowledge.ROYAL_DECREE:
                if (oppTopChain && isLikelyTrapCode(top.code)
                        && !hasActiveUnnegatedMyChain(RpgAiCardKnowledge.ROYAL_DECREE)) {
                    return 820;
                }
                return 0;

            case RpgAiCardKnowledge.WABOKU:
                // Never double-chain Waboku or stack Waboku on top of our own Mirror Force
                if (hasActiveUnnegatedMyChain(
                        RpgAiCardKnowledge.WABOKU,
                        RpgAiCardKnowledge.MIRROR_FORCE)) {
                    return 0;
                }
                // ONLY activate Waboku during opponent's Battle Phase when being attacked!
                if (!state.isMyTurn() && state.isBattlePhase()) {
                    if (state.lastAttackerLocalCtrl == 1) {
                        return 800;
                    }
                }
                // Or if opponent is about to destroy our Waboku with Feather Duster/Heavy Storm
                if (oppTopChain && (top.code == RpgAiCardKnowledge.HARPIES_FEATHER_DUSTER
                        || top.code == RpgAiCardKnowledge.HEAVY_STORM)
                        && !state.isMyTurn() && !state.getMonsters(1).isEmpty()) {
                    return 700;
                }
                return 0;

            case RpgAiCardKnowledge.KUNAI_WITH_CHAIN:
                // Iconic 1999 DM2 battle trap: stop opponent's attack (switch to DEF) and/or buff +500 ATK!
                if (hasActiveUnnegatedMyChain(
                        RpgAiCardKnowledge.KUNAI_WITH_CHAIN,
                        RpgAiCardKnowledge.METALMORPH,
                        RpgAiCardKnowledge.REINFORCEMENTS,
                        RpgAiCardKnowledge.MIRROR_FORCE,
                        RpgAiCardKnowledge.WABOKU)) {
                    return 0;
                }
                if (state.isBattlePhase()) {
                    if (!state.isMyTurn() && state.lastAttackerLocalCtrl == 1) {
                        return 830;
                    }
                    if (state.isMyTurn() && !state.getMonsters(0).isEmpty() && !state.getMonsters(1).isEmpty()) {
                        return 770;
                    }
                }
                return 0;

            case RpgAiCardKnowledge.CASTLE_WALLS:
                if (hasActiveUnnegatedMyChain(
                        RpgAiCardKnowledge.CASTLE_WALLS,
                        RpgAiCardKnowledge.MIRROR_FORCE,
                        RpgAiCardKnowledge.WABOKU)) {
                    return 0;
                }
                // Only use +500 DEF when opponent is attacking one of our Defense monsters
                if (!state.isMyTurn() && state.isBattlePhase()
                        && state.lastAttackerLocalCtrl == 1
                        && state.lastDefenderLocalCtrl == 0
                        && state.lastDefenderSeq >= 0 && state.lastDefenderSeq < 7) {
                    RpgAiDuelState.CardSlot defSlot = state.mzone[0][state.lastDefenderSeq];
                    if (defSlot != null && defSlot.isDefensePos()) {
                        return 720;
                    }
                }
                return 0;

            case RpgAiCardKnowledge.JUST_DESSERTS: {
                if (hasActiveUnnegatedMyChain(RpgAiCardKnowledge.JUST_DESSERTS)) return 0;
                int oppCount = state.getMonsters(1).size();
                if (oppCount == 0) return 0;
                int burn = oppCount * 500;
                if (burn >= state.lp[1]) return 960; // immediate lethal!
                if (oppCount >= 2) return 780;
                if (oppTopChain && (top.code == RpgAiCardKnowledge.HARPIES_FEATHER_DUSTER
                        || top.code == RpgAiCardKnowledge.HEAVY_STORM)) {
                    return 720; // chain before destruction for free 500 burn
                }
                return 0;
            }

            case RpgAiCardKnowledge.HORN_OF_HEAVEN: {
                if (hasActiveUnnegatedMyChain(
                        RpgAiCardKnowledge.HORN_OF_HEAVEN,
                        RpgAiCardKnowledge.SOLEMN_JUDGMENT)) {
                    return 0;
                }
                // Tribute 1 monster to negate a summon: only worth it against a real Boss/threat
                // when we have a weaker/expendable monster (or Sangan/Witch) to tribute!
                if (!state.isMyTurn() && state.currentChain.isEmpty() && !state.getMonsters(0).isEmpty()) {
                    int summonCode = state.lastOppSummonedCode;
                    int summonAtk = summonCode != 0 ? state.lastOppSummonedAtk : state.getStrongestAtk(1);
                    int weakestMyVal = Integer.MAX_VALUE;
                    boolean hasSearcher = false;
                    for (RpgAiDuelState.CardSlot m : state.getMonsters(0)) {
                        if (RpgAiCardKnowledge.isSearcherMonster(m.code)) hasSearcher = true;
                        weakestMyVal = Math.min(weakestMyVal, m.effectiveCombatValue(state.eraEstimatedFaceDownDef));
                    }
                    boolean worthNegating = summonAtk >= state.eraBossAtk
                            || isMustTrapHoleEffectMonster(summonCode)
                            || (summonAtk >= state.eraTopLowerAtk && (hasSearcher || weakestMyVal + 400 <= summonAtk));
                    if (worthNegating && (hasSearcher || weakestMyVal < summonAtk)) {
                        return 790;
                    }
                }
                return 0;
            }

            case RpgAiCardKnowledge.METALMORPH:
            case RpgAiCardKnowledge.REINFORCEMENTS:
                if (hasActiveUnnegatedMyChain(
                        RpgAiCardKnowledge.METALMORPH,
                        RpgAiCardKnowledge.REINFORCEMENTS,
                        RpgAiCardKnowledge.KUNAI_WITH_CHAIN,
                        RpgAiCardKnowledge.MIRROR_FORCE,
                        RpgAiCardKnowledge.WABOKU)) {
                    return 0;
                }
                if (state.isBattlePhase() && !state.getMonsters(0).isEmpty()) {
                    return 760;
                }
                return 0;

            case RpgAiCardKnowledge.TWO_PRONGED_ATTACK:
                if (hasActiveUnnegatedMyChain(
                        RpgAiCardKnowledge.TWO_PRONGED_ATTACK,
                        RpgAiCardKnowledge.TRAP_HOLE,
                        RpgAiCardKnowledge.MIRROR_FORCE)) {
                    return 0;
                }
                // Only if opponent has an era-defining boss monster and we have >= 2 expendable monsters
                int bossThreshold = Math.max(state.eraHighThreatAtk, state.eraTopLowerAtk + 400);
                if (state.getStrongestThreatValue(1) >= bossThreshold
                        && state.getStrongestThreatValue(1) > state.getStrongestAtk(0)
                        && state.getMonsters(0).size() >= 2) {
                    return 600;
                }
                return 0;

            case RpgAiCardKnowledge.MAGICIAN_OF_FAITH:
                return state.hasGoodSpellInMyGrave() ? 900 : 0;
            case RpgAiCardKnowledge.MASK_OF_DARKNESS:
                return state.hasGoodTrapInMyGrave() ? 900 : 0;
            case RpgAiCardKnowledge.SANGAN:
            case RpgAiCardKnowledge.WITCH_OF_BLACK_FOREST:
            case RpgAiCardKnowledge.LAST_WILL:
            case RpgAiCardKnowledge.SKELENGEL:
            case RpgAiCardKnowledge.NEEDLE_WORM:
            case RpgAiCardKnowledge.WALL_OF_ILLUSION:
                return 920;
            case RpgAiCardKnowledge.MAN_EATER_BUG:
            case RpgAiCardKnowledge.HANE_HANE:
            case RpgAiCardKnowledge.PENGUIN_SOLDIER:
                return !state.getMonsters(1).isEmpty() ? 860 : 0;
            case RpgAiCardKnowledge.ARMED_NINJA:
            case RpgAiCardKnowledge.REAPER_OF_THE_CARDS:
            case RpgAiCardKnowledge.TRAP_MASTER:
                return !state.getSpellTraps(1).isEmpty() ? 840 : 0;
            case RpgAiCardKnowledge.MORPHING_JAR:
                if (isExodiaStyle() && state.getUniqueExodiaPiecesInHand().size() >= 2) return 0;
                return state.hand[0].size() <= 3 ? 750 : 0;
            default:
                // Unknown trigger/quick effect: activate on opponent's turn only if chaining to something,
                // or if it's a monster trigger effect
                RpgCard c = state.lookupCard(code);
                if (c != null && c.isMonster()) return 400;
                return 0;
        }
    }

    // =========================================================================
    // MSG_SELECT_EFFECTYN & MSG_SELECT_YESNO
    // =========================================================================

    public int chooseEffectYn(ByteBuffer data) {
        if (data.remaining() < 13) return 1;
        data.get(); // player
        int code = data.getInt();
        // Sanga/Suijin/Kazejin damage-0 effect, Sangan/Witch/Last Will, Magician of Faith, etc.
        if (code == RpgAiCardKnowledge.MAN_EATER_BUG || code == RpgAiCardKnowledge.HANE_HANE
                || code == RpgAiCardKnowledge.PENGUIN_SOLDIER) {
            return !state.getMonsters(1).isEmpty() ? 1 : 0;
        }
        if (code == RpgAiCardKnowledge.MORPHING_JAR && isExodiaStyle()
                && state.getUniqueExodiaPiecesInHand().size() >= 2) {
            return 0;
        }
        pendingActivatingCode = code;
        return 1;
    }

    public int chooseYesNo(ByteBuffer data) {
        if (data.remaining() < 5) return 1;
        data.get(); // player
        int desc = data.getInt();
        // sys 210 ("Continue selecting?" / optional extra action) -> yes if beneficial
        if (desc == 210) return 0;
        return 1;
    }

    // =========================================================================
    // MSG_SELECT_CARD (Attack Target, Equip Target, Discard, Search, Reborn, Destroy, Bounce)
    // =========================================================================

    public byte[] chooseSelectCard(ByteBuffer data) {
        if (data.remaining() < 5) return intBytes(0);
        data.get(); // player
        boolean cancelable = (data.get() & 0xFF) != 0;
        int min = data.get() & 0xFF;
        int max = data.get() & 0xFF;
        int count = data.get() & 0xFF;

        List<SelectCardItem> items = new ArrayList<>();
        for (int i = 0; i < count && data.remaining() >= 8; i++) {
            int rawCode = data.getInt();
            int ctrl = data.get() & 0xFF;
            int loc = data.get() & 0xFF;
            int seq = data.get() & 0xFF;
            int subSeq = data.get() & 0xFF;
            int localCtrl = state.localPlayer(ctrl & 1);
            int code = rawCode;
            if (code == 0) {
                RpgAiDuelState.CardSlot s = state.getCard(localCtrl, loc, seq);
                if (s != null) code = s.code;
            }
            items.add(new SelectCardItem(code, ctrl, localCtrl, loc, seq, subSeq, i, 0));
        }
        if (items.isEmpty()) return intBytes(cancelable ? -1 : 0);

        int hint = state.lastSelectHint;
        int activeCode = pendingActivatingCode != 0 ? pendingActivatingCode : state.lastActivatingCode;

        List<Integer> chosenIndices = selectBestCards(items, min, max, hint, activeCode);
        state.lastSelectHint = 0;

        if (chosenIndices.isEmpty() && cancelable) {
            return intBytes(-1);
        }
        // Clamp count to [min, max]
        while (chosenIndices.size() < min) {
            for (SelectCardItem it : items) {
                if (!chosenIndices.contains(it.index)) {
                    chosenIndices.add(it.index);
                    if (chosenIndices.size() >= min) break;
                }
            }
            if (chosenIndices.size() < min) break;
        }
        while (chosenIndices.size() > max && max > 0) {
            chosenIndices.remove(chosenIndices.size() - 1);
        }

        byte[] resp = new byte[1 + chosenIndices.size()];
        resp[0] = (byte) chosenIndices.size();
        for (int i = 0; i < chosenIndices.size(); i++) {
            resp[1 + i] = (byte) chosenIndices.get(i).intValue();
        }
        return resp;
    }

    private List<Integer> selectBestCards(List<SelectCardItem> items, int min, int max, int hint, int activeCode) {
        boolean allMyHand = allMatch(items, 0, RpgAiDuelState.LOC_HAND);
        boolean allMyDeck = allMatch(items, 0, RpgAiDuelState.LOC_DECK);
        boolean allGrave = allInLocation(items, RpgAiDuelState.LOC_GRAVE);
        boolean allExtra = allInLocation(items, RpgAiDuelState.LOC_EXTRA);
        boolean allOppMzone = allMatch(items, 1, RpgAiDuelState.LOC_MZONE);
        boolean hasOppField = anyOnField(items, 1);
        boolean hasMyField = anyOnField(items, 0);

        // 1) Attack target selection in Battle Phase!
        if (state.isBattlePhase() && pendingAttackerSeq >= 0 && allOppMzone) {
            int attackerAtk = getFieldMonsterAtk(0, pendingAttackerSeq, 0);
            return pickTopN(items, min, (a, b) -> Integer.compare(
                    scoreAttackTarget(b, attackerAtk), scoreAttackTarget(a, attackerAtk)));
        }

        // 2) Equip Spell / Metalmorph / Reinforcements target: ALWAYS pick OUR OWN highest-ATK monster!
        if (RpgAiCardKnowledge.isEquipSpell(activeCode)
                || activeCode == RpgAiCardKnowledge.METALMORPH
                || activeCode == RpgAiCardKnowledge.REINFORCEMENTS
                || hint == 518) { // HINTMSG_EQUIP
            return pickTopN(items, min, (a, b) -> Integer.compare(
                    scoreFriendlyBuffTarget(b), scoreFriendlyBuffTarget(a)));
        }

        // 3) Discard from hand (Graceful Charity, Tribute to the Doomed, End Phase discard hint=501/504)
        if (allMyHand && (hint == 501 || hint == 504
                || activeCode == RpgAiCardKnowledge.GRACEFUL_CHARITY
                || activeCode == RpgAiCardKnowledge.TRIBUTE_TO_THE_DOOMED)) {
            return pickTopN(items, min, (a, b) -> Integer.compare(
                    scoreDiscardPriority(b), scoreDiscardPriority(a)));
        }

        // 4) Search / Special Summon from Deck (Sangan, Witch, Last Will, Fusion Sage)
        if (allMyDeck) {
            return pickTopN(items, min, (a, b) -> Integer.compare(
                    scoreDeckSearchTarget(b, activeCode), scoreDeckSearchTarget(a, activeCode)));
        }

        // 5) Extra Deck selection (Cyber-Stein / Polymerization)
        if (allExtra) {
            return pickTopN(items, min, (a, b) -> Integer.compare(
                    state.getCardBaseAtk(b.code), state.getCardBaseAtk(a.code)));
        }

        // 6) Graveyard selection (Monster Reborn, Magician of Faith, Mask of Darkness)
        if (allGrave) {
            return pickTopN(items, min, (a, b) -> Integer.compare(
                    scoreGraveTarget(b, activeCode), scoreGraveTarget(a, activeCode)));
        }

        // 7) Destroy / Bounce / Steal / Target on field (Man-Eater Bug, Penguin Soldier, Hane-Hane,
        //    Change of Heart, Tribute to the Doomed, Armed Ninja, Stop Defense, Two-Pronged Attack)
        if (hasOppField) {
            // For multi-target bounce like Penguin Soldier (min=1, max=2): ONLY select opponent's monsters,
            // do NOT select our own monsters beyond min!
            List<SelectCardItem> oppOnly = new ArrayList<>();
            for (SelectCardItem it : items) {
                if (it.localCtrl == 1) oppOnly.add(it);
            }
            if (!oppOnly.isEmpty()) {
                int want = Math.min(max, Math.max(min, oppOnly.size()));
                if (oppOnly.size() >= want) {
                    return pickTopN(oppOnly, want, (a, b) -> Integer.compare(
                            scoreEnemyRemovalTarget(b), scoreEnemyRemovalTarget(a)));
                }
            }
            return pickTopN(items, min, (a, b) -> Integer.compare(
                    scoreEnemyRemovalTarget(b), scoreEnemyRemovalTarget(a)));
        }

        // 8) Tribute/Cost from our own field (Cannon Soldier, Crush Card Virus, Two-Pronged Attack)
        if (hasMyField) {
            return pickTopN(items, min, (a, b) -> Integer.compare(
                    scoreTributeSacrificePriority(b), scoreTributeSacrificePriority(a)));
        }

        // Default: highest base ATK / value
        return pickTopN(items, min, (a, b) -> Integer.compare(
                state.getCardBaseAtk(b.code), state.getCardBaseAtk(a.code)));
    }

    private int scoreAttackTarget(SelectCardItem item, int attackerAtk) {
        RpgAiDuelState.CardSlot slot = state.getCard(item.localCtrl, item.location, item.sequence);
        if (slot == null) return 0;
        if (slot.isFaceUp() && slot.isAttackPos()) {
            if (attackerAtk > slot.atk) {
                // Maximize damage / remove biggest killable threat
                return 10000 + slot.atk;
            }
            if (attackerAtk == slot.atk) return 3000 + slot.atk;
            return -5000 - (slot.atk - attackerAtk);
        }
        if (slot.isFaceUp() && slot.isDefensePos()) {
            if (attackerAtk > slot.def) return 6000 + slot.def;
            return -4000 - (slot.def - attackerAtk);
        }
        // Face-down defense
        return 4500;
    }

    private int scoreFriendlyBuffTarget(SelectCardItem item) {
        if (item.localCtrl != 0) return -100000; // NEVER buff opponent's monster!
        RpgAiDuelState.CardSlot slot = state.getCard(0, item.location, item.sequence);
        int atk = slot != null ? slot.atk : state.getCardBaseAtk(item.code);
        int bonus = (slot != null && slot.isAttackPos()) ? 1000 : 0;
        return atk + bonus;
    }

    /** Higher score = MORE eager to discard. */
    private int scoreDiscardPriority(SelectCardItem item) {
        int code = item.code;
        // NEVER discard unique Exodia pieces!
        if (RpgAiCardKnowledge.isExodiaPiece(code)) {
            int copies = state.countInHand(code);
            return copies >= 2 ? 5000 : -100000;
        }
        // Pre-errata Sangan/Witch discarded by Graceful Charity/Tribute to the Doomed triggers search!
        if (state.isPreErrataSanganEra() && RpgAiCardKnowledge.isSearcherMonster(code)) {
            return isExodiaStyle() ? 4500 : 2200;
        }
        // High-level brick when Monster Reborn is in hand (classic Kaiba discard Blue-Eyes + Reborn!)
        if (state.hasCardInHand(RpgAiCardKnowledge.MONSTER_REBORN)
                && state.tributesNeededForLevel(state.getCardLevel(code)) >= 2) {
            return 2800;
        }
        // Never discard top-tier power spells
        if (code == RpgAiCardKnowledge.POT_OF_GREED) return -9000;
        if (code == RpgAiCardKnowledge.GRACEFUL_CHARITY) return -8500;
        if (code == RpgAiCardKnowledge.RAIGEKI) return -8000;
        if (code == RpgAiCardKnowledge.MONSTER_REBORN) return -7500;
        if (code == RpgAiCardKnowledge.CHANGE_OF_HEART) return -7000;
        if (code == RpgAiCardKnowledge.DARK_HOLE) return -6500;
        if (code == RpgAiCardKnowledge.HARPIES_FEATHER_DUSTER) return -6000;
        if (code == RpgAiCardKnowledge.LAST_WILL) return -5500;
        if (code == RpgAiCardKnowledge.CRUSH_CARD_VIRUS) return -6000;
        if (code == RpgAiCardKnowledge.SWORDS_OF_REVEALING_LIGHT) return -5000;

        // Unplayable high-tribute monster with no tributes on field
        int tributes = state.tributesNeededForLevel(state.getCardLevel(code));
        if (tributes > 0 && state.getMonsters(0).size() < tributes) {
            return 1800 - state.getCardBaseAtk(code) / 10;
        }

        // Duplicate cards are easier to discard
        int dupBonus = state.countInHand(code) >= 2 ? 400 : 0;
        int atk = state.getCardBaseAtk(code);
        return 1000 + dupBonus - atk / 2;
    }

    private int scoreDeckSearchTarget(SelectCardItem item, int activeCode) {
        int code = item.code;
        if (isExodiaStyle()) {
            Set<Integer> owned = state.getUniqueExodiaPiecesInHand();
            if (RpgAiCardKnowledge.isExodiaPiece(code)) {
                return !owned.contains(code) ? 100000 : 10;
            }
            if (code == RpgAiCardKnowledge.SANGAN || code == RpgAiCardKnowledge.WITCH_OF_BLACK_FOREST) return 5000;
            if (code == RpgAiCardKnowledge.MAGICIAN_OF_FAITH) return 4000;
            if (code == RpgAiCardKnowledge.SKELENGEL) return 3500;
        }
        if (isFusionStyle()) {
            if (code == RpgAiCardKnowledge.CYBER_STEIN && state.lp[0] > 5000) return 95000;
            if (code == RpgAiCardKnowledge.POLYMERIZATION) return 90000;
            if (RpgAiCardKnowledge.FUSION_SUBS.contains(code)) return 5000;
            if (code == RpgAiCardKnowledge.SANGAN || code == RpgAiCardKnowledge.WITCH_OF_BLACK_FOREST) return 4500;
        }
        if (isBurnStyle()) {
            if (code == RpgAiCardKnowledge.CANNON_SOLDIER) return 80000;
            if (code == RpgAiCardKnowledge.SANGAN || code == RpgAiCardKnowledge.WITCH_OF_BLACK_FOREST) return 6000;
            if (code == RpgAiCardKnowledge.MAGICIAN_OF_FAITH) return 5500;
        }
        if (isDarkVirusStyle()) {
            if (code == RpgAiCardKnowledge.MASK_OF_DARKNESS) return 75000;
            if (code == RpgAiCardKnowledge.SANGAN) return 70000;
            if (code == RpgAiCardKnowledge.SUMMONED_SKULL) return 65000;
        }
        if (activeCode == RpgAiCardKnowledge.LAST_WILL) {
            // Special Summon directly to field: prefer Cyber-Stein (if LP > 5000) or highest ATK/Sangan/Witch
            if (code == RpgAiCardKnowledge.CYBER_STEIN && state.lp[0] > 5000) return 95000;
            if (code == RpgAiCardKnowledge.SANGAN || code == RpgAiCardKnowledge.WITCH_OF_BLACK_FOREST) return 6000;
            return 2000 + state.getCardBaseAtk(code);
        }
        // General priority: Magician of Faith, Summoned Skull, Sangan, Witch, Man-Eater Bug, highest ATK
        if (code == RpgAiCardKnowledge.SUMMONED_SKULL) return 5500;
        if (code == RpgAiCardKnowledge.MAGICIAN_OF_FAITH && state.hasGoodSpellInMyGrave()) return 5200;
        if (code == RpgAiCardKnowledge.SANGAN || code == RpgAiCardKnowledge.WITCH_OF_BLACK_FOREST) return 4800;
        if (code == RpgAiCardKnowledge.MAN_EATER_BUG || code == RpgAiCardKnowledge.PENGUIN_SOLDIER) return 4500;
        return 1000 + state.getCardBaseAtk(code);
    }

    private int scoreGraveTarget(SelectCardItem item, int activeCode) {
        int code = item.code;
        if (activeCode == RpgAiCardKnowledge.MAGICIAN_OF_FAITH) {
            return RpgAiCardKnowledge.getSpellRecoveryScore(code, style, state.lp[1]);
        }
        if (activeCode == RpgAiCardKnowledge.MASK_OF_DARKNESS) {
            return RpgAiCardKnowledge.getTrapRecoveryScore(code, style);
        }
        // Monster Reborn: pick best monster across both graveyards!
        if (isExodiaStyle()) {
            if (code == RpgAiCardKnowledge.SANGAN || code == RpgAiCardKnowledge.WITCH_OF_BLACK_FOREST) return 9000;
            if (code == RpgAiCardKnowledge.MAGICIAN_OF_FAITH) return 8500;
        }
        if (isFusionStyle() && code == RpgAiCardKnowledge.CYBER_STEIN && state.lp[0] > 5000) {
            return 10000;
        }
        int atk = state.getCardBaseAtk(code);
        int def = state.getCardBaseDef(code);
        int ownerBonus = (item.localCtrl == 1) ? 100 : 0; // slightly prefer stealing opponent's boss!
        return Math.max(atk, def / 2) + ownerBonus;
    }

    private int scoreEnemyRemovalTarget(SelectCardItem item) {
        if (item.localCtrl == 0) {
            // Avoid targeting our own cards! If forced, pick weakest own card
            return -50000 - getCardCombatValue(item);
        }
        RpgAiDuelState.CardSlot slot = state.getCard(1, item.location, item.sequence);
        if (slot != null && slot.isFaceUp()) {
            return 10000 + Math.max(slot.atk, slot.def);
        }
        return 8000; // face-down opponent card
    }

    /** Higher score = MORE willing to sacrifice/tribute. */
    private int scoreTributeSacrificePriority(SelectCardItem item) {
        RpgAiDuelState.CardSlot slot = state.getCard(item.localCtrl, item.location, item.sequence);
        // Always tribute stolen opponent monsters first!
        if (slot != null && slot.isStolenFromOpponent()) return 100000;
        // Sangan / Witch trigger search when sent from field to GY!
        if (RpgAiCardKnowledge.isSearcherMonster(item.code)) return 80000;
        // Already-flipped Magician of Faith / Man-Eater Bug / weak monsters
        int atk = slot != null ? slot.atk : state.getCardBaseAtk(item.code);
        return 10000 - atk;
    }

    // =========================================================================
    // MSG_SELECT_TRIBUTE
    // =========================================================================

    public byte[] chooseSelectTribute(ByteBuffer data) {
        if (data.remaining() < 5) return intBytes(0);
        data.get(); // player
        boolean cancelable = (data.get() & 0xFF) != 0;
        int min = data.get() & 0xFF;
        int max = data.get() & 0xFF;
        int count = data.get() & 0xFF;

        List<SelectCardItem> items = new ArrayList<>();
        for (int i = 0; i < count && data.remaining() >= 8; i++) {
            int rawCode = data.getInt();
            int ctrl = data.get() & 0xFF;
            int loc = data.get() & 0xFF;
            int seq = data.get() & 0xFF;
            int tribVal = data.get() & 0xFF;
            int localCtrl = state.localPlayer(ctrl & 1);
            int code = rawCode;
            if (code == 0) {
                RpgAiDuelState.CardSlot s = state.getCard(localCtrl, loc, seq);
                if (s != null) code = s.code;
            }
            items.add(new SelectCardItem(code, ctrl, localCtrl, loc, seq, 0, i, tribVal));
        }
        if (items.isEmpty()) return intBytes(cancelable ? -1 : 0);

        List<SelectCardItem> sorted = new ArrayList<>(items);
        Collections.sort(sorted, (a, b) -> Integer.compare(
                scoreTributeSacrificePriority(b), scoreTributeSacrificePriority(a)));

        List<Integer> chosen = new ArrayList<>();
        int sum = 0;
        for (SelectCardItem it : sorted) {
            chosen.add(it.index);
            sum += Math.max(1, it.opParam & 0xFF);
            if (sum >= min && chosen.size() >= min) break;
            if (chosen.size() >= max && max > 0) break;
        }
        byte[] resp = new byte[1 + chosen.size()];
        resp[0] = (byte) chosen.size();
        for (int i = 0; i < chosen.size(); i++) {
            resp[1 + i] = (byte) chosen.get(i).intValue();
        }
        return resp;
    }

    // =========================================================================
    // MSG_SELECT_POSITION
    // =========================================================================

    public int choosePosition(ByteBuffer data) {
        if (data.remaining() < 6) return RpgAiDuelState.POS_FACEUP_ATTACK;
        data.get(); // player
        int code = data.getInt();
        int positions = data.get() & 0x0F;

        // If only 1 option is legal, return it immediately
        if (Integer.bitCount(positions) == 1) return positions;

        int atk = state.getCardBaseAtk(code);
        int def = state.getCardBaseDef(code);
        int oppStrongest = state.getStrongestThreatValue(1);

        boolean preferDefense = RpgAiCardKnowledge.isFlipMonster(code)
                || RpgAiCardKnowledge.isExodiaPiece(code)
                || (def > atk && (def >= state.eraWallDefThreshold || isExodiaStyle() || isBurnStyle() || oppStrongest > atk))
                || (RpgAiCardKnowledge.isSearcherMonster(code) && oppStrongest > atk);

        if (preferDefense) {
            if ((positions & RpgAiDuelState.POS_FACEDOWN_DEFENSE) != 0) return RpgAiDuelState.POS_FACEDOWN_DEFENSE;
            if ((positions & RpgAiDuelState.POS_FACEUP_DEFENSE) != 0) return RpgAiDuelState.POS_FACEUP_DEFENSE;
        }
        if ((positions & RpgAiDuelState.POS_FACEUP_ATTACK) != 0) return RpgAiDuelState.POS_FACEUP_ATTACK;
        if ((positions & RpgAiDuelState.POS_FACEUP_DEFENSE) != 0) return RpgAiDuelState.POS_FACEUP_DEFENSE;
        if ((positions & RpgAiDuelState.POS_FACEDOWN_DEFENSE) != 0) return RpgAiDuelState.POS_FACEDOWN_DEFENSE;
        return RpgAiDuelState.POS_FACEDOWN_ATTACK;
    }

    // =========================================================================
    // MSG_SELECT_PLACE / MSG_SELECT_DISFIELD
    // =========================================================================

    public byte[] choosePlace(ByteBuffer data) {
        if (data.remaining() < 6) return new byte[]{(byte) state.protoPlayer(0), RpgAiDuelState.LOC_MZONE, 2};
        int player = data.get() & 0xFF;
        int count = Math.max(1, data.get() & 0xFF);
        int rawMask = data.getInt();
        int available = ~rawMask;

        // Preferred center-outward zone order (2, 1, 3, 0, 4, 5, 6)
        int[] mOrder = new int[]{2, 1, 3, 0, 4, 5, 6};
        int[] sOrder = new int[]{2, 1, 3, 0, 4, 5, 6, 7};

        int selfProto = player & 1;
        int otherProto = 1 - selfProto;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int picked = 0;

        // Own MZone (bits 0..6)
        for (int seq : mOrder) {
            if (picked >= count) break;
            if ((available & (1 << seq)) != 0) {
                out.write(selfProto);
                out.write(RpgAiDuelState.LOC_MZONE);
                out.write(seq);
                picked++;
            }
        }
        // Own SZone (bits 8..15)
        for (int seq : sOrder) {
            if (picked >= count) break;
            if ((available & (1 << (8 + seq))) != 0) {
                out.write(selfProto);
                out.write(RpgAiDuelState.LOC_SZONE);
                out.write(seq);
                picked++;
            }
        }
        // Opponent MZone (bits 16..22)
        for (int seq : mOrder) {
            if (picked >= count) break;
            if ((available & (1 << (16 + seq))) != 0) {
                out.write(otherProto);
                out.write(RpgAiDuelState.LOC_MZONE);
                out.write(seq);
                picked++;
            }
        }
        // Opponent SZone (bits 24..31)
        for (int seq : sOrder) {
            if (picked >= count) break;
            if ((available & (1 << (24 + seq))) != 0) {
                out.write(otherProto);
                out.write(RpgAiDuelState.LOC_SZONE);
                out.write(seq);
                picked++;
            }
        }
        if (picked == 0) {
            return new byte[]{(byte) selfProto, 0, 0};
        }
        return out.toByteArray();
    }

    // =========================================================================
    // Remaining MSG_SELECT_* & MSG_ANNOUNCE_* (complete deadlock-free coverage)
    // =========================================================================

    public int chooseOption(ByteBuffer data) {
        return 0;
    }

    public byte[] chooseSelectSum(ByteBuffer data) {
        if (data.remaining() < 9) return intBytes(0);
        int selectMode = data.get() & 0xFF;
        data.get(); // player
        int sumVal = data.getInt();
        int min = data.get() & 0xFF;
        int max = data.get() & 0xFF;
        int mustCount = data.get() & 0xFF;
        int mustSum = 0;
        for (int i = 0; i < mustCount && data.remaining() >= 11; i++) {
            data.getInt(); data.get(); data.get(); data.get();
            mustSum += (data.getInt() & 0xFFFF);
        }
        int count = data.remaining() >= 1 ? (data.get() & 0xFF) : 0;
        int[] vals = new int[count];
        for (int i = 0; i < count && data.remaining() >= 11; i++) {
            data.getInt(); data.get(); data.get(); data.get();
            vals[i] = Math.max(1, data.getInt() & 0xFFFF);
        }
        List<Integer> picked = new ArrayList<>();
        int target = Math.max(0, sumVal - mustSum);
        findSumCombination(vals, 0, target, selectMode, min, max == 0 ? count : max, picked);
        byte[] resp = new byte[1 + mustCount + picked.size()];
        resp[0] = (byte) (mustCount + picked.size());
        for (int i = 0; i < mustCount; i++) resp[1 + i] = 0;
        for (int i = 0; i < picked.size(); i++) resp[1 + mustCount + i] = (byte) picked.get(i).intValue();
        return resp;
    }

    private boolean findSumCombination(int[] vals, int idx, int rem, int mode, int min, int max, List<Integer> cur) {
        if (cur.size() >= min && ((mode == 0 && rem <= 0) || (mode != 0 && rem == 0))) {
            return true;
        }
        if (idx >= vals.length || cur.size() >= max) return false;
        cur.add(idx);
        if (findSumCombination(vals, idx + 1, rem - vals[idx], mode, min, max, cur)) return true;
        cur.remove(cur.size() - 1);
        return findSumCombination(vals, idx + 1, rem, mode, min, max, cur);
    }

    public byte[] chooseUnselectCard(ByteBuffer data) {
        if (data.remaining() < 6) return intBytes(-1);
        data.get(); // player
        boolean finishable = (data.get() & 0xFF) != 0;
        boolean cancelable = (data.get() & 0xFF) != 0;
        data.get(); // min
        data.get(); // max
        int count1 = data.get() & 0xFF;
        if (finishable) return intBytes(-1);
        if (count1 > 0) return new byte[]{1, 0};
        return intBytes(cancelable ? -1 : 0);
    }

    public byte[] chooseSortCard(ByteBuffer data) {
        if (data.remaining() < 2) return new byte[0];
        data.get(); // player
        int count = data.get() & 0xFF;
        byte[] resp = new byte[count];
        for (int i = 0; i < count; i++) resp[i] = (byte) i;
        return resp;
    }

    public byte[] chooseCounter(ByteBuffer data) {
        if (data.remaining() < 6) return new byte[0];
        data.get(); // player
        data.getShort(); // type
        int need = data.getShort() & 0xFFFF;
        int count = data.get() & 0xFF;
        ByteBuffer resp = ByteBuffer.allocate(count * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < count && data.remaining() >= 9; i++) {
            data.getInt(); data.get(); data.get(); data.get();
            int avail = data.getShort() & 0xFFFF;
            int take = Math.min(need, avail);
            need -= take;
            resp.putShort((short) take);
        }
        return resp.array();
    }

    public int chooseAnnounceBitmask(ByteBuffer data) {
        if (data.remaining() < 6) return 1;
        data.get(); // player
        int count = Math.max(1, data.get() & 0xFF);
        int avail = data.getInt();
        int chosen = 0;
        int picked = 0;
        for (int b = 0; b < 32 && picked < count; b++) {
            if ((avail & (1 << b)) != 0) {
                chosen |= (1 << b);
                picked++;
            }
        }
        return chosen != 0 ? chosen : 1;
    }

    public int chooseAnnounceNumber(ByteBuffer data) {
        return 0;
    }

    public int chooseAnnounceCard(ByteBuffer data) {
        return RpgAiCardKnowledge.POT_OF_GREED;
    }

    // =========================================================================
    // Helper Utilities
    // =========================================================================

    private int activateIdle(CmdEntry e) {
        pendingActivatingCode = e.code;
        return (e.index << 16) + 5;
    }

    private int reposIdle(CmdEntry e) {
        pendingActivatingCode = e.code;
        return (e.index << 16) + 2;
    }

    private CmdEntry findActivatable(IdleCmdPrompt p, int code) {
        for (CmdEntry e : p.activatable) {
            if (e.code == code) return e;
        }
        return null;
    }

    private CmdEntry pickHighestAtkEntry(List<CmdEntry> list) {
        CmdEntry best = null;
        int bestAtk = -1;
        for (CmdEntry e : list) {
            int atk = state.getCardBaseAtk(e.code);
            if (atk > bestAtk) {
                bestAtk = atk;
                best = e;
            }
        }
        return best;
    }

    private int getBestPlayableSummonAtk(IdleCmdPrompt p) {
        int best = 0;
        for (CmdEntry e : p.summonable) {
            if (RpgAiCardKnowledge.isFlipMonster(e.code) || RpgAiCardKnowledge.isExodiaPiece(e.code)) continue;
            best = Math.max(best, state.getCardBaseAtk(e.code) + getBestHandEquipBonusFor(e.code));
        }
        return best;
    }

    private boolean hasTributeSummonCandidate(IdleCmdPrompt p) {
        for (CmdEntry e : p.summonable) {
            if (state.tributesNeededForLevel(state.getCardLevel(e.code)) > 0) return true;
        }
        for (RpgAiDuelState.CardSlot c : state.hand[0]) {
            if (c != null && c.isMonster() && state.tributesNeededForLevel(state.getCardLevel(c.code)) == 1) {
                return true;
            }
        }
        return false;
    }

    private int estimateTributeFodderValue(int tributesNeeded) {
        List<Integer> vals = new ArrayList<>();
        for (RpgAiDuelState.CardSlot m : state.getMonsters(0)) {
            if (m.isStolenFromOpponent()) {
                vals.add(0); // stolen monster is free fodder!
            } else if (RpgAiCardKnowledge.isSearcherMonster(m.code)) {
                vals.add(200); // tributing Sangan/Witch triggers search!
            } else {
                vals.add(m.isFaceUp() ? Math.max(m.atk, m.def / 2) : 600);
            }
        }
        Collections.sort(vals);
        int sum = 0;
        for (int i = 0; i < Math.min(tributesNeeded, vals.size()); i++) {
            sum += vals.get(i);
        }
        return sum;
    }

    private int getBestHandEquipBonusFor(int monsterCode) {
        int attr = state.getCardAttrMask(monsterCode);
        int race = state.getCardRaceMask(monsterCode);
        int bonus = 0;
        for (RpgAiDuelState.CardSlot c : state.hand[0]) {
            if (c != null && RpgAiCardKnowledge.isEquipSpell(c.code)
                    && RpgAiCardKnowledge.canEquipTarget(c.code, attr, race)) {
                bonus += RpgAiCardKnowledge.getEquipAtkBonus(c.code);
            }
        }
        return bonus;
    }

    private boolean hasFaceDownMonster(int localPlayer) {
        for (RpgAiDuelState.CardSlot m : state.getMonsters(localPlayer)) {
            if (m.isFaceDown()) return true;
        }
        return false;
    }

    private boolean hasFaceUpMonster(int localPlayer) {
        for (RpgAiDuelState.CardSlot m : state.getMonsters(localPlayer)) {
            if (m.isFaceUp()) return true;
        }
        return false;
    }

    private boolean hasDefenseMonster(int localPlayer) {
        for (RpgAiDuelState.CardSlot m : state.getMonsters(localPlayer)) {
            if (m.isDefensePos()) return true;
        }
        return false;
    }

    private boolean hasMonsterOnField(int localPlayer, int code) {
        for (RpgAiDuelState.CardSlot m : state.getMonsters(localPlayer)) {
            if (m.code == code) return true;
        }
        return false;
    }

    private boolean hasCodeInMyGrave(int code) {
        for (RpgAiDuelState.CardSlot c : state.grave[0]) {
            if (c != null && c.code == code) return true;
        }
        return false;
    }

    private boolean hasSearcherInHand() {
        for (RpgAiDuelState.CardSlot c : state.hand[0]) {
            if (c != null && RpgAiCardKnowledge.isSearcherMonster(c.code)) return true;
        }
        return false;
    }

    private boolean canSafelyDiscard(int need) {
        int discardable = 0;
        Set<Integer> seenExodia = new HashSet<>();
        for (RpgAiDuelState.CardSlot c : state.hand[0]) {
            if (c == null) continue;
            if (RpgAiCardKnowledge.isExodiaPiece(c.code)) {
                if (seenExodia.contains(c.code)) discardable++;
                else seenExodia.add(c.code);
            } else if (c.code != RpgAiCardKnowledge.TRIBUTE_TO_THE_DOOMED) {
                discardable++;
            }
        }
        return discardable >= need;
    }

    private boolean hasProfitableAttackAvailable() {
        List<RpgAiDuelState.CardSlot> myMons = state.getMonsters(0);
        if (myMons.isEmpty()) return false;
        for (RpgAiDuelState.CardSlot m : myMons) {
            if (m.isFaceUp() && m.isAttackPos() && canMonsterAttackProfitably(m.atk)) {
                return true;
            }
        }
        return false;
    }

    private boolean canMonsterAttackProfitably(int myAtk) {
        if (myAtk <= 0) return false;
        List<RpgAiDuelState.CardSlot> oppMons = state.getMonsters(1);
        if (oppMons.isEmpty()) return true;
        for (RpgAiDuelState.CardSlot opp : oppMons) {
            if (opp.isFaceUp() && opp.isAttackPos() && myAtk > opp.atk) return true;
            if (opp.isFaceUp() && opp.isDefensePos() && myAtk > opp.def) return true;
            if (opp.isFaceDown() && myAtk >= 1450) return true;
        }
        return false;
    }

    private int getFieldMonsterAtk(int localPlayer, int seq, int fallbackCode) {
        RpgAiDuelState.CardSlot s = state.getCard(localPlayer, RpgAiDuelState.LOC_MZONE, seq);
        if (s != null && s.isFaceUp()) return s.atk;
        return state.getCardBaseAtk(fallbackCode);
    }

    private boolean isStolenSlot(int seq) {
        RpgAiDuelState.CardSlot s = state.getCard(0, RpgAiDuelState.LOC_MZONE, seq);
        return s != null && s.isStolenFromOpponent();
    }

    private int getCardCombatValue(SelectCardItem item) {
        RpgAiDuelState.CardSlot s = state.getCard(item.localCtrl, item.location, item.sequence);
        if (s != null) return s.effectiveCombatValue();
        return state.getCardBaseAtk(item.code);
    }

    private boolean isKnownTrap(int code) {
        return code == RpgAiCardKnowledge.TRAP_HOLE
                || code == RpgAiCardKnowledge.ACID_TRAP_HOLE
                || code == RpgAiCardKnowledge.CRUSH_CARD_VIRUS
                || code == RpgAiCardKnowledge.KUNAI_WITH_CHAIN
                || code == RpgAiCardKnowledge.CASTLE_WALLS
                || code == RpgAiCardKnowledge.JUST_DESSERTS
                || code == RpgAiCardKnowledge.HORN_OF_HEAVEN
                || code == RpgAiCardKnowledge.SOLEMN_JUDGMENT
                || code == RpgAiCardKnowledge.SEVEN_TOOLS
                || code == RpgAiCardKnowledge.WABOKU
                || code == RpgAiCardKnowledge.METALMORPH
                || code == RpgAiCardKnowledge.ROYAL_DECREE
                || code == RpgAiCardKnowledge.TWO_PRONGED_ATTACK
                || code == RpgAiCardKnowledge.MIRROR_FORCE
                || code == RpgAiCardKnowledge.MAGIC_JAMMER
                || code == RpgAiCardKnowledge.REINFORCEMENTS;
    }

    private static boolean allMatch(List<SelectCardItem> items, int localCtrl, int loc) {
        for (SelectCardItem it : items) {
            if (it.localCtrl != localCtrl || (it.location & 0x7F) != loc) return false;
        }
        return !items.isEmpty();
    }

    private static boolean allInLocation(List<SelectCardItem> items, int loc) {
        for (SelectCardItem it : items) {
            if ((it.location & 0x7F) != loc) return false;
        }
        return !items.isEmpty();
    }

    private static boolean anyOnField(List<SelectCardItem> items, int localCtrl) {
        for (SelectCardItem it : items) {
            int loc = it.location & 0x7F;
            if (it.localCtrl == localCtrl && (loc == RpgAiDuelState.LOC_MZONE || loc == RpgAiDuelState.LOC_SZONE)) {
                return true;
            }
        }
        return false;
    }

    private static List<Integer> pickTopN(List<SelectCardItem> items, int n, Comparator<SelectCardItem> cmp) {
        List<SelectCardItem> copy = new ArrayList<>(items);
        Collections.sort(copy, cmp);
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < Math.min(n, copy.size()); i++) {
            out.add(copy.get(i).index);
        }
        return out;
    }

    public static byte[] intBytes(int v) {
        ByteBuffer b = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(v);
        return b.array();
    }
}

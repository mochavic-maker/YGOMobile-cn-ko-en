package cn.garymb.ygomobile.rpg.ai;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import cn.garymb.ygomobile.rpg.model.OcgRuleSet;
import cn.garymb.ygomobile.rpg.model.RpgCard;
import cn.garymb.ygomobile.rpg.model.RpgCatalog;

/**
 * Pure-Java authoritative board state model for the RPG AI client.
 * Tracks both players' LP, turn/phase, Monster/Spell-Trap/Hand/Grave/Extra zones,
 * active chain stack, and hints directly from YGOPro STOC_GAME_MSG binary payloads.
 * Uses local perspective: index 0 = AI (self), index 1 = Opponent.
 */
public class RpgAiDuelState {

    // ===== Locations =====
    public static final int LOC_DECK    = 0x01;
    public static final int LOC_HAND    = 0x02;
    public static final int LOC_MZONE   = 0x04;
    public static final int LOC_SZONE   = 0x08;
    public static final int LOC_GRAVE   = 0x10;
    public static final int LOC_REMOVED = 0x20;
    public static final int LOC_EXTRA   = 0x40;
    public static final int LOC_OVERLAY = 0x80;

    // ===== Positions =====
    public static final int POS_FACEUP_ATTACK    = 0x01;
    public static final int POS_FACEDOWN_ATTACK  = 0x02;
    public static final int POS_FACEUP_DEFENSE   = 0x04;
    public static final int POS_FACEDOWN_DEFENSE = 0x08;
    public static final int POS_FACEUP           = 0x05;
    public static final int POS_FACEDOWN         = 0x0A;
    public static final int POS_ATTACK           = 0x03;
    public static final int POS_DEFENSE          = 0x0C;

    // ===== Card Types =====
    public static final int TYPE_MONSTER = 0x01;
    public static final int TYPE_SPELL   = 0x02;
    public static final int TYPE_TRAP    = 0x04;
    public static final int TYPE_NORMAL  = 0x10;
    public static final int TYPE_EFFECT  = 0x20;
    public static final int TYPE_FUSION  = 0x40;
    public static final int TYPE_QUICKPLAY = 0x10000;
    public static final int TYPE_CONTINUOUS = 0x20000;
    public static final int TYPE_EQUIP   = 0x40000;
    public static final int TYPE_FIELD   = 0x80000;
    public static final int TYPE_COUNTER = 0x100000;
    public static final int TYPE_FLIP    = 0x200000;

    // ===== Phases =====
    public static final int PHASE_DRAW         = 0x01;
    public static final int PHASE_STANDBY      = 0x02;
    public static final int PHASE_MAIN1        = 0x04;
    public static final int PHASE_BATTLE_START = 0x08;
    public static final int PHASE_BATTLE_STEP  = 0x10;
    public static final int PHASE_DAMAGE       = 0x20;
    public static final int PHASE_DAMAGE_CAL   = 0x40;
    public static final int PHASE_BATTLE       = 0x80;
    public static final int PHASE_MAIN2        = 0x100;
    public static final int PHASE_END          = 0x200;

    // ===== Query Flags =====
    private static final int QUERY_CODE         = 0x01;
    private static final int QUERY_POSITION     = 0x02;
    private static final int QUERY_ALIAS        = 0x04;
    private static final int QUERY_TYPE         = 0x08;
    private static final int QUERY_LEVEL        = 0x10;
    private static final int QUERY_RANK         = 0x20;
    private static final int QUERY_ATTRIBUTE    = 0x40;
    private static final int QUERY_RACE         = 0x80;
    private static final int QUERY_ATTACK       = 0x100;
    private static final int QUERY_DEFENSE      = 0x200;
    private static final int QUERY_BASE_ATTACK  = 0x400;
    private static final int QUERY_BASE_DEFENSE = 0x800;
    private static final int QUERY_REASON       = 0x1000;
    private static final int QUERY_REASON_CARD  = 0x2000;
    private static final int QUERY_EQUIP_CARD   = 0x4000;
    private static final int QUERY_TARGET_CARD  = 0x8000;
    private static final int QUERY_OVERLAY_CARD = 0x10000;
    private static final int QUERY_COUNTERS     = 0x20000;
    private static final int QUERY_OWNER        = 0x40000;
    private static final int QUERY_STATUS       = 0x80000;
    private static final int QUERY_LSCALE       = 0x200000;
    private static final int QUERY_RSCALE       = 0x400000;
    private static final int QUERY_LINK         = 0x800000;

    public static class CardSlot {
        public int code;
        public int alias;
        public int controller; // local: 0=me, 1=opp
        public int owner;      // local: 0=me, 1=opp (from QUERY_OWNER or default controller)
        public int location;
        public int sequence;
        public int position;
        public int type;
        public int level;
        public int attribute;
        public int race;
        public int atk;
        public int def;
        public int baseAtk;
        public int baseDef;
        public int status;

        public boolean isFaceUp() {
            return (position & POS_FACEUP) != 0;
        }

        public boolean isFaceDown() {
            return (position & POS_FACEDOWN) != 0;
        }

        public boolean isAttackPos() {
            return (position & POS_ATTACK) != 0;
        }

        public boolean isDefensePos() {
            return (position & POS_DEFENSE) != 0;
        }

        public boolean isMonster() {
            return (type & TYPE_MONSTER) != 0;
        }

        public boolean isSpell() {
            return (type & TYPE_SPELL) != 0;
        }

        public boolean isTrap() {
            return (type & TYPE_TRAP) != 0;
        }

        public boolean isDisabled() {
            return (status & 0x01) != 0; // STATUS_DISABLED
        }

        public boolean isStolenFromOpponent() {
            return controller == 0 && owner == 1;
        }

        public int effectiveCombatValue() {
            if (isFaceDown()) return 1400; // conservative estimate for unknown face-down
            return isAttackPos() ? Math.max(0, atk) : Math.max(0, def);
        }

        public void applyQuery(ByteBuffer buf, RpgAiDuelState state) {
            if (buf.remaining() < 4) return;
            int flag = buf.getInt();
            if (flag == 0) {
                return;
            }
            if ((flag & QUERY_CODE) != 0 && buf.remaining() >= 4) {
                int c = buf.getInt();
                if (c != 0) code = c;
            }
            if ((flag & QUERY_POSITION) != 0 && buf.remaining() >= 4) {
                position = (buf.getInt() >> 24) & 0xFF;
            }
            if ((flag & QUERY_ALIAS) != 0 && buf.remaining() >= 4) {
                alias = buf.getInt();
            }
            if ((flag & QUERY_TYPE) != 0 && buf.remaining() >= 4) {
                type = buf.getInt();
            }
            if ((flag & QUERY_LEVEL) != 0 && buf.remaining() >= 4) {
                level = buf.getInt() & 0xFF;
            }
            if ((flag & QUERY_RANK) != 0 && buf.remaining() >= 4) {
                buf.getInt();
            }
            if ((flag & QUERY_ATTRIBUTE) != 0 && buf.remaining() >= 4) {
                attribute = buf.getInt();
            }
            if ((flag & QUERY_RACE) != 0 && buf.remaining() >= 4) {
                race = buf.getInt();
            }
            if ((flag & QUERY_ATTACK) != 0 && buf.remaining() >= 4) {
                atk = buf.getInt();
            }
            if ((flag & QUERY_DEFENSE) != 0 && buf.remaining() >= 4) {
                def = buf.getInt();
            }
            if ((flag & QUERY_BASE_ATTACK) != 0 && buf.remaining() >= 4) {
                baseAtk = buf.getInt();
            }
            if ((flag & QUERY_BASE_DEFENSE) != 0 && buf.remaining() >= 4) {
                baseDef = buf.getInt();
            }
            if ((flag & QUERY_REASON) != 0 && buf.remaining() >= 4) {
                buf.getInt();
            }
            if ((flag & QUERY_REASON_CARD) != 0 && buf.remaining() >= 4) {
                buf.getInt();
            }
            if ((flag & QUERY_EQUIP_CARD) != 0 && buf.remaining() >= 4) {
                buf.getInt();
            }
            if ((flag & QUERY_TARGET_CARD) != 0 && buf.remaining() >= 4) {
                int cnt = buf.getInt();
                for (int i = 0; i < cnt && buf.remaining() >= 4; i++) buf.getInt();
            }
            if ((flag & QUERY_OVERLAY_CARD) != 0 && buf.remaining() >= 4) {
                int cnt = buf.getInt();
                for (int i = 0; i < cnt && buf.remaining() >= 4; i++) buf.getInt();
            }
            if ((flag & QUERY_COUNTERS) != 0 && buf.remaining() >= 4) {
                int cnt = buf.getInt();
                for (int i = 0; i < cnt && buf.remaining() >= 4; i++) buf.getInt();
            }
            if ((flag & QUERY_OWNER) != 0 && buf.remaining() >= 4) {
                int rawOwner = buf.getInt() & 1;
                owner = state.localPlayer(rawOwner);
            }
            if ((flag & QUERY_STATUS) != 0 && buf.remaining() >= 4) {
                status = buf.getInt();
            }
            if ((flag & QUERY_LSCALE) != 0 && buf.remaining() >= 4) {
                buf.getInt();
            }
            if ((flag & QUERY_RSCALE) != 0 && buf.remaining() >= 4) {
                buf.getInt();
            }
            if ((flag & QUERY_LINK) != 0 && buf.remaining() >= 8) {
                buf.getInt();
                buf.getInt();
            }
            state.enrichFromCatalog(this);
        }
    }

    public static class ChainLink {
        public final int code;
        public final int controller; // local: 0=me, 1=opp
        public final int location;
        public final int sequence;
        public final int desc;

        public ChainLink(int code, int controller, int location, int sequence, int desc) {
            this.code = code;
            this.controller = controller;
            this.location = location;
            this.sequence = sequence;
            this.desc = desc;
        }
    }

    public final RpgCatalog catalog;
    public final OcgRuleSet ruleSet;
    public final String currentTimelineDate;

    public int myProtocolPlayer = 1;
    public int duelRule = 1;
    public final int[] lp = new int[]{8000, 8000};
    public final int[] deckCount = new int[]{40, 40};
    public int turnPlayer = 0; // local: 0=me, 1=opp
    public int turnCount = 0;
    public int phase = PHASE_DRAW;

    public final CardSlot[][] mzone = new CardSlot[2][7];
    public final CardSlot[][] szone = new CardSlot[2][8];
    public final List<CardSlot>[] hand = new List[]{new ArrayList<>(), new ArrayList<>()};
    public final List<CardSlot>[] grave = new List[]{new ArrayList<>(), new ArrayList<>()};
    public final List<CardSlot>[] extra = new List[]{new ArrayList<>(), new ArrayList<>()};
    public final List<CardSlot>[] removed = new List[]{new ArrayList<>(), new ArrayList<>()};

    public final List<ChainLink> currentChain = new ArrayList<>();
    public int lastSelectHint = 0;
    public int lastEventHint = 0;
    public int lastActivatingCode = 0;
    public int lastAttackerLocalCtrl = -1;
    public int lastAttackerSeq = -1;
    public int lastDefenderLocalCtrl = -1;
    public int lastDefenderSeq = -1;
    public boolean normalSummonedThisTurn = false;
    public boolean lastWillActivatedThisTurn = false;
    public boolean changeOfHeartUsedThisTurn = false;
    public int lastSummonedCodeThisTurn = 0;

    public RpgAiDuelState(RpgCatalog catalog, OcgRuleSet ruleSet, String currentTimelineDate) {
        this.catalog = catalog;
        this.ruleSet = ruleSet;
        this.currentTimelineDate = currentTimelineDate != null ? currentTimelineDate : "1999-12-31";
    }

    public int localPlayer(int protoPlayer) {
        return ((protoPlayer & 1) == myProtocolPlayer) ? 0 : 1;
    }

    public int protoPlayer(int local) {
        return (local == 0) ? myProtocolPlayer : (1 - myProtocolPlayer);
    }

    public boolean isMyTurn() {
        return turnPlayer == 0;
    }

    public boolean isMain1() {
        return phase == PHASE_MAIN1;
    }

    public boolean isMain2() {
        return phase == PHASE_MAIN2;
    }

    public boolean isBattlePhase() {
        return phase == PHASE_BATTLE_START || phase == PHASE_BATTLE_STEP
                || phase == PHASE_DAMAGE || phase == PHASE_DAMAGE_CAL || phase == PHASE_BATTLE;
    }

    public boolean isTributeSummonEnabled() {
        return ruleSet == null || ruleSet.tributeSummonEnabled;
    }

    /** True during 1999-11-18 ~ 1999-12-19 when Sangan & Witch triggered even when discarded from hand. */
    public boolean isPreErrataSanganEra() {
        return currentTimelineDate.compareTo("1999-12-20") < 0;
    }

    public int tributesNeededForLevel(int level) {
        if (!isTributeSummonEnabled()) return 0;
        if (level >= 7) return 2;
        if (level >= 5) return 1;
        return 0;
    }

    public void reset() {
        Arrays.fill(mzone[0], null);
        Arrays.fill(mzone[1], null);
        Arrays.fill(szone[0], null);
        Arrays.fill(szone[1], null);
        hand[0].clear();
        hand[1].clear();
        grave[0].clear();
        grave[1].clear();
        extra[0].clear();
        extra[1].clear();
        removed[0].clear();
        removed[1].clear();
        currentChain.clear();
        turnCount = 0;
        phase = PHASE_DRAW;
        lastSelectHint = 0;
        lastEventHint = 0;
        lastActivatingCode = 0;
        clearTurnFlags();
    }

    private void clearTurnFlags() {
        normalSummonedThisTurn = false;
        lastWillActivatedThisTurn = false;
        changeOfHeartUsedThisTurn = false;
        lastSummonedCodeThisTurn = 0;
        lastAttackerLocalCtrl = -1;
        lastAttackerSeq = -1;
        lastDefenderLocalCtrl = -1;
        lastDefenderSeq = -1;
    }

    public void enrichFromCatalog(CardSlot slot) {
        if (slot == null || slot.code <= 0 || catalog == null) return;
        RpgCard card = catalog.findCard(slot.code);
        if (card == null) return;
        if (slot.type == 0) {
            slot.type = inferTypeMask(card);
        }
        if (slot.level == 0 && card.level > 0) {
            slot.level = card.level;
        }
        if (slot.baseAtk == 0 && card.atk > 0) {
            slot.baseAtk = card.atk;
        }
        if (slot.baseDef == 0 && card.def > 0) {
            slot.baseDef = card.def;
        }
        if (slot.atk == 0 && card.atk > 0 && slot.location != LOC_MZONE) {
            slot.atk = card.atk;
        }
        if (slot.def == 0 && card.def > 0 && slot.location != LOC_MZONE) {
            slot.def = card.def;
        }
        if (slot.attribute == 0 && card.attribute != null) {
            slot.attribute = parseAttributeMask(card.attribute);
        }
        if (slot.race == 0 && card.race != null) {
            slot.race = parseRaceMask(card.race);
        }
    }

    public RpgCard lookupCard(int code) {
        return catalog != null && code > 0 ? catalog.findCard(code) : null;
    }

    public int getCardBaseAtk(int code) {
        RpgCard c = lookupCard(code);
        return c != null ? Math.max(0, c.atk) : 0;
    }

    public int getCardBaseDef(int code) {
        RpgCard c = lookupCard(code);
        return c != null ? Math.max(0, c.def) : 0;
    }

    public int getCardLevel(int code) {
        RpgCard c = lookupCard(code);
        return c != null ? c.level : 0;
    }

    public int getCardAttrMask(int code) {
        RpgCard c = lookupCard(code);
        return c != null && c.attribute != null ? parseAttributeMask(c.attribute) : 0;
    }

    public int getCardRaceMask(int code) {
        RpgCard c = lookupCard(code);
        return c != null && c.race != null ? parseRaceMask(c.race) : 0;
    }

    public static int inferTypeMask(RpgCard card) {
        if (card == null || card.frameType == null) return 0;
        String ft = card.frameType.toLowerCase();
        if ("spell".equals(ft)) return TYPE_SPELL;
        if ("trap".equals(ft)) return TYPE_TRAP;
        int mask = TYPE_MONSTER;
        if (ft.contains("normal")) mask |= TYPE_NORMAL;
        if (ft.contains("effect")) mask |= TYPE_EFFECT;
        if (ft.contains("fusion")) mask |= TYPE_FUSION;
        if (RpgAiCardKnowledge.isFlipMonster(card.id)) mask |= TYPE_FLIP;
        return mask;
    }

    public static int parseAttributeMask(String attr) {
        if (attr == null) return 0;
        switch (attr.trim().toUpperCase()) {
            case "EARTH": return 0x01;
            case "WATER": return 0x02;
            case "FIRE":  return 0x04;
            case "WIND":  return 0x08;
            case "LIGHT": return 0x10;
            case "DARK":  return 0x20;
            default:      return 0;
        }
    }

    public static int parseRaceMask(String race) {
        if (race == null) return 0;
        switch (race.trim().toLowerCase()) {
            case "warrior":       return 0x01;
            case "spellcaster":   return 0x02;
            case "fairy":         return 0x04;
            case "fiend":         return 0x08;
            case "zombie":        return 0x10;
            case "machine":       return 0x20;
            case "aqua":          return 0x40;
            case "pyro":          return 0x80;
            case "rock":          return 0x100;
            case "winged beast":  return 0x200;
            case "plant":         return 0x400;
            case "insect":        return 0x800;
            case "thunder":       return 0x1000;
            case "dragon":        return 0x2000;
            case "beast":         return 0x4000;
            case "beast-warrior": return 0x8000;
            case "dinosaur":      return 0x10000;
            case "fish":          return 0x20000;
            case "sea serpent":   return 0x40000;
            case "reptile":       return 0x80000;
            default:              return 0;
        }
    }

    // ===== Board Query Helpers =====

    public List<CardSlot> getMonsters(int localPlayer) {
        List<CardSlot> out = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            if (mzone[localPlayer][i] != null) out.add(mzone[localPlayer][i]);
        }
        return out;
    }

    public List<CardSlot> getSpellTraps(int localPlayer) {
        List<CardSlot> out = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            if (szone[localPlayer][i] != null) out.add(szone[localPlayer][i]);
        }
        return out;
    }

    public CardSlot getCard(int localPlayer, int loc, int seq) {
        int pureLoc = loc & 0x7F;
        if (localPlayer < 0 || localPlayer > 1) return null;
        if (pureLoc == LOC_MZONE) {
            return (seq >= 0 && seq < 7) ? mzone[localPlayer][seq] : null;
        }
        if (pureLoc == LOC_SZONE) {
            return (seq >= 0 && seq < 8) ? szone[localPlayer][seq] : null;
        }
        List<CardSlot> list = getDynamicList(localPlayer, pureLoc);
        if (list != null && seq >= 0 && seq < list.size()) {
            return list.get(seq);
        }
        return null;
    }

    public int getFreeMainMzoneCount(int localPlayer) {
        int free = 0;
        for (int i = 0; i < 5; i++) {
            if (mzone[localPlayer][i] == null) free++;
        }
        return free;
    }

    public int getFreeMainSzoneCount(int localPlayer) {
        int free = 0;
        for (int i = 0; i < 5; i++) {
            if (szone[localPlayer][i] == null) free++;
        }
        return free;
    }

    public int getStrongestAtk(int localPlayer) {
        int best = 0;
        for (CardSlot m : getMonsters(localPlayer)) {
            if (m.isFaceUp()) {
                best = Math.max(best, m.atk);
            }
        }
        return best;
    }

    public int getStrongestThreatValue(int localPlayer) {
        int best = 0;
        for (CardSlot m : getMonsters(localPlayer)) {
            best = Math.max(best, m.effectiveCombatValue());
        }
        return best;
    }

    public int getTotalFaceUpAtk(int localPlayer) {
        int sum = 0;
        for (CardSlot m : getMonsters(localPlayer)) {
            if (m.isFaceUp() && m.isAttackPos() && m.atk > 0) sum += m.atk;
        }
        return sum;
    }

    public boolean hasFaceUpCardOnField(int localPlayer, int code) {
        for (CardSlot m : getMonsters(localPlayer)) {
            if (m.isFaceUp() && m.code == code) return true;
        }
        for (CardSlot s : getSpellTraps(localPlayer)) {
            if (s.isFaceUp() && s.code == code) return true;
        }
        return false;
    }

    public boolean hasCardInHand(int code) {
        for (CardSlot c : hand[0]) {
            if (c != null && c.code == code) return true;
        }
        return false;
    }

    public boolean hasStolenMonsterOnMyField() {
        for (CardSlot m : getMonsters(0)) {
            if (m.isStolenFromOpponent()) return true;
        }
        return false;
    }

    public Set<Integer> getUniqueExodiaPiecesInHand() {
        Set<Integer> owned = new HashSet<>();
        for (CardSlot c : hand[0]) {
            if (c != null && RpgAiCardKnowledge.isExodiaPiece(c.code)) {
                owned.add(c.code);
            }
        }
        return owned;
    }

    public int countInHand(int code) {
        int cnt = 0;
        for (CardSlot c : hand[0]) {
            if (c != null && c.code == code) cnt++;
        }
        return cnt;
    }

    public boolean hasEligibleFriendlyEquipTarget(int equipCode) {
        for (CardSlot m : getMonsters(0)) {
            if (!m.isFaceUp()) continue;
            int attr = m.attribute != 0 ? m.attribute : getCardAttrMask(m.code);
            int race = m.race != 0 ? m.race : getCardRaceMask(m.code);
            if (RpgAiCardKnowledge.canEquipTarget(equipCode, attr, race)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasGoodSpellInMyGrave() {
        for (CardSlot c : grave[0]) {
            if (c != null && (c.isSpell() || (lookupCard(c.code) != null && "spell".equalsIgnoreCase(lookupCard(c.code).frameType)))) {
                return true;
            }
        }
        return false;
    }

    public boolean hasGoodTrapInMyGrave() {
        for (CardSlot c : grave[0]) {
            if (c != null && (c.isTrap() || (lookupCard(c.code) != null && "trap".equalsIgnoreCase(lookupCard(c.code).frameType)))) {
                return true;
            }
        }
        return false;
    }

    public boolean hasDarkVirusTributeOnMyField() {
        for (CardSlot m : getMonsters(0)) {
            int attr = m.attribute != 0 ? m.attribute : getCardAttrMask(m.code);
            int atk = m.isFaceUp() ? m.atk : getCardBaseAtk(m.code);
            if ((attr & 0x20) != 0 && atk <= 1000) {
                return true;
            }
        }
        return false;
    }

    public int countGraveMonsters(int localPlayer) {
        int count = 0;
        for (CardSlot c : grave[localPlayer]) {
            if (c != null && (c.isMonster() || (lookupCard(c.code) != null && lookupCard(c.code).isMonster()))) {
                count++;
            }
        }
        return count;
    }

    public int getBestGraveMonsterAtk() {
        int best = 0;
        for (int p = 0; p < 2; p++) {
            for (CardSlot c : grave[p]) {
                if (c != null && (c.isMonster() || (lookupCard(c.code) != null && lookupCard(c.code).isMonster()))) {
                    int atk = Math.max(c.baseAtk, getCardBaseAtk(c.code));
                    if (atk > best) best = atk;
                }
            }
        }
        return best;
    }

    // ===== State-Update Message Application =====

    public void onStart(ByteBuffer buf) {
        if (buf.remaining() < 18) return;
        reset();
        int playerType = buf.get() & 0xFF;
        duelRule = buf.get() & 0xFF;
        boolean duelIsFirst = (playerType & 1) == 0;
        myProtocolPlayer = duelIsFirst ? 0 : 1;
        int lp0 = buf.getInt();
        int lp1 = buf.getInt();
        int d0 = buf.getShort() & 0xFFFF;
        buf.getShort(); // e0
        int d1 = buf.getShort() & 0xFFFF;
        buf.getShort(); // e1
        lp[localPlayer(0)] = lp0;
        lp[localPlayer(1)] = lp1;
        deckCount[localPlayer(0)] = d0;
        deckCount[localPlayer(1)] = d1;
    }

    public void onHint(ByteBuffer buf) {
        if (buf.remaining() < 6) return;
        int type = buf.get() & 0xFF;
        buf.get(); // player
        int data = buf.getInt();
        if (type == 1) {
            lastEventHint = data;
        } else if (type == 3) {
            lastSelectHint = data;
        }
    }

    public void onNewTurn(ByteBuffer buf) {
        if (buf.remaining() < 1) return;
        int p = buf.get() & 0xFF;
        turnPlayer = localPlayer(p & 1);
        turnCount++;
        currentChain.clear();
        clearTurnFlags();
    }

    public void onNewPhase(ByteBuffer buf) {
        if (buf.remaining() < 2) return;
        phase = buf.getShort() & 0xFFFF;
        currentChain.clear();
    }

    public void onDamage(ByteBuffer buf) {
        if (buf.remaining() < 5) return;
        int p = localPlayer(buf.get() & 0xFF);
        int amt = buf.getInt();
        lp[p] = Math.max(0, lp[p] - amt);
    }

    public void onRecover(ByteBuffer buf) {
        if (buf.remaining() < 5) return;
        int p = localPlayer(buf.get() & 0xFF);
        int amt = buf.getInt();
        lp[p] += amt;
    }

    public void onPayLpCost(ByteBuffer buf) {
        if (buf.remaining() < 5) return;
        int p = localPlayer(buf.get() & 0xFF);
        int amt = buf.getInt();
        lp[p] = Math.max(0, lp[p] - amt);
    }

    public void onLpUpdate(ByteBuffer buf) {
        if (buf.remaining() < 5) return;
        int p = localPlayer(buf.get() & 0xFF);
        lp[p] = buf.getInt();
    }

    public void onDraw(ByteBuffer buf) {
        if (buf.remaining() < 2) return;
        int p = localPlayer(buf.get() & 0xFF);
        int count = buf.get() & 0xFF;
        deckCount[p] = Math.max(0, deckCount[p] - count);
        for (int i = 0; i < count && buf.remaining() >= 4; i++) {
            int code = buf.getInt() & 0x7FFFFFFF;
            CardSlot c = new CardSlot();
            c.code = code;
            c.controller = p;
            c.owner = p;
            c.location = LOC_HAND;
            c.sequence = hand[p].size();
            c.position = POS_FACEUP_ATTACK;
            enrichFromCatalog(c);
            hand[p].add(c);
        }
    }

    public void onShuffleHand(ByteBuffer buf) {
        if (buf.remaining() < 2) return;
        int p = localPlayer(buf.get() & 0xFF);
        int count = buf.get() & 0xFF;
        for (int i = 0; i < count && buf.remaining() >= 4; i++) {
            int code = buf.getInt();
            if (i < hand[p].size()) {
                hand[p].get(i).code = code;
                enrichFromCatalog(hand[p].get(i));
            }
        }
    }

    public void onMove(ByteBuffer buf) {
        if (buf.remaining() < 16) return;
        int code = buf.getInt();
        int oldCtrl = localPlayer(buf.get() & 0xFF);
        int oldLoc = buf.get() & 0xFF;
        int oldSeq = buf.get() & 0xFF;
        buf.get(); // oldPos
        int newCtrl = localPlayer(buf.get() & 0xFF);
        int newLoc = buf.get() & 0xFF;
        int newSeq = buf.get() & 0xFF;
        int newPos = buf.get() & 0xFF;
        buf.getInt(); // reason

        CardSlot moved = removeCard(oldCtrl, oldLoc, oldSeq);
        if (moved == null) {
            moved = new CardSlot();
            moved.owner = oldCtrl;
        }
        if (code != 0) moved.code = code;
        moved.controller = newCtrl;
        moved.location = newLoc;
        moved.sequence = newSeq;
        moved.position = newPos;
        if (oldLoc == LOC_DECK) deckCount[oldCtrl] = Math.max(0, deckCount[oldCtrl] - 1);
        if (newLoc == LOC_DECK) deckCount[newCtrl]++;
        enrichFromCatalog(moved);
        placeCard(moved, newCtrl, newLoc, newSeq);
    }

    public void onPosChange(ByteBuffer buf) {
        if (buf.remaining() < 9) return;
        int code = buf.getInt();
        int ctrl = localPlayer(buf.get() & 0xFF);
        int loc = buf.get() & 0xFF;
        int seq = buf.get() & 0xFF;
        buf.get(); // oldPos
        int newPos = buf.get() & 0xFF;
        CardSlot c = getCard(ctrl, loc, seq);
        if (c != null) {
            if (code != 0) c.code = code;
            c.position = newPos;
            enrichFromCatalog(c);
        }
    }

    public void onSet(ByteBuffer buf) {
        if (buf.remaining() < 8) return;
        int code = buf.getInt();
        int ctrl = localPlayer(buf.get() & 0xFF);
        int loc = buf.get() & 0xFF;
        int seq = buf.get() & 0xFF;
        int pos = buf.get() & 0xFF;
        CardSlot c = getCard(ctrl, loc, seq);
        if (c != null) {
            if (code != 0) c.code = code;
            c.position = pos;
        }
    }

    public void onSwap(ByteBuffer buf) {
        if (buf.remaining() < 16) return;
        buf.getInt(); // c1code
        int c1ctrl = localPlayer(buf.get() & 0xFF);
        int c1loc = buf.get() & 0xFF;
        int c1seq = buf.get() & 0xFF;
        buf.get();
        buf.getInt(); // c2code
        int c2ctrl = localPlayer(buf.get() & 0xFF);
        int c2loc = buf.get() & 0xFF;
        int c2seq = buf.get() & 0xFF;
        buf.get();
        CardSlot a = getCard(c1ctrl, c1loc, c1seq);
        CardSlot b = getCard(c2ctrl, c2loc, c2seq);
        if (c1loc == LOC_MZONE && c2loc == LOC_MZONE && c1seq < 7 && c2seq < 7) {
            mzone[c1ctrl][c1seq] = b;
            mzone[c2ctrl][c2seq] = a;
            if (a != null) { a.controller = c2ctrl; a.sequence = c2seq; }
            if (b != null) { b.controller = c1ctrl; b.sequence = c1seq; }
        }
    }

    public void onSummoning(ByteBuffer buf) {
        if (buf.remaining() < 8) return;
        int code = buf.getInt();
        int ctrl = localPlayer(buf.get() & 0xFF);
        buf.get(); // loc
        buf.get(); // seq
        buf.get(); // pos
        if (ctrl == 0) {
            normalSummonedThisTurn = true;
            lastSummonedCodeThisTurn = code;
        }
    }

    public void onChaining(ByteBuffer buf) {
        if (buf.remaining() < 16) return;
        int code = buf.getInt();
        int pcc = localPlayer(buf.get() & 0xFF);
        int pcl = buf.get() & 0xFF;
        int pcs = buf.get() & 0xFF;
        buf.get(); // subs
        int cc = localPlayer(buf.get() & 0xFF);
        int cl = buf.get() & 0xFF;
        int cs = buf.get() & 0xFF;
        int desc = buf.getInt();
        buf.get(); // ct
        currentChain.add(new ChainLink(code, cc, cl, cs, desc));
        lastActivatingCode = code;
        if (cc == 0) {
            if (code == RpgAiCardKnowledge.LAST_WILL) lastWillActivatedThisTurn = true;
            if (code == RpgAiCardKnowledge.CHANGE_OF_HEART) changeOfHeartUsedThisTurn = true;
        }
        CardSlot src = getCard(pcc, pcl, pcs);
        if (src != null && code != 0) {
            src.code = code;
            enrichFromCatalog(src);
        }
    }

    public void onChainEnd() {
        currentChain.clear();
    }

    public void onAttack(ByteBuffer buf) {
        if (buf.remaining() < 8) return;
        lastAttackerLocalCtrl = localPlayer(buf.get() & 0xFF);
        buf.get(); // aLoc
        lastAttackerSeq = buf.get() & 0xFF;
        buf.get(); // aPos
        lastDefenderLocalCtrl = localPlayer(buf.get() & 0xFF);
        int dLoc = buf.get() & 0xFF;
        lastDefenderSeq = (dLoc != 0) ? (buf.get() & 0xFF) : -1;
        if (dLoc == 0 && buf.remaining() >= 1) buf.get();
        if (buf.remaining() >= 1) buf.get(); // dPos
    }

    /**
     * Authoritative zone snapshot from DuelAnalyzer (sent right before every IDLECMD/BATTLECMD).
     * Reconciles exact slots and current ATK/DEF/position/owner/status for both players.
     */
    public void onUpdateData(ByteBuffer buf) {
        if (buf.remaining() < 2) return;
        int player = localPlayer(buf.get() & 0xFF);
        int loc = buf.get() & 0xFF;
        if (loc == LOC_MZONE || loc == LOC_SZONE) {
            CardSlot[] arr = (loc == LOC_MZONE) ? mzone[player] : szone[player];
            for (int i = 0; i < arr.length && buf.remaining() >= 4; i++) {
                int len = buf.getInt();
                if (len < 4) break;
                int next = buf.position() + (len - 4);
                if (next < buf.position() || next > buf.limit()) break;
                if (len <= 8) {
                    // Empty fixed slot (len == 4)
                    if (len == 4) arr[i] = null;
                } else {
                    if (arr[i] == null) {
                        arr[i] = new CardSlot();
                        arr[i].controller = player;
                        arr[i].owner = player;
                        arr[i].location = loc;
                        arr[i].sequence = i;
                    }
                    ByteBuffer sub = buf.slice().order(ByteOrder.LITTLE_ENDIAN);
                    sub.limit(Math.min(sub.limit(), len - 4));
                    arr[i].applyQuery(sub, this);
                }
                buf.position(next);
            }
        } else {
            List<CardSlot> list = getDynamicList(player, loc);
            if (list == null) return;
            List<CardSlot> updated = new ArrayList<>();
            int idx = 0;
            while (buf.remaining() >= 4) {
                int len = buf.getInt();
                if (len < 4) break;
                int next = buf.position() + (len - 4);
                if (next < buf.position() || next > buf.limit()) break;
                CardSlot slot = (idx < list.size() && list.get(idx) != null)
                        ? list.get(idx)
                        : new CardSlot();
                slot.controller = player;
                slot.location = loc;
                slot.sequence = idx;
                if (len > 8) {
                    ByteBuffer sub = buf.slice().order(ByteOrder.LITTLE_ENDIAN);
                    sub.limit(Math.min(sub.limit(), len - 4));
                    slot.applyQuery(sub, this);
                }
                updated.add(slot);
                idx++;
                buf.position(next);
            }
            list.clear();
            list.addAll(updated);
        }
    }

    public void onUpdateCard(ByteBuffer buf) {
        if (buf.remaining() < 7) return;
        int player = localPlayer(buf.get() & 0xFF);
        int loc = buf.get() & 0xFF;
        int seq = buf.get() & 0xFF;
        int len = buf.getInt();
        if (len <= 8 || buf.remaining() < len - 4) return;
        CardSlot c = getCard(player, loc, seq);
        if (c == null && (loc == LOC_MZONE || loc == LOC_SZONE)) {
            c = new CardSlot();
            c.controller = player;
            c.owner = player;
            c.location = loc;
            c.sequence = seq;
            placeCard(c, player, loc, seq);
        }
        if (c != null) {
            ByteBuffer sub = buf.slice().order(ByteOrder.LITTLE_ENDIAN);
            sub.limit(Math.min(sub.limit(), len - 4));
            c.applyQuery(sub, this);
        }
    }

    /** Handles Undo full field reload cleanly (MSG_RELOAD_FIELD, followed by MSG_UPDATE_DATA bursts). */
    public void onReloadField(ByteBuffer buf) {
        if (buf.remaining() < 1) return;
        duelRule = buf.get() & 0xFF;
        Arrays.fill(mzone[0], null);
        Arrays.fill(mzone[1], null);
        Arrays.fill(szone[0], null);
        Arrays.fill(szone[1], null);
        hand[0].clear();
        hand[1].clear();
        grave[0].clear();
        grave[1].clear();
        extra[0].clear();
        extra[1].clear();
        removed[0].clear();
        removed[1].clear();
        currentChain.clear();
        for (int p = 0; p < 2 && buf.remaining() >= 4; p++) {
            int lpLocal = localPlayer(p);
            lp[lpLocal] = buf.getInt();
            for (int seq = 0; seq < 7 && buf.remaining() >= 1; seq++) {
                int present = buf.get() & 0xFF;
                if (present != 0 && buf.remaining() >= 2) {
                    CardSlot c = new CardSlot();
                    c.controller = lpLocal;
                    c.owner = lpLocal;
                    c.location = LOC_MZONE;
                    c.sequence = seq;
                    c.position = buf.get() & 0xFF;
                    buf.get(); // overlay count
                    mzone[lpLocal][seq] = c;
                }
            }
            for (int seq = 0; seq < 8 && buf.remaining() >= 1; seq++) {
                int present = buf.get() & 0xFF;
                if (present != 0 && buf.remaining() >= 1) {
                    CardSlot c = new CardSlot();
                    c.controller = lpLocal;
                    c.owner = lpLocal;
                    c.location = LOC_SZONE;
                    c.sequence = seq;
                    c.position = buf.get() & 0xFF;
                    szone[lpLocal][seq] = c;
                }
            }
            if (buf.remaining() >= 1) deckCount[lpLocal] = buf.get() & 0xFF;
            if (buf.remaining() >= 1) fillPlaceholder(hand[lpLocal], lpLocal, LOC_HAND, buf.get() & 0xFF);
            if (buf.remaining() >= 1) fillPlaceholder(grave[lpLocal], lpLocal, LOC_GRAVE, buf.get() & 0xFF);
            if (buf.remaining() >= 1) fillPlaceholder(removed[lpLocal], lpLocal, LOC_REMOVED, buf.get() & 0xFF);
            if (buf.remaining() >= 1) fillPlaceholder(extra[lpLocal], lpLocal, LOC_EXTRA, buf.get() & 0xFF);
            if (buf.remaining() >= 1) buf.get(); // extra_p_count
        }
    }

    private void fillPlaceholder(List<CardSlot> list, int player, int loc, int count) {
        list.clear();
        for (int i = 0; i < count; i++) {
            CardSlot c = new CardSlot();
            c.controller = player;
            c.owner = player;
            c.location = loc;
            c.sequence = i;
            list.add(c);
        }
    }

    private List<CardSlot> getDynamicList(int player, int loc) {
        switch (loc) {
            case LOC_HAND:    return hand[player];
            case LOC_GRAVE:   return grave[player];
            case LOC_EXTRA:   return extra[player];
            case LOC_REMOVED: return removed[player];
            default:          return null;
        }
    }

    private CardSlot removeCard(int player, int loc, int seq) {
        if ((loc & LOC_OVERLAY) != 0) return null;
        if (loc == LOC_MZONE) {
            if (seq >= 0 && seq < 7) {
                CardSlot c = mzone[player][seq];
                mzone[player][seq] = null;
                return c;
            }
            return null;
        }
        if (loc == LOC_SZONE) {
            if (seq >= 0 && seq < 8) {
                CardSlot c = szone[player][seq];
                szone[player][seq] = null;
                return c;
            }
            return null;
        }
        List<CardSlot> list = getDynamicList(player, loc);
        if (list != null && seq >= 0 && seq < list.size()) {
            CardSlot removedCard = list.remove(seq);
            for (int i = 0; i < list.size(); i++) list.get(i).sequence = i;
            return removedCard;
        }
        return null;
    }

    private void placeCard(CardSlot card, int player, int loc, int seq) {
        if ((loc & LOC_OVERLAY) != 0 || loc == 0) return;
        if (loc == LOC_MZONE) {
            if (seq >= 0 && seq < 7) {
                card.sequence = seq;
                mzone[player][seq] = card;
            }
            return;
        }
        if (loc == LOC_SZONE) {
            if (seq >= 0 && seq < 8) {
                card.sequence = seq;
                szone[player][seq] = card;
            }
            return;
        }
        List<CardSlot> list = getDynamicList(player, loc);
        if (list != null) {
            if (seq >= 0 && seq <= list.size()) {
                list.add(seq, card);
            } else {
                list.add(card);
            }
            for (int i = 0; i < list.size(); i++) list.get(i).sequence = i;
        }
    }
}

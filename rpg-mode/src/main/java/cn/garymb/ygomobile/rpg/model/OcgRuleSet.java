package cn.garymb.ygomobile.rpg.model;

/**
 * Historical OCG rule version snapshot automatically enforced by the timeline engine.
 * Players cannot override the rule era; moving the timeline always selects the latest
 * OCG rule set with effectiveDate <= currentTimelineDate.
 */
public class OcgRuleSet {
    public String id;
    public String name;
    /** ISO-8601 date string YYYY-MM-DD. */
    public String effectiveDate;
    public String description;
    public int startLp = 8000;
    public int startHand = 5;
    /** False under 1999-02-04 Official Rule; true from 1999-05-05 Expert Rule onward. */
    public boolean tributeSummonEnabled = true;
    /** True under 1999-02-04 Official Rule (max 1 Spell + 1 Trap from hand per turn). */
    public boolean spellTrapOncePerTurnLimit = false;
    /** True from 1999-05-05 Expert Rule until 2014-03-20; false in 1999-02-04 Official Rule and MR3+ (2014-03-21). */
    public boolean firstTurnDrawEnabled = false;
    /** True under 1999-02-04 Official Rule (deck-out decides winner by higher LP). */
    public boolean deckOutCompareLp = false;
    /** Corresponding YGOPro / YGOMobile duel_rule parameter (1..5). */
    public int ygoproDuelRule = 1;

    public int requiredTributes(int level) {
        if (!tributeSummonEnabled) return 0;
        if (level >= 7) return 2;
        if (level >= 5) return 1;
        return 0;
    }
}

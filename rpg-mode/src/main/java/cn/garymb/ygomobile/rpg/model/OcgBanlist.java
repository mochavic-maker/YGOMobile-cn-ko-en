package cn.garymb.ygomobile.rpg.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Historical OCG Forbidden & Limited List snapshot effective from effectiveDate.
 */
public class OcgBanlist {
    public String id;
    public String name;
    /** ISO-8601 date string YYYY-MM-DD. */
    public String effectiveDate;
    public String description;
    public List<Integer> forbidden = new ArrayList<>();
    public List<Integer> limited = new ArrayList<>();
    public List<Integer> semiLimited = new ArrayList<>();

    public int getMaxCopies(int cardId) {
        if (forbidden != null && forbidden.contains(cardId)) return 0;
        if (limited != null && limited.contains(cardId)) return 1;
        if (semiLimited != null && semiLimited.contains(cardId)) return 2;
        return 3;
    }

    public String getLimitBadge(int cardId) {
        int max = getMaxCopies(cardId);
        if (max == 0) return "禁止";
        if (max == 1) return "限制";
        if (max == 2) return "准限";
        return "";
    }
}

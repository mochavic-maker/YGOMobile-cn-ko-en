package cn.garymb.ygomobile.rpg.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Historical OCG errata record for a single card across all official Konami revisions.
 */
public class OcgCardErrata {
    public int cardId;
    public String nameCn;
    public String nameJp;
    public String nameEn;
    public String era;
    public String category; // UNBAN_ERRATA, EFFECT_ERRATA, TEXT_REVISION
    public boolean in1999Catalog;
    public List<Revision> revisions = new ArrayList<>();

    public static class Revision {
        public int versionIndex;
        public String effectiveDate; // ISO YYYY-MM-DD
        public String sourceLabel;
        public String summary;
        public String descCn;
        public List<String> flags = new ArrayList<>();

        public boolean hasFlag(String flag) {
            return flags != null && flag != null && flags.contains(flag);
        }
    }

    public String getDisplayName() {
        if (nameCn != null && !nameCn.isEmpty()) return nameCn;
        if (nameJp != null && !nameJp.isEmpty()) return nameJp;
        return nameEn != null ? nameEn : String.valueOf(cardId);
    }

    public String getCategoryLabelCn() {
        if ("UNBAN_ERRATA".equals(category)) return "禁止解除勘误";
        if ("EFFECT_CHANGE".equals(category) || "EFFECT_ERRATA".equals(category)) return "官方效果变更";
        if ("EARLY_TEXT_FIX".equals(category) || "TEXT_REVISION".equals(category)) return "初期文本/裁定修正";
        return "效果勘误";
    }
}

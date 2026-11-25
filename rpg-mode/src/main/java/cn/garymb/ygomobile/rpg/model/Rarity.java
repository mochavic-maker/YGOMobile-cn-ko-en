package cn.garymb.ygomobile.rpg.model;

/**
 * OCG card rarity tiers used by the RPG pack simulator and crafting economy.
 */
public enum Rarity {
    N("N", "平卡", "#8B95A5", 10, 30),
    R("R", "银字", "#4DA6FF", 25, 80),
    SR("SR", "面闪", "#C084FC", 80, 240),
    UR("UR", "金闪", "#F59E0B", 200, 600),
    SCR("SCR", "银碎", "#38BDF8", 300, 900);

    public final String code;
    public final String labelCn;
    public final String colorHex;
    public final int defaultDismantleDust;
    public final int defaultCraftCostDust;

    Rarity(String code, String labelCn, String colorHex,
           int defaultDismantleDust, int defaultCraftCostDust) {
        this.code = code;
        this.labelCn = labelCn;
        this.colorHex = colorHex;
        this.defaultDismantleDust = defaultDismantleDust;
        this.defaultCraftCostDust = defaultCraftCostDust;
    }

    public static Rarity fromCode(String code) {
        if (code == null) return N;
        String u = code.trim().toUpperCase();
        if (u.equals("SCR") || u.contains("SECRET")) return SCR;
        switch (u) {
            case "UR":
            case "ULTRA RARE":
                return UR;
            case "SR":
            case "SUPER RARE":
                return SR;
            case "R":
            case "RARE":
            case "NORMAL RARE":
                return R;
            default:
                return N;
        }
    }
}

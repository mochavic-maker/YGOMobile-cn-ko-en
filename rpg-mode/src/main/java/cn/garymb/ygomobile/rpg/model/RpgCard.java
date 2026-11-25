package cn.garymb.ygomobile.rpg.model;

/**
 * Single OCG card entry inside an OCG booster set.
 */
public class RpgCard {
    /** 8-digit Konami passcode matching YGOMobile cards.cdb id. */
    public int id;
    public String setCode;
    public String rarity;
    public String nameCn;
    public String nameJp;
    public String nameEn;
    public String frameType;
    public String cardType;
    public String typesCn;
    public String attribute;
    public String race;
    public int level;
    public int atk;
    public int def;
    public String descCn;

    public Rarity getRarityEnum() {
        return Rarity.fromCode(rarity);
    }

    public boolean isExtraDeckCard() {
        if (frameType == null) return false;
        String ft = frameType.toLowerCase();
        return ft.contains("fusion") || ft.contains("synchro")
                || ft.contains("xyz") || ft.contains("link");
    }

    public boolean isMonster() {
        if (frameType == null) return false;
        String ft = frameType.toLowerCase();
        return !ft.equals("spell") && !ft.equals("trap");
    }

    public String getDisplayName() {
        return (nameCn != null && !nameCn.isEmpty()) ? nameCn : nameEn;
    }
}

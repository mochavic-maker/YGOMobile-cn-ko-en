package cn.garymb.ygomobile.rpg.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Root wrapper for rpg/ocg_errata_history.json (Series 1 ~ Series 13 official OCG errata DB).
 */
public class OcgErrataCatalog {
    public String format = "OCG";
    public String source;
    public int totalCards;
    public int in1999Count;
    public List<OcgCardErrata> records = new ArrayList<>();
}

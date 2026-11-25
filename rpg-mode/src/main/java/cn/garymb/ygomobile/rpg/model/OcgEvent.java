package cn.garymb.ygomobile.rpg.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Historical OCG special promo / giveaway event tied to the timeline (e.g., tournament attendance,
 * guidebook/game promos, Starter Box pre-order bonus).
 */
public class OcgEvent {
    public String id;
    public String name;
    /** ISO-8601 date YYYY-MM-DD when the event begins and its cards enter the OCG timeline pool. */
    public String releaseDate;
    /** Optional historical end date YYYY-MM-DD for display context. */
    public String endDate;
    public String description;
    /** FIXED (grant all rewardCards) or RANDOM_PICK (grant randomPickCount distinct cards). */
    public String mode = "FIXED";
    public int randomPickCount = 0;
    /** Optional duel win requirement to claim tournament prize events. */
    public int requiredWins = 0;
    public List<RpgCard> rewardCards = new ArrayList<>();
}

package cn.garymb.ygomobile.rpg.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;

import cn.garymb.ygomobile.rpg.model.OcgEvent;
import cn.garymb.ygomobile.rpg.model.RpgCard;
import cn.garymb.ygomobile.rpg.model.RpgProfile;

/**
 * Handles claiming historical 1999 OCG special promo & tournament card giveaway events.
 */
public final class EventClaimEngine {
    private EventClaimEngine() {}

    public static class ClaimResult {
        public final boolean success;
        public final String errorMessage;
        public final List<RpgCard> grantedCards;
        public final int autoDismantledCount;
        public final int autoDismantledDust;

        private ClaimResult(boolean success, String errorMessage, List<RpgCard> grantedCards,
                            int autoDismantledCount, int autoDismantledDust) {
            this.success = success;
            this.errorMessage = errorMessage;
            this.grantedCards = grantedCards;
            this.autoDismantledCount = autoDismantledCount;
            this.autoDismantledDust = autoDismantledDust;
        }

        public static ClaimResult error(String msg) {
            return new ClaimResult(false, msg, new ArrayList<>(), 0, 0);
        }

        public static ClaimResult ok(List<RpgCard> cards, int autoDismantledCount, int autoDismantledDust) {
            return new ClaimResult(true, null, cards, autoDismantledCount, autoDismantledDust);
        }
    }

    public static ClaimResult claim(RpgProfile profile, OcgEvent event, Random random) {
        if (profile == null || event == null) {
            return ClaimResult.error("活动参数无效");
        }
        if (profile.claimedEventIds == null) {
            profile.claimedEventIds = new LinkedHashSet<>();
        }
        if (profile.claimedEventIds.contains(event.id)) {
            return ClaimResult.error("该活动特典已经领取过了");
        }
        if (!TimelineEngine.isReleasedOnOrBefore(event.releaseDate, profile.currentTimelineDate)) {
            return ClaimResult.error("该活动开启于 " + event.releaseDate + "，晚于当前时间轴 " + profile.currentTimelineDate);
        }
        if (event.requiredWins > 0 && profile.wins < event.requiredWins) {
            return ClaimResult.error("需先累计赢得 " + event.requiredWins + " 场决斗才可领取（当前胜场：" + profile.wins + "）");
        }
        if (event.rewardCards == null || event.rewardCards.isEmpty()) {
            return ClaimResult.error("活动奖励为空");
        }

        List<RpgCard> granted = new ArrayList<>();
        int dismantledCount = 0;
        int dismantledDust = 0;
        List<RpgCard> toGrant;
        if ("RANDOM_PICK".equalsIgnoreCase(event.mode) && event.randomPickCount > 0) {
            List<RpgCard> pool = new ArrayList<>(event.rewardCards);
            Collections.shuffle(pool, random != null ? random : new Random());
            int count = Math.min(event.randomPickCount, pool.size());
            toGrant = pool.subList(0, count);
        } else {
            toGrant = event.rewardCards;
        }
        for (RpgCard c : toGrant) {
            int qty = Math.max(1, c.quantity);
            for (int q = 0; q < qty; q++) {
                if (profile.getOwnedCount(c.id) >= PackOpeningEngine.MAX_OWNED_COPIES) {
                    int dust = c.getRarityEnum().defaultDismantleDust;
                    profile.dust += dust;
                    dismantledCount++;
                    dismantledDust += dust;
                } else {
                    profile.addCard(c.id, 1);
                }
            }
            granted.add(c);
        }
        profile.claimedEventIds.add(event.id);
        return ClaimResult.ok(granted, dismantledCount, dismantledDust);
    }
}

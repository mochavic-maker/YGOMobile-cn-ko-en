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

        private ClaimResult(boolean success, String errorMessage, List<RpgCard> grantedCards) {
            this.success = success;
            this.errorMessage = errorMessage;
            this.grantedCards = grantedCards;
        }

        public static ClaimResult error(String msg) {
            return new ClaimResult(false, msg, new ArrayList<>());
        }

        public static ClaimResult ok(List<RpgCard> cards) {
            return new ClaimResult(true, null, cards);
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
        if ("RANDOM_PICK".equalsIgnoreCase(event.mode) && event.randomPickCount > 0) {
            List<RpgCard> pool = new ArrayList<>(event.rewardCards);
            Collections.shuffle(pool, random != null ? random : new Random());
            int count = Math.min(event.randomPickCount, pool.size());
            for (int i = 0; i < count; i++) {
                RpgCard c = pool.get(i);
                profile.addCard(c.id, Math.max(1, c.quantity));
                granted.add(c);
            }
        } else {
            for (RpgCard c : event.rewardCards) {
                profile.addCard(c.id, Math.max(1, c.quantity));
                granted.add(c);
            }
        }
        profile.claimedEventIds.add(event.id);
        return ClaimResult.ok(granted);
    }
}

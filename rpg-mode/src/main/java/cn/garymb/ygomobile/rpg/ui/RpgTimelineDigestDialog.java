package cn.garymb.ygomobile.rpg.ui;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.rpg.engine.TimelineEngine;
import cn.garymb.ygomobile.rpg.model.AiOpponent;
import cn.garymb.ygomobile.rpg.model.OcgBanlist;
import cn.garymb.ygomobile.rpg.model.OcgEvent;
import cn.garymb.ygomobile.rpg.model.OcgPack;
import cn.garymb.ygomobile.rpg.model.OcgRuleSet;
import cn.garymb.ygomobile.rpg.model.RpgCard;
import cn.garymb.ygomobile.rpg.model.RpgCatalog;
import cn.garymb.ygomobile.rpg.model.RpgProfile;

/**
 * Rich visual modal displaying everything that happened at a selected OCG timeline date
 * or across a forward timeline jump: new packs/boxes (with covers), promo events, rule revisions,
 * banlist updates, official card errata, and AI opponent unlocks / deck evolutions.
 */
public final class RpgTimelineDigestDialog {

    private RpgTimelineDigestDialog() {}

    public static void show(final Activity activity,
                            final RpgCatalog catalog,
                            final RpgProfile profile,
                            final TimelineEngine.TimelineDigest digest) {
        if (activity == null || digest == null) return;

        final Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable rootBg = new GradientDrawable();
        rootBg.setColor(Color.parseColor("#0B1120"));
        rootBg.setCornerRadius(dp(activity, 14));
        rootBg.setStroke(dp(activity, 1), Color.parseColor("#334155"));
        root.setBackground(rootBg);
        root.setPadding(dp(activity, 14), dp(activity, 12), dp(activity, 14), dp(activity, 12));

        // Title header
        TextView tvTitle = new TextView(activity);
        tvTitle.setText("📰 OCG 历史编年史 · " + digest.getPeriodTitle());
        tvTitle.setTextColor(Color.parseColor("#F8FAFC"));
        tvTitle.setTextSize(16f);
        tvTitle.setTypeface(null, Typeface.BOLD);
        root.addView(tvTitle);

        TextView tvMeta = new TextView(activity);
        tvMeta.setText(digest.getTotalChangeCount() > 0
                ? ("共收录 " + digest.getTotalChangeCount() + " 项历史更新（卡包 / 特典 / 规则 / 禁限表 / 勘误 / AI 进化）")
                : "该日期当日无新发售、规则变更或 AI 调整记录。");
        tvMeta.setTextColor(Color.parseColor("#94A3B8"));
        tvMeta.setTextSize(12f);
        tvMeta.setPadding(0, dp(activity, 4), 0, dp(activity, 8));
        root.addView(tvMeta);

        ScrollView scroll = new ScrollView(activity);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content);
        root.addView(scroll);

        if (digest.getTotalChangeCount() == 0) {
            LinearLayout emptyBox = makeSectionCard(activity, "#334155");
            emptyBox.addView(makeBodyText(activity,
                    "提示：点击顶部时间轴快捷日期（如 1999-02-04、1999-03-18、1999-05-05、1999-08-26、1999-12-16、1999-12-20 等）可直达各个历史节点。",
                    "#CBD5E1"));
            content.addView(emptyBox);
        }

        // 1. New Packs / Boxes / Vending Machines
        if (!digest.newPacks.isEmpty()) {
            content.addView(makeSectionTitle(activity, "📦 新发售卡包 / 礼盒 / 贩卖机（" + digest.newPacks.size() + "）", "#F59E0B"));
            for (final OcgPack pack : digest.newPacks) {
                LinearLayout card = makeSectionCard(activity, "#D97706");
                card.setOrientation(LinearLayout.HORIZONTAL);
                card.setGravity(Gravity.CENTER_VERTICAL);

                ImageView iv = new ImageView(activity);
                LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(activity, 52), dp(activity, 76));
                ilp.setMarginEnd(dp(activity, 10));
                iv.setLayoutParams(ilp);
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                RpgCardArtLoader.loadPackCoverInto(activity, iv, pack.id);
                card.addView(iv);

                LinearLayout info = new LinearLayout(activity);
                info.setOrientation(LinearLayout.VERTICAL);
                info.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                String unit = pack.fixedContents ? "盒/套" : ("VENDING_MACHINE".equals(pack.productType) ? "次" : "包");
                info.addView(makeItemTitle(activity,
                        "[" + pack.getProductTypeLabelCn() + "] " + pack.name, "#FDE68A"));
                info.addView(makeBodyText(activity,
                        "发售日：" + pack.releaseDate + " · " + pack.cardsPerPack + " 张/" + unit
                                + " · 定价 " + pack.priceTokens + " 代币（原价 ¥" + pack.priceTokens + "）",
                        "#94A3B8"));
                if (pack.description != null && !pack.description.isEmpty()) {
                    info.addView(makeBodyText(activity, pack.description, "#E2E8F0"));
                }
                card.addView(info);
                content.addView(card);
            }
        }

        // 2. Promo & Tournament Events
        if (!digest.newEvents.isEmpty()) {
            content.addView(makeSectionTitle(activity, "🎁 新开启特典 / 大会活动（" + digest.newEvents.size() + "）", "#C084FC"));
            for (OcgEvent ev : digest.newEvents) {
                LinearLayout card = makeSectionCard(activity, "#7C3AED");
                card.addView(makeItemTitle(activity, ev.name + "（" + ev.releaseDate + "）", "#E9D5FF"));
                if (ev.description != null && !ev.description.isEmpty()) {
                    card.addView(makeBodyText(activity, ev.description, "#CBD5E1"));
                }
                if (ev.rewardCards != null && !ev.rewardCards.isEmpty()) {
                    List<String> names = new ArrayList<>();
                    for (RpgCard c : ev.rewardCards) {
                        names.add("[" + c.rarity + "] " + c.getDisplayName());
                    }
                    String cond = ev.requiredWins > 0 ? (" · 需累计胜场 ≥ " + ev.requiredWins) : " · 免费领取";
                    card.addView(makeBodyText(activity,
                            "收录特典卡：" + joinList(names, "、") + cond, "#38BDF8"));
                }
                content.addView(card);
            }
        }

        // 3. OCG Rule Changes
        if (!digest.ruleChanges.isEmpty()) {
            content.addView(makeSectionTitle(activity, "📜 OCG 规则改版（" + digest.ruleChanges.size() + "）", "#38BDF8"));
            for (OcgRuleSet rule : digest.ruleChanges) {
                LinearLayout card = makeSectionCard(activity, "#0284C7");
                card.addView(makeItemTitle(activity, rule.name + "（" + rule.effectiveDate + " 生效）", "#7DD3FC"));
                String bullets = "• 初始 LP：" + rule.startLp + "  |  起手：" + rule.startHand + " 张\n"
                        + "• 上级召唤：" + (rule.tributeSummonEnabled ? "需祭品（5~6星1祭品，7星+2祭品）" : "免祭品直接通常召唤") + "\n"
                        + "• 先攻第1回合抽卡：" + (rule.firstTurnDrawEnabled ? "允许抽卡" : "不抽卡") + "\n"
                        + "• 抽干判定：" + (rule.deckOutCompareLp ? "比较双方 LP 决胜（无手牌上限）" : "直接判负");
                card.addView(makeBodyText(activity, bullets, "#E2E8F0"));
                if (rule.description != null && !rule.description.isEmpty()) {
                    card.addView(makeBodyText(activity, rule.description, "#94A3B8"));
                }
                content.addView(card);
            }
        }

        // 4. Banlist Updates
        if (!digest.banlistChanges.isEmpty()) {
            content.addView(makeSectionTitle(activity, "🚫 禁限卡表变更（" + digest.banlistChanges.size() + "）", "#F87171"));
            for (OcgBanlist bl : digest.banlistChanges) {
                LinearLayout card = makeSectionCard(activity, "#DC2626");
                card.addView(makeItemTitle(activity, bl.name + "（" + bl.effectiveDate + " 生效）", "#FCA5A5"));
                if (bl.description != null && !bl.description.isEmpty()) {
                    card.addView(makeBodyText(activity, bl.description, "#CBD5E1"));
                }
                if (bl.forbidden != null && !bl.forbidden.isEmpty()) {
                    card.addView(makeBodyText(activity,
                            "• 禁止卡（0张）：" + resolveCardNames(catalog, bl.forbidden), "#F87171"));
                }
                if (bl.limited != null && !bl.limited.isEmpty()) {
                    card.addView(makeBodyText(activity,
                            "• 限制卡（1张）：" + resolveCardNames(catalog, bl.limited), "#FBBF24"));
                }
                if (bl.semiLimited != null && !bl.semiLimited.isEmpty()) {
                    card.addView(makeBodyText(activity,
                            "• 准限制卡（2张）：" + resolveCardNames(catalog, bl.semiLimited), "#38BDF8"));
                }
                content.addView(card);
            }
        }

        // 5. Official Card Errata
        if (!digest.errataChanges.isEmpty()) {
            content.addView(makeSectionTitle(activity, "📝 官方卡片效果勘误（" + digest.errataChanges.size() + "）", "#FBBF24"));
            for (final TimelineEngine.ErrataChange ec : digest.errataChanges) {
                LinearLayout card = makeSectionCard(activity, "#D97706");
                card.setOrientation(LinearLayout.HORIZONTAL);
                card.setGravity(Gravity.CENTER_VERTICAL);

                ImageView iv = new ImageView(activity);
                LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(activity, 48), dp(activity, 70));
                ilp.setMarginEnd(dp(activity, 10));
                iv.setLayoutParams(ilp);
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                RpgCardArtLoader.loadInto(iv, ec.errata.cardId, dp(activity, 48));
                RpgCard catalogCard = catalog != null ? catalog.findCard(ec.errata.cardId) : null;
                if (catalogCard != null && profile != null) {
                    iv.setOnClickListener(v -> RpgPackVisualDialogs.showCardDetailDialog(activity, catalog, profile, catalogCard));
                }
                card.addView(iv);

                LinearLayout info = new LinearLayout(activity);
                info.setOrientation(LinearLayout.VERTICAL);
                info.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                info.addView(makeItemTitle(activity,
                        ec.errata.getDisplayName() + "（" + ec.revision.effectiveDate + " · " + ec.errata.getCategoryLabelCn() + "）",
                        "#FDE68A"));
                if (ec.revision.summary != null && !ec.revision.summary.isEmpty()) {
                    info.addView(makeBodyText(activity, "变动要点：" + ec.revision.summary, "#F8FAFC"));
                }
                if (ec.previousRevision != null && ec.previousRevision.descCn != null && !ec.previousRevision.descCn.isEmpty()) {
                    info.addView(makeBodyText(activity, "旧版文本：" + ec.previousRevision.descCn, "#94A3B8"));
                }
                if (ec.revision.descCn != null && !ec.revision.descCn.isEmpty()) {
                    info.addView(makeBodyText(activity, "新版文本：" + ec.revision.descCn, "#34D399"));
                }
                card.addView(info);
                content.addView(card);
            }
        }

        // 6. AI Opponents Unlocked / Deck Evolutions
        if (!digest.aiChanges.isEmpty()) {
            content.addView(makeSectionTitle(activity, "🤖 AI 对手登场与卡组调整（" + digest.aiChanges.size() + "）", "#34D399"));
            for (TimelineEngine.AiChange ac : digest.aiChanges) {
                LinearLayout card = makeSectionCard(activity, "#059669");
                card.setOrientation(LinearLayout.HORIZONTAL);
                card.setGravity(Gravity.CENTER_VERTICAL);

                ImageView avatar = new ImageView(activity);
                LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(dp(activity, 46), dp(activity, 46));
                alp.setMarginEnd(dp(activity, 10));
                avatar.setLayoutParams(alp);
                avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
                avatar.setPadding(dp(activity, 2), dp(activity, 2), dp(activity, 2), dp(activity, 2));
                avatar.setBackground(RpgCardArtLoader.createAvatarPlaceholderDrawable());
                RpgCardArtLoader.loadOpponentAvatarInto(activity, avatar, ac.opponent.id);
                card.addView(avatar);

                LinearLayout info = new LinearLayout(activity);
                info.setOrientation(LinearLayout.VERTICAL);
                info.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

                String tag = ac.newlyUnlocked ? "【新对手登场】" : "【卡组进化】";
                info.addView(makeItemTitle(activity,
                        tag + " " + ac.opponent.name + "（" + ac.opponent.title + "）", "#6EE7B7"));
                AiOpponent.EraDeckProfile np = ac.newProfile;
                if (np != null) {
                    String deckLine;
                    if (!ac.newlyUnlocked && ac.previousProfile != null
                            && !ac.previousProfile.deckName.equals(np.deckName)) {
                        deckLine = "卡组调整：" + ac.previousProfile.deckName
                                + "  →  " + np.deckName + " [" + np.eraTag + "]（" + np.effectiveDate + "）";
                    } else {
                        deckLine = "使用卡组：" + np.deckName + " [" + np.eraTag + "]（" + np.effectiveDate + "）";
                    }
                    info.addView(makeBodyText(activity, deckLine, "#38BDF8"));
                    if (np.description != null && !np.description.isEmpty()) {
                        info.addView(makeBodyText(activity, np.description, "#E2E8F0"));
                    }
                }
                card.addView(info);
                content.addView(card);
            }
        }

        // Close button bar
        LinearLayout bottom = new LinearLayout(activity);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.END);
        bottom.setPadding(0, dp(activity, 10), 0, 0);

        Button closeBtn = new Button(activity);
        closeBtn.setText("知道了");
        closeBtn.setTextColor(Color.WHITE);
        closeBtn.setTextSize(13f);
        closeBtn.setAllCaps(false);
        closeBtn.setBackgroundColor(Color.parseColor("#0284C7"));
        closeBtn.setPadding(dp(activity, 18), 0, dp(activity, 18), 0);
        closeBtn.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(activity, 38)));
        closeBtn.setOnClickListener(v -> dialog.dismiss());
        bottom.addView(closeBtn);
        root.addView(bottom);

        dialog.setContentView(root);
        if (dialog.getWindow() != null) {
            DisplayMetrics dm = activity.getResources().getDisplayMetrics();
            dialog.getWindow().setLayout(
                    Math.round(dm.widthPixels * 0.94f),
                    Math.round(dm.heightPixels * 0.88f));
        }
        dialog.show();
    }

    private static String resolveCardNames(RpgCatalog catalog, List<Integer> ids) {
        List<String> names = new ArrayList<>();
        for (Integer id : ids) {
            if (id == null) continue;
            RpgCard c = catalog != null ? catalog.findCard(id) : null;
            names.add(c != null ? c.getDisplayName() : String.valueOf(id));
        }
        return joinList(names, "、");
    }

    private static String joinList(List<String> items, String sep) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(sep);
            sb.append(items.get(i));
        }
        return sb.toString();
    }

    private static TextView makeSectionTitle(Activity activity, String text, String colorHex) {
        TextView tv = new TextView(activity);
        tv.setText(text);
        tv.setTextColor(Color.parseColor(colorHex));
        tv.setTextSize(14f);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setPadding(0, dp(activity, 10), 0, dp(activity, 4));
        return tv;
    }

    private static LinearLayout makeSectionCard(Activity activity, String borderHex) {
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#1E293B"));
        bg.setCornerRadius(dp(activity, 8));
        bg.setStroke(dp(activity, 1), Color.parseColor(borderHex));
        box.setBackground(bg);
        box.setPadding(dp(activity, 10), dp(activity, 8), dp(activity, 10), dp(activity, 8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(activity, 6);
        box.setLayoutParams(lp);
        return box;
    }

    private static TextView makeItemTitle(Activity activity, String text, String colorHex) {
        TextView tv = new TextView(activity);
        tv.setText(text);
        tv.setTextColor(Color.parseColor(colorHex));
        tv.setTextSize(13f);
        tv.setTypeface(null, Typeface.BOLD);
        return tv;
    }

    private static TextView makeBodyText(Activity activity, String text, String colorHex) {
        TextView tv = new TextView(activity);
        tv.setText(text);
        tv.setTextColor(Color.parseColor(colorHex));
        tv.setTextSize(12f);
        tv.setPadding(0, dp(activity, 2), 0, 0);
        return tv;
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}

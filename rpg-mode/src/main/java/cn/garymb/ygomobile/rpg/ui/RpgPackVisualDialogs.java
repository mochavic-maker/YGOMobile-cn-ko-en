package cn.garymb.ygomobile.rpg.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import cn.garymb.ygomobile.rpg.engine.CardErrataEngine;
import cn.garymb.ygomobile.rpg.engine.PackOpeningEngine;
import cn.garymb.ygomobile.rpg.engine.TimelineEngine;
import cn.garymb.ygomobile.rpg.model.OcgBanlist;
import cn.garymb.ygomobile.rpg.model.OcgPack;
import cn.garymb.ygomobile.rpg.model.Rarity;
import cn.garymb.ygomobile.rpg.model.RpgCard;
import cn.garymb.ygomobile.rpg.model.RpgCatalog;
import cn.garymb.ygomobile.rpg.model.RpgProfile;

/**
 * YGOPRODeck Pack Simulator-inspired visual dialogs for:
 * 1) Interactive pack opening with real card artwork, rarity glow borders, flip-to-reveal, and "Open Again"
 * 2) Visual card pool browser with real card artwork, ownership badges, and rarity/missing filters
 * 3) Large card detail preview with timeline-resolved errata text
 */
public final class RpgPackVisualDialogs {

    public interface OnOpenAgainListener {
        void onOpenAgain(OcgPack pack, int count);
    }

    private RpgPackVisualDialogs() {}

    public static void showPackOpeningSimulator(final Activity activity,
                                                final RpgCatalog catalog,
                                                final RpgProfile profile,
                                                final OcgPack pack,
                                                final int count,
                                                final PackOpeningEngine.PackOpenResult result,
                                                final OnOpenAgainListener openAgainListener) {
        final Dialog dialog = createFullWidthDialog(activity);
        final int cols = isLandscape(activity) ? 5 : 3;
        final int cardWidthPx = computeCardWidthPx(activity, cols, 48);
        final int cardHeightPx = Math.round(cardWidthPx * 1.45f);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable rootBg = new GradientDrawable();
        rootBg.setColor(Color.parseColor("#0B1120"));
        rootBg.setCornerRadius(dp(activity, 14));
        rootBg.setStroke(dp(activity, 1), Color.parseColor("#334155"));
        root.setBackground(rootBg);
        root.setPadding(dp(activity, 12), dp(activity, 12), dp(activity, 12), dp(activity, 12));

        // Header
        TextView tvTitle = new TextView(activity);
        String actionWord = pack.fixedContents ? "整套开箱" : "模拟抽卡";
        tvTitle.setText("✨ " + actionWord + " · " + pack.name + " ×" + count);
        tvTitle.setTextColor(Color.parseColor("#F8FAFC"));
        tvTitle.setTextSize(16f);
        tvTitle.setTypeface(null, Typeface.BOLD);
        root.addView(tvTitle);

        // Rarity pull summary
        Map<String, Integer> rarityCounts = new LinkedHashMap<>();
        int newCount = 0;
        for (PackOpeningEngine.PulledCard pc : result.pulledCards) {
            String code = pc.card.getRarityEnum().code;
            rarityCounts.put(code, rarityCounts.getOrDefault(code, 0) + 1);
            if (pc.isNew) newCount++;
        }
        StringBuilder sumSb = new StringBuilder();
        sumSb.append("共获得 ").append(result.pulledCards.size()).append(" 张");
        if (newCount > 0) sumSb.append(" · ★NEW ").append(newCount).append(" 张");
        sumSb.append(" · ");
        boolean first = true;
        for (Map.Entry<String, Integer> e : rarityCounts.entrySet()) {
            if (!first) sumSb.append(" / ");
            sumSb.append(e.getKey()).append("×").append(e.getValue());
            first = false;
        }
        sumSb.append("  |  剩余代币: ").append(profile.tokens);

        TextView tvSummary = new TextView(activity);
        tvSummary.setText(sumSb.toString());
        tvSummary.setTextColor(Color.parseColor("#94A3B8"));
        tvSummary.setTextSize(12f);
        tvSummary.setPadding(0, dp(activity, 4), 0, dp(activity, 8));
        root.addView(tvSummary);

        // Single non-fixed pack (<= 10 cards) starts face-down for flip-to-reveal excitement!
        final boolean flipMode = !pack.fixedContents && count == 1 && result.pulledCards.size() <= 10;
        final List<Runnable> pendingFlips = new ArrayList<>();

        ScrollView scroll = new ScrollView(activity);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        scroll.setLayoutParams(scrollLp);

        GridLayout grid = new GridLayout(activity);
        grid.setColumnCount(cols);
        grid.setUseDefaultMargins(false);
        grid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
        scroll.addView(grid);
        root.addView(scroll);

        for (int i = 0; i < result.pulledCards.size(); i++) {
            final PackOpeningEngine.PulledCard pc = result.pulledCards.get(i);
            final RpgCard card = pc.card;
            final Rarity rarity = card.getRarityEnum();
            final int rarityColor = parseRarityColor(rarity);

            LinearLayout tile = new LinearLayout(activity);
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setGravity(Gravity.CENTER_HORIZONTAL);
            GridLayout.LayoutParams glp = new GridLayout.LayoutParams();
            glp.width = cardWidthPx;
            glp.setMargins(dp(activity, 4), dp(activity, 4), dp(activity, 4), dp(activity, 8));
            tile.setLayoutParams(glp);

            final FrameLayout cardFrame = new FrameLayout(activity);
            cardFrame.setLayoutParams(new LinearLayout.LayoutParams(cardWidthPx, cardHeightPx));
            cardFrame.setPadding(dp(activity, 2), dp(activity, 2), dp(activity, 2), dp(activity, 2));

            final GradientDrawable frameBg = new GradientDrawable();
            frameBg.setCornerRadius(dp(activity, 6));
            final boolean highRarity = rarity == Rarity.UR || rarity == Rarity.SR || rarity == Rarity.SCR;
            frameBg.setColor(Color.parseColor("#1E293B"));
            frameBg.setStroke(dp(activity, highRarity ? 2 : 1),
                    flipMode ? (highRarity ? rarityColor : Color.parseColor("#64748B")) : rarityColor);
            cardFrame.setBackground(frameBg);

            final ImageView ivArt = new ImageView(activity);
            ivArt.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            ivArt.setScaleType(ImageView.ScaleType.FIT_CENTER);
            cardFrame.addView(ivArt);

            // Top-left rarity badge
            final TextView tvRarity = makeBadge(activity, rarity.code, rarityColor, Color.parseColor("#0F172A"));
            FrameLayout.LayoutParams rarLp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP | Gravity.START);
            rarLp.setMargins(dp(activity, 3), dp(activity, 3), 0, 0);
            tvRarity.setLayoutParams(rarLp);
            cardFrame.addView(tvRarity);

            // Top-right NEW or owned count badge
            final TextView tvStatus = pc.isNew
                    ? makeBadge(activity, "★NEW", Color.parseColor("#F59E0B"), Color.parseColor("#0F172A"))
                    : makeBadge(activity, "×" + pc.ownedAfter, Color.parseColor("#CC0F172A"), Color.parseColor("#E2E8F0"));
            FrameLayout.LayoutParams stLp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP | Gravity.END);
            stLp.setMargins(0, dp(activity, 3), dp(activity, 3), 0);
            tvStatus.setLayoutParams(stLp);
            cardFrame.addView(tvStatus);

            // Tap-to-flip hint overlay when face down
            final TextView tvFlipHint = new TextView(activity);
            tvFlipHint.setText(highRarity ? "✨\n点击翻开" : "点击翻开");
            tvFlipHint.setGravity(Gravity.CENTER);
            tvFlipHint.setTextColor(highRarity ? rarityColor : Color.parseColor("#FDE68A"));
            tvFlipHint.setTextSize(11f);
            tvFlipHint.setTypeface(null, Typeface.BOLD);
            tvFlipHint.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));
            cardFrame.addView(tvFlipHint);

            final TextView tvName = new TextView(activity);
            tvName.setTextColor(Color.parseColor("#E2E8F0"));
            tvName.setTextSize(10.5f);
            tvName.setMaxLines(2);
            tvName.setEllipsize(TextUtils.TruncateAt.END);
            tvName.setGravity(Gravity.CENTER_HORIZONTAL);
            tvName.setPadding(dp(activity, 2), dp(activity, 3), dp(activity, 2), 0);

            final boolean[] revealed = new boolean[]{!flipMode};
            final Runnable revealAction = () -> {
                if (revealed[0]) return;
                revealed[0] = true;
                cardFrame.animate()
                        .rotationY(90f)
                        .setDuration(120L)
                        .withEndAction(() -> {
                            tvFlipHint.setVisibility(View.GONE);
                            tvRarity.setVisibility(View.VISIBLE);
                            tvStatus.setVisibility(View.VISIBLE);
                            tvName.setText(card.getDisplayName());
                            frameBg.setStroke(dp(activity, highRarity ? 3 : 1), rarityColor);
                            RpgCardArtLoader.loadInto(ivArt, card.id, cardWidthPx);
                            cardFrame.setRotationY(-90f);
                            cardFrame.animate().rotationY(0f).setDuration(140L).start();
                        })
                        .start();
            };

            if (flipMode) {
                ivArt.setImageDrawable(RpgCardArtLoader.createCardBackDrawable());
                tvRarity.setVisibility(View.INVISIBLE);
                tvStatus.setVisibility(View.INVISIBLE);
                tvFlipHint.setVisibility(View.VISIBLE);
                tvName.setText("???");
                pendingFlips.add(revealAction);
            } else {
                tvFlipHint.setVisibility(View.GONE);
                tvName.setText(card.getDisplayName());
                frameBg.setStroke(dp(activity, highRarity ? 2 : 1), rarityColor);
                RpgCardArtLoader.loadInto(ivArt, card.id, cardWidthPx);
            }

            tile.setOnClickListener(v -> {
                if (!revealed[0]) {
                    revealAction.run();
                } else {
                    showCardDetailDialog(activity, catalog, profile, card);
                }
            });

            tile.addView(cardFrame);
            tile.addView(tvName);
            grid.addView(tile);
        }

        // Bottom action bar
        LinearLayout bottomRow = new LinearLayout(activity);
        bottomRow.setOrientation(LinearLayout.HORIZONTAL);
        bottomRow.setGravity(Gravity.END);
        bottomRow.setPadding(0, dp(activity, 10), 0, 0);

        if (flipMode && !pendingFlips.isEmpty()) {
            Button btnFlipAll = makeDialogButton(activity, "✨ 全部翻开", "#7C3AED");
            btnFlipAll.setOnClickListener(v -> {
                for (int i = 0; i < pendingFlips.size(); i++) {
                    final Runnable r = pendingFlips.get(i);
                    grid.postDelayed(r, i * 90L);
                }
                btnFlipAll.setEnabled(false);
                btnFlipAll.setAlpha(0.5f);
            });
            bottomRow.addView(btnFlipAll);
        }

        int totalCost = pack.priceTokens * count;
        if (openAgainListener != null) {
            String unit = pack.fixedContents ? "套" : ("VENDING_MACHINE".equals(pack.productType) ? "次" : "包");
            Button btnAgain = makeDialogButton(activity,
                    "再来 " + count + " " + unit + " (" + totalCost + ")",
                    profile.tokens >= totalCost ? "#D97706" : "#475569");
            btnAgain.setEnabled(profile.tokens >= totalCost);
            btnAgain.setOnClickListener(v -> {
                dialog.dismiss();
                openAgainListener.onOpenAgain(pack, count);
            });
            bottomRow.addView(btnAgain);
        }

        Button btnClose = makeDialogButton(activity, "收入卡库", "#059669");
        btnClose.setOnClickListener(v -> dialog.dismiss());
        bottomRow.addView(btnClose);

        root.addView(bottomRow);
        dialog.setContentView(root);
        sizeDialogToScreen(activity, dialog, 0.96f, 0.90f);
        dialog.show();
    }

    public static void showPackPoolVisualDialog(final Activity activity,
                                                final RpgCatalog catalog,
                                                final RpgProfile profile,
                                                final OcgPack pack) {
        final Dialog dialog = createFullWidthDialog(activity);
        final int cols = isLandscape(activity) ? 5 : 3;
        final int cardWidthPx = computeCardWidthPx(activity, cols, 48);
        final int cardHeightPx = Math.round(cardWidthPx * 1.45f);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable rootBg = new GradientDrawable();
        rootBg.setColor(Color.parseColor("#0B1120"));
        rootBg.setCornerRadius(dp(activity, 14));
        rootBg.setStroke(dp(activity, 1), Color.parseColor("#334155"));
        root.setBackground(rootBg);
        root.setPadding(dp(activity, 12), dp(activity, 12), dp(activity, 12), dp(activity, 12));

        // Deduplicate & compute progress
        Set<Integer> seenIds = new LinkedHashSet<>();
        final List<RpgCard> uniqueCards = new ArrayList<>();
        int unlockedCount = 0;
        int playset3Count = 0;
        for (RpgCard c : pack.cards) {
            if (seenIds.add(c.id)) {
                uniqueCards.add(c);
                int own = profile.getOwnedCount(c.id);
                if (own >= 1) unlockedCount++;
                if (own >= 3) playset3Count++;
            }
        }

        TextView tvTitle = new TextView(activity);
        tvTitle.setText("🃏 卡池一览 · " + pack.name);
        tvTitle.setTextColor(Color.parseColor("#F8FAFC"));
        tvTitle.setTextSize(16f);
        tvTitle.setTypeface(null, Typeface.BOLD);
        root.addView(tvTitle);

        TextView tvSub = new TextView(activity);
        tvSub.setText("共 " + uniqueCards.size() + " 种 · 已解锁 " + unlockedCount + "/" + uniqueCards.size()
                + " · 已满3张 " + playset3Count + "/" + uniqueCards.size() + "（点击卡图查看详情）");
        tvSub.setTextColor(Color.parseColor("#38BDF8"));
        tvSub.setTextSize(12f);
        tvSub.setPadding(0, dp(activity, 4), 0, dp(activity, 8));
        root.addView(tvSub);

        // Filter bar
        HorizontalScrollView filterScroll = new HorizontalScrollView(activity);
        filterScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout filterRow = new LinearLayout(activity);
        filterRow.setOrientation(LinearLayout.HORIZONTAL);
        filterScroll.addView(filterRow);
        root.addView(filterScroll);

        ScrollView scroll = new ScrollView(activity);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        scrollLp.topMargin = dp(activity, 6);
        scroll.setLayoutParams(scrollLp);

        final GridLayout grid = new GridLayout(activity);
        grid.setColumnCount(cols);
        grid.setUseDefaultMargins(false);
        scroll.addView(grid);
        root.addView(scroll);

        final String[] filters = new String[]{"全部", "未拥有", "高罕(UR/SR/SER)", "R", "N"};
        final String[] activeFilter = new String[]{"全部"};
        final List<Button> filterButtons = new ArrayList<>();

        final Runnable renderPoolGrid = () -> {
            grid.removeAllViews();
            for (Button fb : filterButtons) {
                boolean sel = activeFilter[0].equals(fb.getTag());
                fb.setBackgroundColor(Color.parseColor(sel ? "#0284C7" : "#1E293B"));
            }
            for (final RpgCard card : uniqueCards) {
                int own = profile.getOwnedCount(card.id);
                Rarity r = card.getRarityEnum();
                if ("未拥有".equals(activeFilter[0]) && own > 0) continue;
                if ("高罕(UR/SR/SER)".equals(activeFilter[0])
                        && !(r == Rarity.UR || r == Rarity.SR || r == Rarity.SCR)) continue;
                if ("R".equals(activeFilter[0]) && r != Rarity.R) continue;
                if ("N".equals(activeFilter[0]) && r != Rarity.N) continue;

                int rarityColor = parseRarityColor(r);

                LinearLayout tile = new LinearLayout(activity);
                tile.setOrientation(LinearLayout.VERTICAL);
                tile.setGravity(Gravity.CENTER_HORIZONTAL);
                GridLayout.LayoutParams glp = new GridLayout.LayoutParams();
                glp.width = cardWidthPx;
                glp.setMargins(dp(activity, 4), dp(activity, 4), dp(activity, 4), dp(activity, 8));
                tile.setLayoutParams(glp);

                FrameLayout frame = new FrameLayout(activity);
                frame.setLayoutParams(new LinearLayout.LayoutParams(cardWidthPx, cardHeightPx));
                frame.setPadding(dp(activity, 2), dp(activity, 2), dp(activity, 2), dp(activity, 2));
                GradientDrawable bg = new GradientDrawable();
                bg.setCornerRadius(dp(activity, 6));
                bg.setColor(Color.parseColor("#1E293B"));
                bg.setStroke(dp(activity, own > 0 ? 2 : 1), own > 0 ? rarityColor : Color.parseColor("#334155"));
                frame.setBackground(bg);

                ImageView iv = new ImageView(activity);
                iv.setLayoutParams(new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                iv.setAlpha(own > 0 ? 1.0f : 0.40f);
                RpgCardArtLoader.loadInto(iv, card.id, cardWidthPx);
                frame.addView(iv);

                TextView tvRarity = makeBadge(activity, r.code, rarityColor, Color.parseColor("#0F172A"));
                FrameLayout.LayoutParams rlp = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.TOP | Gravity.START);
                rlp.setMargins(dp(activity, 3), dp(activity, 3), 0, 0);
                tvRarity.setLayoutParams(rlp);
                frame.addView(tvRarity);

                String ownLabel = own <= 0 ? "未拥有" : (own >= 3 ? "满3 (×" + own + ")" : "×" + own);
                int ownBg = own <= 0 ? Color.parseColor("#CC475569")
                        : (own >= 3 ? Color.parseColor("#DD059669") : Color.parseColor("#DD0284C7"));
                TextView tvOwn = makeBadge(activity, ownLabel, ownBg, Color.WHITE);
                FrameLayout.LayoutParams olp = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.BOTTOM | Gravity.END);
                olp.setMargins(0, 0, dp(activity, 3), dp(activity, 3));
                tvOwn.setLayoutParams(olp);
                frame.addView(tvOwn);

                TextView tvName = new TextView(activity);
                tvName.setText(card.getDisplayName());
                tvName.setTextColor(own > 0 ? Color.parseColor("#E2E8F0") : Color.parseColor("#64748B"));
                tvName.setTextSize(10.5f);
                tvName.setMaxLines(2);
                tvName.setEllipsize(TextUtils.TruncateAt.END);
                tvName.setGravity(Gravity.CENTER_HORIZONTAL);
                tvName.setPadding(dp(activity, 2), dp(activity, 3), dp(activity, 2), 0);

                tile.addView(frame);
                tile.addView(tvName);
                tile.setOnClickListener(v -> showCardDetailDialog(activity, catalog, profile, card));
                grid.addView(tile);
            }
        };

        for (final String f : filters) {
            Button fb = new Button(activity);
            fb.setTag(f);
            fb.setText(f);
            fb.setTextSize(11f);
            fb.setAllCaps(false);
            fb.setTextColor(Color.WHITE);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(activity, 32));
            lp.setMarginEnd(dp(activity, 6));
            fb.setLayoutParams(lp);
            fb.setPadding(dp(activity, 10), 0, dp(activity, 10), 0);
            fb.setOnClickListener(v -> {
                activeFilter[0] = f;
                renderPoolGrid.run();
            });
            filterButtons.add(fb);
            filterRow.addView(fb);
        }
        renderPoolGrid.run();

        LinearLayout bottomRow = new LinearLayout(activity);
        bottomRow.setOrientation(LinearLayout.HORIZONTAL);
        bottomRow.setGravity(Gravity.END);
        bottomRow.setPadding(0, dp(activity, 8), 0, 0);
        Button btnClose = makeDialogButton(activity, "关闭", "#475569");
        btnClose.setOnClickListener(v -> dialog.dismiss());
        bottomRow.addView(btnClose);
        root.addView(bottomRow);

        dialog.setContentView(root);
        sizeDialogToScreen(activity, dialog, 0.96f, 0.90f);
        dialog.show();
    }

    public static void showCardDetailDialog(final Activity activity,
                                            final RpgCatalog catalog,
                                            final RpgProfile profile,
                                            final RpgCard card) {
        OcgBanlist banlist = TimelineEngine.getActiveBanlist(catalog, profile.currentTimelineDate);
        String limitBadge = banlist.getLimitBadge(card.id);
        String errataBadge = CardErrataEngine.getVersionBadge(catalog, card.id, profile.currentTimelineDate);
        String effectiveDesc = CardErrataEngine.getEffectiveDescCn(catalog, card, profile.currentTimelineDate);
        int owned = profile.getOwnedCount(card.id);
        Rarity rarity = card.getRarityEnum();

        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);
        layout.setBackgroundColor(Color.parseColor("#0F172A"));
        layout.setPadding(dp(activity, 16), dp(activity, 16), dp(activity, 16), dp(activity, 12));

        ImageView iv = new ImageView(activity);
        int artW = dp(activity, 190);
        int artH = Math.round(artW * 1.45f);
        iv.setLayoutParams(new LinearLayout.LayoutParams(artW, artH));
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        RpgCardArtLoader.loadInto(iv, card.id, artW);
        layout.addView(iv);

        TextView tvName = new TextView(activity);
        tvName.setText("[" + rarity.code + "] " + card.getDisplayName()
                + (limitBadge.isEmpty() ? "" : " 【" + limitBadge + "】"));
        tvName.setTextColor(parseRarityColor(rarity));
        tvName.setTextSize(16f);
        tvName.setTypeface(null, Typeface.BOLD);
        tvName.setGravity(Gravity.CENTER_HORIZONTAL);
        tvName.setPadding(0, dp(activity, 10), 0, dp(activity, 4));
        layout.addView(tvName);

        String stats = card.isMonster()
                ? String.format(Locale.US, "Lv.%d %s/%s  ATK %d / DEF %d  ·  已有 ×%d",
                card.level, card.attribute, card.race, card.atk, card.def, owned)
                : card.cardType + "  ·  已有 ×" + owned;
        TextView tvStats = new TextView(activity);
        tvStats.setText(stats + (errataBadge.isEmpty() ? "" : "\n⟪" + errataBadge + "⟫"));
        tvStats.setTextColor(Color.parseColor("#94A3B8"));
        tvStats.setTextSize(12.5f);
        tvStats.setGravity(Gravity.CENTER_HORIZONTAL);
        layout.addView(tvStats);

        if (!effectiveDesc.isEmpty()) {
            ScrollView descScroll = new ScrollView(activity);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 120));
            dlp.topMargin = dp(activity, 10);
            descScroll.setLayoutParams(dlp);
            TextView tvDesc = new TextView(activity);
            tvDesc.setText(effectiveDesc);
            tvDesc.setTextColor(errataBadge.isEmpty() ? Color.parseColor("#E2E8F0") : Color.parseColor("#FDE68A"));
            tvDesc.setTextSize(13f);
            descScroll.addView(tvDesc);
            layout.addView(descScroll);
        }

        new AlertDialog.Builder(activity)
                .setView(layout)
                .setPositiveButton("关闭", null)
                .show();
    }

    private static Dialog createFullWidthDialog(Activity activity) {
        Dialog d = new Dialog(activity);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        if (d.getWindow() != null) {
            d.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        return d;
    }

    private static void sizeDialogToScreen(Activity activity, Dialog dialog, float widthFraction, float heightFraction) {
        if (dialog.getWindow() == null) return;
        DisplayMetrics dm = activity.getResources().getDisplayMetrics();
        dialog.getWindow().setLayout(
                Math.round(dm.widthPixels * widthFraction),
                Math.round(dm.heightPixels * heightFraction));
    }

    private static boolean isLandscape(Activity activity) {
        return activity.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    private static int computeCardWidthPx(Activity activity, int cols, int horizontalPaddingDp) {
        DisplayMetrics dm = activity.getResources().getDisplayMetrics();
        int dialogW = Math.round(dm.widthPixels * 0.96f);
        int usable = dialogW - dp(activity, horizontalPaddingDp) - (cols * dp(activity, 8));
        return Math.max(dp(activity, 82), usable / cols);
    }

    private static TextView makeBadge(Activity activity, String text, int bgColor, int textColor) {
        TextView tv = new TextView(activity);
        tv.setText(text);
        tv.setTextColor(textColor);
        tv.setTextSize(9.5f);
        tv.setTypeface(null, Typeface.BOLD);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(bgColor);
        bg.setCornerRadius(dp(activity, 4));
        tv.setBackground(bg);
        tv.setPadding(dp(activity, 4), dp(activity, 1), dp(activity, 4), dp(activity, 1));
        return tv;
    }

    private static Button makeDialogButton(Activity activity, String text, String hexColor) {
        Button btn = new Button(activity);
        btn.setText(text);
        btn.setTextSize(12f);
        btn.setAllCaps(false);
        btn.setTextColor(Color.WHITE);
        btn.setBackgroundColor(Color.parseColor(hexColor));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(activity, 38));
        lp.setMarginStart(dp(activity, 8));
        btn.setLayoutParams(lp);
        btn.setPadding(dp(activity, 12), 0, dp(activity, 12), 0);
        return btn;
    }

    private static int parseRarityColor(Rarity rarity) {
        try {
            return Color.parseColor(rarity.colorHex);
        } catch (Exception e) {
            return Color.parseColor("#94A3B8");
        }
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}

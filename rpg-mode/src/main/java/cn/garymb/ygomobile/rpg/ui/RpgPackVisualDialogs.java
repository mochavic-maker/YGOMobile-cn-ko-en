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

import cn.garymb.ygomobile.rpg.ai.RpgAiStrategy;
import cn.garymb.ygomobile.rpg.engine.AiDeckGenerator;
import cn.garymb.ygomobile.rpg.engine.CardErrataEngine;
import cn.garymb.ygomobile.rpg.engine.PackOpeningEngine;
import cn.garymb.ygomobile.rpg.engine.TimelineEngine;
import cn.garymb.ygomobile.rpg.model.AiOpponent;
import cn.garymb.ygomobile.rpg.model.OcgBanlist;
import cn.garymb.ygomobile.rpg.model.OcgEvent;
import cn.garymb.ygomobile.rpg.model.OcgPack;
import cn.garymb.ygomobile.rpg.model.OcgRuleSet;
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
        final int cols = 5; // Fixed 5 cards (1 booster pack) per row
        final int cardWidthPx = computeCardWidthPx(activity, cols, 36);
        final int cardHeightPx = Math.round(cardWidthPx * 1.45f);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable rootBg = new GradientDrawable();
        rootBg.setColor(Color.parseColor("#0B1120"));
        rootBg.setCornerRadius(dp(activity, 14));
        rootBg.setStroke(dp(activity, 1), Color.parseColor("#334155"));
        root.setBackground(rootBg);
        root.setPadding(dp(activity, 10), dp(activity, 10), dp(activity, 10), dp(activity, 10));

        // Header row with authentic pack cover thumbnail + title + rarity pull summary
        LinearLayout headerRow = new LinearLayout(activity);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        headerRow.setPadding(0, 0, 0, dp(activity, 8));

        ImageView ivCover = new ImageView(activity);
        LinearLayout.LayoutParams coverLp = new LinearLayout.LayoutParams(dp(activity, 44), dp(activity, 64));
        coverLp.setMarginEnd(dp(activity, 10));
        ivCover.setLayoutParams(coverLp);
        ivCover.setScaleType(ImageView.ScaleType.FIT_CENTER);
        RpgCardArtLoader.loadPackCoverInto(activity, ivCover, pack.id);
        headerRow.addView(ivCover);

        LinearLayout headerText = new LinearLayout(activity);
        headerText.setOrientation(LinearLayout.VERTICAL);
        headerText.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvTitle = new TextView(activity);
        String actionWord = pack.fixedContents ? "整套开箱" : "模拟抽卡";
        tvTitle.setText("✨ " + actionWord + " · " + pack.name + " ×" + count);
        tvTitle.setTextColor(Color.parseColor("#F8FAFC"));
        tvTitle.setTextSize(15f);
        tvTitle.setTypeface(null, Typeface.BOLD);
        headerText.addView(tvTitle);

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
        if (result.autoDismantledCount > 0) {
            sumSb.append(" · 自动分解 ").append(result.autoDismantledCount)
                    .append(" 张(+").append(result.autoDismantledDust).append("尘)");
        }
        sumSb.append(" · ");
        boolean first = true;
        for (Map.Entry<String, Integer> e : rarityCounts.entrySet()) {
            if (!first) sumSb.append(" / ");
            sumSb.append(e.getKey()).append("×").append(e.getValue());
            first = false;
        }
        sumSb.append("  |  代币: ").append(profile.tokens).append("  尘埃: ").append(profile.dust);

        TextView tvSummary = new TextView(activity);
        tvSummary.setText(sumSb.toString());
        tvSummary.setTextColor(Color.parseColor("#94A3B8"));
        tvSummary.setTextSize(11.5f);
        tvSummary.setPadding(0, dp(activity, 3), 0, 0);
        headerText.addView(tvSummary);

        headerRow.addView(headerText);
        root.addView(headerRow);

        // Single non-fixed pack (<= 10 cards) starts face-down for flip-to-reveal excitement!
        final boolean flipMode = !pack.fixedContents && count == 1 && result.pulledCards.size() <= 10;
        final List<Runnable> pendingFlips = new ArrayList<>();

        ScrollView scroll = new ScrollView(activity);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        scroll.setLayoutParams(scrollLp);

        final LinearLayout rowsContainer = new LinearLayout(activity);
        rowsContainer.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(rowsContainer);
        root.addView(scroll);

        // Group into 1 pack per row (5 cards per row for standard 5-card boosters and fixed sets;
        // for 3-card vending machine multi-pulls, each pull also gets its own clean row).
        int perRow = (!pack.fixedContents && count > 1 && pack.cardsPerPack > 0 && pack.cardsPerPack <= 5)
                ? pack.cardsPerPack
                : cols;
        int totalCards = result.pulledCards.size();
        int packIdx = 0;
        for (int start = 0; start < totalCards; start += perRow) {
            packIdx++;
            int end = Math.min(start + perRow, totalCards);

            if (!pack.fixedContents && count > 1) {
                String unitLabel = "VENDING_MACHINE".equals(pack.productType) ? "次" : "包";
                TextView packHeader = new TextView(activity);
                packHeader.setText("第 " + packIdx + " " + unitLabel);
                packHeader.setTextColor(Color.parseColor("#38BDF8"));
                packHeader.setTextSize(11f);
                packHeader.setTypeface(null, Typeface.BOLD);
                packHeader.setPadding(dp(activity, 2), packIdx == 1 ? 0 : dp(activity, 6), 0, dp(activity, 2));
                rowsContainer.addView(packHeader);
            }

            LinearLayout rowLayout = new LinearLayout(activity);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            rowLayout.setWeightSum(cols);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rowLp.bottomMargin = dp(activity, 6);
            rowLayout.setLayoutParams(rowLp);

            for (int i = start; i < end; i++) {
                final PackOpeningEngine.PulledCard pc = result.pulledCards.get(i);
                final RpgCard card = pc.card;
                final Rarity rarity = card.getRarityEnum();
                final int rarityColor = parseRarityColor(rarity);

                LinearLayout tile = new LinearLayout(activity);
                tile.setOrientation(LinearLayout.VERTICAL);
                tile.setGravity(Gravity.CENTER_HORIZONTAL);
                LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                tlp.setMargins(dp(activity, 2), 0, dp(activity, 2), 0);
                tile.setLayoutParams(tlp);

                final FrameLayout cardFrame = new FrameLayout(activity);
                cardFrame.setLayoutParams(new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, cardHeightPx));
                cardFrame.setPadding(dp(activity, 2), dp(activity, 2), dp(activity, 2), dp(activity, 2));

                final GradientDrawable frameBg = new GradientDrawable();
                frameBg.setCornerRadius(dp(activity, 5));
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
                rarLp.setMargins(dp(activity, 2), dp(activity, 2), 0, 0);
                tvRarity.setLayoutParams(rarLp);
                cardFrame.addView(tvRarity);

                // Top-right NEW / auto-dismantled dust / owned count badge
                final TextView tvStatus;
                if (pc.autoDismantled) {
                    tvStatus = makeBadge(activity, "+" + pc.dustGained + "尘",
                            Color.parseColor("#7C3AED"), Color.WHITE);
                } else if (pc.isNew) {
                    tvStatus = makeBadge(activity, "NEW",
                            Color.parseColor("#F59E0B"), Color.parseColor("#0F172A"));
                } else {
                    tvStatus = makeBadge(activity, "×" + pc.ownedAfter,
                            Color.parseColor("#CC0F172A"), Color.parseColor("#E2E8F0"));
                }
                FrameLayout.LayoutParams stLp = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.TOP | Gravity.END);
                stLp.setMargins(0, dp(activity, 2), dp(activity, 2), 0);
                tvStatus.setLayoutParams(stLp);
                cardFrame.addView(tvStatus);

                // Tap-to-flip hint overlay when face down
                final TextView tvFlipHint = new TextView(activity);
                tvFlipHint.setText(highRarity ? "✨\n翻开" : "翻开");
                tvFlipHint.setGravity(Gravity.CENTER);
                tvFlipHint.setTextColor(highRarity ? rarityColor : Color.parseColor("#FDE68A"));
                tvFlipHint.setTextSize(10f);
                tvFlipHint.setTypeface(null, Typeface.BOLD);
                tvFlipHint.setLayoutParams(new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));
                cardFrame.addView(tvFlipHint);

                final TextView tvName = new TextView(activity);
                tvName.setTextColor(Color.parseColor("#E2E8F0"));
                tvName.setTextSize(9.5f);
                tvName.setMaxLines(2);
                tvName.setEllipsize(TextUtils.TruncateAt.END);
                tvName.setGravity(Gravity.CENTER_HORIZONTAL);
                tvName.setPadding(dp(activity, 1), dp(activity, 2), dp(activity, 1), 0);

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
                rowLayout.addView(tile);
            }

            // Pad remaining slots in row so every card keeps uniform 1/5 width
            for (int pad = end - start; pad < cols; pad++) {
                View spacer = new View(activity);
                spacer.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
                rowLayout.addView(spacer);
            }
            rowsContainer.addView(rowLayout);
        }

        // Bottom action bar
        LinearLayout bottomRow = new LinearLayout(activity);
        bottomRow.setOrientation(LinearLayout.HORIZONTAL);
        bottomRow.setGravity(Gravity.END);
        bottomRow.setPadding(0, dp(activity, 8), 0, 0);

        if (flipMode && !pendingFlips.isEmpty()) {
            Button btnFlipAll = makeDialogButton(activity, "✨ 全部翻开", "#7C3AED");
            btnFlipAll.setOnClickListener(v -> {
                for (int i = 0; i < pendingFlips.size(); i++) {
                    final Runnable r = pendingFlips.get(i);
                    rowsContainer.postDelayed(r, i * 90L);
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

    private static final class PoolTileHolder {
        final int cardId;
        final LinearLayout tile;
        final GradientDrawable frameBg;
        final int normalStrokeColor;
        final int normalStrokeWidthPx;

        PoolTileHolder(int cardId, LinearLayout tile, GradientDrawable frameBg,
                       int normalStrokeColor, int normalStrokeWidthPx) {
            this.cardId = cardId;
            this.tile = tile;
            this.frameBg = frameBg;
            this.normalStrokeColor = normalStrokeColor;
            this.normalStrokeWidthPx = normalStrokeWidthPx;
        }
    }

    public static void showPackPoolVisualDialog(final Activity activity,
                                                final RpgCatalog catalog,
                                                final RpgProfile profile,
                                                final OcgPack pack) {
        showCardPoolSplitDialog(
                activity,
                catalog,
                profile,
                "🃏 卡池 · ",
                pack.name,
                pack.id,
                null,
                pack.fixedContents,
                false,
                null,
                null,
                pack.cards);
    }

    public static void showEventPoolVisualDialog(final Activity activity,
                                                 final RpgCatalog catalog,
                                                 final RpgProfile profile,
                                                 final OcgEvent event) {
        String extraNote = ("RANDOM_PICK".equalsIgnoreCase(event.mode) && event.randomPickCount > 0)
                ? (" · 随机抽 " + event.randomPickCount + " 张")
                : " · 全部赠送";
        showCardPoolSplitDialog(
                activity,
                catalog,
                profile,
                "🎁 特典卡池 · ",
                event.name,
                null,
                extraNote,
                false,
                false,
                null,
                null,
                event.rewardCards);
    }

    public static void showBanlistVisualDialog(final Activity activity,
                                               final RpgCatalog catalog,
                                               final RpgProfile profile,
                                               final OcgBanlist banlist) {
        OcgBanlist resolved = banlist != null
                ? banlist
                : TimelineEngine.getActiveBanlist(catalog, profile.currentTimelineDate);
        List<RpgCard> banCards = new ArrayList<>();
        Set<Integer> added = new LinkedHashSet<>();
        if (resolved != null) {
            appendBanlistCards(catalog, resolved.forbidden, added, banCards);
            appendBanlistCards(catalog, resolved.limited, added, banCards);
            appendBanlistCards(catalog, resolved.semiLimited, added, banCards);
        }
        String name = resolved != null && resolved.name != null ? resolved.name : "当前禁限表";
        String desc = resolved != null && resolved.description != null ? resolved.description : null;
        showCardPoolSplitDialog(
                activity,
                catalog,
                profile,
                "🚫 禁限卡表 · ",
                name,
                null,
                desc,
                false,
                true,
                resolved,
                null,
                banCards);
    }

    public static void showAiDeckVisualDialog(final Activity activity,
                                              final RpgCatalog catalog,
                                              final RpgProfile profile,
                                              final AiOpponent opponent,
                                              final AiDeckGenerator.GeneratedAiDeck aiDeck) {
        Map<Integer, Integer> deckCopies = new LinkedHashMap<>();
        List<RpgCard> orderedCards = new ArrayList<>();
        if (aiDeck != null) {
            collectDeckCards(catalog, aiDeck.main, deckCopies, orderedCards);
            collectDeckCards(catalog, aiDeck.extra, deckCopies, orderedCards);
        }
        AiOpponent.EraDeckProfile era = opponent != null
                ? opponent.getActiveDeckProfile(profile.currentTimelineDate) : null;
        String deckTitle = (opponent != null ? opponent.name : "AI对手")
                + (era != null && era.deckName != null ? (" · " + era.deckName) : "");
        int mainSize = (aiDeck != null && aiDeck.main != null) ? aiDeck.main.size() : 0;
        int extraSize = (aiDeck != null && aiDeck.extra != null) ? aiDeck.extra.size() : 0;
        String style = (aiDeck != null && aiDeck.appliedProfile != null)
                ? RpgAiStrategy.getStyleDisplayName(aiDeck.appliedProfile.fillStyle) : "";
        String subNote = "主卡组 " + mainSize + " 张 · 额外 " + extraSize + " 张"
                + (!style.isEmpty() ? (" · 策略: " + style) : "");
        showCardPoolSplitDialog(
                activity,
                catalog,
                profile,
                "🃏 AI卡组 · ",
                deckTitle,
                null,
                subNote,
                false,
                false,
                null,
                deckCopies,
                orderedCards);
    }

    public static void showCanonDeckVisualDialog(final Activity activity,
                                                 final RpgCatalog catalog,
                                                 final RpgProfile profile,
                                                 final cn.garymb.ygomobile.rpg.engine.CanonDuelDeckBuilder.BuiltCanonDeck builtDeck,
                                                 final String episodeSubtitle) {
        if (builtDeck == null) return;
        Map<Integer, Integer> deckCopies = new LinkedHashMap<>();
        List<RpgCard> orderedCards = new ArrayList<>();
        collectDeckCards(catalog, builtDeck.main, deckCopies, orderedCards);
        collectDeckCards(catalog, builtDeck.extra, deckCopies, orderedCards);
        String subNote = (episodeSubtitle != null ? episodeSubtitle + " · " : "")
                + "原著卡 " + builtDeck.canonMainCount + " 张 + 当期卡池补足 " + builtDeck.paddedMainCount + " 张"
                + "（主卡 " + builtDeck.main.size() + " / 额外 " + builtDeck.extra.size()
                + " · AI风格: " + RpgAiStrategy.getStyleDisplayName(builtDeck.fillStyle) + "）";
        showCardPoolSplitDialog(
                activity,
                catalog,
                profile,
                "🎬 还原卡组 · ",
                builtDeck.deckName,
                null,
                subNote,
                false,
                false,
                null,
                deckCopies,
                orderedCards,
                builtDeck.canonCardIds);
    }

    private static void collectDeckCards(RpgCatalog catalog, List<Integer> ids,
                                         Map<Integer, Integer> counts, List<RpgCard> orderedOut) {
        if (ids == null || catalog == null) return;
        for (Integer id : ids) {
            if (id == null) continue;
            RpgCard card = catalog.findCardOrCanon(id);
            if (card == null) continue;
            Integer prev = counts.get(id);
            if (prev == null) {
                counts.put(id, 1);
                orderedOut.add(card);
            } else {
                counts.put(id, prev + 1);
            }
        }
    }

    private static void appendBanlistCards(RpgCatalog catalog, List<Integer> ids,
                                           Set<Integer> added, List<RpgCard> out) {
        if (ids == null || catalog == null) return;
        for (Integer id : ids) {
            if (id == null || !added.add(id)) continue;
            RpgCard card = catalog.findCard(id);
            if (card != null) {
                out.add(card);
            }
        }
    }

    public static View createPlayerDeckEmbeddedView(final Activity activity,
                                                    final RpgCatalog catalog,
                                                    final RpgProfile profile) {
        Map<Integer, Integer> deckCopies = new LinkedHashMap<>();
        List<RpgCard> orderedCards = new ArrayList<>();
        collectDeckCards(catalog, profile.activeDeckMain, deckCopies, orderedCards);
        collectDeckCards(catalog, profile.activeDeckExtra, deckCopies, orderedCards);
        RpgProfile.SavedDeck activeSaved = profile.findSavedDeck(profile.activeDeckId);
        String deckTitle = activeSaved != null ? activeSaved.name : "当前构筑";
        int mainSize = profile.activeDeckMain != null ? profile.activeDeckMain.size() : 0;
        int extraSize = profile.activeDeckExtra != null ? profile.activeDeckExtra.size() : 0;
        OcgRuleSet activeRule = TimelineEngine.getActiveRuleSet(catalog, profile.currentTimelineDate);
        String inferredStyle = mainSize > 0
                ? RpgAiStrategy.getStyleDisplayName(
                        RpgAiStrategy.inferStyleFromDeck(profile.activeDeckMain, profile.activeDeckExtra, activeRule))
                : "";
        String subNote = "主卡组 " + mainSize + " 张 · 额外 " + extraSize + " 张"
                + (!inferredStyle.isEmpty() ? (" · 风格: " + inferredStyle) : "");
        return buildCardPoolSplitView(
                activity,
                catalog,
                profile,
                "🃏 当前卡组 · ",
                deckTitle,
                null,
                subNote,
                false,
                false,
                null,
                deckCopies,
                orderedCards,
                null,
                true,
                null,
                () -> showPlayerDeckVisualDialog(activity, catalog, profile));
    }

    public static void showPlayerDeckVisualDialog(final Activity activity,
                                                  final RpgCatalog catalog,
                                                  final RpgProfile profile) {
        Map<Integer, Integer> deckCopies = new LinkedHashMap<>();
        List<RpgCard> orderedCards = new ArrayList<>();
        collectDeckCards(catalog, profile.activeDeckMain, deckCopies, orderedCards);
        collectDeckCards(catalog, profile.activeDeckExtra, deckCopies, orderedCards);
        RpgProfile.SavedDeck activeSaved = profile.findSavedDeck(profile.activeDeckId);
        String deckTitle = activeSaved != null ? activeSaved.name : "当前构筑";
        int mainSize = profile.activeDeckMain != null ? profile.activeDeckMain.size() : 0;
        int extraSize = profile.activeDeckExtra != null ? profile.activeDeckExtra.size() : 0;
        OcgRuleSet activeRule = TimelineEngine.getActiveRuleSet(catalog, profile.currentTimelineDate);
        String inferredStyle = mainSize > 0
                ? RpgAiStrategy.getStyleDisplayName(
                        RpgAiStrategy.inferStyleFromDeck(profile.activeDeckMain, profile.activeDeckExtra, activeRule))
                : "";
        String subNote = "主卡组 " + mainSize + " 张 · 额外 " + extraSize + " 张"
                + (!inferredStyle.isEmpty() ? (" · 风格: " + inferredStyle) : "");
        showCardPoolSplitDialog(
                activity,
                catalog,
                profile,
                "🃏 当前卡组 · ",
                deckTitle,
                null,
                subNote,
                false,
                false,
                null,
                deckCopies,
                orderedCards);
    }

    private static void showCardPoolSplitDialog(final Activity activity,
                                                final RpgCatalog catalog,
                                                final RpgProfile profile,
                                                final String titlePrefix,
                                                final String poolName,
                                                final String packCoverIdOrNull,
                                                final String extraSubtitleNote,
                                                final boolean showBoxQuantity,
                                                final boolean banlistMode,
                                                final OcgBanlist explicitBanlist,
                                                final Map<Integer, Integer> deckCopyCounts,
                                                final List<RpgCard> rawCards) {
        showCardPoolSplitDialog(
                activity,
                catalog,
                profile,
                titlePrefix,
                poolName,
                packCoverIdOrNull,
                extraSubtitleNote,
                showBoxQuantity,
                banlistMode,
                explicitBanlist,
                deckCopyCounts,
                rawCards,
                null);
    }

    private static void showCardPoolSplitDialog(final Activity activity,
                                                final RpgCatalog catalog,
                                                final RpgProfile profile,
                                                final String titlePrefix,
                                                final String poolName,
                                                final String packCoverIdOrNull,
                                                final String extraSubtitleNote,
                                                final boolean showBoxQuantity,
                                                final boolean banlistMode,
                                                final OcgBanlist explicitBanlist,
                                                final Map<Integer, Integer> deckCopyCounts,
                                                final List<RpgCard> rawCards,
                                                final Set<Integer> canonCoreIds) {
        final Dialog dialog = createFullWidthDialog(activity);
        View root = buildCardPoolSplitView(
                activity,
                catalog,
                profile,
                titlePrefix,
                poolName,
                packCoverIdOrNull,
                extraSubtitleNote,
                showBoxQuantity,
                banlistMode,
                explicitBanlist,
                deckCopyCounts,
                rawCards,
                canonCoreIds,
                false,
                dialog::dismiss,
                null);
        dialog.setContentView(root);
        sizeDialogToScreen(activity, dialog, 0.97f, 0.94f);
        dialog.show();
    }

    private static View buildCardPoolSplitView(final Activity activity,
                                               final RpgCatalog catalog,
                                               final RpgProfile profile,
                                               final String titlePrefix,
                                               final String poolName,
                                               final String packCoverIdOrNull,
                                               final String extraSubtitleNote,
                                               final boolean showBoxQuantity,
                                               final boolean banlistMode,
                                               final OcgBanlist explicitBanlist,
                                               final Map<Integer, Integer> deckCopyCounts,
                                               final List<RpgCard> rawCards,
                                               final Set<Integer> canonCoreIds,
                                               final boolean embeddedInPage,
                                               final Runnable onCloseAction,
                                               final Runnable onExpandFullscreenAction) {
        final boolean landscape = isLandscape(activity);
        final int cols = landscape ? 6 : 4;
        final int cardWidthPx = computeCardWidthPx(activity, cols, embeddedInPage ? 56 : 36);
        final boolean deckMode = deckCopyCounts != null;
        final OcgBanlist activeBanlist = explicitBanlist != null
                ? explicitBanlist
                : TimelineEngine.getActiveBanlist(catalog, profile.currentTimelineDate);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable rootBg = new GradientDrawable();
        rootBg.setColor(Color.parseColor("#0B1120"));
        rootBg.setCornerRadius(dp(activity, 14));
        rootBg.setStroke(dp(activity, 1), Color.parseColor("#334155"));
        root.setBackground(rootBg);
        root.setPadding(dp(activity, 10), dp(activity, 10), dp(activity, 10), dp(activity, 10));

        // Deduplicate & compute progress + per-filter counts
        Set<Integer> seenIds = new LinkedHashSet<>();
        final List<RpgCard> uniqueCards = new ArrayList<>();
        int unlockedCount = 0;
        int playset3Count = 0;
        int under3Count = 0;
        int highRarityCount = 0;
        int rareCount = 0;
        int normalCount = 0;
        int forbiddenCount = 0;
        int limitedCount = 0;
        int semiLimitedCount = 0;
        int deckTotalCopies = 0;
        int deckMonsterCopies = 0;
        int deckSpellCopies = 0;
        int deckTrapCopies = 0;
        int deckExtraCopies = 0;
        int deckCanonCopies = 0;
        final boolean hasCanonHighlight = deckMode && canonCoreIds != null && !canonCoreIds.isEmpty();
        if (rawCards != null) {
            for (RpgCard c : rawCards) {
                if (c != null && seenIds.add(c.id)) {
                    uniqueCards.add(c);
                    int own = profile.getOwnedCount(c.id);
                    if (own >= 1) unlockedCount++;
                    if (own >= 3) playset3Count++;
                    else under3Count++;
                    Rarity rr = c.getRarityEnum();
                    if (rr == Rarity.UR || rr == Rarity.SR || rr == Rarity.SCR) highRarityCount++;
                    else if (rr == Rarity.R) rareCount++;
                    else if (rr == Rarity.N) normalCount++;
                    int maxCopies = activeBanlist != null ? activeBanlist.getMaxCopies(c.id) : 3;
                    if (maxCopies == 0) forbiddenCount++;
                    else if (maxCopies == 1) limitedCount++;
                    else if (maxCopies == 2) semiLimitedCount++;
                    if (deckMode) {
                        int copies = deckCopyCounts.getOrDefault(c.id, 1);
                        deckTotalCopies += copies;
                        if (hasCanonHighlight && canonCoreIds.contains(c.id)) {
                            deckCanonCopies += copies;
                        }
                        if (c.isExtraDeckCard()) {
                            deckExtraCopies += copies;
                        } else if (c.isMonster()) {
                            deckMonsterCopies += copies;
                        } else if (c.frameType != null && "trap".equalsIgnoreCase(c.frameType)) {
                            deckTrapCopies += copies;
                        } else {
                            deckSpellCopies += copies;
                        }
                    }
                }
            }
        }

        // Compact header bar with pack cover (or first card thumbnail), title, progress, and Close/Fullscreen button
        LinearLayout poolHeader = new LinearLayout(activity);
        poolHeader.setOrientation(LinearLayout.HORIZONTAL);
        poolHeader.setGravity(Gravity.CENTER_VERTICAL);
        poolHeader.setPadding(0, 0, 0, dp(activity, 6));

        ImageView ivPoolCover = new ImageView(activity);
        LinearLayout.LayoutParams pCoverLp = new LinearLayout.LayoutParams(dp(activity, 28), dp(activity, 40));
        pCoverLp.setMarginEnd(dp(activity, 8));
        ivPoolCover.setLayoutParams(pCoverLp);
        ivPoolCover.setScaleType(ImageView.ScaleType.FIT_CENTER);
        if (packCoverIdOrNull != null && !packCoverIdOrNull.isEmpty()) {
            RpgCardArtLoader.loadPackCoverInto(activity, ivPoolCover, packCoverIdOrNull);
            poolHeader.addView(ivPoolCover);
        } else if (!uniqueCards.isEmpty()) {
            RpgCardArtLoader.loadInto(ivPoolCover, uniqueCards.get(0).id, dp(activity, 28));
            poolHeader.addView(ivPoolCover);
        }

        LinearLayout pHeaderText = new LinearLayout(activity);
        pHeaderText.setOrientation(LinearLayout.VERTICAL);
        pHeaderText.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvTitle = new TextView(activity);
        tvTitle.setText(titlePrefix + poolName);
        tvTitle.setTextColor(Color.parseColor("#F8FAFC"));
        tvTitle.setTextSize(14f);
        tvTitle.setTypeface(null, Typeface.BOLD);
        tvTitle.setSingleLine(true);
        tvTitle.setEllipsize(TextUtils.TruncateAt.END);
        pHeaderText.addView(tvTitle);

        TextView tvSub = new TextView(activity);
        if (deckMode) {
            tvSub.setText(extraSubtitleNote != null ? extraSubtitleNote : ("共 " + deckTotalCopies + " 张"));
        } else if (banlistMode) {
            if (uniqueCards.isEmpty()) {
                tvSub.setText("当前时间点无禁限卡片（全卡最多可投入 3 张）");
            } else {
                String eff = activeBanlist != null && activeBanlist.effectiveDate != null
                        ? (" · 生效日 " + activeBanlist.effectiveDate) : "";
                tvSub.setText("禁止 " + forbiddenCount + " · 限制 " + limitedCount
                        + " · 准限 " + semiLimitedCount + eff);
            }
        } else {
            tvSub.setText("共 " + uniqueCards.size() + " 种 · 已解锁 " + unlockedCount + "/" + uniqueCards.size()
                    + " · 满3张 " + playset3Count + "/" + uniqueCards.size()
                    + (extraSubtitleNote != null ? extraSubtitleNote : ""));
        }
        tvSub.setTextColor(Color.parseColor(banlistMode ? "#FCA5A5" : "#38BDF8"));
        tvSub.setTextSize(11f);
        tvSub.setSingleLine(true);
        tvSub.setEllipsize(TextUtils.TruncateAt.END);
        pHeaderText.addView(tvSub);

        poolHeader.addView(pHeaderText);

        if (!embeddedInPage && onCloseAction != null) {
            Button btnClose = makeDialogButton(activity, "关闭", "#475569");
            LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(activity, 32));
            closeLp.setMarginStart(dp(activity, 6));
            btnClose.setLayoutParams(closeLp);
            btnClose.setPadding(dp(activity, 12), 0, dp(activity, 12), 0);
            btnClose.setOnClickListener(v -> onCloseAction.run());
            poolHeader.addView(btnClose);
        } else if (embeddedInPage && onExpandFullscreenAction != null) {
            Button btnFull = makeDialogButton(activity, "🔍 全屏", "#334155");
            LinearLayout.LayoutParams fullLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(activity, 30));
            fullLp.setMarginStart(dp(activity, 6));
            btnFull.setLayoutParams(fullLp);
            btnFull.setPadding(dp(activity, 10), 0, dp(activity, 10), 0);
            btnFull.setOnClickListener(v -> onExpandFullscreenAction.run());
            poolHeader.addView(btnFull);
        }
        root.addView(poolHeader);

        // ==================== TOP PANE: PERSISTENT CARD DETAIL PANEL ====================
        LinearLayout detailPanel = new LinearLayout(activity);
        detailPanel.setOrientation(LinearLayout.HORIZONTAL);
        detailPanel.setGravity(Gravity.TOP);
        LinearLayout.LayoutParams detailLp = embeddedInPage
                ? new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, landscape ? 148 : 172))
                : new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 0, landscape ? 0.42f : 0.36f);
        detailLp.bottomMargin = dp(activity, 6);
        detailPanel.setLayoutParams(detailLp);
        GradientDrawable detailBg = new GradientDrawable();
        detailBg.setColor(Color.parseColor("#111C33"));
        detailBg.setCornerRadius(dp(activity, 10));
        detailBg.setStroke(dp(activity, 1), Color.parseColor("#334155"));
        detailPanel.setBackground(detailBg);
        detailPanel.setPadding(dp(activity, 8), dp(activity, 8), dp(activity, 8), dp(activity, 8));

        final int detailArtW = dp(activity, landscape ? 70 : 96);
        final int detailArtH = Math.round(detailArtW * 1.45f);

        final FrameLayout detailArtFrame = new FrameLayout(activity);
        LinearLayout.LayoutParams artFrameLp = new LinearLayout.LayoutParams(detailArtW, detailArtH);
        artFrameLp.setMarginEnd(dp(activity, 10));
        artFrameLp.gravity = Gravity.CENTER_VERTICAL;
        detailArtFrame.setLayoutParams(artFrameLp);
        detailArtFrame.setPadding(dp(activity, 2), dp(activity, 2), dp(activity, 2), dp(activity, 2));
        final GradientDrawable detailArtBg = new GradientDrawable();
        detailArtBg.setColor(Color.parseColor("#0F172A"));
        detailArtBg.setCornerRadius(dp(activity, 6));
        detailArtBg.setStroke(dp(activity, 2), Color.parseColor("#475569"));
        detailArtFrame.setBackground(detailArtBg);

        final ImageView ivDetailArt = new ImageView(activity);
        ivDetailArt.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        ivDetailArt.setScaleType(ImageView.ScaleType.FIT_CENTER);
        detailArtFrame.addView(ivDetailArt);
        detailPanel.addView(detailArtFrame);

        LinearLayout detailInfoCol = new LinearLayout(activity);
        detailInfoCol.setOrientation(LinearLayout.VERTICAL);
        detailInfoCol.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        final TextView tvDetailName = new TextView(activity);
        tvDetailName.setTextColor(Color.parseColor("#F8FAFC"));
        tvDetailName.setTextSize(14.5f);
        tvDetailName.setTypeface(null, Typeface.BOLD);
        tvDetailName.setMaxLines(2);
        tvDetailName.setEllipsize(TextUtils.TruncateAt.END);
        detailInfoCol.addView(tvDetailName);

        final TextView tvDetailStats = new TextView(activity);
        tvDetailStats.setTextColor(Color.parseColor("#CBD5E1"));
        tvDetailStats.setTextSize(11.5f);
        tvDetailStats.setPadding(0, dp(activity, 2), 0, 0);
        detailInfoCol.addView(tvDetailStats);

        final TextView tvDetailMeta = new TextView(activity);
        tvDetailMeta.setTextColor(Color.parseColor("#38BDF8"));
        tvDetailMeta.setTextSize(11f);
        tvDetailMeta.setPadding(0, dp(activity, 2), 0, dp(activity, 4));
        detailInfoCol.addView(tvDetailMeta);

        View detailDivider = new View(activity);
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 1));
        divLp.bottomMargin = dp(activity, 4);
        detailDivider.setLayoutParams(divLp);
        detailDivider.setBackgroundColor(Color.parseColor("#1E293B"));
        detailInfoCol.addView(detailDivider);

        final ScrollView detailDescScroll = new ScrollView(activity);
        detailDescScroll.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        detailDescScroll.setNestedScrollingEnabled(true);
        final TextView tvDetailDesc = new TextView(activity);
        tvDetailDesc.setTextColor(Color.parseColor("#E2E8F0"));
        tvDetailDesc.setTextSize(12f);
        tvDetailDesc.setLineSpacing(dp(activity, 2), 1.0f);
        detailDescScroll.addView(tvDetailDesc);
        detailInfoCol.addView(detailDescScroll);

        detailPanel.addView(detailInfoCol);
        root.addView(detailPanel);

        // ==================== MIDDLE: COMPACT FILTER BAR ====================
        HorizontalScrollView filterScroll = new HorizontalScrollView(activity);
        filterScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout.LayoutParams filterScrollLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        filterScrollLp.bottomMargin = dp(activity, 6);
        filterScroll.setLayoutParams(filterScrollLp);
        LinearLayout filterRow = new LinearLayout(activity);
        filterRow.setOrientation(LinearLayout.HORIZONTAL);
        filterScroll.addView(filterRow);
        root.addView(filterScroll);

        // ==================== BOTTOM PANE: CARD POOL / DECK GRID ====================
        GradientDrawable listBg = new GradientDrawable();
        listBg.setColor(Color.parseColor("#0F172A"));
        listBg.setCornerRadius(dp(activity, 8));
        listBg.setStroke(dp(activity, 1), Color.parseColor("#1E293B"));

        final LinearLayout gridContainer = new LinearLayout(activity);
        gridContainer.setOrientation(LinearLayout.VERTICAL);
        gridContainer.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final ScrollView gridScroll;
        if (embeddedInPage) {
            gridScroll = null;
            LinearLayout gridWrapper = new LinearLayout(activity);
            gridWrapper.setOrientation(LinearLayout.VERTICAL);
            gridWrapper.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            gridWrapper.setBackground(listBg);
            gridWrapper.setPadding(dp(activity, 4), dp(activity, 4), dp(activity, 4), dp(activity, 4));
            gridWrapper.addView(gridContainer);
            root.addView(gridWrapper);
        } else {
            gridScroll = new ScrollView(activity);
            LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, landscape ? 0.58f : 0.64f);
            gridScroll.setLayoutParams(scrollLp);
            gridScroll.setBackground(listBg);
            gridScroll.setPadding(dp(activity, 4), dp(activity, 4), dp(activity, 4), dp(activity, 4));
            gridScroll.addView(gridContainer);
            root.addView(gridScroll);
        }

        final RpgCard[] selectedCardRef = new RpgCard[1];
        final PoolTileHolder[] selectedHolderRef = new PoolTileHolder[1];
        final Map<Integer, PoolTileHolder> holderByCardId = new LinkedHashMap<>();
        final int finalUnlockedCount = unlockedCount;
        final int finalUnder3Count = under3Count;
        final int finalForbiddenCount = forbiddenCount;
        final int finalLimitedCount = limitedCount;
        final int finalSemiLimitedCount = semiLimitedCount;
        final int finalDeckTotal = deckTotalCopies;
        final int finalDeckMonsters = deckMonsterCopies;
        final int finalDeckSpells = deckSpellCopies;
        final int finalDeckTraps = deckTrapCopies;
        final int finalDeckExtra = deckExtraCopies;
        final int finalDeckCanon = deckCanonCopies;

        final Runnable clearDetailEmpty = () -> {
            selectedCardRef[0] = null;
            selectedHolderRef[0] = null;
            ivDetailArt.setImageDrawable(RpgCardArtLoader.createCardBackDrawable());
            ivDetailArt.setOnClickListener(null);
            detailArtBg.setStroke(dp(activity, 1), Color.parseColor("#334155"));
            if (banlistMode && uniqueCards.isEmpty()) {
                tvDetailName.setText("当前时间点无禁限卡片");
                tvDetailName.setTextColor(Color.parseColor("#34D399"));
                tvDetailStats.setText(extraSubtitleNote != null && !extraSubtitleNote.isEmpty()
                        ? extraSubtitleNote
                        : "当前环境所有已发售卡片均可投入最多 3 张");
                tvDetailMeta.setText("推进时间轴至 1999-08-26 及以后可查看历次禁限卡表");
                tvDetailDesc.setText("");
            } else if (deckMode && uniqueCards.isEmpty()) {
                tvDetailName.setText("当前卡组为空");
                tvDetailName.setTextColor(Color.parseColor("#94A3B8"));
                tvDetailStats.setText("点击上方「🛠️ 原版卡组编辑器」或「智能组卡」开始构筑");
                tvDetailMeta.setText("也可点击「📂 读取卡组」载入已保存构筑");
                tvDetailDesc.setText("");
            } else {
                tvDetailName.setText("当前筛选条件下暂无卡片");
                tvDetailName.setTextColor(Color.parseColor("#94A3B8"));
                tvDetailStats.setText("请切换下方筛选标签查看其余卡片");
                tvDetailMeta.setText("");
                tvDetailDesc.setText("");
            }
        };

        final java.util.function.Consumer<RpgCard> selectCard = card -> {
            if (card == null) {
                clearDetailEmpty.run();
                return;
            }
            selectedCardRef[0] = card;

            // Update tile highlight in-place without rebuilding the grid
            if (selectedHolderRef[0] != null) {
                PoolTileHolder prev = selectedHolderRef[0];
                prev.frameBg.setStroke(prev.normalStrokeWidthPx, prev.normalStrokeColor);
                prev.tile.setBackground(null);
            }
            PoolTileHolder nextHolder = holderByCardId.get(card.id);
            if (nextHolder != null) {
                nextHolder.frameBg.setStroke(dp(activity, 3), Color.parseColor("#38BDF8"));
                GradientDrawable selTileBg = new GradientDrawable();
                selTileBg.setColor(Color.parseColor("#1E3A5F"));
                selTileBg.setCornerRadius(dp(activity, 6));
                nextHolder.tile.setBackground(selTileBg);
                selectedHolderRef[0] = nextHolder;
            } else {
                selectedHolderRef[0] = null;
            }

            // Populate top detail panel
            Rarity rarity = card.getRarityEnum();
            int rarityColor = parseRarityColor(rarity);
            int owned = profile.getOwnedCount(card.id);
            String limitBadge = activeBanlist != null ? activeBanlist.getLimitBadge(card.id) : "";
            String errataBadge = CardErrataEngine.getVersionBadge(catalog, card.id, profile.currentTimelineDate);
            String effectiveDesc = CardErrataEngine.getEffectiveDescCn(catalog, card, profile.currentTimelineDate);

            detailArtBg.setStroke(dp(activity, 2), rarityColor);
            RpgCardArtLoader.loadInto(ivDetailArt, card.id, detailArtW);
            ivDetailArt.setOnClickListener(v -> showCardDetailDialog(activity, catalog, profile, card));

            tvDetailName.setText("[" + rarity.code + "] " + card.getDisplayName()
                    + (limitBadge.isEmpty() ? "" : " 【" + limitBadge + "】"));
            tvDetailName.setTextColor(rarityColor);

            String statsText;
            if (card.isMonster()) {
                String typeTag = (card.typesCn != null && !card.typesCn.isEmpty()) ? (card.typesCn + " · ") : "";
                statsText = String.format(Locale.US, "%sLv.%d %s/%s · ATK %d / DEF %d",
                        typeTag, card.level,
                        card.attribute != null ? card.attribute : "",
                        card.race != null ? card.race : "",
                        card.atk, card.def);
            } else {
                statsText = (card.typesCn != null && !card.typesCn.isEmpty())
                        ? card.typesCn
                        : (card.cardType != null ? card.cardType : "魔法/陷阱");
            }
            tvDetailStats.setText(statsText);

            String ownStr = owned <= 0 ? "我方未拥有" : (owned >= 3 ? "我方已满3 (×" + owned + ")" : "我方已有 ×" + owned);
            StringBuilder metaSb = new StringBuilder();
            if (deckMode) {
                int copies = deckCopyCounts.getOrDefault(card.id, 1);
                if (hasCanonHighlight) {
                    metaSb.append(canonCoreIds.contains(card.id) ? "【原著用卡】" : "【当期补足】").append(" · ");
                }
                metaSb.append(card.isExtraDeckCard() ? "额外投入 ×" : "卡组投入 ×").append(copies).append(" · ");
            } else if (banlistMode && activeBanlist != null) {
                int maxC = activeBanlist.getMaxCopies(card.id);
                metaSb.append(maxC == 0 ? "禁止投入" : ("最多投入 " + maxC + " 张")).append(" · ");
            }
            metaSb.append(deckMode ? ownStr
                    : (owned <= 0 ? "未拥有" : (owned >= 3 ? "已满3 (×" + owned + ")" : "已有 ×" + owned)));
            if (showBoxQuantity && card.quantity > 1) {
                metaSb.append(" · 盒内×").append(card.quantity);
            }
            if (card.setCode != null && !card.setCode.isEmpty()) {
                metaSb.append(" · ").append(card.setCode);
            }
            if (!errataBadge.isEmpty()) {
                metaSb.append(" · ⟪").append(errataBadge).append("⟫");
            }
            tvDetailMeta.setText(metaSb.toString());
            tvDetailMeta.setTextColor(deckMode
                    ? (hasCanonHighlight && canonCoreIds.contains(card.id)
                            ? Color.parseColor("#F472B6") : Color.parseColor("#38BDF8"))
                    : (banlistMode ? Color.parseColor("#FCA5A5")
                    : (owned >= 3 ? Color.parseColor("#34D399")
                    : (owned > 0 ? Color.parseColor("#38BDF8") : Color.parseColor("#94A3B8")))));

            tvDetailDesc.setText(!effectiveDesc.isEmpty() ? effectiveDesc : "（无效果文本）");
            tvDetailDesc.setTextColor(errataBadge.isEmpty()
                    ? Color.parseColor("#E2E8F0")
                    : Color.parseColor("#FDE68A"));
            detailDescScroll.scrollTo(0, 0);
        };

        final String[] filterKeys = deckMode
                ? (hasCanonHighlight
                ? new String[]{"全部", "原著", "怪兽", "魔法", "陷阱", "额外"}
                : new String[]{"全部", "怪兽", "魔法", "陷阱", "额外"})
                : (banlistMode
                ? new String[]{"全部", "禁止", "限制", "准限", "已拥有"}
                : new String[]{"全部", "未拥有", "高罕", "R", "N"});
        final String[] filterLabels = deckMode
                ? (hasCanonHighlight
                ? new String[]{
                        "全部(" + finalDeckTotal + ")",
                        "原著(" + finalDeckCanon + ")",
                        "怪兽(" + finalDeckMonsters + ")",
                        "魔法(" + finalDeckSpells + ")",
                        "陷阱(" + finalDeckTraps + ")",
                        "额外(" + finalDeckExtra + ")"
                }
                : new String[]{
                        "全部(" + finalDeckTotal + ")",
                        "怪兽(" + finalDeckMonsters + ")",
                        "魔法(" + finalDeckSpells + ")",
                        "陷阱(" + finalDeckTraps + ")",
                        "额外(" + finalDeckExtra + ")"
                })
                : (banlistMode
                ? new String[]{
                        "全部(" + uniqueCards.size() + ")",
                        "禁止(" + finalForbiddenCount + ")",
                        "限制(" + finalLimitedCount + ")",
                        "准限(" + finalSemiLimitedCount + ")",
                        "已拥有(" + finalUnlockedCount + ")"
                }
                : new String[]{
                        "全部(" + uniqueCards.size() + ")",
                        "未拥有/缺张(" + finalUnder3Count + ")",
                        "高罕(" + highRarityCount + ")",
                        "R(" + rareCount + ")",
                        "N(" + normalCount + ")"
                });
        final String[] activeFilter = new String[]{"全部"};
        final List<Button> filterButtons = new ArrayList<>();

        final java.util.function.Function<RpgCard, LinearLayout> createTileView = card -> {
            int own = profile.getOwnedCount(card.id);
            Rarity r = card.getRarityEnum();
            int maxCopies = activeBanlist != null ? activeBanlist.getMaxCopies(card.id) : 3;

            int rarityColor = parseRarityColor(r);
            int limitBorderColor = maxCopies == 0 ? Color.parseColor("#EF4444")
                    : (maxCopies == 1 ? Color.parseColor("#F97316") : Color.parseColor("#EAB308"));
            boolean fullBright = deckMode || banlistMode || own > 0;
            boolean isCanonCard = hasCanonHighlight && canonCoreIds.contains(card.id);
            int normalStrokeW = dp(activity, fullBright ? 2 : 1);
            int normalStrokeC = banlistMode ? limitBorderColor
                    : (isCanonCard ? Color.parseColor("#EC4899")
                    : (fullBright ? rarityColor : Color.parseColor("#334155")));

            LinearLayout tile = new LinearLayout(activity);
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setGravity(Gravity.CENTER_HORIZONTAL);
            tile.setPadding(dp(activity, 2), dp(activity, 2), dp(activity, 2), dp(activity, 2));
            GridLayout.LayoutParams glp = new GridLayout.LayoutParams();
            glp.width = cardWidthPx;
            glp.setMargins(dp(activity, 2), dp(activity, 2), dp(activity, 2), dp(activity, 5));
            tile.setLayoutParams(glp);

            FrameLayout frame = new FrameLayout(activity);
            int innerArtW = Math.max(dp(activity, 44), cardWidthPx - dp(activity, 4));
            int innerArtH = Math.round(innerArtW * 1.45f);
            frame.setLayoutParams(new LinearLayout.LayoutParams(innerArtW, innerArtH));
            frame.setPadding(dp(activity, 2), dp(activity, 2), dp(activity, 2), dp(activity, 2));
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(activity, 5));
            bg.setColor(Color.parseColor("#1E293B"));
            bg.setStroke(normalStrokeW, normalStrokeC);
            frame.setBackground(bg);

            ImageView iv = new ImageView(activity);
            iv.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            iv.setAlpha(fullBright ? 1.0f : 0.45f);
            RpgCardArtLoader.loadInto(iv, card.id, innerArtW);
            frame.addView(iv);

            final TextView tvTopLeft;
            if (banlistMode) {
                String badgeText = maxCopies == 0 ? "禁止" : (maxCopies == 1 ? "限制" : "准限");
                int badgeBg = maxCopies == 0 ? Color.parseColor("#DC2626")
                        : (maxCopies == 1 ? Color.parseColor("#EA580C") : Color.parseColor("#CA8A04"));
                int badgeFg = maxCopies == 2 ? Color.parseColor("#0F172A") : Color.WHITE;
                tvTopLeft = makeBadge(activity, badgeText, badgeBg, badgeFg);
            } else {
                tvTopLeft = makeBadge(activity, r.code, rarityColor, Color.parseColor("#0F172A"));
            }
            FrameLayout.LayoutParams rlp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP | Gravity.START);
            rlp.setMargins(dp(activity, 2), dp(activity, 2), 0, 0);
            tvTopLeft.setLayoutParams(rlp);
            frame.addView(tvTopLeft);

            if (isCanonCard) {
                TextView tvCanon = makeBadge(activity, "原著", Color.parseColor("#DDDB2777"), Color.WHITE);
                FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.TOP | Gravity.END);
                clp.setMargins(0, dp(activity, 2), dp(activity, 2), 0);
                tvCanon.setLayoutParams(clp);
                frame.addView(tvCanon);
            }

            final TextView tvBottomRight;
            if (deckMode) {
                int copies = deckCopyCounts.getOrDefault(card.id, 1);
                int copyBg = copies >= 3 ? Color.parseColor("#DD059669")
                        : (copies == 2 ? Color.parseColor("#DD0284C7") : Color.parseColor("#CC475569"));
                tvBottomRight = makeBadge(activity, "×" + copies, copyBg, Color.WHITE);
            } else {
                String ownLabel = own <= 0 ? "未拥有" : (own >= 3 ? "满3" : "×" + own);
                int ownBg = own <= 0 ? Color.parseColor("#CC475569")
                        : (own >= 3 ? Color.parseColor("#DD059669") : Color.parseColor("#DD0284C7"));
                tvBottomRight = makeBadge(activity, ownLabel, ownBg, Color.WHITE);
            }
            FrameLayout.LayoutParams olp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM | Gravity.END);
            olp.setMargins(0, 0, dp(activity, 2), dp(activity, 2));
            tvBottomRight.setLayoutParams(olp);
            frame.addView(tvBottomRight);

            TextView tvName = new TextView(activity);
            tvName.setText(card.getDisplayName());
            tvName.setTextColor(fullBright ? Color.parseColor("#E2E8F0") : Color.parseColor("#64748B"));
            tvName.setTextSize(9.5f);
            tvName.setMaxLines(2);
            tvName.setEllipsize(TextUtils.TruncateAt.END);
            tvName.setGravity(Gravity.CENTER_HORIZONTAL);
            tvName.setPadding(dp(activity, 1), dp(activity, 2), dp(activity, 1), 0);

            tile.addView(frame);
            tile.addView(tvName);

            PoolTileHolder holder = new PoolTileHolder(card.id, tile, bg, normalStrokeC, normalStrokeW);
            holderByCardId.put(card.id, holder);

            tile.setOnClickListener(v -> selectCard.accept(card));
            return tile;
        };

        final Runnable renderPoolGrid = () -> {
            gridContainer.removeAllViews();
            holderByCardId.clear();
            selectedHolderRef[0] = null;
            for (Button fb : filterButtons) {
                boolean sel = activeFilter[0].equals(fb.getTag());
                GradientDrawable fbg = new GradientDrawable();
                fbg.setColor(Color.parseColor(sel ? (banlistMode ? "#DC2626" : "#0284C7") : "#1E293B"));
                fbg.setCornerRadius(dp(activity, 6));
                fb.setBackground(fbg);
            }
            RpgCard firstVisible = null;
            boolean currentStillVisible = false;

            boolean groupedUnownedMode = !deckMode && !banlistMode && "未拥有".equals(activeFilter[0]);
            if (groupedUnownedMode) {
                List<RpgCard> own0 = new ArrayList<>();
                List<RpgCard> own1 = new ArrayList<>();
                List<RpgCard> own2 = new ArrayList<>();
                for (RpgCard card : uniqueCards) {
                    int own = profile.getOwnedCount(card.id);
                    if (own <= 0) own0.add(card);
                    else if (own == 1) own1.add(card);
                    else if (own == 2) own2.add(card);
                }
                List<List<RpgCard>> groups = new ArrayList<>();
                groups.add(own0);
                groups.add(own1);
                groups.add(own2);
                String[] titles = new String[]{
                        "未拥有 · 持有 0 张",
                        "仅拥有 1 张",
                        "仅拥有 2 张"
                };
                String[] accentHexes = new String[]{"#F59E0B", "#38BDF8", "#34D399"};
                boolean addedAnySection = false;
                for (int g = 0; g < groups.size(); g++) {
                    List<RpgCard> list = groups.get(g);
                    if (list.isEmpty()) continue;

                    TextView secHeader = new TextView(activity);
                    secHeader.setText(titles[g] + " (" + list.size() + " 种)");
                    secHeader.setTextColor(Color.parseColor(accentHexes[g]));
                    secHeader.setTextSize(11.5f);
                    secHeader.setTypeface(null, Typeface.BOLD);
                    GradientDrawable hBg = new GradientDrawable();
                    hBg.setColor(Color.parseColor("#1E293B"));
                    hBg.setCornerRadius(dp(activity, 5));
                    secHeader.setBackground(hBg);
                    secHeader.setPadding(dp(activity, 8), dp(activity, 4), dp(activity, 8), dp(activity, 4));
                    LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    hlp.topMargin = addedAnySection ? dp(activity, 8) : dp(activity, 2);
                    hlp.bottomMargin = dp(activity, 4);
                    secHeader.setLayoutParams(hlp);
                    gridContainer.addView(secHeader);

                    GridLayout secGrid = new GridLayout(activity);
                    secGrid.setColumnCount(cols);
                    secGrid.setUseDefaultMargins(false);
                    for (RpgCard card : list) {
                        if (firstVisible == null) firstVisible = card;
                        if (selectedCardRef[0] != null && selectedCardRef[0].id == card.id) {
                            currentStillVisible = true;
                        }
                        secGrid.addView(createTileView.apply(card));
                    }
                    gridContainer.addView(secGrid);
                    addedAnySection = true;
                }
            } else {
                GridLayout grid = new GridLayout(activity);
                grid.setColumnCount(cols);
                grid.setUseDefaultMargins(false);
                for (final RpgCard card : uniqueCards) {
                    int own = profile.getOwnedCount(card.id);
                    Rarity r = card.getRarityEnum();
                    int maxCopies = activeBanlist != null ? activeBanlist.getMaxCopies(card.id) : 3;
                    if (deckMode) {
                        boolean isExtra = card.isExtraDeckCard();
                        boolean isMonster = !isExtra && card.isMonster();
                        boolean isTrap = !isExtra && !isMonster && card.frameType != null && "trap".equalsIgnoreCase(card.frameType);
                        boolean isSpell = !isExtra && !isMonster && !isTrap;
                        if ("原著".equals(activeFilter[0]) && !(hasCanonHighlight && canonCoreIds.contains(card.id))) continue;
                        if ("怪兽".equals(activeFilter[0]) && !isMonster) continue;
                        if ("魔法".equals(activeFilter[0]) && !isSpell) continue;
                        if ("陷阱".equals(activeFilter[0]) && !isTrap) continue;
                        if ("额外".equals(activeFilter[0]) && !isExtra) continue;
                    } else if (banlistMode) {
                        if ("禁止".equals(activeFilter[0]) && maxCopies != 0) continue;
                        if ("限制".equals(activeFilter[0]) && maxCopies != 1) continue;
                        if ("准限".equals(activeFilter[0]) && maxCopies != 2) continue;
                        if ("已拥有".equals(activeFilter[0]) && own <= 0) continue;
                    } else {
                        if ("高罕".equals(activeFilter[0])
                                && !(r == Rarity.UR || r == Rarity.SR || r == Rarity.SCR)) continue;
                        if ("R".equals(activeFilter[0]) && r != Rarity.R) continue;
                        if ("N".equals(activeFilter[0]) && r != Rarity.N) continue;
                    }

                    if (firstVisible == null) firstVisible = card;
                    if (selectedCardRef[0] != null && selectedCardRef[0].id == card.id) {
                        currentStillVisible = true;
                    }
                    grid.addView(createTileView.apply(card));
                }
                gridContainer.addView(grid);
            }

            if (gridScroll != null) {
                gridScroll.scrollTo(0, 0);
            }
            if (currentStillVisible && selectedCardRef[0] != null) {
                selectCard.accept(selectedCardRef[0]);
            } else {
                selectCard.accept(firstVisible);
            }
        };

        for (int i = 0; i < filterKeys.length; i++) {
            final String fKey = filterKeys[i];
            Button fb = new Button(activity);
            fb.setTag(fKey);
            fb.setText(filterLabels[i]);
            fb.setTextSize(11f);
            fb.setAllCaps(false);
            fb.setTextColor(Color.WHITE);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(activity, 28));
            lp.setMarginEnd(dp(activity, 6));
            fb.setLayoutParams(lp);
            fb.setPadding(dp(activity, 10), 0, dp(activity, 10), 0);
            fb.setMinHeight(0);
            fb.setMinWidth(0);
            fb.setOnClickListener(v -> {
                activeFilter[0] = fKey;
                renderPoolGrid.run();
            });
            filterButtons.add(fb);
            filterRow.addView(fb);
        }
        renderPoolGrid.run();
        return root;
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
                card.level,
                card.attribute != null ? card.attribute : "",
                card.race != null ? card.race : "",
                card.atk, card.def, owned)
                : ((card.typesCn != null && !card.typesCn.isEmpty() ? card.typesCn
                : (card.cardType != null ? card.cardType : "魔法/陷阱")) + "  ·  已有 ×" + owned);
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
        int usable = dialogW - dp(activity, horizontalPaddingDp) - (cols * dp(activity, 4));
        return Math.max(dp(activity, 52), usable / cols);
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

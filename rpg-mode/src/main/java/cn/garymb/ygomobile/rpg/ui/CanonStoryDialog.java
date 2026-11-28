package cn.garymb.ygomobile.rpg.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.rpg.model.OcgCanonDuel;
import cn.garymb.ygomobile.rpg.model.RpgProfile;

/**
 * Legacy of the Duelist-style AVG visual-novel dialogue scene for Canon Duels:
 * displays left/right character portraits with active-speaker spotlighting,
 * speaker nameplate, emotion tinting, tap-to-advance dialogue box, Skip control,
 * and outcome-branching post-duel endings (Side A win / Side B win / Draw).
 */
public final class CanonStoryDialog {

    private CanonStoryDialog() {}

    public static void showIntro(Activity activity, OcgCanonDuel duel, Runnable onProceedToDuel) {
        if (duel == null || duel.introDialogue == null || duel.introDialogue.isEmpty()) {
            if (onProceedToDuel != null) onProceedToDuel.run();
            return;
        }
        showDialogue(activity, duel, duel.introDialogue, "🎬 战前剧情", "#F59E0B", "⚔ 进入决斗！", onProceedToDuel);
    }

    public static void showOutro(Activity activity, OcgCanonDuel duel, Runnable onFinished) {
        if (duel == null) {
            if (onFinished != null) onFinished.run();
            return;
        }
        String canonSide = (duel.canonWinnerSide != null && !duel.canonWinnerSide.isEmpty())
                ? duel.canonWinnerSide : "A";
        List<OcgCanonDuel.DialogueLine> lines = duel.getOutroForWinner(canonSide);
        if (lines == null || lines.isEmpty()) {
            if (onFinished != null) onFinished.run();
            return;
        }
        showDialogue(activity, duel, lines, "🏆 战后尾声", "#10B981", "✔ 查看战果", onFinished);
    }

    /**
     * Plays the outcome-matched post-duel AVG dialogue for ALL Canon Duel results
     * (Win, Loss, or Draw) across all 4 challenge modes (ROLE_A, ROLE_B, OWN_VS_A, OWN_VS_B).
     */
    public static void showOutcomeOutro(Activity activity,
                                        OcgCanonDuel duel,
                                        String canonMode,
                                        boolean playerWon,
                                        boolean isDraw,
                                        Runnable onFinished) {
        if (duel == null) {
            if (onFinished != null) onFinished.run();
            return;
        }
        String nameA = duel.sideA != null ? duel.sideA.characterName : "角色A";
        String nameB = duel.sideB != null ? duel.sideB.characterName : "角色B";
        String canonWinner = "B".equalsIgnoreCase(duel.canonWinnerSide) ? "B" : "A";

        // 1) Draw cutscene
        if (isDraw) {
            List<OcgCanonDuel.DialogueLine> drawLines = new ArrayList<>();
            OcgCanonDuel.DialogueLine d1 = new OcgCanonDuel.DialogueLine();
            d1.speakerSide = "NARRATOR";
            d1.speakerName = "平局终场";
            d1.emotion = "normal";
            d1.text = "「" + nameA + "」与「" + nameB + "」激战至最后一刻，双方势均力敌、以平局收场！";
            drawLines.add(d1);
            OcgCanonDuel.DialogueLine d2 = new OcgCanonDuel.DialogueLine();
            d2.speakerSide = "NARRATOR";
            d2.speakerName = "再战预告";
            d2.emotion = "determined";
            d2.text = "这场难解难分的名局尚未彻底画上句号——稍作整顿，随时再次一决高下吧！";
            drawLines.add(d2);
            showDialogue(activity, duel, drawLines, "⚖ 平局终场", "#64748B", "✔ 查看战果", onFinished);
            return;
        }

        // 2) Own-deck challenge modes (filter opponent's matching reaction + narrator wrap-up so absent rival doesn't barge in)
        if (RpgProfile.CANON_MODE_OWN_VS_A.equals(canonMode) || RpgProfile.CANON_MODE_OWN_VS_B.equals(canonMode)) {
            String oppSide = RpgProfile.CANON_MODE_OWN_VS_A.equals(canonMode) ? "A" : "B";
            String oppName = "A".equals(oppSide) ? nameA : nameB;
            String oppLostWinner = "A".equals(oppSide) ? "B" : "A";
            List<OcgCanonDuel.DialogueLine> source = duel.getOutroForWinner(playerWon ? oppLostWinner : oppSide);
            List<OcgCanonDuel.DialogueLine> ownLines = new ArrayList<>();
            if (source != null) {
                for (OcgCanonDuel.DialogueLine dl : source) {
                    if (dl != null && oppSide.equalsIgnoreCase(dl.speakerSide)) {
                        ownLines.add(dl);
                    }
                }
            }
            OcgCanonDuel.DialogueLine wrap = new OcgCanonDuel.DialogueLine();
            wrap.speakerSide = "NARRATOR";
            wrap.speakerName = playerWon ? "自组制胜" : "惜败收场";
            wrap.emotion = playerWon ? "victory" : "normal";
            wrap.text = playerWon
                    ? ("你凭自组卡组成功击败了「" + oppName + "」！原著强敌在你的构筑与操作面前甘拜下风。")
                    : ("「" + oppName + "」凭借原著招牌卡组守住了防线。不妨调整当期自组构筑，再度前来挑战！");
            ownLines.add(wrap);

            String stageLabel = playerWon ? "🏆 自组胜出" : "💔 挑战落败";
            String stageColor = playerWon ? "#10B981" : "#EF4444";
            showDialogue(activity, duel, ownLines, stageLabel, stageColor, "✔ 查看战果", onFinished);
            return;
        }

        // 3) Role-play modes (ROLE_A or ROLE_B)
        boolean playingB = RpgProfile.CANON_MODE_ROLE_B.equals(canonMode);
        String winnerSide = playingB ? (playerWon ? "B" : "A") : (playerWon ? "A" : "B");
        String winnerName = "A".equals(winnerSide) ? nameA : nameB;
        List<OcgCanonDuel.DialogueLine> lines = duel.getOutroForWinner(winnerSide);
        if (lines == null || lines.isEmpty()) {
            if (onFinished != null) onFinished.run();
            return;
        }

        boolean matchesCanon = winnerSide.equalsIgnoreCase(canonWinner);
        final String stageLabel;
        final String stageColor;
        if (playerWon && matchesCanon) {
            stageLabel = "🏆 正史胜利 · " + winnerName + "胜";
            stageColor = "#10B981";
        } else if (playerWon) {
            stageLabel = "⚡ IF 逆转 · " + winnerName + "胜";
            stageColor = "#8B5CF6";
        } else {
            stageLabel = "💔 战败结局 · " + winnerName + "胜";
            stageColor = "#EF4444";
        }
        showDialogue(activity, duel, lines, stageLabel, stageColor, "✔ 查看战果", onFinished);
    }

    public static void showFullStoryReplay(Activity activity, OcgCanonDuel duel) {
        if (activity == null || activity.isFinishing() || duel == null) return;
        boolean hasA = duel.outroSideAWin != null && !duel.outroSideAWin.isEmpty();
        boolean hasB = duel.outroSideBWin != null && !duel.outroSideBWin.isEmpty();
        if (hasA && hasB) {
            String nameA = duel.sideA != null ? duel.sideA.characterName : "角色A";
            String nameB = duel.sideB != null ? duel.sideB.characterName : "角色B";
            boolean bIsCanon = "B".equalsIgnoreCase(duel.canonWinnerSide);
            String[] choices = new String[]{
                    "🎬 战前开场剧情",
                    (bIsCanon ? "⚡ 结局 A：" : "🏆 结局 A：") + nameA + "胜" + (bIsCanon ? "（IF 逆转）" : "（正史）"),
                    (bIsCanon ? "🏆 结局 B：" : "⚡ 结局 B：") + nameB + "胜" + (bIsCanon ? "（正史）" : "（IF 逆转）"),
                    "📖 完整连播（开场 + 正史结局）"
            };
            new AlertDialog.Builder(activity)
                    .setTitle("📖 剧情回放 · " + (duel.title != null ? duel.title : "原著剧情"))
                    .setItems(choices, (dialog, which) -> {
                        if (which == 0) {
                            showDialogue(activity, duel, duel.introDialogue, "🎬 战前剧情", "#F59E0B", "✔ 关闭", null);
                        } else if (which == 1) {
                            showDialogue(activity, duel, duel.outroSideAWin,
                                    (bIsCanon ? "⚡ IF 结局 · " : "🏆 正史结局 · ") + nameA + "胜",
                                    bIsCanon ? "#8B5CF6" : "#10B981", "✔ 关闭", null);
                        } else if (which == 2) {
                            showDialogue(activity, duel, duel.outroSideBWin,
                                    (bIsCanon ? "🏆 正史结局 · " : "⚡ IF 结局 · ") + nameB + "胜",
                                    bIsCanon ? "#10B981" : "#8B5CF6", "✔ 关闭", null);
                        } else {
                            playCombinedCanonReplay(activity, duel);
                        }
                    })
                    .setNegativeButton("取消", null)
                    .show();
            return;
        }
        playCombinedCanonReplay(activity, duel);
    }

    private static void playCombinedCanonReplay(Activity activity, OcgCanonDuel duel) {
        List<OcgCanonDuel.DialogueLine> combined = new ArrayList<>();
        if (duel.introDialogue != null) {
            combined.addAll(duel.introDialogue);
        }
        String canonSide = "B".equalsIgnoreCase(duel.canonWinnerSide) ? "B" : "A";
        List<OcgCanonDuel.DialogueLine> canonOutro = duel.getOutroForWinner(canonSide);
        if (canonOutro != null && !canonOutro.isEmpty()) {
            if (!combined.isEmpty()) {
                OcgCanonDuel.DialogueLine divider = new OcgCanonDuel.DialogueLine();
                divider.speakerSide = "NARRATOR";
                divider.speakerName = "决斗终局";
                divider.emotion = "victory";
                divider.text = "—— 历经激烈交锋，这场载入决斗史册的名局终于分出了胜负 ——";
                combined.add(divider);
            }
            combined.addAll(canonOutro);
        }
        if (combined.isEmpty()) return;
        showDialogue(activity, duel, combined, "📖 正史回顾", "#38BDF8", "✔ 关闭", null);
    }

    private static void showDialogue(Activity activity,
                                     OcgCanonDuel duel,
                                     List<OcgCanonDuel.DialogueLine> lines,
                                     String stageLabel,
                                     String stageColorHex,
                                     String finalButtonText,
                                     Runnable onDone) {
        if (activity == null || activity.isFinishing() || lines == null || lines.isEmpty()) {
            if (onDone != null) onDone.run();
            return;
        }

        final float d = activity.getResources().getDisplayMetrics().density;
        final Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);

        final boolean[] callbackFired = {false};
        final Runnable fireDone = () -> {
            if (!callbackFired[0]) {
                callbackFired[0] = true;
                if (dialog.isShowing()) {
                    dialog.dismiss();
                }
                if (onDone != null) {
                    onDone.run();
                }
            }
        };
        dialog.setOnCancelListener(di -> fireDone.run());

        // Outer container
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(d, 14), dp(d, 12), dp(d, 14), dp(d, 12));
        GradientDrawable rootBg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.parseColor("#0F172A"), Color.parseColor("#090E1A")});
        rootBg.setCornerRadius(dp(d, 14));
        rootBg.setStroke(dp(d, 2), Color.parseColor("#D97706"));
        root.setBackground(rootBg);

        // 1. Header bar (stage pill + title + step counter + Skip)
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView stagePill = new TextView(activity);
        stagePill.setText(stageLabel);
        stagePill.setTextColor(Color.WHITE);
        stagePill.setTextSize(10.5f);
        stagePill.setTypeface(null, Typeface.BOLD);
        stagePill.setPadding(dp(d, 7), dp(d, 2), dp(d, 7), dp(d, 2));
        GradientDrawable pillBg = new GradientDrawable();
        pillBg.setColor(Color.parseColor(stageColorHex));
        pillBg.setCornerRadius(dp(d, 8));
        stagePill.setBackground(pillBg);
        header.addView(stagePill);

        TextView titleTv = new TextView(activity);
        titleTv.setText(duel.title != null ? duel.title : "原著剧情");
        titleTv.setTextColor(Color.parseColor("#FDE68A"));
        titleTv.setTextSize(13f);
        titleTv.setTypeface(null, Typeface.BOLD);
        titleTv.setMaxLines(1);
        titleTv.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleLp.setMarginStart(dp(d, 8));
        titleLp.setMarginEnd(dp(d, 6));
        titleTv.setLayoutParams(titleLp);
        header.addView(titleTv);

        TextView progressTv = new TextView(activity);
        progressTv.setTextColor(Color.parseColor("#94A3B8"));
        progressTv.setTextSize(11f);
        progressTv.setPadding(0, 0, dp(d, 8), 0);
        header.addView(progressTv);

        Button skipBtn = new Button(activity);
        skipBtn.setText("跳过 ⏭");
        skipBtn.setAllCaps(false);
        skipBtn.setTextColor(Color.parseColor("#CBD5E1"));
        skipBtn.setTextSize(11f);
        skipBtn.setPadding(dp(d, 8), dp(d, 2), dp(d, 8), dp(d, 2));
        skipBtn.setMinHeight(0);
        skipBtn.setMinimumHeight(0);
        GradientDrawable skipBg = new GradientDrawable();
        skipBg.setColor(Color.parseColor("#1E293B"));
        skipBg.setCornerRadius(dp(d, 8));
        skipBg.setStroke(dp(d, 1), Color.parseColor("#475569"));
        skipBtn.setBackground(skipBg);
        skipBtn.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(d, 28)));
        skipBtn.setOnClickListener(v -> fireDone.run());
        header.addView(skipBtn);
        root.addView(header);

        if (duel.episodeLabel != null && !duel.episodeLabel.isEmpty()) {
            TextView epTv = new TextView(activity);
            epTv.setText(duel.episodeLabel + " · " + duel.timelineDate + " OCG环境");
            epTv.setTextColor(Color.parseColor("#94A3B8"));
            epTv.setTextSize(10.5f);
            epTv.setPadding(0, dp(d, 4), 0, dp(d, 8));
            root.addView(epTv);
        }

        // 2. Portrait Stage (Left = Side A, Right = Side B)
        LinearLayout stageRow = new LinearLayout(activity);
        stageRow.setOrientation(LinearLayout.HORIZONTAL);
        stageRow.setGravity(Gravity.CENTER_VERTICAL);
        stageRow.setPadding(dp(d, 6), dp(d, 8), dp(d, 6), dp(d, 10));
        GradientDrawable stageBg = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.parseColor("#2A1215"), Color.parseColor("#111827"), Color.parseColor("#0C2535")});
        stageBg.setCornerRadius(dp(d, 10));
        stageBg.setStroke(dp(d, 1), Color.parseColor("#334155"));
        stageRow.setBackground(stageBg);

        // Left Portrait (Side A)
        LinearLayout colA = new LinearLayout(activity);
        colA.setOrientation(LinearLayout.VERTICAL);
        colA.setGravity(Gravity.CENTER_HORIZONTAL);
        colA.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ImageView avA = new ImageView(activity);
        avA.setLayoutParams(new LinearLayout.LayoutParams(dp(d, 84), dp(d, 84)));
        avA.setScaleType(ImageView.ScaleType.CENTER_CROP);
        avA.setPadding(dp(d, 3), dp(d, 3), dp(d, 3), dp(d, 3));
        if (duel.sideA != null) {
            RpgCardArtLoader.loadOpponentAvatarInto(activity, avA, duel.sideA.avatarId);
        }
        colA.addView(avA);

        TextView labelA = new TextView(activity);
        labelA.setText(duel.sideA != null ? duel.sideA.characterName : "角色A");
        labelA.setTextColor(Color.parseColor("#FCA5A5"));
        labelA.setTextSize(11.5f);
        labelA.setTypeface(null, Typeface.BOLD);
        labelA.setPadding(0, dp(d, 4), 0, 0);
        colA.addView(labelA);
        stageRow.addView(colA);

        // Center VS emblem
        TextView vsTv = new TextView(activity);
        vsTv.setText("VS");
        vsTv.setTextColor(Color.parseColor("#F59E0B"));
        vsTv.setTextSize(16f);
        vsTv.setTypeface(null, Typeface.BOLD_ITALIC);
        vsTv.setPadding(dp(d, 8), 0, dp(d, 8), 0);
        stageRow.addView(vsTv);

        // Right Portrait (Side B)
        LinearLayout colB = new LinearLayout(activity);
        colB.setOrientation(LinearLayout.VERTICAL);
        colB.setGravity(Gravity.CENTER_HORIZONTAL);
        colB.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ImageView avB = new ImageView(activity);
        avB.setLayoutParams(new LinearLayout.LayoutParams(dp(d, 84), dp(d, 84)));
        avB.setScaleType(ImageView.ScaleType.CENTER_CROP);
        avB.setPadding(dp(d, 3), dp(d, 3), dp(d, 3), dp(d, 3));
        if (duel.sideB != null) {
            RpgCardArtLoader.loadOpponentAvatarInto(activity, avB, duel.sideB.avatarId);
        }
        colB.addView(avB);

        TextView labelB = new TextView(activity);
        labelB.setText(duel.sideB != null ? duel.sideB.characterName : "角色B");
        labelB.setTextColor(Color.parseColor("#A5F3FC"));
        labelB.setTextSize(11.5f);
        labelB.setTypeface(null, Typeface.BOLD);
        labelB.setPadding(0, dp(d, 4), 0, 0);
        colB.addView(labelB);
        stageRow.addView(colB);

        root.addView(stageRow);

        // 3. Dialogue Box (clickable to advance)
        LinearLayout dialogBox = new LinearLayout(activity);
        dialogBox.setOrientation(LinearLayout.VERTICAL);
        dialogBox.setPadding(dp(d, 12), dp(d, 10), dp(d, 12), dp(d, 12));
        LinearLayout.LayoutParams boxLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        boxLp.topMargin = dp(d, 10);
        dialogBox.setLayoutParams(boxLp);

        LinearLayout speakerRow = new LinearLayout(activity);
        speakerRow.setOrientation(LinearLayout.HORIZONTAL);
        speakerRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView speakerBadge = new TextView(activity);
        speakerBadge.setTextColor(Color.WHITE);
        speakerBadge.setTextSize(12f);
        speakerBadge.setTypeface(null, Typeface.BOLD);
        speakerBadge.setPadding(dp(d, 9), dp(d, 3), dp(d, 9), dp(d, 3));
        speakerRow.addView(speakerBadge);

        TextView tapHint = new TextView(activity);
        tapHint.setText("点击对话框继续 ▾");
        tapHint.setTextColor(Color.parseColor("#64748B"));
        tapHint.setTextSize(10f);
        tapHint.setGravity(Gravity.END);
        tapHint.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        speakerRow.addView(tapHint);
        dialogBox.addView(speakerRow);

        ScrollView textScroll = new ScrollView(activity);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(d, 104));
        scrollLp.topMargin = dp(d, 8);
        textScroll.setLayoutParams(scrollLp);

        TextView lineTextTv = new TextView(activity);
        lineTextTv.setTextColor(Color.parseColor("#F8FAFC"));
        lineTextTv.setTextSize(14f);
        lineTextTv.setLineSpacing(dp(d, 3), 1.15f);
        textScroll.addView(lineTextTv);
        dialogBox.addView(textScroll);
        root.addView(dialogBox);

        // 4. Bottom horizontal action row
        LinearLayout btnRow = new LinearLayout(activity);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams btnRowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        btnRowLp.topMargin = dp(d, 10);
        btnRow.setLayoutParams(btnRowLp);

        Button prevBtn = makeButton(activity, d, "◀ 上一句", "#334155");
        LinearLayout.LayoutParams prevLp = new LinearLayout.LayoutParams(0, dp(d, 40), 1f);
        prevLp.setMarginEnd(dp(d, 8));
        prevBtn.setLayoutParams(prevLp);

        Button nextBtn = makeButton(activity, d, "继续 ▶", "#D97706");
        LinearLayout.LayoutParams nextLp = new LinearLayout.LayoutParams(0, dp(d, 40), 1.6f);
        nextBtn.setLayoutParams(nextLp);

        btnRow.addView(prevBtn);
        btnRow.addView(nextBtn);
        root.addView(btnRow);

        final int[] idx = {0};
        final Runnable render = () -> {
            OcgCanonDuel.DialogueLine line = lines.get(idx[0]);
            progressTv.setText((idx[0] + 1) + " / " + lines.size());

            String side = line.speakerSide != null ? line.speakerSide.toUpperCase() : "NARRATOR";
            String accentHex = resolveAccentColor(side, line.emotion);
            int accentColor = Color.parseColor(accentHex);

            // Spotlight active speaker
            if ("A".equals(side)) {
                stylePortrait(avA, d, accentColor, true);
                stylePortrait(avB, d, Color.parseColor("#334155"), false);
                colA.setAlpha(1.0f);
                colB.setAlpha(0.42f);
                colA.setScaleX(1.04f);
                colA.setScaleY(1.04f);
                colB.setScaleX(0.92f);
                colB.setScaleY(0.92f);
            } else if ("B".equals(side)) {
                stylePortrait(avA, d, Color.parseColor("#334155"), false);
                stylePortrait(avB, d, accentColor, true);
                colA.setAlpha(0.42f);
                colB.setAlpha(1.0f);
                colA.setScaleX(0.92f);
                colA.setScaleY(0.92f);
                colB.setScaleX(1.04f);
                colB.setScaleY(1.04f);
            } else {
                stylePortrait(avA, d, Color.parseColor("#475569"), false);
                stylePortrait(avB, d, Color.parseColor("#475569"), false);
                colA.setAlpha(0.68f);
                colB.setAlpha(0.68f);
                colA.setScaleX(0.96f);
                colA.setScaleY(0.96f);
                colB.setScaleX(0.96f);
                colB.setScaleY(0.96f);
            }

            // Speaker badge & box border
            String defaultName = "A".equals(side) && duel.sideA != null ? duel.sideA.characterName
                    : ("B".equals(side) && duel.sideB != null ? duel.sideB.characterName : "旁白");
            String speakerLabel = (line.speakerName != null && !line.speakerName.isEmpty())
                    ? line.speakerName : defaultName;
            speakerBadge.setText(emotionPrefix(line.emotion) + speakerLabel);
            GradientDrawable badgeBg = new GradientDrawable();
            badgeBg.setColor(accentColor);
            badgeBg.setCornerRadius(dp(d, 8));
            speakerBadge.setBackground(badgeBg);

            GradientDrawable boxBg = new GradientDrawable();
            boxBg.setColor(Color.parseColor("#111827"));
            boxBg.setCornerRadius(dp(d, 10));
            boxBg.setStroke(dp(d, 1.5f), accentColor);
            dialogBox.setBackground(boxBg);

            lineTextTv.setText(line.text != null ? line.text : "");
            lineTextTv.setAlpha(0f);
            lineTextTv.animate().alpha(1f).setDuration(180).start();
            textScroll.scrollTo(0, 0);

            prevBtn.setEnabled(idx[0] > 0);
            prevBtn.setAlpha(idx[0] > 0 ? 1.0f : 0.4f);

            boolean isLast = idx[0] >= lines.size() - 1;
            nextBtn.setText(isLast ? finalButtonText : "继续 ▶");
            GradientDrawable nextBg = new GradientDrawable();
            nextBg.setColor(Color.parseColor(isLast ? stageColorHex : "#D97706"));
            nextBg.setCornerRadius(dp(d, 9));
            nextBtn.setBackground(nextBg);
        };

        View.OnClickListener advanceListener = v -> {
            if (idx[0] < lines.size() - 1) {
                idx[0]++;
                render.run();
            } else {
                fireDone.run();
            }
        };
        dialogBox.setOnClickListener(advanceListener);
        lineTextTv.setOnClickListener(advanceListener);
        nextBtn.setOnClickListener(advanceListener);
        prevBtn.setOnClickListener(v -> {
            if (idx[0] > 0) {
                idx[0]--;
                render.run();
            }
        });

        render.run();
        dialog.setContentView(root);
        Window win = dialog.getWindow();
        if (win != null) {
            win.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int screenW = activity.getResources().getDisplayMetrics().widthPixels;
            int targetW = Math.min((int) (screenW * 0.94f), dp(d, 560));
            win.setLayout(targetW, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        dialog.show();
    }

    private static void stylePortrait(ImageView iv, float d, int strokeColor, boolean active) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#0F172A"));
        bg.setCornerRadius(dp(d, 10));
        bg.setStroke(dp(d, active ? 3 : 1), strokeColor);
        iv.setBackground(bg);
    }

    private static String resolveAccentColor(String side, String emotion) {
        if ("fierce".equals(emotion)) return "#DC2626";
        if ("shock".equals(emotion)) return "#EA580C";
        if ("victory".equals(emotion)) return "#059669";
        if ("smirk".equals(emotion)) return "#7C3AED";
        if ("A".equals(side)) return "#DC2626";
        if ("B".equals(side)) return "#0284C7";
        return "#D97706";
    }

    private static String emotionPrefix(String emotion) {
        if ("fierce".equals(emotion)) return "🔥 ";
        if ("shock".equals(emotion)) return "⚡ ";
        if ("smirk".equals(emotion)) return "😏 ";
        if ("victory".equals(emotion)) return "✨ ";
        if ("determined".equals(emotion)) return "⚔ ";
        return "";
    }

    private static Button makeButton(Activity activity, float d, String text, String bgHex) {
        Button b = new Button(activity);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(12.5f);
        b.setTypeface(null, Typeface.BOLD);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor(bgHex));
        bg.setCornerRadius(dp(d, 9));
        b.setBackground(bg);
        return b;
    }

    private static int dp(float density, float value) {
        return Math.round(value * density);
    }
}

package cn.garymb.ygomobile.rpg.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import cn.garymb.ygomobile.rpg.model.AiOpponent;
import cn.garymb.ygomobile.rpg.model.RpgCatalog;
import cn.garymb.ygomobile.rpg.model.RpgProfile;

/**
 * Pure-Java OCG Timeline Tournament Engine (4-player Shop Cup / 8-player Grand Championship).
 * Randomly seeds the player and unlocked AI opponents from the current timeline date into a
 * single-elimination bracket, simulates AI-vs-AI matches under the active timeline's rules & errata,
 * and computes tiered token rewards.
 */
public final class TournamentEngine {
    private TournamentEngine() {}

    public static class Participant {
        public final String id;
        public final String displayName;
        public final boolean isPlayer;
        public final AiOpponent opponent;
        public final String activeDeckName;
        public final String eraTag;
        public final List<Integer> deckMain;
        public final List<Integer> deckExtra;

        public Participant(String id, String displayName, boolean isPlayer, AiOpponent opponent,
                           String activeDeckName, String eraTag,
                           List<Integer> deckMain, List<Integer> deckExtra) {
            this.id = id;
            this.displayName = displayName;
            this.isPlayer = isPlayer;
            this.opponent = opponent;
            this.activeDeckName = activeDeckName;
            this.eraTag = eraTag;
            this.deckMain = deckMain != null ? new ArrayList<>(deckMain) : new ArrayList<Integer>();
            this.deckExtra = deckExtra != null ? new ArrayList<>(deckExtra) : new ArrayList<Integer>();
        }
    }

    public static class Match {
        public final int roundIndex;
        public final int matchIndex;
        public final Participant p1;
        public final Participant p2;
        public Participant winner;
        public String summary = "";
        public List<String> logs = new ArrayList<>();
        public boolean finished = false;

        public Match(int roundIndex, int matchIndex, Participant p1, Participant p2) {
            this.roundIndex = roundIndex;
            this.matchIndex = matchIndex;
            this.p1 = p1;
            this.p2 = p2;
        }

        public boolean involvesPlayer() {
            return (p1 != null && p1.isPlayer) || (p2 != null && p2.isPlayer);
        }

        public Participant getPlayerOpponent() {
            if (p1 != null && p1.isPlayer) return p2;
            if (p2 != null && p2.isPlayer) return p1;
            return null;
        }
    }

    public static class TournamentBracket {
        public final int size;
        public final String timelineDate;
        public final String tournamentName;
        public final List<List<Match>> rounds = new ArrayList<>();
        public int currentRound = 0;
        public boolean finished = false;
        public boolean playerEliminated = false;
        public int playerEliminatedRound = -1;
        public Participant champion = null;
        public boolean rewardClaimed = false;
        public int rewardTokens = 0;
        public String playerPlacementLabel = "";

        public TournamentBracket(int size, String timelineDate, String tournamentName) {
            this.size = size;
            this.timelineDate = timelineDate;
            this.tournamentName = tournamentName;
        }

        public int totalRounds() {
            return size == 8 ? 3 : 2;
        }

        public String getRoundTitle(int roundIndex) {
            int rem = size >> roundIndex;
            if (rem <= 2) return "决赛 (Final)";
            if (rem == 4) return "半决赛 (Top 4)";
            return "1/4 决赛 (Top 8)";
        }

        public Match findPendingPlayerMatch() {
            if (finished || playerEliminated || currentRound >= rounds.size()) return null;
            for (Match m : rounds.get(currentRound)) {
                if (!m.finished && m.involvesPlayer()) return m;
            }
            return null;
        }
    }

    /**
     * Creates a randomized 4-player or 8-player bracket using the player and unlocked AI opponents
     * available at {@code profile.currentTimelineDate}.
     */
    public static TournamentBracket createBracket(RpgCatalog catalog,
                                                  RpgProfile profile,
                                                  int requestedSize,
                                                  Random rng) {
        int size = (requestedSize >= 8) ? 8 : 4;
        String date = profile.currentTimelineDate;
        String title = (size == 8)
                ? ("🏆 " + date + " OCG 8人锦标赛")
                : ("🏅 " + date + " 卡牌屋 4人店赛");

        List<AiOpponent> unlocked = new ArrayList<>();
        for (AiOpponent opp : AiDeckGenerator.getDemoOpponents()) {
            if (TimelineEngine.isReleasedOnOrBefore(opp.unlockDate, date)) {
                unlocked.add(opp);
            }
        }
        if (unlocked.isEmpty()) {
            unlocked.add(AiDeckGenerator.getDemoOpponents().get(0));
        }

        List<AiOpponent> shuffledPool = new ArrayList<>(unlocked);
        Collections.shuffle(shuffledPool, rng);

        List<Participant> entrants = new ArrayList<>();
        entrants.add(new Participant(
                "player",
                "你（玩家）",
                true,
                null,
                "玩家自构 RPG 卡组",
                date,
                profile.activeDeckMain,
                profile.activeDeckExtra
        ));

        int neededAi = size - 1;
        for (int i = 0; i < neededAi; i++) {
            AiOpponent baseOpp;
            String displayName;
            if (i < shuffledPool.size()) {
                baseOpp = shuffledPool.get(i);
                displayName = baseOpp.name;
            } else {
                // Early timeline (< 7 unique AIs unlocked): supplement with regional qualifier clones of lower-tier unlocked AIs
                baseOpp = unlocked.get(rng.nextInt(unlocked.size()));
                displayName = baseOpp.name + "·海选" + (i - shuffledPool.size() + 1);
            }
            AiDeckGenerator.GeneratedAiDeck aiDeck = AiDeckGenerator.generateDeck(catalog, baseOpp, date, rng);
            String deckName = aiDeck.appliedProfile != null ? aiDeck.appliedProfile.deckName : "动态构筑";
            String eraTag = aiDeck.appliedProfile != null ? aiDeck.appliedProfile.eraTag : date;
            entrants.add(new Participant(
                    baseOpp.id + "_" + i,
                    displayName,
                    false,
                    baseOpp,
                    deckName,
                    eraTag,
                    aiDeck.main,
                    aiDeck.extra
            ));
        }

        // Randomize full tournament bracket seeding
        Collections.shuffle(entrants, rng);

        TournamentBracket bracket = new TournamentBracket(size, date, title);
        List<Match> firstRound = new ArrayList<>();
        for (int i = 0; i < size; i += 2) {
            firstRound.add(new Match(0, i / 2, entrants.get(i), entrants.get(i + 1)));
        }
        bracket.rounds.add(firstRound);
        return bracket;
    }

    /**
     * Simulates a single AI-vs-AI match under the tournament's timeline date rules & errata.
     * Guarantees a winner (breaks rare draws by higher remaining LP, then tie-break coin flip).
     */
    public static void simulateAiMatch(RpgCatalog catalog,
                                       Match match,
                                       String currentDate,
                                       Random rng) {
        if (match == null || match.finished || match.p1 == null || match.p2 == null) return;
        QuickDuelSimulator.QuickDuelReport rep = QuickDuelSimulator.simulateBetweenDecks(
                catalog,
                match.p1.displayName,
                match.p1.deckMain,
                match.p2.displayName,
                match.p2.deckMain,
                currentDate,
                rng
        );
        boolean p1Wins;
        String tieNote = "";
        if (!rep.isDraw) {
            p1Wins = rep.playerWon;
        } else if (rep.playerLp != rep.aiLp) {
            p1Wins = rep.playerLp > rep.aiLp;
            tieNote = "（LP 判定）";
        } else {
            p1Wins = rng.nextBoolean();
            tieNote = "（加赛骤死判定）";
        }
        match.winner = p1Wins ? match.p1 : match.p2;
        match.finished = true;
        match.logs = rep.logs;
        match.summary = match.winner.displayName + " 胜 · T" + rep.turns
                + " · LP " + rep.playerLp + ":" + rep.aiLp + tieNote;
    }

    /**
     * Records the player's match result for the current round, resolves all sibling AI-vs-AI matches
     * in the same round, and advances the bracket to the next round (or concludes the tournament).
     */
    public static void recordPlayerMatchResult(RpgCatalog catalog,
                                               TournamentBracket bracket,
                                               boolean playerWon,
                                               String summary,
                                               List<String> logs,
                                               Random rng) {
        if (bracket == null || bracket.finished) return;
        Match pm = bracket.findPendingPlayerMatch();
        if (pm == null) return;

        Participant playerPart = pm.p1.isPlayer ? pm.p1 : pm.p2;
        Participant oppPart = pm.p1.isPlayer ? pm.p2 : pm.p1;
        pm.winner = playerWon ? playerPart : oppPart;
        pm.finished = true;
        pm.summary = summary != null ? summary : (playerWon ? "你获胜晋级！" : oppPart.displayName + " 获胜");
        pm.logs = logs != null ? new ArrayList<>(logs) : new ArrayList<String>();

        if (!playerWon) {
            bracket.playerEliminated = true;
            bracket.playerEliminatedRound = bracket.currentRound;
        }

        // Resolve sibling AI-vs-AI matches in this round
        for (Match m : bracket.rounds.get(bracket.currentRound)) {
            if (!m.finished && !m.involvesPlayer()) {
                simulateAiMatch(catalog, m, bracket.timelineDate, rng);
            }
        }

        advanceRoundIfComplete(bracket);
    }

    /**
     * Simulates all remaining AI-vs-AI rounds after the player has been eliminated so the
     * tournament crowns its champion cleanly.
     */
    public static void simulateRemainingToChampion(RpgCatalog catalog,
                                                   TournamentBracket bracket,
                                                   Random rng) {
        if (bracket == null || bracket.finished) return;
        int guard = 0;
        while (!bracket.finished && guard++ < 8) {
            List<Match> round = bracket.rounds.get(bracket.currentRound);
            for (Match m : round) {
                if (!m.finished) {
                    if (m.involvesPlayer()) {
                        // Do not auto-skip an active player match
                        return;
                    }
                    simulateAiMatch(catalog, m, bracket.timelineDate, rng);
                }
            }
            advanceRoundIfComplete(bracket);
        }
    }

    private static void advanceRoundIfComplete(TournamentBracket bracket) {
        List<Match> cur = bracket.rounds.get(bracket.currentRound);
        for (Match m : cur) {
            if (!m.finished || m.winner == null) return;
        }
        if (cur.size() == 1) {
            bracket.finished = true;
            bracket.champion = cur.get(0).winner;
            finalizePlacementAndReward(bracket);
            return;
        }
        List<Match> nextRound = new ArrayList<>();
        int nextIdx = bracket.currentRound + 1;
        for (int i = 0; i < cur.size(); i += 2) {
            nextRound.add(new Match(nextIdx, i / 2, cur.get(i).winner, cur.get(i + 1).winner));
        }
        bracket.rounds.add(nextRound);
        bracket.currentRound = nextIdx;
    }

    private static void finalizePlacementAndReward(TournamentBracket bracket) {
        boolean playerChamp = bracket.champion != null && bracket.champion.isPlayer;
        if (playerChamp) {
            bracket.playerPlacementLabel = "🏆 冠军";
            bracket.rewardTokens = bracket.size == 8 ? 1500 : 600;
            return;
        }
        int lastRoundIdx = bracket.totalRounds() - 1;
        if (bracket.playerEliminatedRound == lastRoundIdx) {
            bracket.playerPlacementLabel = "🥈 亚军";
            bracket.rewardTokens = bracket.size == 8 ? 650 : 250;
        } else if (bracket.size == 8 && bracket.playerEliminatedRound == 1) {
            bracket.playerPlacementLabel = "🥉 四强";
            bracket.rewardTokens = 300;
        } else {
            bracket.playerPlacementLabel = bracket.size == 8 ? "八强参与奖" : "四强参与奖";
            bracket.rewardTokens = bracket.size == 8 ? 120 : 100;
        }
    }

    /**
     * Grants the tournament reward tokens and updates profile championship counters once.
     */
    public static int claimRewardIfFinished(RpgProfile profile, TournamentBracket bracket) {
        if (profile == null || bracket == null || !bracket.finished || bracket.rewardClaimed) {
            return 0;
        }
        bracket.rewardClaimed = true;
        profile.tournamentsPlayed++;
        if (bracket.champion != null && bracket.champion.isPlayer) {
            profile.tournamentWins++;
        }
        profile.tokens += bracket.rewardTokens;
        return bracket.rewardTokens;
    }
}

package cn.garymb.ygomobile.rpg.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Historical canonical duel recreation entry from the original Yu-Gi-Oh! manga, anime, or film.
 */
public class OcgCanonDuel {
    public String id;
    public String title;
    /** MANGA, TOEI_ANIME, DM_ANIME, MOVIE */
    public String mediaType;
    public String episodeLabel;
    /** Exact original Japanese publication/air/release date (YYYY-MM-DD). */
    public String originalDate;
    /** Timeline unlock date = latest Japan OCG first release date among coreCardIds (YYYY-MM-DD). */
    public String timelineDate;
    /** Selected signature core cards from both sides whose first OCG release determines timelineDate. */
    public List<Integer> coreCardIds = new ArrayList<>();
    /** Human-readable Chinese note explaining which core card(s) determine timelineDate. */
    public String coreUnlockNote;
    public String summary;
    public DuelSide sideA;
    public DuelSide sideB;
    /** Pre-duel AVG dialogue lines (Legacy of the Duelist style Intro cutscene). */
    public List<DialogueLine> introDialogue = new ArrayList<>();
    /** Post-duel victory AVG dialogue lines (Legacy of the Duelist style Outro cutscene). */
    public List<DialogueLine> outroDialogue = new ArrayList<>();

    public static class DialogueLine {
        /** "A" (sideA portrait), "B" (sideB portrait), or "NARRATOR" (centered narration). */
        public String speakerSide;
        public String speakerName;
        /** Optional mood tint: normal, fierce, shock, smirk, determined, victory. */
        public String emotion;
        public String text;
    }

    public static class DuelSide {
        public String characterId;
        public String characterName;
        /** Asset filename stem under assets/rpg/avatars/<avatarId>.webp */
        public String avatarId;
        public String deckName;
        /** Reference Yu-Gi-Oh! Legacy of the Duelist Story Deck name (adapted to event-date OCG pool). */
        public String lotdDeckName;
        /** Tactical AI style registered in RpgAiStrategy.SUPPORTED_STYLES */
        public String fillStyle;
        public List<String> preferredRaces = new ArrayList<>();
        public List<String> preferredAttributes = new ArrayList<>();
        /** Mandatory canon Main Deck card passcodes (all preserved in reconstructed deck). */
        public List<Integer> canonCardIds = new ArrayList<>();
        /** Mandatory canon Extra Deck (Fusion/etc.) card passcodes. */
        public List<Integer> canonExtraIds = new ArrayList<>();
        /** Optional ordered event-date OCG padding card passcodes adapted from LotD Story Deck blueprint. */
        public List<Integer> lotdEraPaddingIds = new ArrayList<>();
    }

    /** Wrapper root JSON object for rpg/ocg_canon_duels.json. */
    public static class CatalogRoot {
        public String version;
        public String maxOriginalDate;
        public List<RpgCard> canonCardCatalog = new ArrayList<>();
        public List<OcgCanonDuel> canonDuels = new ArrayList<>();
    }

    public String getMediaLabelCn() {
        if ("MOVIE".equals(mediaType)) return "剧场版";
        if ("TOEI_ANIME".equals(mediaType)) return "东映动画";
        if ("DM_ANIME".equals(mediaType)) return "DM动画";
        return "原作漫画";
    }

    public boolean isPreOcgOrigin() {
        return originalDate != null && timelineDate != null && originalDate.compareTo(timelineDate) < 0;
    }
}

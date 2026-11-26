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
    /** Timeline unlock date clamped to OCG timeline start (YYYY-MM-DD, >= 1999-02-04). */
    public String timelineDate;
    public String summary;
    public DuelSide sideA;
    public DuelSide sideB;

    public static class DuelSide {
        public String characterId;
        public String characterName;
        /** Asset filename stem under assets/rpg/avatars/<avatarId>.webp */
        public String avatarId;
        public String deckName;
        /** Tactical AI style registered in RpgAiStrategy.SUPPORTED_STYLES */
        public String fillStyle;
        public List<String> preferredRaces = new ArrayList<>();
        public List<String> preferredAttributes = new ArrayList<>();
        /** Mandatory canon Main Deck card passcodes (all preserved in reconstructed deck). */
        public List<Integer> canonCardIds = new ArrayList<>();
        /** Mandatory canon Extra Deck (Fusion/etc.) card passcodes. */
        public List<Integer> canonExtraIds = new ArrayList<>();
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

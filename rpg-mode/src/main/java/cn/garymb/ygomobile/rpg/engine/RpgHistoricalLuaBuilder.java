package cn.garymb.ygomobile.rpg.engine;

import cn.garymb.ygomobile.rpg.model.OcgRuleSet;

/**
 * Generates the per-duel Lua preload script ({@code rpg_rule_override.lua}) injected into ocgcore
 * BEFORE {@code newCard} loads decks in RPG Mode.
 *
 * <p>Because ocgcore's {@code interpreter::load_card_script(code)} skips loading
 * {@code script/c<code>.lua} from {@code scripts.zip} whenever global table {@code _G["c<code>"]}
 * is already defined, defining pre-errata card tables here restores exact historical OCG card
 * effects (e.g., 1999 Crush Card Virus 3-turn draw destruction, 1999 pre-errata Sangan & Witch)
 * strictly inside RPG duels with ZERO pollution of {@code expansions/script/} or non-RPG duels.
 */
public final class RpgHistoricalLuaBuilder {
    private RpgHistoricalLuaBuilder() {}

    /**
     * Builds the complete Lua override script for the given historical OCG rule set and timeline date.
     */
    public static String buildScript(OcgRuleSet rule, String timelineDate) {
        String date = (timelineDate != null && !timelineDate.trim().isEmpty())
                ? timelineDate.trim()
                : "1999-12-31";
        StringBuilder lua = new StringBuilder(2048);
        lua.append("-- Auto-generated OCG Timeline RPG rule & pre-errata override: ")
                .append(rule != null ? rule.id : "default")
                .append(" @ ").append(date).append("\n");

        if (rule != null) {
            appendRuleEffects(lua, rule);
        }
        appendPreErrataCards(lua, date);
        return lua.toString();
    }

    private static void appendRuleEffects(StringBuilder lua, OcgRuleSet rule) {
        if (!rule.tributeSummonEnabled) {
            // 1999.02.04 公式ルール: Level 5+ monsters require 0 tributes to Normal Summon or Normal Set
            lua.append("local e1=Effect.GlobalEffect()\n");
            lua.append("e1:SetType(EFFECT_TYPE_FIELD)\n");
            lua.append("e1:SetCode(EFFECT_DECREASE_TRIBUTE)\n");
            lua.append("e1:SetProperty(EFFECT_FLAG_IGNORE_IMMUNE)\n");
            lua.append("e1:SetTargetRange(LOCATION_HAND,LOCATION_HAND)\n");
            lua.append("e1:SetValue(0x20002)\n");
            lua.append("Duel.RegisterEffect(e1,0)\n");

            lua.append("local e2=Effect.GlobalEffect()\n");
            lua.append("e2:SetType(EFFECT_TYPE_FIELD)\n");
            lua.append("e2:SetCode(EFFECT_DECREASE_TRIBUTE_SET)\n");
            lua.append("e2:SetProperty(EFFECT_FLAG_IGNORE_IMMUNE)\n");
            lua.append("e2:SetTargetRange(LOCATION_HAND,LOCATION_HAND)\n");
            lua.append("e2:SetValue(0x20002)\n");
            lua.append("Duel.RegisterEffect(e2,0)\n");
        }
        if (!rule.firstTurnDrawEnabled) {
            // 1999 Series 1 Official & Expert Rules: Turn 1 player does not draw in Draw Phase
            lua.append("local e3=Effect.GlobalEffect()\n");
            lua.append("e3:SetType(EFFECT_TYPE_FIELD)\n");
            lua.append("e3:SetProperty(EFFECT_FLAG_PLAYER_TARGET)\n");
            lua.append("e3:SetCode(EFFECT_DRAW_COUNT)\n");
            lua.append("e3:SetTargetRange(1,1)\n");
            lua.append("e3:SetCondition(function(e) return Duel.GetTurnCount()==1 end)\n");
            lua.append("e3:SetValue(0)\n");
            lua.append("Duel.RegisterEffect(e3,0)\n");
        }
        if (rule.deckOutCompareLp) {
            // 1999 Series 1 rules had no 6-card hand size limit in End Phase
            lua.append("local e4=Effect.GlobalEffect()\n");
            lua.append("e4:SetType(EFFECT_TYPE_FIELD)\n");
            lua.append("e4:SetProperty(EFFECT_FLAG_PLAYER_TARGET)\n");
            lua.append("e4:SetCode(EFFECT_HAND_LIMIT)\n");
            lua.append("e4:SetTargetRange(1,1)\n");
            lua.append("e4:SetValue(100)\n");
            lua.append("Duel.RegisterEffect(e4,0)\n");
        }
    }

    private static void appendPreErrataCards(StringBuilder lua, String date) {
        // 1) Crush Card Virus (57728570): 1999-07-08 original effect until 2015-01-01 errata
        if (date.compareTo("2015-01-01") < 0) {
            appendCrushCardVirus1999(lua);
        }
        // 2) Sangan (26202165): pre-2016-04-01 (and pre-1999-12-20 any-location trigger!)
        if (date.compareTo("2016-04-01") < 0) {
            boolean anyLocation = date.compareTo("1999-12-20") < 0;
            appendSanganPreErrata(lua, anyLocation);
        }
        // 3) Witch of the Black Forest (78010363): pre-2017-04-01 (and pre-1999-12-20 any-location trigger!)
        if (date.compareTo("2017-04-01") < 0) {
            boolean anyLocation = date.compareTo("1999-12-20") < 0;
            appendWitchPreErrata(lua, anyLocation);
        }
        // 4) Catapult Turtle (95727991): 1999-07-08 original (no Once Per Turn) until 2014-03-21 errata
        if (date.compareTo("2014-03-21") < 0) {
            appendCatapultTurtlePreErrata(lua);
        }
    }

    /**
     * 1999-07-08 Original OCG Crush Card Virus (死のデッキ破壊ウイルス):
     * Tribute 1 DARK monster with 1000 or less ATK; check opponent's monsters on field, hand,
     * and all cards opponent draws for 3 of opponent's turns, and destroy all monsters with 1500+ ATK.
     * Modeled verbatim on official YGOPro Virus architecture (c35027493.lua).
     */
    private static void appendCrushCardVirus1999(StringBuilder lua) {
        lua.append("\n-- Pre-2015 Original Crush Card Virus (57728570)\n");
        lua.append("c57728570={}\n");
        lua.append("c57728570.__index=c57728570\n");
        lua.append("if Card then setmetatable(c57728570,Card) end\n");
        lua.append("function c57728570.initial_effect(c)\n");
        lua.append("\tlocal e1=Effect.CreateEffect(c)\n");
        lua.append("\te1:SetCategory(CATEGORY_DESTROY)\n");
        lua.append("\te1:SetType(EFFECT_TYPE_ACTIVATE)\n");
        lua.append("\te1:SetCode(EVENT_FREE_CHAIN)\n");
        lua.append("\te1:SetHintTiming(0,TIMING_TOHAND+TIMINGS_CHECK_MONSTER)\n");
        lua.append("\te1:SetCost(c57728570.cost)\n");
        lua.append("\te1:SetTarget(c57728570.target)\n");
        lua.append("\te1:SetOperation(c57728570.activate)\n");
        lua.append("\tc:RegisterEffect(e1)\n");
        lua.append("end\n");
        lua.append("function c57728570.costfilter(c)\n");
        lua.append("\treturn c:IsAttribute(ATTRIBUTE_DARK) and c:IsAttackBelow(1000)\n");
        lua.append("end\n");
        lua.append("function c57728570.cost(e,tp,eg,ep,ev,re,r,rp,chk)\n");
        lua.append("\tif chk==0 then return Duel.CheckReleaseGroup(tp,c57728570.costfilter,1,nil) end\n");
        lua.append("\tlocal g=Duel.SelectReleaseGroup(tp,c57728570.costfilter,1,1,nil)\n");
        lua.append("\tDuel.Release(g,REASON_COST)\n");
        lua.append("end\n");
        lua.append("function c57728570.tgfilter(c)\n");
        lua.append("\treturn c:IsFaceup() and c:IsAttackAbove(1500)\n");
        lua.append("end\n");
        lua.append("function c57728570.target(e,tp,eg,ep,ev,re,r,rp,chk)\n");
        lua.append("\tif chk==0 then return true end\n");
        lua.append("\tlocal g=Duel.GetMatchingGroup(c57728570.tgfilter,tp,0,LOCATION_MZONE,nil)\n");
        lua.append("\tDuel.SetOperationInfo(0,CATEGORY_DESTROY,g,g:GetCount(),0,0)\n");
        lua.append("end\n");
        lua.append("function c57728570.filter(c)\n");
        lua.append("\treturn c:IsType(TYPE_MONSTER) and c:IsAttackAbove(1500)\n");
        lua.append("end\n");
        lua.append("function c57728570.activate(e,tp,eg,ep,ev,re,r,rp)\n");
        lua.append("\tlocal conf=Duel.GetFieldGroup(tp,0,LOCATION_MZONE+LOCATION_HAND)\n");
        lua.append("\tif conf:GetCount()>0 then\n");
        lua.append("\t\tDuel.ConfirmCards(tp,conf)\n");
        lua.append("\t\tlocal dg=conf:Filter(c57728570.filter,nil)\n");
        lua.append("\t\tDuel.Destroy(dg,REASON_EFFECT)\n");
        lua.append("\t\tDuel.ShuffleHand(1-tp)\n");
        lua.append("\tend\n");
        lua.append("\tlocal e1=Effect.CreateEffect(e:GetHandler())\n");
        lua.append("\te1:SetType(EFFECT_TYPE_FIELD+EFFECT_TYPE_CONTINUOUS)\n");
        lua.append("\te1:SetProperty(EFFECT_FLAG_DELAY)\n");
        lua.append("\te1:SetCode(EVENT_DRAW)\n");
        lua.append("\te1:SetOperation(c57728570.desop)\n");
        lua.append("\te1:SetReset(RESET_PHASE+PHASE_END+RESET_OPPO_TURN,3)\n");
        lua.append("\tDuel.RegisterEffect(e1,tp)\n");
        lua.append("\tlocal e2=Effect.CreateEffect(e:GetHandler())\n");
        lua.append("\te2:SetType(EFFECT_TYPE_FIELD+EFFECT_TYPE_CONTINUOUS)\n");
        lua.append("\te2:SetCode(EVENT_PHASE+PHASE_END)\n");
        lua.append("\te2:SetCountLimit(1)\n");
        lua.append("\te2:SetCondition(c57728570.turncon)\n");
        lua.append("\te2:SetOperation(c57728570.turnop)\n");
        lua.append("\te2:SetReset(RESET_PHASE+PHASE_END+RESET_OPPO_TURN,3)\n");
        lua.append("\tDuel.RegisterEffect(e2,tp)\n");
        lua.append("\te2:SetLabelObject(e1)\n");
        lua.append("\te:GetHandler():RegisterFlagEffect(1082946,RESET_PHASE+PHASE_END+RESET_OPPO_TURN,0,3)\n");
        lua.append("\tc57728570[e:GetHandler()]=e2\n");
        lua.append("end\n");
        lua.append("function c57728570.desop(e,tp,eg,ep,ev,re,r,rp)\n");
        lua.append("\tif ep==tp then return end\n");
        lua.append("\tlocal hg=eg:Filter(Card.IsLocation,nil,LOCATION_HAND)\n");
        lua.append("\tif hg:GetCount()==0 then return end\n");
        lua.append("\tDuel.ConfirmCards(1-ep,hg)\n");
        lua.append("\tlocal dg=hg:Filter(c57728570.filter,nil)\n");
        lua.append("\tDuel.Destroy(dg,REASON_EFFECT)\n");
        lua.append("\tDuel.ShuffleHand(ep)\n");
        lua.append("end\n");
        lua.append("function c57728570.turncon(e,tp,eg,ep,ev,re,r,rp)\n");
        lua.append("\treturn Duel.GetTurnPlayer()~=tp\n");
        lua.append("end\n");
        lua.append("function c57728570.turnop(e,tp,eg,ep,ev,re,r,rp)\n");
        lua.append("\tlocal ct=e:GetLabel()\n");
        lua.append("\tct=ct+1\n");
        lua.append("\te:SetLabel(ct)\n");
        lua.append("\te:GetHandler():SetTurnCounter(ct)\n");
        lua.append("\tif ct==3 then\n");
        lua.append("\t\te:GetLabelObject():Reset()\n");
        lua.append("\t\te:GetOwner():ResetFlagEffect(1082946)\n");
        lua.append("\tend\n");
        lua.append("end\n");
    }

    /**
     * Pre-2016 Sangan (26202165): no HOPT, no activation lock on searched card;
     * when {@code anyLocation == true} (1999-11-18 ~ 1999-12-19), triggers from ANY zone to GY!
     */
    private static void appendSanganPreErrata(StringBuilder lua, boolean anyLocation) {
        lua.append("\n-- Pre-Errata Sangan (26202165, anyLocation=").append(anyLocation).append(")\n");
        lua.append("c26202165={}\n");
        lua.append("c26202165.__index=c26202165\n");
        lua.append("if Card then setmetatable(c26202165,Card) end\n");
        lua.append("function c26202165.initial_effect(c)\n");
        lua.append("\tlocal e1=Effect.CreateEffect(c)\n");
        lua.append("\te1:SetDescription(aux.Stringid(26202165,0))\n");
        lua.append("\te1:SetCategory(CATEGORY_TOHAND+CATEGORY_SEARCH)\n");
        lua.append("\te1:SetType(EFFECT_TYPE_SINGLE+EFFECT_TYPE_TRIGGER_F)\n");
        lua.append("\te1:SetCode(EVENT_TO_GRAVE)\n");
        if (!anyLocation) {
            lua.append("\te1:SetCondition(c26202165.condition)\n");
        }
        lua.append("\te1:SetTarget(c26202165.target)\n");
        lua.append("\te1:SetOperation(c26202165.operation)\n");
        lua.append("\tc:RegisterEffect(e1)\n");
        lua.append("end\n");
        if (!anyLocation) {
            lua.append("function c26202165.condition(e,tp,eg,ep,ev,re,r,rp)\n");
            lua.append("\treturn e:GetHandler():IsPreviousLocation(LOCATION_ONFIELD)\n");
            lua.append("end\n");
        }
        lua.append("function c26202165.target(e,tp,eg,ep,ev,re,r,rp,chk)\n");
        lua.append("\tif chk==0 then return true end\n");
        lua.append("\tDuel.SetOperationInfo(0,CATEGORY_TOHAND,nil,1,tp,LOCATION_DECK)\n");
        lua.append("end\n");
        lua.append("function c26202165.filter(c)\n");
        lua.append("\treturn c:IsAttackBelow(1500) and c:IsType(TYPE_MONSTER) and c:IsAbleToHand()\n");
        lua.append("end\n");
        lua.append("function c26202165.operation(e,tp,eg,ep,ev,re,r,rp)\n");
        lua.append("\tDuel.Hint(HINT_SELECTMSG,tp,HINTMSG_ATOHAND)\n");
        lua.append("\tlocal g=Duel.SelectMatchingCard(tp,c26202165.filter,tp,LOCATION_DECK,0,1,1,nil)\n");
        lua.append("\tif g:GetCount()>0 then\n");
        lua.append("\t\tDuel.SendtoHand(g,nil,REASON_EFFECT)\n");
        lua.append("\t\tDuel.ConfirmCards(1-tp,g)\n");
        lua.append("\tend\n");
        lua.append("end\n");
    }

    /**
     * Pre-2017 Witch of the Black Forest (78010363): no HOPT, no activation lock on searched card;
     * when {@code anyLocation == true} (1999-11-18 ~ 1999-12-19), triggers from ANY zone to GY!
     */
    private static void appendWitchPreErrata(StringBuilder lua, boolean anyLocation) {
        lua.append("\n-- Pre-Errata Witch of the Black Forest (78010363, anyLocation=").append(anyLocation).append(")\n");
        lua.append("c78010363={}\n");
        lua.append("c78010363.__index=c78010363\n");
        lua.append("if Card then setmetatable(c78010363,Card) end\n");
        lua.append("function c78010363.initial_effect(c)\n");
        lua.append("\tlocal e1=Effect.CreateEffect(c)\n");
        lua.append("\te1:SetDescription(aux.Stringid(78010363,0))\n");
        lua.append("\te1:SetCategory(CATEGORY_TOHAND+CATEGORY_SEARCH)\n");
        lua.append("\te1:SetType(EFFECT_TYPE_SINGLE+EFFECT_TYPE_TRIGGER_F)\n");
        lua.append("\te1:SetCode(EVENT_TO_GRAVE)\n");
        if (!anyLocation) {
            lua.append("\te1:SetCondition(c78010363.condition)\n");
        }
        lua.append("\te1:SetTarget(c78010363.target)\n");
        lua.append("\te1:SetOperation(c78010363.operation)\n");
        lua.append("\tc:RegisterEffect(e1)\n");
        lua.append("end\n");
        if (!anyLocation) {
            lua.append("function c78010363.condition(e,tp,eg,ep,ev,re,r,rp)\n");
            lua.append("\treturn e:GetHandler():IsPreviousLocation(LOCATION_ONFIELD)\n");
            lua.append("end\n");
        }
        lua.append("function c78010363.target(e,tp,eg,ep,ev,re,r,rp,chk)\n");
        lua.append("\tif chk==0 then return true end\n");
        lua.append("\tDuel.SetOperationInfo(0,CATEGORY_TOHAND,nil,1,tp,LOCATION_DECK)\n");
        lua.append("end\n");
        lua.append("function c78010363.filter(c)\n");
        lua.append("\treturn c:IsDefenseBelow(1500) and c:IsType(TYPE_MONSTER) and c:IsAbleToHand()\n");
        lua.append("end\n");
        lua.append("function c78010363.operation(e,tp,eg,ep,ev,re,r,rp)\n");
        lua.append("\tDuel.Hint(HINT_SELECTMSG,tp,HINTMSG_ATOHAND)\n");
        lua.append("\tlocal g=Duel.SelectMatchingCard(tp,c78010363.filter,tp,LOCATION_DECK,0,1,1,nil)\n");
        lua.append("\tif g:GetCount()>0 then\n");
        lua.append("\t\tDuel.SendtoHand(g,nil,REASON_EFFECT)\n");
        lua.append("\t\tDuel.ConfirmCards(1-tp,g)\n");
        lua.append("\tend\n");
        lua.append("end\n");
    }

    /**
     * Pre-2014-03-21 Catapult Turtle (95727991): no once-per-turn restriction.
     */
    private static void appendCatapultTurtlePreErrata(StringBuilder lua) {
        lua.append("\n-- Pre-2014 Original Catapult Turtle (95727991)\n");
        lua.append("c95727991={}\n");
        lua.append("c95727991.__index=c95727991\n");
        lua.append("if Card then setmetatable(c95727991,Card) end\n");
        lua.append("function c95727991.initial_effect(c)\n");
        lua.append("\tlocal e1=Effect.CreateEffect(c)\n");
        lua.append("\te1:SetDescription(aux.Stringid(95727991,0))\n");
        lua.append("\te1:SetCategory(CATEGORY_DAMAGE)\n");
        lua.append("\te1:SetProperty(EFFECT_FLAG_PLAYER_TARGET)\n");
        lua.append("\te1:SetType(EFFECT_TYPE_IGNITION)\n");
        lua.append("\te1:SetRange(LOCATION_MZONE)\n");
        lua.append("\te1:SetCost(c95727991.cost)\n");
        lua.append("\te1:SetTarget(c95727991.target)\n");
        lua.append("\te1:SetOperation(c95727991.operation)\n");
        lua.append("\tc:RegisterEffect(e1)\n");
        lua.append("end\n");
        lua.append("function c95727991.filter(c)\n");
        lua.append("\treturn c:GetAttack()>0\n");
        lua.append("end\n");
        lua.append("function c95727991.cost(e,tp,eg,ep,ev,re,r,rp,chk)\n");
        lua.append("\tif chk==0 then return Duel.CheckReleaseGroup(tp,c95727991.filter,1,nil) end\n");
        lua.append("\tlocal sg=Duel.SelectReleaseGroup(tp,c95727991.filter,1,1,nil)\n");
        lua.append("\te:SetLabel(math.floor(sg:GetFirst():GetAttack()/2))\n");
        lua.append("\tDuel.Release(sg,REASON_COST)\n");
        lua.append("end\n");
        lua.append("function c95727991.target(e,tp,eg,ep,ev,re,r,rp,chk)\n");
        lua.append("\tif chk==0 then return true end\n");
        lua.append("\tDuel.SetTargetPlayer(1-tp)\n");
        lua.append("\tDuel.SetTargetParam(e:GetLabel())\n");
        lua.append("\tDuel.SetOperationInfo(0,CATEGORY_DAMAGE,nil,0,1-tp,e:GetLabel())\n");
        lua.append("end\n");
        lua.append("function c95727991.operation(e,tp,eg,ep,ev,re,r,rp)\n");
        lua.append("\tlocal p,d=Duel.GetChainInfo(0,CHAININFO_TARGET_PLAYER,CHAININFO_TARGET_PARAM)\n");
        lua.append("\tDuel.Damage(p,d,REASON_EFFECT)\n");
        lua.append("end\n");
    }
}

package hyperdrive.campaign.abilities;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.ai.CampaignFleetAIAPI;
import com.fs.starfarer.api.campaign.ai.FleetAIFlags;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.abilities.ai.BaseAbilityAI;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import hyperdrive.CampaignScript;
import org.lwjgl.util.vector.Vector2f;
import hyperdrive.ModPlugin;

public class HyperdriveAbilityAI extends BaseAbilityAI {
    public static final float AI_FREQUENCY_MULT = 0.5f;

    private IntervalUtil interval = new IntervalUtil(0.05f, 0.15f);

    private SectorEntityToken.VisibilityLevel getVisibilityLevelOfPlayerFleet() {
        CampaignFleetAPI pf = Global.getSector().getPlayerFleet();

        if(pf == null || !fleet.isInCurrentLocation()) return SectorEntityToken.VisibilityLevel.NONE;

        //if(!fleet.isInHyperspace()) return pf.getVisibilityLevelTo(fleet);

        float dist = Math.max(0, Misc.getDistance(pf, fleet) - fleet.getRadius() - pf.getRadius());

        if (dist > 6000) {
            return SectorEntityToken.VisibilityLevel.NONE;
        } else {
            //float maxRng = BaseCampaignEntity.getMaxSensorRangeToDetect(fleet, pf);
            float playerProfile = pf.getStats().getDetectedRangeMod().computeEffective(pf.getSensorProfile());
            float npcSensorStrength = fleet.getStats().getSensorRangeMod().computeEffective(fleet.getSensorProfile());
            float maxRng = playerProfile + npcSensorStrength;
            float compRng = maxRng * 0.5f;
            float factionRng = Math.max(maxRng * 0.1f, 50);
            boolean transponderIsOn = pf.isTransponderOn();

            //if(Global.getSettings().isDevMode()) HyperdriveAbility.print("Max: " + playerProfile + " + " + npcSensorStrength + " = " + maxRng);

            boolean playerWarpDriveIsActive = pf.hasAbility(HyperdriveAbility.ID)
                    && (pf.getAbility(HyperdriveAbility.ID).isOnCooldown()
                    || ((HyperdriveAbility)pf.getAbility(HyperdriveAbility.ID)).isFadingOut());

            if ((!(transponderIsOn || playerWarpDriveIsActive) || dist > maxRng) && dist > factionRng) {
                if (dist <= compRng) {
                    return SectorEntityToken.VisibilityLevel.COMPOSITION_DETAILS;
                } else {
                    return dist <= maxRng ? SectorEntityToken.VisibilityLevel.SENSOR_CONTACT : SectorEntityToken.VisibilityLevel.NONE;
                }
            } else {
                return SectorEntityToken.VisibilityLevel.COMPOSITION_AND_FACTION_DETAILS;
            }
        }
    }

    @Override
    public void advance(float days) {
        if(ModPlugin.REMOVE_ALL_DATA_AND_FEATURES) {
            fleet.removeAbility(HyperdriveAbility.ID);
            return;
        }

        interval.advance(days * AI_FREQUENCY_MULT);

        if (!ModPlugin.NPC_FLEETS_CAN_USE_TO_INTERCEPT_PLAYER && !ModPlugin.NPC_FLEETS_CAN_USE_FOR_TRAVEL) return;

        if (!interval.intervalElapsed()
                || fleet == null
                || fleet.getAI() == null
                || fleet.getBattle() != null
                || ability == null
                || ability.isActiveOrInProgress()
                || ability.isOnCooldown()
                || ((HyperdriveAbility)ability).getFleet() == null) return;

        if(fleet == CampaignScript.monitoredFleet) {
            boolean isMereBreakpointLine = true;
        }

        MemoryAPI mem = fleet.getMemoryWithoutUpdate();

        CampaignFleetAIAPI.EncounterOption option;
        CampaignFleetAPI pf = Global.getSector().getPlayerFleet();
        boolean isBusyWithOtherThings = true;
        boolean isVisibleEnough = false;
        boolean printExcuses = ModPlugin.isTestingModeActive() && fleet == CampaignScript.monitoredFleet;
        boolean wantsToInterceptPlayer = ModPlugin.NPC_FLEETS_CAN_USE_TO_INTERCEPT_PLAYER
                && fleet.getFaction().isHostileTo(pf.getFaction())
                && fleet.getFleetData().getMinBurnLevel() >= pf.getFleetData().getMinBurnLevel()
                && (option = fleet.getAI().pickEncounterOption(null, pf, true)) != null
                && option == CampaignFleetAIAPI.EncounterOption.ENGAGE;
        boolean wantsToCatchUpWithAlly = mem.contains("$core_fleetBusy_nex_followMe")
                && fleet.getCurrentAssignment() != null
                && fleet.getCurrentAssignment().getAssignment() == FleetAssignment.ORBIT_PASSIVE
                && fleet.getCurrentAssignment().getTarget() == pf;

        if(pf == null) return;

        switch (getVisibilityLevelOfPlayerFleet()) {
            case COMPOSITION_AND_FACTION_DETAILS:
                isVisibleEnough = true;

                if(pf.hasAbility(HyperdriveAbility.ID) && pf.getAbility(HyperdriveAbility.ID).isOnCooldown()) {
                    mem.set(MemFlags.MEMORY_KEY_SAW_PLAYER_WITH_TRANSPONDER_ON, true, 7 + (float) Math.random() * 7);
                }
                break;
            case COMPOSITION_DETAILS: isVisibleEnough = Math.random() < 0.5; break;
            case SENSOR_CONTACT: isVisibleEnough = Math.random() < 0.25; break;
        }

        if((wantsToInterceptPlayer || wantsToCatchUpWithAlly)
                && (isVisibleEnough || wantsToCatchUpWithAlly)
                && fleet.getAI() != null
                && !pf.isInHyperspaceTransition()) {

            if (fleet.getAI().getCurrentAssignment() != null && wantsToInterceptPlayer) {
                switch (fleet.getAI().getCurrentAssignmentType()) {
                    case RAID_SYSTEM: case PATROL_SYSTEM: isBusyWithOtherThings = false; break;
                    case INTERCEPT:
                        isBusyWithOtherThings = !(fleet.getCurrentAssignment().getTarget() != null
                            && fleet.getCurrentAssignment().getTarget().isPlayerFleet());
                        // TODO - Reduce likelihood to do this
                        break;
                }
            } else if(wantsToCatchUpWithAlly) isBusyWithOtherThings = false;

            if(mem.getBoolean(MemFlags.MEMORY_KEY_PURSUE_PLAYER)) isBusyWithOtherThings = false;

            float pfBurnLevel = pf.getFleetData().getBurnLevel();
            Vector2f jumpDest = new Vector2f(pf.getVelocity());
            if(jumpDest.length() > 0) jumpDest.normalise().scale(300 + pfBurnLevel * (30f + (float)Math.random() * 60f));
            jumpDest.translate(pf.getLocation().x, pf.getLocation().y);

            float
                    jumpPointDist = Misc.getDistance(fleet.getLocation(), jumpDest),
                    targetDist = Misc.getDistance(pf, fleet),
                    interceptDist = Misc.getDistance(pf.getLocation(), jumpDest),
                    minDist = HyperdriveAbility.MIN_BURN_LEVEL * HyperdriveAbility.getDistancePerBurn(),
                    maxDist = fleet.getFleetData().getMaxBurnLevel() * HyperdriveAbility.getDistancePerBurn();

            if(printExcuses) {
                if(isBusyWithOtherThings) HyperdriveAbility.print(fleet.getFullName() + " Busy: " + fleet.getAI().getCurrentAssignmentType());
                else if(jumpPointDist < minDist) HyperdriveAbility.print(fleet.getFullName() + " Too close! " + jumpPointDist + " < " + minDist);
                else if(jumpPointDist > maxDist) HyperdriveAbility.print(fleet.getFullName() + " Too far! " + jumpPointDist + " > " + maxDist);
                else if(interceptDist > targetDist) HyperdriveAbility.print(fleet.getFullName() + " Already close enough! " + interceptDist + " > " + targetDist);
            }

            if(isBusyWithOtherThings
                    || jumpPointDist < minDist
                    || jumpPointDist > maxDist
                    || interceptDist > targetDist)
                return;

            Vector2f delta = new Vector2f(jumpDest);

            delta.translate(-fleet.getLocation().x, -fleet.getLocation().y);
            delta.scale(1f / (HyperdriveAbility.getDistancePerBurn() / Global.getSettings().getSpeedPerBurnLevel()));
            delta.scale(0.80f + 0.2f * (float)Math.random());

            fleet.getVelocity().set(delta);

            if(printExcuses && !ability.isUsable()) {
                HyperdriveAbility wd = (HyperdriveAbility)ability;

                HyperdriveAbility.print(fleet.getFullName() + " I can't getcha for some reason!");

                if( fleet == null) HyperdriveAbility.print("     null fleet");
                if( fleet.isInHyperspaceTransition()) HyperdriveAbility.print("     in transition");
                if( !(fleet.isInHyperspace() ? ModPlugin.USABLE_IN_HYPERSPACE : ModPlugin.USABLE_IN_NORMAL_SPACE)) HyperdriveAbility.print("     not allowed");
                if( !(fleet.isAIMode() || (int)Math.floor(fleet.getCurrBurnLevel()) >= wd.MIN_BURN_LEVEL)) HyperdriveAbility.print("     too slow");
                if( !wd.hasSufficientFuelForMinimalJump()) HyperdriveAbility.print("     insufficient fuel");
                if( !(ModPlugin.USABLE_AT_NEUTRON_STARS || !wd.isInSystemWithNeutronStar())) HyperdriveAbility.print("     neutron star");
                if( !wd.getNonReadyShips().isEmpty()) HyperdriveAbility.print("     non ready ships");
                if( !wd.isClearOfOtherFleets()) HyperdriveAbility.print("     interference");

                return;
            }

            ability.activate();

            if(printExcuses && !ability.isUsable()) {
                HyperdriveAbility.print(fleet.getFullName() + ":  Gon' getcha!  -  " + getVisibilityLevelOfPlayerFleet());
            }

            return;
        }


        if (ModPlugin.NPC_FLEETS_CAN_USE_FOR_TRAVEL) {
            if (mem.getBoolean(FleetAIFlags.HAS_LOWER_DETECTABILITY) && !ability.isActive()) {
                if(printExcuses) HyperdriveAbility.print("Maintaining a low profile");
                return;
            }


            if (fleet.getAI().getCurrentAssignment() != null) {
                FleetAssignment curr = fleet.getAI().getCurrentAssignmentType();

                if (curr == FleetAssignment.PATROL_SYSTEM ||
                        curr == FleetAssignment.RAID_SYSTEM ||
                        curr == FleetAssignment.STANDING_DOWN) {

                    if(printExcuses) HyperdriveAbility.print("Incompatible Assignment");
                    return;
                }
            }

            SectorEntityToken destination = fleet.getCurrentAssignment() != null
                    ? fleet.getCurrentAssignment().getTarget()
                    : null;
            //Vector2f target = mem.getVector2f(FleetAIFlags.TRAVEL_DEST);
            if (destination != null && fleet.getCurrBurnLevel() >= fleet.getFleetData().getMaxBurnLevel() - 1) {
                Vector2f target = fleet.isInHyperspace() ? destination.getLocationInHyperspace() : destination.getLocation();
                Vector2f offset = ((HyperdriveAbility) ability).getJumpOffset();

                float distToTarget = Misc.getDistance(fleet.getLocation(), target);
                float distToJumpPoint = offset.length();
                float distFromJumpPointToTarget = Misc.getDistance(target, offset.translate(fleet.getLocation().x, fleet.getLocation().y));

                if (distToJumpPoint < distToTarget
                        && distFromJumpPointToTarget < distToTarget
                        && distFromJumpPointToTarget > HyperdriveAbility.MOMENTUM_CARRY_DISTANCE) {

                    ability.activate();
                } else if(printExcuses) {
                    if(distToJumpPoint >= distToTarget) HyperdriveAbility.print("Would overshoot");
                    if(distFromJumpPointToTarget >= distToTarget) HyperdriveAbility.print("Close to destination");
                    if(distFromJumpPointToTarget <= HyperdriveAbility.MOMENTUM_CARRY_DISTANCE) HyperdriveAbility.print("Jump point too close");
                }
            } else if(printExcuses) {
                if(destination == null) HyperdriveAbility.print("No destination");
                if(fleet.getCurrBurnLevel() < fleet.getFleetData().getMaxBurnLevel() - 1) HyperdriveAbility.print("Not at max speed");
            }
        }
    }
}

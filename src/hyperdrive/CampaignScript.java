package hyperdrive;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.ai.CampaignFleetAIAPI;
import com.fs.starfarer.api.campaign.ai.FleetAssignmentDataAPI;
import com.fs.starfarer.api.campaign.listeners.CampaignInputListener;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.util.Misc;
import hyperdrive.campaign.abilities.HyperdriveAbility;
import org.lwjgl.input.Mouse;
import org.lwjgl.util.vector.Vector2f;

import java.util.List;

import static hyperdrive.ModPlugin.reportCrash;
import static hyperdrive.campaign.abilities.HyperdriveAbility.ID;

public class CampaignScript extends BaseCampaignEventListener implements EveryFrameScript, CampaignInputListener {
    boolean throttleBurnLevel = false, skipThrottleForOneFrame = false, distantEnoughLastFrame = true;
    float inputCooldown = 0;

    boolean tooCloseForWarp() {
        CampaignFleetAPI pf = Global.getSector().getPlayerFleet();
        float dist = Misc.getDistance(pf.getLocation(), pf.getMoveDestination());
        float minDist = HyperdriveAbility.getMinimumJumpDistance() + HyperdriveAbility.MOMENTUM_CARRY_DISTANCE;

        if(dist < minDist && (pf.isInHyperspace() || pf.isInHyperspaceTransition())) {
            SectorEntityToken target = Global.getSector().getUIData().getCourseTarget();
            dist = Math.max(dist, Misc.getDistance(pf.getLocationInHyperspace(), target.getLocationInHyperspace()));
        }

        return dist < minDist;
    }

    public CampaignScript() {
        super(false);
    }

    @Override
    public int getListenerInputPriority() {
        return 1;
    }

    @Override
    public void processCampaignInputPreCore(List<InputEventAPI> events) {
        CampaignFleetAPI pf = Global.getSector().getPlayerFleet();
        CampaignUIAPI ui = Global.getSector().getCampaignUI();
        SectorEntityToken target = Global.getSector().getUIData().getCourseTarget();

        if(inputCooldown > 0 || pf == null || target == null || ui == null || ui.isShowingDialog() || ui.isShowingMenu()
                || !(ui.getCurrentCoreTab() == null) || Global.getSector().isInNewGameAdvance()) {

            return;
        }

        for (InputEventAPI e : events) {
            if (!e.isConsumed() && e.isKeyDownEvent() && e.getEventValue() == ModPlugin.THROTTLE_BURN_LEVEL_ACTIVATION_KEY
                    && inputCooldown <= 0) {

                throttleBurnLevel = ui.isFollowingDirectCommand() ? true : !throttleBurnLevel;
                String message = null;

                if(throttleBurnLevel) {
                    if (ui.isFollowingDirectCommand()) {
                        ui.setFollowingDirectCommand(false);
                        skipThrottleForOneFrame = true;
                    } else if (tooCloseForWarp()) {
                        throttleBurnLevel = false;
                        message = "Too close to warp";
                    } else {
                        message = "Throttling burn for hyperwarp jump to " + target.getName().split(",")[0];
                    }
                } else {
                    message = "Resuming max burn";
                }

                if(message != null) {
                    ui.getMessageDisplay().addMessage(message);
                }

                inputCooldown = 0.1f;

                break;
            }
        }
    }

    @Override
    public void processCampaignInputPreFleetControl(List<InputEventAPI> events) {}

    @Override
    public void processCampaignInputPostCore(List<InputEventAPI> events) {}

    @Override
    public boolean isDone() {
        return ModPlugin.REMOVE_ALL_DATA_AND_FEATURES;
    }

    @Override
    public boolean runWhilePaused() {
        return true;
    }

    @Override
    public void advance(float amount) {
        try {
            if(ModPlugin.isTestingModeActive()) showHoveredFleetAIInfo();

            inputCooldown = Math.max(-1, inputCooldown - amount);

            if(Global.getSector().isPaused()) return;

            ModPlugin.ensureReducedTimeLimitForMissions(false);

            if(skipThrottleForOneFrame) {
                skipThrottleForOneFrame = false;
            } else if(Global.getSector().getUIData().getCourseTarget() == null
                    || Global.getSector().getCampaignUI().isFollowingDirectCommand()) {

                throttleBurnLevel = ModPlugin.THROTTLE_BURN_LEVEL_BY_DEFAULT_DURING_AUTOPILOT;
            } else if(throttleBurnLevel) {
                CampaignFleetAPI pf = Global.getSector().getPlayerFleet();
                Vector2f delta = new Vector2f(pf.getMoveDestination());

                delta.translate(-pf.getLocation().x, -pf.getLocation().y); // Distance between fleet and destination
                delta.scale((delta.length() - HyperdriveAbility.MOMENTUM_CARRY_DISTANCE) / delta.length()); // Reduced to account for momentum
                delta.scale(1f / (HyperdriveAbility.getDistancePerBurn() / Global.getSettings().getSpeedPerBurnLevel())); // Converted to ideal velocity for jump

                boolean facingTarget = Misc.getAngleDiff(Misc.getAngleInDegrees(delta), Misc.getAngleInDegrees(pf.getVelocity())) < 90,
                        distantEnough = !tooCloseForWarp(),
                        currentlyTooFast = delta.length() < pf.getVelocity().length();

                if(facingTarget && distantEnough && currentlyTooFast) {
                    if(Misc.getDiff(pf.getVelocity(), delta).length() > Global.getSettings().getSpeedPerBurnLevel() * 2) {
                        float newSpeed = (pf.getVelocity().length() - pf.getAcceleration() * amount * 3);
                        delta.set(pf.getVelocity()).scale(newSpeed / pf.getVelocity().length());
                    }

                    pf.setVelocity(delta.x, delta.y);
                }

                if(distantEnoughLastFrame && !distantEnough) {
                    Global.getSector().getCampaignUI().getMessageDisplay().addMessage("Too close to warp - resuming max burn");
                }

                distantEnoughLastFrame = distantEnough;
            }
        } catch (Exception e) { reportCrash(e); }
    }

    public static CampaignFleetAPI overFleet = null, previousOverFleet = null, attachedFleet = null, monitoredFleet = null;
    void showHoveredFleetAIInfo() {
        final ViewportAPI view = Global.getSector().getViewport();
        final Vector2f target = new Vector2f(view.convertScreenXToWorldX(Mouse.getX()),
                view.convertScreenYToWorldY(Mouse.getY()));
        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();

        overFleet = null;

        if(playerFleet == null) return;

        for (CampaignFleetAPI flt : Misc.getVisibleFleets(Global.getSector().getPlayerFleet(), true)) {
            if (Misc.getDistance(target, flt.getLocation()) < flt.getRadius()) {
                overFleet = flt;
            }
        }

        if(Mouse.isButtonDown(2)) {
            attachedFleet = overFleet;

            if(overFleet != null) monitoredFleet = overFleet;
        }

        if(attachedFleet != null) {
            //playerFleet.setContainingLocation(attachedFleet.getContainingLocation());
            playerFleet.setVelocity(attachedFleet.getVelocity().x, attachedFleet.getVelocity().y);
            playerFleet.setLocation(attachedFleet.getLocation().x,
                    attachedFleet.getLocation().y - 200 - playerFleet.getRadius() - attachedFleet.getRadius());
        }

        if(previousOverFleet != overFleet && overFleet != null) {
            String nl = "\n   ";
            String msg = overFleet.getFullName();
            FleetAssignmentDataAPI data = overFleet.getCurrentAssignment();
            CampaignFleetAIAPI ai = overFleet.getAI();

            msg += nl + "HasAbility: " + overFleet.hasAbility(ID);
            msg += nl + "BurnLevel: " + overFleet.getFleetData().getMinBurnLevel();

            if(data != null) {
                msg += nl + "AssignDataClass: " + data.getClass().getSimpleName();
                msg += nl + "Assignment: " + data.getAssignment();
                msg += nl + "Target: " + (data.getTarget() == null ? null : data.getTarget().getFullName());
            }

            if(ai != null) {
                msg += nl + "AIClass: " + ai.getClass().getSimpleName();
                msg += nl + "EncounterOption: " + ai.pickEncounterOption(null, playerFleet, true);
                msg += nl + "AIHostile: " + ai.isHostileTo(playerFleet);
                msg += nl + "FactionHostile: " + overFleet.getFaction().isHostileTo(playerFleet.getFaction());
            }

            Global.getSector().getCampaignUI().addMessage(msg);
        }

        previousOverFleet = overFleet;
    }
}

package hyperdrive;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
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
    boolean throttleBurnLevel = false, skipThrottleForOneFrame = false;

    boolean tooCloseForWarp() {
        CampaignFleetAPI pf = Global.getSector().getPlayerFleet();

        float dist = Misc.getDistance(pf.getLocation(), pf.getMoveDestination());
        float minDist = HyperdriveAbility.getMinimumJumpDistance() + HyperdriveAbility.MOMENTUM_CARRY_DISTANCE;

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
        SectorEntityToken target = Global.getSector().getUIData().getCourseTarget();

        if(pf == null || target == null) return;

        for (InputEventAPI e : events) {
            if (!e.isConsumed() && e.isKeyDownEvent() && e.getEventValue() == ModPlugin.THROTTLE_BURN_LEVEL_ACTIVATION_KEY) {
                throttleBurnLevel = Global.getSector().getCampaignUI().isFollowingDirectCommand()
                    ? true : !throttleBurnLevel;

                Global.getSector().getCampaignUI().addMessage("" + Misc.getDistance(pf.getLocation(), pf.getMoveDestination()));

                String message = null;

                if(throttleBurnLevel) {
                    if (Global.getSector().getCampaignUI().isFollowingDirectCommand()) {
                        Global.getSector().getCampaignUI().setFollowingDirectCommand(false);
                        skipThrottleForOneFrame = true;
                    } else if (tooCloseForWarp()) {
                        throttleBurnLevel = false;
                        message = "Too close to warp";
                    } else {
                        message = "Throttling burn for hyperwarp jump to " + target.getName();
                    }
                } else {
                    message = "Resuming max burn";
                }

                if(message != null) {
                    Global.getSector().getCampaignUI().getMessageDisplay().addMessage(message);
                }

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
        return false;
    }

    @Override
    public boolean runWhilePaused() {
        return true;
    }

    @Override
    public void advance(float amount) {
        try {
            if(ModPlugin.isTestingModeActive()) showHoveredFleetAIInfo();

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
                        delta.set(pf.getVelocity()).scale(0.99f);
                    }

                    pf.setVelocity(delta.x, delta.y);
                }
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

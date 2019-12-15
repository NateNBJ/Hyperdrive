package hyperdrive;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ModManagerAPI;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.comm.IntelManagerAPI;
import com.fs.starfarer.api.impl.campaign.intel.AnalyzeEntityMissionIntel;
import com.fs.starfarer.api.impl.campaign.intel.BaseMissionIntel;
import com.fs.starfarer.api.impl.campaign.intel.PersonBountyIntel;
import com.fs.starfarer.api.impl.campaign.intel.SurveyPlanetMissionIntel;
import data.scripts.VayraModPlugin;
import org.json.JSONObject;
import starship_legends.events.FamousDerelictIntel;

import java.awt.*;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class ModPlugin extends BaseModPlugin {
    public final static String ID = "sun_hyperdrive";
    public final static String SETTINGS_PATH = "HYPERDRIVE_OPTIONS.ini";
    public final static String ABILITY_ID = "sun_hd_hyperdrive";
    public final static Map<Class, Float> NORMAL_MISSION_DURATIONS = new HashMap<>();

    static {
        NORMAL_MISSION_DURATIONS.put(AnalyzeEntityMissionIntel.class, 120f);
        NORMAL_MISSION_DURATIONS.put(SurveyPlanetMissionIntel.class, 120f);
    }

    public static float
            SENSOR_PROFILE_INCREASE = 5000,
            LIGHTYEARS_JUMPED_PER_BURN_LEVEL = 0.5f,
            FLEET_INTERFERENCE_RANGE_MULT = 1,
            CR_CONSUMPTION_MULT = 1,
            FUEL_CONSUMPTION_MULT = 1,
            MISSION_TIME_LIMIT_MULT = 0.5f;

    public static int
            THROTTLE_BURN_LEVEL_ACTIVATION_KEY = 17;

    public static boolean
            THROTTLE_BURN_LEVEL_BY_DEFAULT_DURING_AUTOPILOT = false,
            SHOW_WARP_PULSE_ANIMATION = true,
            USABLE_IN_HYPERSPACE = true,
            USABLE_IN_NORMAL_SPACE = true,
            USABLE_AT_NEUTRON_STARS = false,
            NPC_FLEETS_CAN_USE_FOR_TRAVEL = true,
            NPC_FLEETS_CAN_USE_TO_INTERCEPT_PLAYER = true,
            SHOW_DEBUG_INFO_IN_DEV_MODE = false;

    public static void ensureReducedTimeLimitForMissions(boolean includeOldMissions) {
        IntelManagerAPI manager = Global.getSector().getIntelManager();

        for(Map.Entry<Class, Float> entry : NORMAL_MISSION_DURATIONS.entrySet()) {
            float newLimit = entry.getValue() * MISSION_TIME_LIMIT_MULT;

            if(!BaseMissionIntel.class.isAssignableFrom(entry.getKey())) continue;

            Collection<IntelInfoPlugin> missions = manager.getCommQueue(entry.getKey());

            if(includeOldMissions) missions.addAll(manager.getIntel(entry.getKey()));


            for (IntelInfoPlugin intel : missions) {
                BaseMissionIntel bmi = (BaseMissionIntel) intel;
                if (bmi.getDuration() > newLimit) {
//                    HyperdriveAbility.print("Reduced duration for mission: " + bmi.getSmallDescriptionTitle());
                    bmi.setDuration(newLimit);
                }
            }
        }
    }
    public static boolean reportCrash(Exception exception) {
        try {
            String stackTrace = "", message = "Hyperdrive encountered an error!\nPlease let the mod author know.";

            for(int i = 0; i < exception.getStackTrace().length; i++) {
                StackTraceElement ste = exception.getStackTrace()[i];
                stackTrace += "    " + ste.toString() + System.lineSeparator();
            }

            Global.getLogger(ModPlugin.class).error(exception.getMessage() + System.lineSeparator() + stackTrace);

            if (Global.getCombatEngine() != null && Global.getCurrentState() == GameState.COMBAT) {
                Global.getCombatEngine().getCombatUI().addMessage(1, Color.ORANGE, exception.getMessage());
                Global.getCombatEngine().getCombatUI().addMessage(2, Color.RED, message);
            } else if (Global.getSector() != null) {
                CampaignUIAPI ui = Global.getSector().getCampaignUI();

                ui.addMessage(message, Color.RED);
                ui.addMessage(exception.getMessage(), Color.ORANGE);
                ui.showConfirmDialog(message + "\n\n" + exception.getMessage(), "Ok", null, null, null);

                if(ui.getCurrentInteractionDialog() != null) ui.getCurrentInteractionDialog().dismiss();
            } else return false;

            return true;
        } catch (Exception e) {
            return false;
        }
    }
    public static boolean isTestingModeActive() {
        return SHOW_DEBUG_INFO_IN_DEV_MODE && Global.getSettings().isDevMode();
    }

    private CampaignScript script;
    private boolean settingsAlreadyRead = false;

    @Override
    public void onGameLoad(boolean newGame) {
        try {
            Global.getSector().addTransientScript(script = new CampaignScript());
            Global.getSector().getListenerManager().addListener(script, true);

            ensureReducedTimeLimitForMissions(true);

            if (!Global.getSector().getPlayerFleet().hasAbility(ABILITY_ID)) {
                Global.getSector().getCharacterData().addAbility(ABILITY_ID);

                ensureReducedTimeLimitForMissions(true);
            }

            if (!settingsAlreadyRead) {
                try {
                    JSONObject cfg = Global.getSettings().getMergedJSONForMod(SETTINGS_PATH, ID);

                    SENSOR_PROFILE_INCREASE = (float) cfg.getDouble("SensorProfileIncrease");
                    LIGHTYEARS_JUMPED_PER_BURN_LEVEL = (float) cfg.getDouble("LightYearsJumpedPerBurnLevel");
                    FLEET_INTERFERENCE_RANGE_MULT = (float) cfg.getDouble("fleetInterferenceRangeMult");
                    CR_CONSUMPTION_MULT = (float) cfg.getDouble("crConsumptionMult");
                    FUEL_CONSUMPTION_MULT = (float) cfg.getDouble("fuelConsumptionMult");
                    MISSION_TIME_LIMIT_MULT = (float) cfg.getDouble("missionTimeLimitMult");

                    SHOW_WARP_PULSE_ANIMATION = cfg.getBoolean("showWarpPulseAnimation");
                    USABLE_IN_HYPERSPACE = cfg.getBoolean("usableInHyperspace");
                    USABLE_IN_NORMAL_SPACE = cfg.getBoolean("usableInNormalSpace");
                    USABLE_AT_NEUTRON_STARS = cfg.getBoolean("usableAtNeutronStars");
                    NPC_FLEETS_CAN_USE_FOR_TRAVEL = cfg.getBoolean("npcFleetsCanUseForTravel");
                    NPC_FLEETS_CAN_USE_TO_INTERCEPT_PLAYER = cfg.getBoolean("npcFleetsCanUseToInterceptPlayer");

                    SHOW_DEBUG_INFO_IN_DEV_MODE = cfg.getBoolean("showDebugInfoInDevMode");

                    THROTTLE_BURN_LEVEL_ACTIVATION_KEY = cfg.getInt("throttleBurnActivationKey");
                    THROTTLE_BURN_LEVEL_BY_DEFAULT_DURING_AUTOPILOT = cfg.getBoolean("throttleBurnLevelByDefaultDuringAutopilot");
                } catch (Exception e) { reportCrash(e); }

                PersonBountyIntel.MAX_DURATION *= MISSION_TIME_LIMIT_MULT;

                ModManagerAPI mm = Global.getSettings().getModManager();

                if(mm.isModEnabled("sun_starship_legends")) {
                    try {
                        FamousDerelictIntel.MAX_DURATION *= MISSION_TIME_LIMIT_MULT;
                    } catch (Exception e) { }
                }

                if(mm.isModEnabled("vayrasector")) {
                    try {
                        VayraModPlugin.BOUNTY_DURATION *= MISSION_TIME_LIMIT_MULT;
                    } catch (Exception e) { }
                }

                settingsAlreadyRead = true;
            }
        } catch (Exception e) { reportCrash(e); }
    }

    @Override
    public void beforeGameSave() {
        try {
            Global.getSector().removeTransientScript(script);
            Global.getSector().removeListener(script);
            Global.getSector().removeScriptsOfClass(CampaignScript.class);
        } catch (Exception e) { reportCrash(e); }
    }

    @Override
    public void afterGameSave() {
        try {
            Global.getSector().addTransientScript(script = new CampaignScript());
            Global.getSector().getListenerManager().addListener(script, true);
        } catch (Exception e) { reportCrash(e); }
    }
}

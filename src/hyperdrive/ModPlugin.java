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
import com.fs.starfarer.api.impl.campaign.missions.*;
import com.fs.starfarer.api.impl.campaign.missions.cb.CBStats;
import data.scripts.VayraModPlugin;
import hyperdrive.campaign.abilities.HyperdriveAbility;
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
            FUEL_CONSUMPTION_MULT_IN_NORMAL_SPACE = 0,
            MISSION_TIME_LIMIT_MULT = 0.5f,
            HALF_MISSION_TIME_LIMIT_MULT = 0.75f;

    public static int
            THROTTLE_BURN_LEVEL_ACTIVATION_KEY = 17;

    public static boolean
            REMOVE_ALL_DATA_AND_FEATURES = false,
            THROTTLE_BURN_LEVEL_BY_DEFAULT_DURING_AUTOPILOT = false,
            SHOW_WARP_PULSE_ANIMATION = true,
            USABLE_IN_HYPERSPACE = true,
            USABLE_IN_NORMAL_SPACE = true,
            USABLE_AT_NEUTRON_STARS = false,
            NPC_FLEETS_CAN_USE_FOR_TRAVEL = true,
            NPC_FLEETS_CAN_USE_TO_INTERCEPT_PLAYER = true,
            SHOW_DEBUG_INFO_IN_DEV_MODE = false,
            NPC_FLEETS_CAN_INTERCEPT_PLAYER_IF_UNAWARE_OF_IDENTITY = true;

    public static void ensureReducedTimeLimitForMissions(boolean includeOldMissions) {
        if(REMOVE_ALL_DATA_AND_FEATURES) return;

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
        return !REMOVE_ALL_DATA_AND_FEATURES && SHOW_DEBUG_INFO_IN_DEV_MODE && Global.getSettings().isDevMode();
    }

    private CampaignScript script;
    private boolean settingsAlreadyRead = false;

    @Override
    public void onGameLoad(boolean newGame) {
        try {
            if(!REMOVE_ALL_DATA_AND_FEATURES) {
                Global.getSector().addTransientScript(script = new CampaignScript());
                Global.getSector().getListenerManager().addListener(script, true);

                if (!Global.getSector().getPlayerFleet().hasAbility(ABILITY_ID)) {
                    Global.getSector().getCharacterData().addAbility(ABILITY_ID);
                }
            }

            if (!settingsAlreadyRead) {
                try {
                    JSONObject cfg = Global.getSettings().getMergedJSONForMod(SETTINGS_PATH, ID);

                    SENSOR_PROFILE_INCREASE = (float) cfg.getDouble("SensorProfileIncrease");
                    LIGHTYEARS_JUMPED_PER_BURN_LEVEL = (float) cfg.getDouble("LightYearsJumpedPerBurnLevel");
                    FLEET_INTERFERENCE_RANGE_MULT = (float) cfg.getDouble("fleetInterferenceRangeMult");
                    CR_CONSUMPTION_MULT = (float) cfg.getDouble("crConsumptionMult");
                    FUEL_CONSUMPTION_MULT = (float) cfg.getDouble("fuelConsumptionMult");
                    FUEL_CONSUMPTION_MULT_IN_NORMAL_SPACE = (float) cfg.getDouble("fuelConsumptionMultInNormalSpace");
                    MISSION_TIME_LIMIT_MULT = (float) cfg.getDouble("missionTimeLimitMult");
                    HALF_MISSION_TIME_LIMIT_MULT = (MISSION_TIME_LIMIT_MULT + 1) / 2f;

                    SHOW_WARP_PULSE_ANIMATION = cfg.getBoolean("showWarpPulseAnimation");
                    USABLE_IN_HYPERSPACE = cfg.getBoolean("usableInHyperspace");
                    USABLE_IN_NORMAL_SPACE = cfg.getBoolean("usableInNormalSpace");
                    USABLE_AT_NEUTRON_STARS = cfg.getBoolean("usableAtNeutronStars");
                    NPC_FLEETS_CAN_USE_FOR_TRAVEL = cfg.getBoolean("npcFleetsCanUseForTravel");
                    NPC_FLEETS_CAN_USE_TO_INTERCEPT_PLAYER = cfg.getBoolean("npcFleetsCanUseToInterceptPlayer");
                    NPC_FLEETS_CAN_INTERCEPT_PLAYER_IF_UNAWARE_OF_IDENTITY = cfg.getBoolean("npcFleetsCanInterceptPlayerIfUnawareOfIdentity");

                    SHOW_DEBUG_INFO_IN_DEV_MODE = cfg.getBoolean("showDebugInfoInDevMode");
                    REMOVE_ALL_DATA_AND_FEATURES = cfg.getBoolean("removeAllDataAndFeatures");

                    THROTTLE_BURN_LEVEL_ACTIVATION_KEY = cfg.getInt("throttleBurnActivationKey");
                    THROTTLE_BURN_LEVEL_BY_DEFAULT_DURING_AUTOPILOT = cfg.getBoolean("throttleBurnLevelByDefaultDuringAutopilot");

                    HyperdriveAbility.MIN_BURN_LEVEL = (int)Math.max(1, 3f * 0.5f / LIGHTYEARS_JUMPED_PER_BURN_LEVEL);
                } catch (Exception e) { reportCrash(e); }

                if(!REMOVE_ALL_DATA_AND_FEATURES) {
                    PersonBountyIntel.MAX_DURATION *= MISSION_TIME_LIMIT_MULT;

                    DeadDropMission.MISSION_DAYS *= MISSION_TIME_LIMIT_MULT;

//                    SmugglingMission.MISSION_DAYS *= HALF_MISSION_TIME_LIMIT_MULT;
//                    SpySatDeployment.MISSION_DAYS *= HALF_MISSION_TIME_LIMIT_MULT;

//                    ExtractionMission.MISSION_DAYS *= MISSION_TIME_LIMIT_MULT;
//                    JailbreakMission.MISSION_DAYS *= MISSION_TIME_LIMIT_MULT;
//                    BaseDisruptIndustry.MISSION_DAYS *= MISSION_TIME_LIMIT_MULT;
//                    DisruptCompetitorMission.MISSION_DAYS *= MISSION_TIME_LIMIT_MULT;
//                    TacticallyBombardColony.MISSION_DAYS *= MISSION_TIME_LIMIT_MULT;

//                    CBStats.DEFAULT_DAYS *= MISSION_TIME_LIMIT_MULT;
//                    CBStats.ENEMY_STATION_DAYS *= MISSION_TIME_LIMIT_MULT;
//                    CBStats.REMNANT_PLUS_DAYS *= MISSION_TIME_LIMIT_MULT;
//                    CBStats.REMNANT_STATION_DAYS *= MISSION_TIME_LIMIT_MULT;

                    ModManagerAPI mm = Global.getSettings().getModManager();

                    if (mm.isModEnabled("sun_starship_legends")) {
                        try {
                            FamousDerelictIntel.MAX_DURATION *= MISSION_TIME_LIMIT_MULT;
                        } catch (Exception e) {
                        }
                    }

                    if (mm.isModEnabled("vayrasector")) {
                        try {
                            VayraModPlugin.BOUNTY_DURATION *= MISSION_TIME_LIMIT_MULT;
                        } catch (Exception e) {
                        }
                    }
                }

                settingsAlreadyRead = true;
            }

            if(REMOVE_ALL_DATA_AND_FEATURES) {
                beforeGameSave();
                Global.getSector().getCharacterData().removeAbility(ABILITY_ID);
            }

            ensureReducedTimeLimitForMissions(true);
        } catch (Exception e) { reportCrash(e); }
    }

    @Override
    public void beforeGameSave() {
        try {
            if(script != null) {
                Global.getSector().removeTransientScript(script);
                Global.getSector().removeListener(script);
                Global.getSector().getListenerManager().removeListener(script);
            }
            Global.getSector().removeScriptsOfClass(CampaignScript.class);
        } catch (Exception e) { reportCrash(e); }
    }

    @Override
    public void afterGameSave() {
        try {
            if(!REMOVE_ALL_DATA_AND_FEATURES) {
                Global.getSector().addTransientScript(script = new CampaignScript());
                Global.getSector().getListenerManager().addListener(script, true);
            }
        } catch (Exception e) { reportCrash(e); }
    }
}

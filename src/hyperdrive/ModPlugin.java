package hyperdrive;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ModManagerAPI;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.PersistentUIDataAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.comm.IntelManagerAPI;
import com.fs.starfarer.api.impl.campaign.intel.AnalyzeEntityMissionIntel;
import com.fs.starfarer.api.impl.campaign.intel.BaseMissionIntel;
import com.fs.starfarer.api.impl.campaign.intel.PersonBountyIntel;
import com.fs.starfarer.api.impl.campaign.intel.SurveyPlanetMissionIntel;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.BarEventManager;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.ScientistAICoreBarEventCreator;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.ScientistAICoreIntel;
import com.fs.starfarer.api.impl.campaign.missions.*;
import com.thoughtworks.xstream.XStream;
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
    public final static Map<Class, Float> NORMAL_MISSION_DURATIONS = new HashMap<>();

    static {
        NORMAL_MISSION_DURATIONS.put(AnalyzeEntityMissionIntel.class, 120f);
        NORMAL_MISSION_DURATIONS.put(SurveyPlanetMissionIntel.class, 120f);
    }

    public static float
            ORIGINAL_BOUNTY_DURATION = 120,
            ORIGINAL_DEAD_DROP_DURATION = 120,
            ORIGINAL_FAMOUS_DERELICT_DURATION = 120,
            ORIGINAL_VAYRA_BOUNTY_DURATION = 120,
            SENSOR_PROFILE_INCREASE = 5000,
            LIGHTYEARS_JUMPED_PER_BURN_LEVEL = 0.5f,
            FLEET_INTERFERENCE_RANGE_MULT = 1,
            CR_CONSUMPTION_MULT = 1,
            FUEL_CONSUMPTION_MULT = 1,
            FUEL_CONSUMPTION_MULT_IN_NORMAL_SPACE = 0,
            MISSION_TIME_LIMIT_MULT = 1.0f,
            HALF_MISSION_TIME_LIMIT_MULT = 0.75f;

    public static int
            THROTTLE_BURN_LEVEL_ACTIVATION_KEY = 17;

    public static boolean
            REMOVE_ALL_DATA_AND_FEATURES = false,
            THROTTLE_BURN_LEVEL_BY_DEFAULT_DURING_AUTOPILOT = false,
            THROTTLE_ONLY_WHEN_ABILITY_IS_USABLE = false,
            SHOW_WARP_PULSE_ANIMATION = true,
            USABLE_IN_HYPERSPACE = true,
            USABLE_IN_NORMAL_SPACE = true,
            USABLE_AT_NEUTRON_STARS = false,
            NPC_FLEETS_CAN_USE_FOR_TRAVEL = true,
            NPC_FLEETS_CAN_USE_TO_INTERCEPT_PLAYER = true,
            SHOW_DEBUG_INFO_IN_DEV_MODE = false,
            UNLOCKED_BY_DEFAULT = false,
            UNLOCKED_BY_DESTROYING_REMNANT_CAPITAL = true,
            AVAILABLE_TO_REMNANTS = true,
            AVAILABLE_TO_PIRATES = false,
            AVAILABLE_TO_INDEPENDENTS = false,
            AVAILABLE_TO_PLAYER_FACTION = false,
            AVAILABLE_TO_NPC_FACTIONS = false,
            AVAILABLE_TO_OTHER = false,
            NPC_FLEETS_CAN_INTERCEPT_PLAYER_IF_UNAWARE_OF_IDENTITY = true;

    static ModPlugin instance = null;

    public static ModPlugin getInstance() { return instance; }
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
    public static boolean isTechCacheLooted() {
        ScientistAICoreBarEventCreator saicbec = null;

        for(BarEventManager.GenericBarEventCreator creator : BarEventManager.getInstance().getCreators()) {
            if(creator instanceof  ScientistAICoreBarEventCreator) saicbec = (ScientistAICoreBarEventCreator) creator;
        }

        float timeout = saicbec == null ? 0 : BarEventManager.getInstance().getTimeout().getRemaining(saicbec);

        // timeout seems to be positive at the beginning of a new game for some reason. 12.638144
        if(timeout > 100) {
            for (IntelInfoPlugin intel : Global.getSector().getIntelManager().getIntel(ScientistAICoreIntel.class)) {
                ScientistAICoreIntel saici = (ScientistAICoreIntel)intel;

                return saici.isDone() || saici.isEnding() || saici.isEnded();
            }

            return true;
        }

        return false;
    }
    public static void addAbilityIfNecessary() {
        // This method was adapted from com.fs.starfarer.api.impl.campaign.rulecmd.AddAbility
        boolean hadAbilityAlready = Global.getSector().getPlayerFleet().hasAbility(HyperdriveAbility.ID);

        Global.getSector().getCharacterData().addAbility(HyperdriveAbility.ID);

        if (!hadAbilityAlready) {
            PersistentUIDataAPI.AbilitySlotsAPI slots = Global.getSector().getUIData().getAbilitySlotsAPI();
            int currBarIndex = slots.getCurrBarIndex();
            OUTER: for (int i = 0; i < 5; i++) {
                slots.setCurrBarIndex(i);
                for (int j = 0; j < 10; j++) {
                    PersistentUIDataAPI.AbilitySlotAPI slot = slots.getCurrSlotsCopy().get(j);
                    if (slot.getAbilityId() == null) {
                        slot.setAbilityId(HyperdriveAbility.ID);
                        break OUTER;
                    }
                }
            }
            slots.setCurrBarIndex(currBarIndex);

            Global.getSector().getCharacterData().getMemoryWithoutUpdate().set("$ability:" + HyperdriveAbility.ID, true, 0);
        }
    }

    private CampaignScript script;
    private boolean settingsAlreadyRead = false;

    public boolean readSettingsIfNecessary(boolean forceRefresh) {
        if(forceRefresh) settingsAlreadyRead = false;

        if(settingsAlreadyRead) return true;

        try {
            JSONObject cfg = Global.getSettings().getMergedJSONForMod(SETTINGS_PATH, ID);

            UNLOCKED_BY_DEFAULT = cfg.getBoolean("unlockedByDefault");
            UNLOCKED_BY_DESTROYING_REMNANT_CAPITAL = cfg.getBoolean("unlockedByDestroyingRemnantCapital");
            AVAILABLE_TO_REMNANTS = cfg.getBoolean("availableToRemnants");
            AVAILABLE_TO_PIRATES = cfg.getBoolean("availableToPirates");
            AVAILABLE_TO_INDEPENDENTS = cfg.getBoolean("availableToIndependents");
            AVAILABLE_TO_PLAYER_FACTION = cfg.getBoolean("availableToPlayerFaction");
            AVAILABLE_TO_NPC_FACTIONS = cfg.getBoolean("availableToNpcFactions");
            AVAILABLE_TO_OTHER = cfg.getBoolean("availableToOther");

            HyperdriveAbility.MIN_BURN_LEVEL = Math.max(1, cfg.getInt("minBurnLevelToJump"));
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
            THROTTLE_ONLY_WHEN_ABILITY_IS_USABLE = cfg.getBoolean("throttleOnlyWhenAbilityIsUsable");

            HyperdriveAbility.MIN_BURN_LEVEL = (int)Math.max(1, 3f * 0.5f / LIGHTYEARS_JUMPED_PER_BURN_LEVEL);

            if(!REMOVE_ALL_DATA_AND_FEATURES) {
                PersonBountyIntel.MAX_DURATION = ORIGINAL_BOUNTY_DURATION * MISSION_TIME_LIMIT_MULT;
                DeadDropMission.MISSION_DAYS = ORIGINAL_DEAD_DROP_DURATION * MISSION_TIME_LIMIT_MULT;

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
                        FamousDerelictIntel.MAX_DURATION = ORIGINAL_FAMOUS_DERELICT_DURATION * MISSION_TIME_LIMIT_MULT;
                    } catch (Exception e) {
                    }
                }

                if (mm.isModEnabled("vayrasector")) {
                    try {
                        VayraModPlugin.BOUNTY_DURATION = ORIGINAL_VAYRA_BOUNTY_DURATION * MISSION_TIME_LIMIT_MULT;
                    } catch (Exception e) {
                    }
                }

                if (ModPlugin.UNLOCKED_BY_DEFAULT) {
                    addAbilityIfNecessary();
                }

                ensureReducedTimeLimitForMissions(true);
            }

            settingsAlreadyRead = true;
        } catch (Exception e) {
            return settingsAlreadyRead = reportCrash(e);
        }

        return true;
    }

    @Override
    public void onApplicationLoad() throws Exception {
        instance = this;

        ORIGINAL_BOUNTY_DURATION = PersonBountyIntel.MAX_DURATION;
        ORIGINAL_DEAD_DROP_DURATION = DeadDropMission.MISSION_DAYS;

        ModManagerAPI mm = Global.getSettings().getModManager();

        if (mm.isModEnabled("sun_starship_legends")) {
            try {
                ORIGINAL_FAMOUS_DERELICT_DURATION = FamousDerelictIntel.MAX_DURATION;
            } catch (Exception e) {
            }
        }

        if (mm.isModEnabled("vayrasector")) {
            try {
                ORIGINAL_VAYRA_BOUNTY_DURATION = VayraModPlugin.BOUNTY_DURATION;
            } catch (Exception e) {
            }
        }
    }

    @Override
    public void onGameLoad(boolean newGame) {
        try {
            readSettingsIfNecessary(true);

            if(REMOVE_ALL_DATA_AND_FEATURES) {
                beforeGameSave();
                Global.getSector().getCharacterData().removeAbility(HyperdriveAbility.ID);
            } else {
                Global.getSector().addTransientScript(script = new CampaignScript());
                Global.getSector().getListenerManager().addListener(script, true);
            }
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

    @Override
    public void configureXStream(XStream x) {
        HyperdriveAbility.configureXStream(x);
        HyperdriveAbility.Pulse.configureXStream(x);
    }
}

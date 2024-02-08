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
import com.fs.starfarer.api.impl.campaign.missions.DeadDropMission;
import com.fs.starfarer.api.impl.campaign.tutorial.TutorialMissionIntel;
import com.thoughtworks.xstream.XStream;
import data.scripts.VayraModPlugin;
import hyperdrive.campaign.abilities.HyperdriveAbility;
import lunalib.lunaSettings.LunaSettings;
import org.json.JSONObject;
import starship_legends.events.FamousDerelictIntel;

import java.awt.*;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.MissingResourceException;

public class ModPlugin extends BaseModPlugin {
    static class Version {
        public final int MAJOR, MINOR, PATCH, RC;

        public Version(String versionStr) {
            String[] temp = versionStr.replace("Starsector ", "").replace("a", "").split("-RC");

            RC = temp.length > 1 ? Integer.parseInt(temp[1]) : 0;

            temp = temp[0].split("\\.");

            MAJOR = temp.length > 0 ? Integer.parseInt(temp[0]) : 0;
            MINOR = temp.length > 1 ? Integer.parseInt(temp[1]) : 0;
            PATCH = temp.length > 2 ? Integer.parseInt(temp[2]) : 0;
        }

        public boolean isOlderThan(Version other, boolean ignoreRC) {
            if(MAJOR < other.MAJOR) return true;
            if(MINOR < other.MINOR) return true;
            if(PATCH < other.PATCH) return true;
            if(!ignoreRC && !other.isOlderThan(this, true) && RC < other.RC) return true;

            return false;
        }

        @Override
        public String toString() {
            return String.format("%d.%d.%d%s-RC%d", MAJOR, MINOR, PATCH, (MAJOR >= 1 ? "" : "a"), RC);
        }
    }

    public final static String ID = "sun_hyperdrive";
    public final static String PREFIX = "sun_hd_";
    public final static String SETTINGS_PATH = "HYPERDRIVE_OPTIONS.ini";
    public final static Version MIN_NS_VERSION_WITH_FUEL_CONSUMPTION_CALCULATION_METHOD = new Version("1.4.0");
    public final static Map<Class, Float> NORMAL_MISSION_DURATIONS = new HashMap<>();

    static final String LUNALIB_ID = "lunalib";
    static JSONObject settingsCfg = null;
    static <T> T get(String id, Class<T> type) throws Exception {
        if(Global.getSettings().getModManager().isModEnabled(LUNALIB_ID)) {
            id = PREFIX + id;

            if(type == Integer.class) return type.cast(LunaSettings.getInt(ModPlugin.ID, id));
            if(type == Float.class) return type.cast(LunaSettings.getFloat(ModPlugin.ID, id));
            if(type == Boolean.class) return type.cast(LunaSettings.getBoolean(ModPlugin.ID, id));
            if(type == Double.class) return type.cast(LunaSettings.getDouble(ModPlugin.ID, id));
            if(type == String.class) return type.cast(LunaSettings.getString(ModPlugin.ID, id));
        } else {
            if(settingsCfg == null) settingsCfg = Global.getSettings().getMergedJSONForMod(SETTINGS_PATH, ID);

            if(type == Integer.class) return type.cast(settingsCfg.getInt(id));
            if(type == Float.class) return type.cast((float) settingsCfg.getDouble(id));
            if(type == Boolean.class) return type.cast(settingsCfg.getBoolean(id));
            if(type == Double.class) return type.cast(settingsCfg.getDouble(id));
            if(type == String.class) return type.cast(settingsCfg.getString(id));
        }

        throw new MissingResourceException("No setting found with id: " + id, type.getName(), id);
    }
    static int getInt(String id) throws Exception { return get(id, Integer.class); }
    static double getDouble(String id) throws Exception { return get(id, Double.class); }
    static float getFloat(String id) throws Exception { return get(id, Float.class); }
    static boolean getBoolean(String id) throws Exception { return get(id, Boolean.class); }
    static String getString(String id) throws Exception { return get(id, String.class); }
    static boolean readSettings() throws Exception {
        UNLOCKED_BY_DEFAULT = getBoolean("unlockedByDefault");
        UNLOCKED_BY_DESTROYING_REMNANT_CAPITAL = getBoolean("unlockedByDestroyingRemnantCapital");
        AVAILABLE_TO_REMNANTS = getBoolean("availableToRemnants");
        AVAILABLE_TO_PIRATES = getBoolean("availableToPirates");
        AVAILABLE_TO_INDEPENDENTS = getBoolean("availableToIndependents");
        AVAILABLE_TO_PLAYER_FACTION = getBoolean("availableToPlayerFaction");
        AVAILABLE_TO_NPC_FACTIONS = getBoolean("availableToNpcFactions");
        AVAILABLE_TO_OTHER = getBoolean("availableToOther");

        HyperdriveAbility.MIN_BURN_LEVEL = Math.max(1, getInt("minBurnLevelToJump"));
        SENSOR_PROFILE_INCREASE = getFloat("SensorProfileIncrease");
        LIGHTYEARS_JUMPED_PER_BURN_LEVEL = getFloat("LightYearsJumpedPerBurnLevel");
        FLEET_INTERFERENCE_RANGE_MULT = getFloat("fleetInterferenceRangeMult");
        CR_CONSUMPTION_MULT = getFloat("crConsumptionMult");
        FUEL_CONSUMPTION_MULT = getFloat("fuelConsumptionMult");
        FUEL_CONSUMPTION_MULT_IN_NORMAL_SPACE = getFloat("fuelConsumptionMultInNormalSpace");
        MISSION_TIME_LIMIT_MULT = getFloat("missionTimeLimitMult");
        HALF_MISSION_TIME_LIMIT_MULT = (MISSION_TIME_LIMIT_MULT + 1) / 2f;

        SHOW_WARP_PULSE_ANIMATION = getBoolean("showWarpPulseAnimation");
        USABLE_IN_HYPERSPACE = getBoolean("usableInHyperspace");
        USABLE_IN_NORMAL_SPACE = getBoolean("usableInNormalSpace");
        USABLE_AT_NEUTRON_STARS = getBoolean("usableAtNeutronStars");
        USABLE_IN_ABYSSAL_HYPERSPACE = getBoolean("usableInAbyssalHyperspace");
        USABLE_WITH_MOTHBALLED_SHIPS = getBoolean("usableWithMothballedShips");
        NPC_FLEETS_CAN_USE_FOR_TRAVEL = getBoolean("npcFleetsCanUseForTravel");
        NPC_FLEETS_CAN_USE_TO_INTERCEPT_PLAYER = getBoolean("npcFleetsCanUseToInterceptPlayer");
        NPC_FLEETS_CAN_INTERCEPT_PLAYER_IF_UNAWARE_OF_IDENTITY = getBoolean("npcFleetsCanInterceptPlayerIfUnawareOfIdentity");

        SHOW_DEBUG_INFO_IN_DEV_MODE = getBoolean("showDebugInfoInDevMode");
        REMOVE_ALL_DATA_AND_FEATURES = getBoolean("removeAllDataAndFeatures");

        THROTTLE_BURN_LEVEL_ACTIVATION_KEY = getInt("throttleBurnActivationKey");
        THROTTLE_BURN_LEVEL_BY_DEFAULT_DURING_AUTOPILOT = getBoolean("throttleBurnLevelByDefaultDuringAutopilot");
        THROTTLE_ONLY_WHEN_ABILITY_IS_USABLE = getBoolean("throttleOnlyWhenAbilityIsUsable");

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
                    if(ORIGINAL_VAYRA_BOUNTY_DURATION == Float.MIN_VALUE) {
                        ORIGINAL_VAYRA_BOUNTY_DURATION = VayraModPlugin.BOUNTY_DURATION;
                    }

                    VayraModPlugin.BOUNTY_DURATION = ORIGINAL_VAYRA_BOUNTY_DURATION * MISSION_TIME_LIMIT_MULT;
                } catch (Exception e) {
                }
            }

            if (ModPlugin.UNLOCKED_BY_DEFAULT) {
                addAbilityIfNecessary();
            }

            checkForNomadicSurvivalAlternateFuelCalculationCompatibility("sun_nomadic_survival");
            checkForNomadicSurvivalAlternateFuelCalculationCompatibility("sun_perilous_expanse");

            ensureReducedTimeLimitForMissions(true);
        }

        return true;
    }
    static void checkForNomadicSurvivalAlternateFuelCalculationCompatibility(String modID) {
        ModManagerAPI mm = Global.getSettings().getModManager();

        if(mm.isModEnabled(modID)) {
            Version currentVersionOfNS = new Version(mm.getModSpec(modID).getVersion());

            if(!currentVersionOfNS.isOlderThan(MIN_NS_VERSION_WITH_FUEL_CONSUMPTION_CALCULATION_METHOD, true)) {
                USE_NS_FUEL_CONSUMPTION_CALCULATION = true;
            }
        }
    }

    static {
        NORMAL_MISSION_DURATIONS.put(AnalyzeEntityMissionIntel.class, 120f);
        NORMAL_MISSION_DURATIONS.put(SurveyPlanetMissionIntel.class, 120f);
    }

    public static float
            ORIGINAL_BOUNTY_DURATION = 120,
            ORIGINAL_DEAD_DROP_DURATION = 120,
            ORIGINAL_FAMOUS_DERELICT_DURATION = 120,
            ORIGINAL_VAYRA_BOUNTY_DURATION = Float.MIN_VALUE,
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
            USE_NS_FUEL_CONSUMPTION_CALCULATION = false,
            REMOVE_ALL_DATA_AND_FEATURES = false,
            THROTTLE_BURN_LEVEL_BY_DEFAULT_DURING_AUTOPILOT = false,
            THROTTLE_ONLY_WHEN_ABILITY_IS_USABLE = false,
            SHOW_WARP_PULSE_ANIMATION = true,
            USABLE_IN_HYPERSPACE = true,
            USABLE_IN_NORMAL_SPACE = true,
            USABLE_AT_NEUTRON_STARS = false,
            USABLE_IN_ABYSSAL_HYPERSPACE = false,
            USABLE_WITH_MOTHBALLED_SHIPS = false,
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
        if(REMOVE_ALL_DATA_AND_FEATURES || MISSION_TIME_LIMIT_MULT == 1f) return;

        IntelManagerAPI manager = Global.getSector().getIntelManager();

        for(Map.Entry<Class, Float> entry : NORMAL_MISSION_DURATIONS.entrySet()) {
            if(BaseMissionIntel.class.isAssignableFrom(entry.getKey())) {
                float newLimit = entry.getValue() * MISSION_TIME_LIMIT_MULT;
                Collection<IntelInfoPlugin> missions = manager.getCommQueue(entry.getKey());

                if(includeOldMissions) missions.addAll(manager.getIntel(entry.getKey()));

                for(IntelInfoPlugin intel : missions) {
                    BaseMissionIntel bmi = (BaseMissionIntel) intel;
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

        if (!hadAbilityAlready && !TutorialMissionIntel.isTutorialInProgress()) {
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
            readSettings();

            settingsAlreadyRead = true;
        } catch (Exception e) {
            return settingsAlreadyRead = reportCrash(e);
        }

        return true;
    }

    void removeScripts() {
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

        // VayraModPlugin.BOUNTY_DURATION == 0 at this point
//        if (mm.isModEnabled("vayrasector")) {
//            try {
//                ORIGINAL_VAYRA_BOUNTY_DURATION = VayraModPlugin.BOUNTY_DURATION;
//            } catch (Exception e) {
//            }
//        }
    }

    @Override
    public void onGameLoad(boolean newGame) {
        try {
            removeScripts();

            if(Global.getSettings().getModManager().isModEnabled(LUNALIB_ID)) {
                LunaSettingsChangedListener.addToManagerIfNeeded();
            }

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
        removeScripts();
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

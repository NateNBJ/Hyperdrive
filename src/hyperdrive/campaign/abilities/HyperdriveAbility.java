package hyperdrive.campaign.abilities;

import java.awt.Color;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;

import com.fs.starfarer.api.SoundAPI;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.abilities.BaseDurationAbility;
import com.fs.starfarer.api.impl.campaign.abilities.InterdictionPulseAbility;
import com.fs.starfarer.api.impl.campaign.ids.Pings;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.campaign.CampaignEngine;
import hyperdrive.CampaignScript;
import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import hyperdrive.ModPlugin;

import static hyperdrive.ModPlugin.reportCrash;

public class HyperdriveAbility extends BaseDurationAbility {
	public static final String ID = "sun_hd_hyperdrive";

	static class Pulse {
		float rotation = 0, initialProgress = 0;
		Vector2f center = new Vector2f();
	}

	public static void print(String msg) { Global.getSector().getCampaignUI().addMessage(msg); }
	public static void log(String msg) { Global.getLogger(HyperdriveAbility.class).info(msg); }

    public static final float
			BASE_CR_COST_MULT = 0.2f,
			PULSE_DISTANCE_TIL_FADE = 5,
			PULSE_AMOUNT = 40 * 85,
			MOMENTUM_CARRY_DISTANCE = 1500f,
			VELOCITY_LIMIT;
	public static final int
			MIN_BURN_LEVEL = 3;
	public static final Color pulseColor = new Color(97, 136, 226, 255);

    static {
		VELOCITY_LIMIT = 60 * Global.getSettings().getSpeedPerBurnLevel();
	}

	SpriteAPI sprite = null;
    boolean transitionStarted = false;
    Vector2f directionAtActivation = null;
    Vector2f forwardOffset = new Vector2f();
    Vector2f travelOffset = new Vector2f();
    Vector2f fleetLocationLastFrame = new Vector2f();
    float initialVelocity = 0;
    float timeSinceStart = 0;
	SectorEntityToken token = null;
	int pulsesSpawned = 0;
	float pulseFacing = 0;
	List<Pulse> pulses = new LinkedList<>();
	Vector2f minWaveSize = new Vector2f(), originalSpriteSize = new Vector2f();
	CampaignFleetAPI fleet = null;
	SoundAPI chargeSound = null;

	public SectorEntityToken getDestinationToken() {
		return token;
	}

	public static float getDistancePerBurn() {
		return ModPlugin.LIGHTYEARS_JUMPED_PER_BURN_LEVEL * Global.getSettings().getUnitsPerLightYear();
	}
	public static float getMinimumJumpDistance() {
		return getDistancePerBurn() * MIN_BURN_LEVEL;
	}

	Vector2f getJumpOffset() {
		Vector2f result = new Vector2f();

		if(getFleet().getVelocity().length() <= 0) return result;

		int bl = (int)Math.floor(getFleet().getCurrBurnLevel() - 1);
		result.set(getFleet().getVelocity()).normalise().scale(bl * getDistancePerBurn());

		return result;
	}

	@Override
	public void advance(float amount) {
		try {
			if ((fleet = getFleet()) == null) return;

			super.advance(amount);

			if (isActiveOrInProgress()) {
				fleet.getStats().getDetectedRangeMod().modifyFlat(getModId(), ModPlugin.SENSOR_PROFILE_INCREASE
						* (float) Math.pow(isFadingOut() ? 1 : getProgressFraction(), 0.3f), "Hyperwarp jump");
			} else if (isOnCooldown()) {
				fleet.getStats().getDetectedRangeMod().modifyFlat(getModId(), ModPlugin.SENSOR_PROFILE_INCREASE
						* (float) Math.pow(1 - getCooldownFraction(), 0.5f), "Hyperwarp jump");
			} else {
				fleet.getStats().getDetectedRangeMod().unmodify(getModId());
			}
		} catch (Exception e) { reportCrash(e); }
	}

	@Override
	public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
		try {
			if ((fleet = getFleet()) == null) return;

			if(!isActiveOrInProgress() && !isOnCooldown()) pulses.clear();

			if (!Global.getSector().isPaused() && chargeSound == null && fleet.isPlayerFleet() && isActiveOrInProgress()) {
				chargeSound = Global.getSoundPlayer().playSound("sun_hd_charge", 0.95f, 0.6f, fleet.getLocation(), fleet.getVelocity());
			} else if(Global.getSector().isPaused() && chargeSound != null && getProgressFraction() > 0.05f) chargeSound.stop();

			if (pulses.isEmpty()
					|| !(fleet.isPlayerFleet() || (CampaignEngine.getInstance().getPlayerFleet() != null && fleet.isVisibleToPlayerFleet()))
					|| !ModPlugin.SHOW_WARP_PULSE_ANIMATION)
				return;

			if (sprite == null) resetSprite();

			List<Pulse> pulsesToRemove = new LinkedList<>();
			Vector2f moveDistanceLastFrame = new Vector2f(fleet.getLocation()).translate(-fleetLocationLastFrame.x, -fleetLocationLastFrame.y);

			if(moveDistanceLastFrame.length() > 200) handleTransition(moveDistanceLastFrame);

			fleetLocationLastFrame.set(fleet.getLocation());

			Vector2f startPoint = new Vector2f(fleet.getLocation())
					.translate(forwardOffset.x, forwardOffset.y);

for(Pulse pulse : pulses) {
	float distanceFromStart = Misc.getDistance(startPoint, pulse.center);
	float distanceFromFleet = Misc.getDistance(fleet.getLocation(), pulse.center);
	float progress = distanceFromStart / (PULSE_DISTANCE_TIL_FADE * (20 + fleet.getRadius()));
	boolean outOfPlace = distanceFromFleet > distanceFromStart
			&& Misc.isInArc(fleet.getFacing(), 180, startPoint, pulse.center);

	if(progress > 1 || outOfPlace) {
		pulsesToRemove.add(pulse);
		continue;
	}

	float scalor = (float)Math.pow(progress, 0.4f);
	Vector2f size = (Vector2f) new Vector2f(minWaveSize).scale(scalor * 5.0f);

	sprite.setSize(size.x, size.y);
	sprite.setCenter(sprite.getWidth() / 2f, sprite.getHeight() / 2f);
	sprite.setAngle(pulseFacing + 90);
	sprite.setAlphaMult(0.2f * (1 - progress));
	sprite.renderAtCenter(pulse.center.x, pulse.center.y);
}

			for(Pulse pulse : pulsesToRemove) pulses.remove(pulse);
		} catch (Exception e) { reportCrash(e); }
	}

    @Override
    protected void activateImpl() {
		try {
			if ((fleet = getFleet()) == null) return;

			resetSprite();

			int bl = (int)Math.floor(fleet.getCurrBurnLevel() - 1);
			float distance = bl * getDistancePerBurn();
			float requiredFuel = computeFuelCost(distance);
			float fuel = fleet.getCargo().getFuel();

			if(requiredFuel > 0) distance = Math.min(distance, distance * (fuel / requiredFuel));

			//pulseFacing = fleet.getFacing();
			initialVelocity = fleet.getVelocity().length();
			directionAtActivation = (Vector2f) new Vector2f(fleet.getVelocity()).normalise();
			forwardOffset.set(directionAtActivation).scale(fleet.getRadius() * 1.2f + 10);
			fleetLocationLastFrame.set(fleet.getLocation());
			minWaveSize.set(originalSpriteSize).scale(fleet.getRadius() / 100f);
			travelOffset.set(fleet.getVelocity()).normalise().scale(distance);
			fleet.getCargo().removeFuel(computeFuelCost(travelOffset.length()));

			token = fleet.getContainingLocation().createToken(fleet.getLocation().x + travelOffset.x,
					fleet.getLocation().y + travelOffset.y);
			fleet.getContainingLocation().addEntity(token);
			JumpPointAPI.JumpDestination dest = new JumpPointAPI.JumpDestination(token, null);
			Global.getSector().doHyperspaceTransition(fleet, null, dest);
			transitionStarted = true;

			if(!fleet.isAIMode() && ModPlugin.CR_CONSUMPTION_MULT > 0) {
			for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
				float crLoss = member.getDeployCost() * BASE_CR_COST_MULT * ModPlugin.CR_CONSUMPTION_MULT;
				member.getRepairTracker().applyCREvent(-crLoss, "Hyperwarp jump");
			}
		}
		} catch (Exception e) { reportCrash(e); }
	}

    @Override
    public void createTooltip(TooltipMakerAPI tooltip, boolean expanded) {
		try {
			if ((fleet = getFleet()) == null) return;

			Color highlight = Misc.getHighlightColor();
			Color bad = Misc.getNegativeHighlightColor();
			Color gray = Misc.getGrayColor();

			tooltip.addTitle(spec.getName());

			float pad = 10f;
			int bl = (int)Math.floor(fleet.getCurrBurnLevel() - 1);
			float requiredFuel = computeFuelCost(bl * getDistancePerBurn());
			float fuel = fleet.getCargo().getFuel();

			tooltip.addPara("Instantaneously travel a great distance by establishing a hyperwarp drive bubble", pad);

			if (isActiveOrInProgress()) {
				tooltip.addPara("Hyperwarp drive bubble established. Jump in progress.", highlight, pad);
			} else if(hasSufficientFuelForMinimalJump() && bl >= MIN_BURN_LEVEL) {
				if (requiredFuel > 0 && requiredFuel >= fuel) {
					tooltip.addPara("With the fleet's remaining %s fuel it can jump %s light-years.", pad, highlight,
							Misc.getRoundedValueMaxOneAfterDecimal(fuel),
							Misc.getRoundedValueMaxOneAfterDecimal(bl * ModPlugin.LIGHTYEARS_JUMPED_PER_BURN_LEVEL * (fuel / requiredFuel)));
				} else {
					String unitDesc = fleet.isInHyperspace() && requiredFuel > 0
							? "%s light-years, consuming %s fuel."
							: "%s light-years.";

					tooltip.addPara("At the current burn level of %s, the fleet will jump forward by " + unitDesc, pad,
							highlight, bl + "",
							Misc.getRoundedValueMaxOneAfterDecimal(bl * ModPlugin.LIGHTYEARS_JUMPED_PER_BURN_LEVEL),
							Misc.getRoundedValueMaxOneAfterDecimal(requiredFuel));
				}
			}

			tooltip.addPara("Reduces the combat readiness of all ships, costing up to %s supplies to recover. "
					 + "Also increases the range at which your fleet can be detected by %s units.", pad, highlight,
					Misc.getRoundedValueMaxOneAfterDecimal(computeSupplyCost()), ((int)ModPlugin.SENSOR_PROFILE_INCREASE) + "");

			if(!isActiveOrInProgress()) {
				if(!hasSufficientFuelForMinimalJump()) {
					tooltip.addPara("Not enough fuel.", bad, pad);
				}

				if (bl < MIN_BURN_LEVEL) {
					tooltip.addPara("Burn level must be at least " + MIN_BURN_LEVEL
							+ ".", bad, pad);
				}

				if (!isClearOfOtherFleets()) {
					tooltip.addPara("Interference from the drive field of at least one nearby non-allied fleet is "
							+ "making it impossible to establish a stable hyperwarp drive bubble.", bad, pad);
				}

				if (isInSystemWithNeutronStar()) {
					tooltip.addPara("Interference from the nearby neutron star is making it impossible to establish a "
							+ "stable hyperwarp drive bubble.", bad, pad);
				}

				List<FleetMemberAPI> nonReady = getNonReadyShips();
				if (!nonReady.isEmpty()) {
					tooltip.addPara("Not all ships have enough combat readiness to initiate a hyperwarp jump. Ships that require higher CR:", pad);
					tooltip.beginGridFlipped(getTooltipWidth(), 1, 30, pad);
					//tooltip.setGridLabelColor(bad);
					int j = 0;
					int max = 7;
					for (FleetMemberAPI member : nonReady) {
						if (j >= max) {
							if (nonReady.size() > max + 1) {
								tooltip.addToGrid(0, j++, "... and several other ships", "", bad);
								break;
							}
						}
						float crLoss = member.getDeployCost() * BASE_CR_COST_MULT;
						String cost = "" + Math.round(crLoss * 100) + "%";
						String str = "";
						if (!member.isFighterWing()) {
							str += member.getShipName() + ", ";
							str += member.getHullSpec().getHullNameWithDashClass();
						} else {
							str += member.getVariant().getFullDesignationWithHullName();
						}
						tooltip.addToGrid(0, j++, str, cost, bad);
					}
					tooltip.addGrid(3f);
				}
			}

			addIncompatibleToTooltip(tooltip, expanded);
		} catch (Exception e) { reportCrash(e); }
    }

    @Override
    protected void applyEffect(float amount, float level) {
		try {
			if ((fleet = getFleet()) == null) return;

			timeSinceStart += amount;

			if (level <= 0 || directionAtActivation == null) return;

			if(getProgressFraction() < 1 && pulsesSpawned <= Math.pow(getProgressFraction(), 1.25f) * PULSE_AMOUNT / fleet.getRadius()) {
				pulsesSpawned++;
				Pulse pulse = new Pulse();
				pulse.rotation = pulsesSpawned % 2 == 0 ? -1 : 1;
				pulse.initialProgress = getProgressFraction();
				pulse.center.set(fleet.getLocation()).translate(forwardOffset.x, forwardOffset.y);
				pulse.center.set(Misc.getPointWithinRadius(pulse.center, fleet.getRadius() / 7f));
				pulses.add(pulse);
			}
			
			float newVel = initialVelocity + (VELOCITY_LIMIT - initialVelocity) * this.getProgressFraction();
			Vector2f v = (Vector2f) new Vector2f().set(directionAtActivation).scale(newVel);
			fleet.setVelocity(v.x, v.y);
			fleet.setFacing(Misc.getAngleInDegrees(fleet.getVelocity()));

			pulseFacing = fleet.getFacing();

			fleet.getCommanderStats().getDynamic().getStat(Stats.NAVIGATION_PENALTY_MULT).modifyMult(getModId(), 1 - level);
			fleet.getStats().getFuelUseHyperMult().modifyMult(getModId(), 0, "Hyperwarp jump");
			fleet.getStats().removeTemporaryMod("going_through_jump_point");

			if(chargeSound != null) chargeSound.setLocation(fleet.getLocation().x, fleet.getLocation().y);
		} catch (Exception e) { reportCrash(e); }
    }

    @Override
    public boolean isUsable() {
		try {
			if ((fleet = getFleet()) == null) return false;

			return super.isUsable()
					&& fleet != null
					&& !fleet.isInHyperspaceTransition()
					&& (fleet.isInHyperspace() ? ModPlugin.USABLE_IN_HYPERSPACE : ModPlugin.USABLE_IN_NORMAL_SPACE)
					&& (fleet.isAIMode() || (int)Math.floor(fleet.getCurrBurnLevel()) >= MIN_BURN_LEVEL)
					&& hasSufficientFuelForMinimalJump()
					&& (ModPlugin.USABLE_AT_NEUTRON_STARS || !isInSystemWithNeutronStar())
					&& getNonReadyShips().isEmpty()
					&& isClearOfOtherFleets();
		} catch (Exception e) { reportCrash(e); }

		return false;
    }

	@Override
    protected void deactivateImpl() { cleanupImpl(); }

    @Override
    protected void cleanupImpl() {
		try {
			fadingOut = false;
			transitionStarted = false;
			pulsesSpawned = 0;
			chargeSound = null;

			if(token != null) {
				token.getContainingLocation().removeEntity(token);
				token = null;
			}

			if ((fleet = getFleet()) == null) return;

			if(fleet.isPlayerFleet()) fleet.clearAssignments();

			fleet.setMoveDestination(fleet.getLocation().x, fleet.getLocation().y);
			fleet.getStats().getFuelUseHyperMult().unmodify(getModId());
			//fleet.getStats().getDetectedRangeMod().unmodify(getModId());
			fleet.getStats().getAccelerationMult().unmodify(getModId());
			fleet.getStats().removeTemporaryMod(getModId());
			fleet.getCommanderStats().getDynamic().getStat(Stats.NAVIGATION_PENALTY_MULT).unmodify(getModId());
		} catch (Exception e) { reportCrash(e); }
    }

    void handleTransition(Vector2f moveDistanceLastFrame) {
		if(fleet.isPlayerFleet()) Global.getSoundPlayer().playUISound("sun_hd_transition_boom", 0.9f, 0.5f);
		else Global.getSoundPlayer().playSound("sun_hd_transition_boom", 0.9f, 0.5f, fleet.getLocation(), fleet.getVelocity());

		if(chargeSound != null) chargeSound.stop();

		for(Pulse pulse : pulses) {
			pulse.center.translate(moveDistanceLastFrame.x, moveDistanceLastFrame.y);
		}

		Global.getSector().addPing(fleet, Pings.SENSOR_BURST);
		fleet.setNoEngaging(2.5f);
		activeDaysLeft = activeDaysLeft > getDeactivationDays() ? getDeactivationDays() : activeDaysLeft;
		if(fleet.isPlayerFleet()) fleet.clearAssignments();

	}
	List<FleetMemberAPI> getNonReadyShips() {
		List<FleetMemberAPI> result = new ArrayList<FleetMemberAPI>();

		float crCostFleetMult = 1f;
		for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
			float crLoss = member.getDeployCost() * BASE_CR_COST_MULT * crCostFleetMult;
			if (Math.round(member.getRepairTracker().getCR() * 100) < Math.round(crLoss * 100)) {
				result.add(member);
			}
		}
		return result;
	}
	boolean hasSufficientFuelForMinimalJump() {
		if(fleet.isAIMode()) return true;

		float requiredFuel = computeFuelCost(MIN_BURN_LEVEL * getDistancePerBurn());
		float fuel = fleet.getCargo().getFuel();

		return fuel >= requiredFuel;
	}
	float computeFuelCost(float distance) {
		if (!fleet.isInHyperspace() || fleet.isAIMode()) return 0f;

		float distanceInLY = distance / Global.getSettings().getUnitsPerLightYear();

		return fleet.getLogistics().getFuelCostPerLightYear() * distanceInLY * ModPlugin.FUEL_CONSUMPTION_MULT;
	}
	float computeSupplyCost() {
		if (ModPlugin.CR_CONSUMPTION_MULT <= 0) return 0f;

		float cost = 0f;
		for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
			cost += member.getDeploymentPointsCost();
		}
		return cost * ModPlugin.CR_CONSUMPTION_MULT * BASE_CR_COST_MULT;
	}
	boolean isInSystemWithNeutronStar() {
		if(fleet.isInHyperspace()) return false;

		StarSystemAPI system = fleet.getStarSystem();

		if(system == null) return false;

		return  (system.getStar() != null && system.getStar().getTypeId().contains("neutron"))
				|| (system.getSecondary() != null && system.getSecondary().getTypeId().contains("neutron"))
				|| (system.getTertiary() != null && system.getTertiary().getTypeId().contains("neutron"));
	}
	boolean isClearOfOtherFleets() {
		return getBlockingFleets().isEmpty();
	}
	void resetSprite() {
		sprite = Global.getSettings().getSprite("hyperdrive", "warp_pulse");
		sprite.setColor(pulseColor);
		sprite.setAdditiveBlend();
		originalSpriteSize.set(sprite.getWidth(), sprite.getHeight());
	}
	List<CampaignFleetAPI> getBlockingFleets() {
		List<CampaignFleetAPI> result = new ArrayList();

		if(ModPlugin.FLEET_INTERFERENCE_RANGE_MULT <= 0) return result;

		boolean visibleToPlayer = CampaignScript.attachedFleet == fleet ? false : fleet.isVisibleToPlayerFleet();

		for (CampaignFleetAPI other : fleet.getContainingLocation().getFleets()) {
			float blockRange = InterdictionPulseAbility.getRange(other) * 2;

			blockRange *= ModPlugin.FLEET_INTERFERENCE_RANGE_MULT;

			if(ModPlugin.isTestingModeActive()
					&& CampaignScript.attachedFleet == fleet
					&& other == Global.getSector().getPlayerFleet())
				continue;

			if(fleet.getFaction().getRelationshipLevel(other.getFaction()) == RepLevel.COOPERATIVE) continue;

			// Below is to imitate AI awareness that they shouldn't block each other from warping
			if (fleet.isAIMode() && !visibleToPlayer && fleet.getFaction().getId().equals(other.getFaction().getId())) {
				blockRange *= 0.30f;
			}

			if (fleet != other && !other.isStationMode() && Misc.getDistance(fleet, other) <= blockRange) {
				result.add(other);
			}
		}

		return result;
	}

	@Override
	public EnumSet<CampaignEngineLayers> getActiveLayers() {
		return EnumSet.of(CampaignEngineLayers.ABOVE);
	}

	@Override
	public boolean hasTooltip() { return true; }

	@Override
	public boolean showProgressIndicator() {
		return false;
	}

	@Override
	public boolean showActiveIndicator() {
		return turnedOn;
	}
}
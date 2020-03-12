1.1.2

Fixed throttling messages sometimes being shown several times in a row
Added "Too close to warp - resuming max burn" message to explain why burn level throttling stops when too close to the target
Added "fuelConsumptionMultInNormalSpace" setting, allowing hyperwarp to consume fuel outside of hyperspace when configured to do so (off by default)
Changed: Hyperwarp cooldown now finishes immediately when transitioning to or from hyperspace


1.1.1

Fixed the possibility for burn level throttling to be toggled in many invalid contexts (most campaign menus)
Fixed an issue that could sometimes cause burn level throttling to toggle several times when the hotkey was pressed


1.1.0

Added a way to automatically align your fleet to precisely warp to the "Lay in course" destination:
	Press W to toggle burn level throttling while autopilot is active
	This will reduce your velocity to the amount needed to warp to your destination
	Added settings for changing the hotkey and setting whether or not burn level is throttled by default
Added a setting for disabling the mod through the options file: "removeAllDataAndFeatures"
Fixed limiting mission time limits prior to reading settings, which could lead to incorrect mission time limit changes
Fixed an incompatibility with old versions of Vayra's Sector that lead to a crash upon loading
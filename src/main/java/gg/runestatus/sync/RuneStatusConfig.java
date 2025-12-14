package gg.runestatus.sync;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup("runestatus")
public interface RuneStatusConfig extends Config
{
	@ConfigSection(
		name = "Privacy",
		description = "Your privacy always comes first - we never store your IP address.",
		position = -1,
		closedByDefault = false
	)
	String privacySection = "privacy";

	@ConfigSection(
		name = "Sync Settings",
		description = "Configure sync behavior",
		position = 0
	)
	String syncSection = "sync";

	@ConfigItem(
		keyName = "enableSync",
		name = "Enable Sync",
		description = "Enable automatic syncing to RuneStatus",
		section = syncSection,
		position = 0
	)
	default boolean enableSync()
	{
		return true;
	}

	@ConfigItem(
		keyName = "syncInterval",
		name = "Sync Interval (minutes)",
		description = "How often to sync data to RuneStatus (in minutes)",
		section = syncSection,
		position = 1
	)
	@Range(min = 1, max = 60)
	default int syncInterval()
	{
		return 5;
	}

	@ConfigSection(
		name = "Data Options",
		description = "Choose what data to sync",
		position = 1
	)
	String dataSection = "data";

	@ConfigItem(
		keyName = "syncSkills",
		name = "Sync Skills",
		description = "Sync skill levels and XP",
		section = dataSection,
		position = 0
	)
	default boolean syncSkills()
	{
		return true;
	}

	@ConfigItem(
		keyName = "syncQuests",
		name = "Sync Quests",
		description = "Sync quest completion status",
		section = dataSection,
		position = 1
	)
	default boolean syncQuests()
	{
		return true;
	}

	@ConfigItem(
		keyName = "syncDiaries",
		name = "Sync Achievement Diaries",
		description = "Sync achievement diary completion",
		section = dataSection,
		position = 2
	)
	default boolean syncDiaries()
	{
		return true;
	}

	@ConfigItem(
		keyName = "syncCombatAchievements",
		name = "Sync Combat Achievements",
		description = "Sync combat achievement tiers",
		section = dataSection,
		position = 3
	)
	default boolean syncCombatAchievements()
	{
		return true;
	}

	@ConfigItem(
		keyName = "syncEquipment",
		name = "Sync Equipment",
		description = "Sync currently equipped gear",
		section = dataSection,
		position = 4
	)
	default boolean syncEquipment()
	{
		return true;
	}

	@ConfigItem(
		keyName = "syncCollectionLog",
		name = "Sync Collection Log",
		description = "Sync collection log when opened",
		section = dataSection,
		position = 5
	)
	default boolean syncCollectionLog()
	{
		return true;
	}

	@ConfigSection(
		name = "Notifications",
		description = "Configure notifications",
		position = 2
	)
	String notificationSection = "notifications";

	@ConfigItem(
		keyName = "showSyncNotification",
		name = "Show Sync Notifications",
		description = "Show a chat message when data is synced",
		section = notificationSection,
		position = 0
	)
	default boolean showSyncNotification()
	{
		return false;
	}
}

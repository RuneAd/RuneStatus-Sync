package gg.runestatus.sync;

import com.google.inject.Provides;
import gg.runestatus.sync.data.CollectionLogItem;
import gg.runestatus.sync.data.PlayerSyncData;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.task.Schedule;

import javax.inject.Inject;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@PluginDescriptor(
	name = "RuneStatus Sync",
	description = "Sync your player data to RuneStatus.gg - We never store your IP address",
	tags = {"runestatus", "sync", "stats", "hiscores", "profile"}
)
public class RuneStatusPlugin extends Plugin
{
	private static final int COLLECTION_LOG_GROUP_ID = 621;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private RuneStatusConfig config;

	@Inject
	private RuneStatusClient runeStatusClient;

	@Inject
	private DataCollector dataCollector;

	@Inject
	private CollectionLogManager collectionLogManager;

	@Inject
	private EventBus eventBus;

	private BurgerMenuManager burgerMenuManager;

	private final AtomicLong lastSyncTime = new AtomicLong(0);
	private volatile boolean isSyncing = false;
	private boolean loggedIn = false;
	private boolean collectionLogOpen = false;

	@Override
	protected void startUp()
	{
		log.info("RuneStatus Sync started");

		// Create BurgerMenuManager for Collection Log hamburger menu button
		try
		{
			burgerMenuManager = new BurgerMenuManager(client, eventBus);
			burgerMenuManager.setOnSyncCallback(this::triggerManualSync);
			burgerMenuManager.startUp();
		}
		catch (Exception e)
		{
			log.error("Failed to create BurgerMenuManager: {}", e.getMessage(), e);
		}
	}

	@Override
	protected void shutDown()
	{
		log.info("RuneStatus Sync stopped");
		if (burgerMenuManager != null)
		{
			burgerMenuManager.shutDown();
		}
		loggedIn = false;
		collectionLogOpen = false;
		collectionLogManager.clearCache();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			if (!loggedIn)
			{
				loggedIn = true;
				// Delay initial sync to ensure all data is loaded
				clientThread.invokeLater(() -> {
					if (client.getGameState() == GameState.LOGGED_IN)
					{
						performSync();
					}
				});
			}
		}
		else if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			loggedIn = false;
			collectionLogManager.clearCache();
		}
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		if (!config.enableSync() || !loggedIn)
		{
			return;
		}

		// Sync on level up (XP change with level change)
		Skill skill = event.getSkill();
		int currentLevel = client.getRealSkillLevel(skill);
		int boostedLevel = event.getLevel();

		// Check if this was a level up by comparing real level
		if (currentLevel == boostedLevel && shouldSync())
		{
			log.debug("Level up detected in {}, triggering sync", skill.getName());
			performSync();
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (!loggedIn)
		{
			return;
		}

		// Collection log opened
		if (event.getGroupId() == COLLECTION_LOG_GROUP_ID)
		{
			collectionLogOpen = true;
			log.debug("Collection log opened");
		}
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		if (event.getGroupId() == COLLECTION_LOG_GROUP_ID && collectionLogOpen)
		{
			collectionLogOpen = false;
			log.debug("Collection log closed");

			// Sync when collection log is closed if we have data
			if (collectionLogManager.hasData() && shouldSync())
			{
				performSync();
			}
		}
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired event)
	{
		if (!config.enableSync() || !loggedIn || !config.syncCollectionLog() || !collectionLogOpen)
		{
			return;
		}

		// Script 4100 is fired when collection log category is changed/loaded
		if (event.getScriptId() == 4100)
		{
			collectionLogManager.processCollectionLogWidget();
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (!config.enableSync() || !loggedIn)
		{
			return;
		}

		// Check if we should sync based on interval
		if (shouldSyncByInterval())
		{
			performSync();
		}
	}

	@Schedule(
		period = 1,
		unit = ChronoUnit.MINUTES,
		asynchronous = true
	)
	public void periodicSyncCheck()
	{
		if (!config.enableSync() || !loggedIn || client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		if (shouldSyncByInterval())
		{
			clientThread.invokeLater(this::performSync);
		}
	}

	private boolean shouldSync()
	{
		long now = System.currentTimeMillis();
		long lastSync = lastSyncTime.get();
		// Minimum 30 seconds between syncs to prevent spam
		return (now - lastSync) > 30_000;
	}

	private boolean shouldSyncByInterval()
	{
		long now = System.currentTimeMillis();
		long lastSync = lastSyncTime.get();
		long intervalMs = config.syncInterval() * 60_000L;
		return (now - lastSync) >= intervalMs;
	}

	private void performSync()
	{
		performSyncInternal(false);
	}

	private void performManualSync()
	{
		performSyncInternal(true);
	}

	/**
	 * Public method for BurgerMenuManager to trigger a manual sync
	 */
	public void triggerManualSync()
	{
		performManualSync();
	}

	private void performSyncInternal(boolean isManual)
	{
		if (!config.enableSync() && !isManual)
		{
			return;
		}

		String username = dataCollector.getUsername();
		if (username == null || username.isEmpty())
		{
			log.debug("Cannot sync - no username available");
			return;
		}

		// Prevent concurrent syncs
		if (isSyncing && !isManual)
		{
			return;
		}

		isSyncing = true;
		PlayerSyncData data = buildSyncData();

		runeStatusClient.syncPlayerData(data).thenAccept(success -> {
			lastSyncTime.set(System.currentTimeMillis());
			isSyncing = false;

			if (success)
			{
				log.debug("Successfully synced data for {}", username);
				if (isManual || config.showSyncNotification())
				{
					clientThread.invokeLater(() ->
						client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
							"RuneStatus: Data synced successfully!", null)
					);
				}
			}
			else
			{
				log.warn("Failed to sync data for {}", username);
				if (isManual)
				{
					clientThread.invokeLater(() ->
						client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
							"RuneStatus: Sync failed!", null)
					);
				}
			}
		});
	}

	private PlayerSyncData buildSyncData()
	{
		PlayerSyncData.PlayerSyncDataBuilder builder = PlayerSyncData.builder()
			.username(dataCollector.getUsername())
			.accountType(dataCollector.getAccountType())
			.world(dataCollector.getWorld())
			.lastSyncedAt(System.currentTimeMillis());

		if (config.syncSkills())
		{
			builder.skills(dataCollector.collectSkills());
		}

		if (config.syncQuests())
		{
			builder.quests(dataCollector.collectQuests());
		}

		if (config.syncDiaries())
		{
			builder.achievementDiaries(dataCollector.collectAchievementDiaries());
		}

		if (config.syncCombatAchievements())
		{
			builder.combatAchievements(dataCollector.collectCombatAchievements());
		}

		if (config.syncEquipment())
		{
			builder.equipment(dataCollector.collectEquipment());
		}

		if (config.syncCollectionLog() && collectionLogManager.hasData())
		{
			Map<String, Map<String, CollectionLogItem>> collectionLog = collectionLogManager.getCollectionLogCache();
			builder.collectionLog(collectionLog);
		}

		return builder.build();
	}

	@Provides
	RuneStatusConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(RuneStatusConfig.class);
	}
}

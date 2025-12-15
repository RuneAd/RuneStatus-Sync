package gg.runestatus.sync;

import com.google.inject.Provides;
import gg.runestatus.sync.data.PlayerSyncData;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.task.Schedule;

import javax.inject.Inject;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@PluginDescriptor(
	name = "RuneStatus Sync",
	description = "Sync your player data to RuneStatus.gg. Your privacy always comes first - we never store your IP address.",
	tags = {"runestatus", "sync", "stats", "hiscores", "profile"}
)
public class RuneStatusPlugin extends Plugin
{
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

	private final AtomicLong lastSyncTime = new AtomicLong(0);
	private volatile boolean isSyncing = false;
	private boolean loggedIn = false;

	@Override
	protected void startUp()
	{
		log.info("RuneStatus Sync started");
		// Start CollectionLogManager to listen for chat messages (new collection log drops)
		collectionLogManager.startUp();
	}

	@Override
	protected void shutDown()
	{
		log.info("RuneStatus Sync stopped");
		collectionLogManager.shutDown();
		loggedIn = false;
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
		if (!config.enableSync())
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
		if (isSyncing)
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
				// Clear recent drops after successful sync
				collectionLogManager.clearRecentDrops();

				if (config.showSyncNotification())
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
			}
		});
	}

	private PlayerSyncData buildSyncData()
	{
		// Get summary counts
		int[] questCounts = dataCollector.getQuestCounts();
		int[] diaryCounts = dataCollector.getDiaryTaskCounts();
		int[] combatCounts = dataCollector.getCombatTaskCounts();

		PlayerSyncData.PlayerSyncDataBuilder builder = PlayerSyncData.builder()
			.username(dataCollector.getUsername())
			.accountType(dataCollector.getAccountType())
			.world(dataCollector.getWorld())
			.lastSyncedAt(System.currentTimeMillis())
			// Summary stats - always included
			.combatLevel(dataCollector.getCombatLevel())
			.totalLevel(dataCollector.getTotalLevel())
			.totalXp(dataCollector.getTotalXp())
			.questsCompleted(questCounts[0])
			.questsTotal(questCounts[1])
			.diaryTasksCompleted(diaryCounts[0])
			.diaryTasksTotal(diaryCounts[1])
			.combatTasksCompleted(combatCounts[0])
			.combatTasksTotal(combatCounts[1])
			.collectionLogObtained(dataCollector.getCollectionLogCount())
			.timePlayedMinutes(dataCollector.getTimePlayedMinutes());

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

		// Include recent drops detected from chat messages
		if (config.syncCollectionLog() && collectionLogManager.hasRecentDrops())
		{
			builder.recentDrops(collectionLogManager.getRecentDropNames());
			log.debug("Including {} recent drops from chat", collectionLogManager.getRecentDropNames().size());
		}

		return builder.build();
	}

	@Provides
	RuneStatusConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(RuneStatusConfig.class);
	}
}

package gg.runestatus.sync;

import gg.runestatus.sync.data.CollectionLogItem;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Singleton
public class CollectionLogManager
{
	private static final int COLLECTION_LOG_SCRIPT = 4100;
	private static final int COLLECTION_LOG_SEARCH_WIDGET = 40697932;

	private final Client client;
	private final EventBus eventBus;

	// Store items by item ID
	private final Map<String, CollectionLogItem> collectionLogItems = new HashMap<>();

	@Setter
	private boolean isManualSync = false;
	private boolean isRetrievingData = false;
	private int tickScriptFired = -1;
	private Runnable onSyncComplete;

	@Inject
	public CollectionLogManager(Client client, EventBus eventBus)
	{
		this.client = client;
		this.eventBus = eventBus;
	}

	public void startUp()
	{
		eventBus.register(this);
	}

	public void shutDown()
	{
		eventBus.unregister(this);
		reset();
	}

	public void reset()
	{
		isManualSync = false;
		isRetrievingData = false;
		tickScriptFired = -1;
	}

	public void setOnSyncComplete(Runnable callback)
	{
		this.onSyncComplete = callback;
	}

	public Map<String, CollectionLogItem> getCollectionLogItems()
	{
		return collectionLogItems;
	}

	public void clearItems()
	{
		collectionLogItems.clear();
	}

	public boolean hasData()
	{
		return !collectionLogItems.isEmpty();
	}

	/**
	 * Triggers the collection log to load all items via script 2240.
	 * This is called when the user clicks the RuneStatus button.
	 */
	public void triggerFullSync()
	{
		if (isRetrievingData)
		{
			log.debug("Already retrieving collection log data");
			return;
		}

		isManualSync = true;
		isRetrievingData = true;
		collectionLogItems.clear();

		// Trigger the search which loads all collection log items
		client.menuAction(-1, COLLECTION_LOG_SEARCH_WIDGET, MenuAction.CC_OP, 1, -1, "Search", null);
		client.runScript(2240);

		log.debug("Triggered collection log full sync via script 2240");
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState gameState = event.getGameState();
		if (gameState != GameState.HOPPING && gameState != GameState.LOGGED_IN)
		{
			reset();
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (tickScriptFired == -1)
		{
			return;
		}

		int tick = client.getTickCount();
		// Wait 2 ticks after last script fire to ensure all items are captured
		if (tickScriptFired + 2 < tick)
		{
			tickScriptFired = -1;

			if (isManualSync && onSyncComplete != null)
			{
				log.debug("Collection log sync complete with {} items", collectionLogItems.size());
				onSyncComplete.run();
				isManualSync = false;
			}

			isRetrievingData = false;
		}
	}

	@Subscribe
	public void onScriptPreFired(ScriptPreFired event)
	{
		if (event.getScriptId() != COLLECTION_LOG_SCRIPT)
		{
			return;
		}

		tickScriptFired = client.getTickCount();

		Object[] args = event.getScriptEvent().getArguments();
		if (args == null || args.length < 3)
		{
			return;
		}

		int itemId = (int) args[1];
		int quantity = (int) args[2];

		// quantity > 0 means obtained
		boolean obtained = quantity > 0;

		collectionLogItems.put(String.valueOf(itemId), CollectionLogItem.builder()
			.obtained(obtained)
			.count(obtained ? quantity : 0)
			.build());
	}
}

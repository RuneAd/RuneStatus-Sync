package gg.runestatus.sync;

import gg.runestatus.sync.data.CollectionLogCounts;
import gg.runestatus.sync.data.CollectionLogItem;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.widgets.Widget;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Singleton
public class CollectionLogManager
{
	private static final int COLLECTION_LOG_SCRIPT = 4100;
	private static final int COLLECTION_LOG_SEARCH_WIDGET = 40697932;
	private static final int COLLECTION_LOG_GROUP_ID = 621;
	private static final int COLLECTION_LOG_TITLE_CHILD_ID = 1;
	// Pattern to match "Unique: X/Y" format in collection log title
	private static final Pattern UNIQUE_COUNT_PATTERN = Pattern.compile("Unique:\\s*(\\d+)\\s*/\\s*(\\d+)");

	private final Client client;
	private final EventBus eventBus;

	// Store items by item ID
	private final Map<String, CollectionLogItem> collectionLogItems = new HashMap<>();

	// Store collection log counts from the UI
	@Getter
	private CollectionLogCounts collectionLogCounts;

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
		collectionLogCounts = null;
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

	public boolean hasCounts()
	{
		return collectionLogCounts != null;
	}

	/**
	 * Captures the collection log counts from the Collection Log header widget.
	 * The widget displays text like "Unique: 854/1692"
	 */
	public void captureCollectionLogCounts()
	{
		Widget titleWidget = client.getWidget(COLLECTION_LOG_GROUP_ID, COLLECTION_LOG_TITLE_CHILD_ID);
		if (titleWidget == null)
		{
			log.debug("Collection log title widget not found");
			return;
		}

		String text = titleWidget.getText();
		if (text == null || text.isEmpty())
		{
			log.debug("Collection log title text is empty");
			return;
		}

		log.debug("Collection log title text: {}", text);

		Matcher matcher = UNIQUE_COUNT_PATTERN.matcher(text);
		if (matcher.find())
		{
			int obtained = Integer.parseInt(matcher.group(1));
			int total = Integer.parseInt(matcher.group(2));

			collectionLogCounts = CollectionLogCounts.builder()
				.obtained(obtained)
				.total(total)
				.build();

			log.info("Captured collection log counts from UI: {}/{}", obtained, total);
		}
		else
		{
			log.warn("Could not parse collection log counts from text: {}", text);
		}
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

			// Capture collection log counts from the UI widget
			captureCollectionLogCounts();

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

package gg.runestatus.sync;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages collection log drop detection from chat messages.
 * Listens for "New item added to your collection log:" messages to track new drops.
 */
@Slf4j
@Singleton
public class CollectionLogManager
{
	private final Client client;
	private final EventBus eventBus;

	// Store recent collection log drops detected from chat messages
	@Getter
	private final List<String> recentDropNames = new ArrayList<>();

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
		recentDropNames.clear();
	}

	public boolean hasRecentDrops()
	{
		return !recentDropNames.isEmpty();
	}

	/**
	 * Clears the recent drops list after they've been synced.
	 */
	public void clearRecentDrops()
	{
		recentDropNames.clear();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		// Clear drops when logging out
		if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			recentDropNames.clear();
		}
	}

	/**
	 * Listens for chat messages to detect new collection log items.
	 * The game sends "New item added to your collection log: <item name>" when you get a new drop.
	 * This allows tracking collection log drops without needing to open the interface.
	 */
	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() != ChatMessageType.GAMEMESSAGE)
		{
			return;
		}

		String message = event.getMessage();

		// Check for collection log notification
		// Format: "New item added to your collection log: <item name>"
		if (message.contains("New item added to your collection log:"))
		{
			// Extract item name - the message may have color tags
			String cleanMessage = message.replaceAll("<[^>]+>", "");
			int prefixIndex = cleanMessage.indexOf("New item added to your collection log:");
			if (prefixIndex >= 0)
			{
				String itemName = cleanMessage.substring(prefixIndex + "New item added to your collection log:".length()).trim();
				if (!itemName.isEmpty())
				{
					recentDropNames.add(itemName);
					log.info("Detected new collection log item from chat: {}", itemName);
				}
			}
		}
	}
}

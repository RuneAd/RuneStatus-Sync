package gg.runestatus.sync;

import gg.runestatus.sync.data.CollectionLogItem;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ScriptID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.ComponentID;

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
	private static final int COLLECTION_LOG_GROUP_ID = 621;
	private static final Pattern COUNT_PATTERN = Pattern.compile("^(\\d+)$");
	private static final Pattern OBTAINED_PATTERN = Pattern.compile("<col=([a-fA-F0-9]+)>");

	private final Client client;

	// Cache of collection log data captured when user opens the interface
	private Map<String, Map<String, CollectionLogItem>> collectionLogCache = new HashMap<>();
	private String currentCategory = null;

	@Inject
	public CollectionLogManager(Client client)
	{
		this.client = client;
	}

	public boolean isCollectionLogOpen()
	{
		Widget collectionLogWidget = client.getWidget(COLLECTION_LOG_GROUP_ID, 0);
		return collectionLogWidget != null && !collectionLogWidget.isHidden();
	}

	public Map<String, Map<String, CollectionLogItem>> getCollectionLogCache()
	{
		return collectionLogCache;
	}

	public void clearCache()
	{
		collectionLogCache.clear();
	}

	public boolean hasData()
	{
		return !collectionLogCache.isEmpty();
	}

	public void processCollectionLogWidget()
	{
		if (!isCollectionLogOpen())
		{
			return;
		}

		try
		{
			// Get the category title
			Widget titleWidget = client.getWidget(COLLECTION_LOG_GROUP_ID, 19);
			if (titleWidget != null && titleWidget.getText() != null)
			{
				currentCategory = titleWidget.getText();
			}

			if (currentCategory == null)
			{
				return;
			}

			// Get the items container
			Widget itemsContainer = client.getWidget(COLLECTION_LOG_GROUP_ID, 35);
			if (itemsContainer == null)
			{
				return;
			}

			Widget[] children = itemsContainer.getDynamicChildren();
			if (children == null)
			{
				return;
			}

			Map<String, CollectionLogItem> categoryItems = collectionLogCache.computeIfAbsent(
				currentCategory, k -> new HashMap<>());

			for (Widget child : children)
			{
				if (child == null)
				{
					continue;
				}

				int itemId = child.getItemId();
				if (itemId == -1)
				{
					continue;
				}

				String itemName = client.getItemDefinition(itemId).getName();
				int quantity = child.getItemQuantity();
				int opacity = child.getOpacity();

				// Items with opacity 0 are obtained, items with higher opacity are not
				boolean obtained = opacity == 0;

				categoryItems.put(itemName, CollectionLogItem.builder()
					.obtained(obtained)
					.count(obtained ? Math.max(quantity, 1) : 0)
					.build());
			}

			log.debug("Processed collection log category: {} with {} items", currentCategory, categoryItems.size());
		}
		catch (Exception e)
		{
			log.error("Error processing collection log widget", e);
		}
	}
}

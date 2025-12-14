package gg.runestatus.sync;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Objects;

@Slf4j
@Singleton
public class BurgerMenuManager
{
	private static final int DRAW_BURGER_MENU = 7812;
	private static final int COLLECTION_LOG_GROUP_ID = 621;
	private static final int FONT_COLOR = 0xFF981F;
	private static final int FONT_COLOR_ACTIVE = 0xFFFFFF;
	private static final String BUTTON_TEXT = "RuneStatus";

	private final Client client;
	private final EventBus eventBus;

	private int baseMenuHeight = -1;
	private Runnable onSyncCallback;

	@Inject
	public BurgerMenuManager(Client client, EventBus eventBus)
	{
		this.client = client;
		this.eventBus = eventBus;
	}

	public void setOnSyncCallback(Runnable callback)
	{
		this.onSyncCallback = callback;
	}

	public void startUp()
	{
		eventBus.register(this);
		log.debug("BurgerMenuManager registered with EventBus");
	}

	public void shutDown()
	{
		eventBus.unregister(this);
	}

	@Subscribe
	public void onScriptPreFired(ScriptPreFired event)
	{
		if (event.getScriptId() != DRAW_BURGER_MENU)
		{
			return;
		}

		Object[] args = event.getScriptEvent().getArguments();
		if (args == null || args.length < 4)
		{
			return;
		}

		int menuId = (int) args[3];

		// Only add to collection log burger menu
		int groupId = menuId >> 16;
		if (groupId != COLLECTION_LOG_GROUP_ID)
		{
			return;
		}

		try
		{
			addButton(menuId);
		}
		catch (Exception e)
		{
			log.debug("Failed to add RuneStatus button to menu: {}", e.getMessage());
		}
	}

	private void addButton(int menuId)
	{
		Widget menu = Objects.requireNonNull(client.getWidget(menuId));
		Widget[] menuChildren = Objects.requireNonNull(menu.getChildren());

		if (baseMenuHeight == -1)
		{
			baseMenuHeight = menu.getOriginalHeight();
		}

		// Single pass to find last rectangle, last text, and check for existing button
		Widget lastRectangle = null;
		Widget lastText = null;
		boolean existingButton = false;

		for (int i = menuChildren.length - 1; i >= 0; i--)
		{
			Widget w = menuChildren[i];
			if (w == null)
			{
				continue;
			}

			if (lastRectangle == null && w.getType() == WidgetType.RECTANGLE)
			{
				lastRectangle = w;
			}
			if (lastText == null && w.getType() == WidgetType.TEXT)
			{
				lastText = w;
			}
			if (BUTTON_TEXT.equals(w.getText()))
			{
				existingButton = true;
			}
		}

		if (lastRectangle == null || lastText == null)
		{
			return;
		}

		final int buttonHeight = lastRectangle.getHeight();
		final int buttonY = lastRectangle.getOriginalY() + buttonHeight;

		if (!existingButton)
		{
			final Widget background = menu.createChild(WidgetType.RECTANGLE)
				.setOriginalWidth(lastRectangle.getOriginalWidth())
				.setOriginalHeight(lastRectangle.getOriginalHeight())
				.setOriginalX(lastRectangle.getOriginalX())
				.setOriginalY(buttonY)
				.setOpacity(lastRectangle.getOpacity())
				.setFilled(lastRectangle.isFilled());
			background.revalidate();

			final Widget text = menu.createChild(WidgetType.TEXT)
				.setText(BUTTON_TEXT)
				.setTextColor(FONT_COLOR)
				.setFontId(lastText.getFontId())
				.setTextShadowed(lastText.getTextShadowed())
				.setOriginalWidth(lastText.getOriginalWidth())
				.setOriginalHeight(lastText.getOriginalHeight())
				.setOriginalX(lastText.getOriginalX())
				.setOriginalY(buttonY)
				.setXTextAlignment(lastText.getXTextAlignment())
				.setYTextAlignment(lastText.getYTextAlignment());

			text.setHasListener(true);
			text.setOnMouseOverListener((JavaScriptCallback) ev -> text.setTextColor(FONT_COLOR_ACTIVE));
			text.setOnMouseLeaveListener((JavaScriptCallback) ev -> text.setTextColor(FONT_COLOR));
			text.setAction(0, "Sync to RuneStatus");
			text.setOnOpListener((JavaScriptCallback) ev -> onButtonClick());
			text.revalidate();
		}

		if (menu.getOriginalHeight() <= baseMenuHeight)
		{
			menu.setOriginalHeight(menu.getOriginalHeight() + buttonHeight);
		}

		menu.revalidate();
		for (Widget child : menuChildren)
		{
			child.revalidate();
		}
	}

	private void onButtonClick()
	{
		log.debug("RuneStatus sync button clicked");
		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
			"RuneStatus: Syncing data...", null);
		if (onSyncCallback != null)
		{
			onSyncCallback.run();
		}
	}
}

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
import java.util.*;

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
		log.info("BurgerMenuManager registered with EventBus");
	}

	public void shutDown()
	{
		eventBus.unregister(this);
		log.info("BurgerMenuManager unregistered from EventBus");
	}

	@Subscribe
	public void onScriptPreFired(ScriptPreFired event)
	{
		if (event.getScriptId() != DRAW_BURGER_MENU)
		{
			return;
		}

		log.info("DRAW_BURGER_MENU script fired!");

		Object[] args = event.getScriptEvent().getArguments();
		if (args == null || args.length < 4)
		{
			log.info("Invalid args");
			return;
		}

		int menuId = (int) args[3];
		log.info("Menu ID: {}, Group: {}", menuId, menuId >> 16);

		// Only add to collection log burger menu
		int groupId = menuId >> 16;
		if (groupId != COLLECTION_LOG_GROUP_ID)
		{
			log.debug("Not collection log menu, group is {}", groupId);
			return;
		}

		try
		{
			log.info("Adding RuneStatus button to menu with ID: {}", menuId);
			addButton(menuId);
		}
		catch (Exception e)
		{
			log.warn("Failed to add RuneStatus button to menu: {}", e.getMessage());
		}
	}

	private void addButton(int menuId) throws NullPointerException, NoSuchElementException
	{
		Widget menu = Objects.requireNonNull(client.getWidget(menuId));
		Widget[] menuChildren = Objects.requireNonNull(menu.getChildren());

		if (baseMenuHeight == -1)
		{
			baseMenuHeight = menu.getOriginalHeight();
		}

		List<Widget> reversedMenuChildren = new ArrayList<>(Arrays.asList(menuChildren));
		Collections.reverse(reversedMenuChildren);

		Widget lastRectangle = reversedMenuChildren.stream()
			.filter(w -> w.getType() == WidgetType.RECTANGLE)
			.findFirst()
			.orElseThrow(() -> new NoSuchElementException("No RECTANGLE widget found in menu"));

		Widget lastText = reversedMenuChildren.stream()
			.filter(w -> w.getType() == WidgetType.TEXT)
			.findFirst()
			.orElseThrow(() -> new NoSuchElementException("No TEXT widget found in menu"));

		final int buttonHeight = lastRectangle.getHeight();
		final int buttonY = lastRectangle.getOriginalY() + buttonHeight;

		// Check if button already exists
		final boolean existingButton = Arrays.stream(menuChildren)
			.anyMatch(w -> w.getText() != null && w.getText().equals(BUTTON_TEXT));

		if (!existingButton)
		{
			log.info("Creating new button at Y={}", buttonY);

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

			log.info("Button created successfully!");
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
		log.info("RuneStatus sync button clicked!");
		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
			"RuneStatus: Syncing data...", null);
		if (onSyncCallback != null)
		{
			onSyncCallback.run();
		}
	}
}

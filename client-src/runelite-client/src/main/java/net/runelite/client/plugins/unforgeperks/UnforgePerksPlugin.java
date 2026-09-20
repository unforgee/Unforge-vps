package net.runelite.client.plugins.unforgeperks;

import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

/**
 * Mouse wheel + button scrolling for the Perks journal tab (interface 1002).
 *
 * The 239 client does not wheel-scroll server-authored scrollable layers on its own: vanilla
 * scrollable areas get their `onScrollWheel` hook installed by cs2 at runtime, and the perks
 * page has no such script. This plugin attaches the equivalent listeners itself: every widget in
 * the interface shares one wheel callback that scrolls the `content` layer, and the up/down
 * buttons are handled locally so the scroll position never desyncs between client and server.
 *
 * Button clicks are consumed before they reach the server. When this plugin is disabled the
 * server-side `IfSetScrollPos` handlers in PerkJournalScript still work as a fallback.
 */
@PluginDescriptor(
	name = "Unforge Perks",
	description = "Mouse wheel scrolling for the Perks journal tab",
	tags = {"unforge", "perks", "journal"},
	enabledByDefault = true
)
public class UnforgePerksPlugin extends Plugin
{
	private static final int GROUP_ID = 1002;
	private static final int MAX_CHILDREN = 1024;

	/** One perk row per wheel notch. */
	private static final int WHEEL_STEP = 42;

	/** Two perk rows per button click; matches SCROLL_STEP in UnforgePerksInterfaceBuilder. */
	private static final int BUTTON_STEP = 84;

	private static final String SCROLL_OP = "Scroll";

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	/** The scrollable content layer (scrollHeight > height). */
	private Widget content;

	/** The root widget instance the hooks were installed on; reloads create a new one. */
	private Widget hookedRoot;

	private int scrollUpId = -1;
	private int scrollDownId = -1;

	@Override
	protected void startUp()
	{
		clientThread.invokeLater(this::refreshHooks);
	}

	@Override
	protected void shutDown()
	{
		content = null;
		hookedRoot = null;
		scrollUpId = scrollDownId = -1;
	}

	@Subscribe
	public void onClientTick(ClientTick tick)
	{
		refreshHooks();
	}

	private void refreshHooks()
	{
		Widget root = client.getWidget(GROUP_ID, 0);
		if (root == hookedRoot)
		{
			return;
		}

		hookedRoot = root;
		content = null;
		scrollUpId = scrollDownId = -1;
		if (root == null)
		{
			return;
		}

		JavaScriptCallback wheel = (JavaScriptCallback) event -> scrollBy(event.getMouseY() * WHEEL_STEP);

		// Install the wheel listener on every component of the interface. The wheel event is
		// delivered to the hovered widget and propagates to ancestors only while
		// `noScrollThrough` is unset, so covering the whole group keeps behaviour identical no
		// matter which part of the page the cursor is over. Each hooked widget gets
		// noScrollThrough so the event is consumed exactly once.
		for (int i = 0; i < MAX_CHILDREN; i++)
		{
			Widget child = client.getWidget(GROUP_ID, i);
			if (child == null)
			{
				continue;
			}

			if (child.getScrollHeight() > child.getHeight())
			{
				content = child;
			}

			String[] actions = child.getActions();
			if (actions != null)
			{
				for (String action : actions)
				{
					if (SCROLL_OP.equals(action))
					{
						if (scrollUpId == -1)
						{
							scrollUpId = child.getId();
						}
						else
						{
							scrollDownId = child.getId();
						}
						break;
					}
				}
			}

			child.setHasListener(true);
			child.setNoScrollThrough(true);
			child.setOnScrollWheelListener(wheel);
		}
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		Widget widget = event.getWidget();
		if (widget == null || (widget.getId() >>> 16) != GROUP_ID || !SCROLL_OP.equals(event.getMenuOption()))
		{
			return;
		}

		// Consume before the vanilla op handler runs so no IfButton packet reaches the server;
		// the scroll position stays client-authoritative and consistent with wheel scrolling.
		event.consume();
		int id = widget.getId();
		if (id == scrollUpId)
		{
			scrollBy(-BUTTON_STEP);
		}
		else if (id == scrollDownId)
		{
			scrollBy(BUTTON_STEP);
		}
		else
		{
			// The click may resolve to a child or wrapper widget instead of the op owner:
			// fall back to comparing its on-screen position against the page middle.
			scrollBy(widget.getCanvasLocation().getY() < midY() ? -BUTTON_STEP : BUTTON_STEP);
		}
	}

	private int midY()
	{
		Widget root = hookedRoot;
		return root == null ? 0 : root.getCanvasLocation().getY() + root.getHeight() / 2;
	}

	private void scrollBy(int delta)
	{
		Widget c = content;
		if (c == null)
		{
			return;
		}
		int max = Math.max(0, c.getScrollHeight() - c.getHeight());
		int next = c.getScrollY() + delta;
		if (next < 0)
		{
			next = 0;
		}
		else if (next > max)
		{
			next = max;
		}
		c.setScrollY(next);
	}
}

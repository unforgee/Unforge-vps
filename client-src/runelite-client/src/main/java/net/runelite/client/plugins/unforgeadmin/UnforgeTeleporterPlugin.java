package net.runelite.client.plugins.unforgeadmin;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.ScriptID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.Keybind;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.input.KeyManager;
import net.runelite.client.util.HotkeyListener;

@PluginDescriptor(
	name = "Unforge Teleporter",
	description = "Teleport to common cities, skilling areas, bosses, monsters and minigames",
	tags = {"unforge", "teleport", "in-game", "ctrl-t", "ctrl-h"},
	enabledByDefault = true
)
public class UnforgeTeleporterPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private KeyManager keyManager;

	private final HotkeyListener hotkeyListener = new HotkeyListener(
		() -> new Keybind(KeyEvent.VK_T, InputEvent.CTRL_DOWN_MASK))
	{
		@Override
		public void hotkeyPressed()
		{
			// The visible UI is the server-owned in-game modal, not a RuneLite plugin panel.
			clientThread.invokeLater(() -> client.runScript(ScriptID.UNFORGE_CHEAT, "teleui"));
		}
	};

	private final HotkeyListener edgevilleHotkeyListener = new HotkeyListener(
		() -> new Keybind(KeyEvent.VK_H, InputEvent.CTRL_DOWN_MASK))
	{
		@Override
		public void hotkeyPressed()
		{
			clientThread.invokeLater(() -> client.runScript(ScriptID.UNFORGE_CHEAT, "edge"));
		}
	};

	@Override
	protected void startUp()
	{
		keyManager.registerKeyListener(hotkeyListener);
		keyManager.registerKeyListener(edgevilleHotkeyListener);
	}

	@Override
	protected void shutDown()
	{
		keyManager.unregisterKeyListener(hotkeyListener);
		keyManager.unregisterKeyListener(edgevilleHotkeyListener);
	}
}

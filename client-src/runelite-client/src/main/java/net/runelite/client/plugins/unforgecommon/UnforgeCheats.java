package net.runelite.client.plugins.unforgecommon;

import net.runelite.api.Client;
import net.runelite.api.ScriptID;
import net.runelite.client.callback.ClientThread;

public final class UnforgeCheats
{
	private UnforgeCheats()
	{
	}

	/** Sends a server cheat command (e.g. "npcadd 100 4151") through the docheat script path. */
	public static void send(ClientThread clientThread, Client client, String command)
	{
		clientThread.invokeLater(() -> client.runScript(ScriptID.UNFORGE_CHEAT, command));
	}
}

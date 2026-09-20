package net.runelite.client.plugins.unforgestudio;

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.callback.ClientThread;

/**
 * Shows the local player's live state.
 *
 * <p>Read straight from the client rather than over the bridge, so it works even when
 * the studio is not running - useful for confirming what the server and the dock both
 * think is true. The game state is read on the client thread and the labels are then
 * updated on the EDT.</p>
 */
class LivePlayerDockPanel extends DockTool
{
	private static final int REFRESH_MS = 1000;

	private static final String[] ROWS = {
		"Name", "World", "Combat level", "Position", "Region",
		"Health", "Run energy", "Special attack", "Animation", "Interacting", "Game state",
	};

	private final Map<String, JLabel> labels = new LinkedHashMap<>();
	private final Timer timer;

	LivePlayerDockPanel(Client client, ClientThread clientThread, SymbolIndex symbols,
		StudioApiClient api, UnforgeStudioDock dock)
	{
		super(client, clientThread, symbols, api, dock);

		buildUi();
		timer = new Timer(REFRESH_MS, e -> refresh());
		timer.setInitialDelay(0);
		timer.start();
	}

	private void buildUi()
	{
		JPanel grid = new JPanel(new GridLayout(0, 2, 8, 2));
		grid.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

		for (String row : ROWS)
		{
			JLabel value = new JLabel("-");
			value.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
			labels.put(row, value);
			grid.add(new JLabel(row));
			grid.add(value);
		}

		add(grid, BorderLayout.NORTH);
	}

	private void refresh()
	{
		clientThread.invokeLater(() ->
		{
			Map<String, String> snapshot = capture();
			SwingUtilities.invokeLater(() -> apply(snapshot));
		});
	}

	/** Reads the game state. Must run on the client thread. */
	private Map<String, String> capture()
	{
		Map<String, String> out = new LinkedHashMap<>();

		GameState state = client.getGameState();
		out.put("Game state", state == null ? "-" : state.name());

		if (client.getLocalPlayer() == null)
		{
			return out;
		}

		out.put("Name", String.valueOf(client.getLocalPlayer().getName()));
		out.put("World", String.valueOf(client.getWorld()));
		out.put("Combat level", String.valueOf(client.getLocalPlayer().getCombatLevel()));

		if (client.getLocalPlayer().getWorldLocation() != null)
		{
			int x = client.getLocalPlayer().getWorldLocation().getX();
			int y = client.getLocalPlayer().getWorldLocation().getY();
			int z = client.getLocalPlayer().getWorldLocation().getPlane();
			out.put("Position", x + ", " + y + ", plane " + z);
			out.put("Region", String.valueOf(((x >> 6) << 8) | (y >> 6)));
		}

		out.put("Health", client.getBoostedSkillLevel(Skill.HITPOINTS) + " / " + client.getRealSkillLevel(Skill.HITPOINTS));
		out.put("Run energy", String.valueOf(client.getEnergy()));

		// Special attack is stored as 0..1000; show whole percent.
		int special = client.getVarpValue(VarPlayerID.SA_ENERGY);
		out.put("Special attack", Math.max(0, Math.min(100, special / 10)) + "%");
		out.put("Animation", String.valueOf(client.getLocalPlayer().getAnimation()));

		if (client.getLocalPlayer().getInteracting() != null)
		{
			String name = client.getLocalPlayer().getInteracting().getName();
			out.put("Interacting", name == null ? "(unnamed)" : name);
		}

		return out;
	}

	/** Applies one snapshot. Runs on the EDT. */
	private void apply(Map<String, String> snapshot)
	{
		for (Map.Entry<String, JLabel> entry : labels.entrySet())
		{
			String value = snapshot.get(entry.getKey());
			entry.getValue().setText(value == null ? "-" : value);
		}
	}

	/** Stops polling; called when the plugin shuts down. */
	void stop()
	{
		timer.stop();
	}
}

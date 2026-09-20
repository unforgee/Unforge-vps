package net.runelite.client.plugins.unforgeadmin;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.Arrays;
import java.util.List;
import javax.inject.Inject;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.ScriptID;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

final class UnforgeNpcAreaPanel extends PluginPanel
{
	private final Client client;
	private final ClientThread clientThread;

	private final JComboBox<String> presetNpcBox;
	private final JTextField npcField = new JTextField("goblin");
	private final JTextField durationField = new JTextField("6000"); // 1 hour default
	private final JTextField xField = new JTextField();
	private final JTextField yField = new JTextField();
	private final JTextField planeField = new JTextField("0");
	private final JLabel status = new JLabel("Valitse NPC tai kirjoita nimi/ID.");

	private static final List<String> COMMON_NPCS = Arrays.asList(
		"goblin", "man", "woman", "cow", "chicken",
		"guard", "abyssal_demon", "dark_beast", "bloodveld",
		"gargoyle", "nechryael", "general_graardor", "corporeal_beast",
		"cerberus", "zulrah", "vorkath", "banker", "shopkeeper"
	);

	@Inject
	UnforgeNpcAreaPanel(Client client, ClientThread clientThread)
	{
		this.client = client;
		this.clientThread = clientThread;

		setLayout(new BorderLayout(0, 8));
		setBorder(new EmptyBorder(10, 10, 10, 10));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		// Header
		JPanel header = new JPanel();
		header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
		header.setOpaque(false);

		JPanel titleCard = new JPanel(new BorderLayout());
		titleCard.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		titleCard.setBorder(new CompoundBorder(
			new LineBorder(new Color(255, 152, 0), 1),
			new EmptyBorder(8, 8, 8, 8)
		));

		JLabel title = new JLabel("👾 NPC SPAWNER & AREA BUILDER");
		title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
		title.setForeground(new Color(255, 183, 77));
		titleCard.add(title, BorderLayout.NORTH);

		JLabel subtitle = new JLabel("Spawn any NPC directly to your coordinates");
		subtitle.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
		subtitle.setForeground(Color.LIGHT_GRAY);
		titleCard.add(subtitle, BorderLayout.SOUTH);

		header.add(titleCard);
		header.add(buildSpacer(6));

		// Preset Quick Select
		JPanel presetRow = new JPanel(new BorderLayout(5, 0));
		presetRow.setOpaque(false);
		JLabel preLbl = new JLabel("Presets:");
		preLbl.setForeground(Color.YELLOW);
		preLbl.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
		presetRow.add(preLbl, BorderLayout.WEST);

		presetNpcBox = new JComboBox<>(COMMON_NPCS.toArray(new String[0]));
		presetNpcBox.addActionListener(e -> {
			String sel = (String) presetNpcBox.getSelectedItem();
			if (sel != null)
			{
				npcField.setText(sel);
			}
		});
		presetRow.add(presetNpcBox, BorderLayout.CENTER);
		header.add(presetRow);

		add(header, BorderLayout.NORTH);

		// Form body
		JPanel form = new JPanel(new GridLayout(0, 2, 6, 6));
		form.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		form.setBorder(new CompoundBorder(
			new LineBorder(ColorScheme.BORDER_COLOR, 1),
			new EmptyBorder(8, 8, 8, 8)
		));

		form.add(new JLabel("NPC ID / Name:"));
		form.add(npcField);

		form.add(new JLabel("Duration (ticks):"));
		form.add(durationField);

		form.add(new JLabel("Coord X:"));
		form.add(xField);

		form.add(new JLabel("Coord Y:"));
		form.add(yField);

		form.add(new JLabel("Plane / Level:"));
		form.add(planeField);

		add(form, BorderLayout.CENTER);

		// Action footer
		JPanel footer = new JPanel();
		footer.setLayout(new BoxLayout(footer, BoxLayout.Y_AXIS));
		footer.setOpaque(false);

		JButton currentLocation = new JButton("📍 Lue nykyinen sijainti");
		currentLocation.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
		currentLocation.addActionListener(event -> loadCurrentLocation());
		footer.add(currentLocation);
		footer.add(buildSpacer(4));

		JButton spawn = new JButton("⚡ SPAWN NPC NYT");
		spawn.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
		spawn.setBackground(new Color(40, 167, 69));
		spawn.setForeground(Color.WHITE);
		spawn.setCursor(new Cursor(Cursor.HAND_CURSOR));
		spawn.addActionListener(event -> spawnNpc());
		footer.add(spawn);
		footer.add(buildSpacer(6));

		status.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 10));
		status.setForeground(new Color(0, 255, 204));
		footer.add(status);

		add(footer, BorderLayout.SOUTH);

		loadCurrentLocation();
	}

	private Component buildSpacer(int height)
	{
		JPanel p = new JPanel();
		p.setPreferredSize(new Dimension(1, height));
		p.setOpaque(false);
		return p;
	}

	private void loadCurrentLocation()
	{
		clientThread.invokeLater(() ->
		{
			Player player = client.getLocalPlayer();
			WorldPoint location = player == null ? null : player.getWorldLocation();
			SwingUtilities.invokeLater(() ->
			{
				if (location == null)
				{
					status.setText("Pelaajaa ei ole vielä kirjautuneena.");
					return;
				}
				xField.setText(Integer.toString(location.getX()));
				yField.setText(Integer.toString(location.getY()));
				planeField.setText(Integer.toString(location.getPlane()));
				status.setText(String.format("Koordinaatit ladattu: %d, %d, %d", location.getX(), location.getY(), location.getPlane()));
			});
		});
	}

	private void spawnNpc()
	{
		String npc = npcField.getText().trim().replace(' ', '_');
		try
		{
			int duration = Integer.parseInt(durationField.getText().trim());
			int x = Integer.parseInt(xField.getText().trim());
			int y = Integer.parseInt(yField.getText().trim());
			int plane = Integer.parseInt(planeField.getText().trim());
			if (npc.isEmpty() || duration <= 0 || x < 0 || y < 0 || plane < 0 || plane > 3)
			{
				status.setText("Tarkista NPC, kesto (ticks), X/Y ja plane.");
				return;
			}
			int mapX = x >> 6;
			int mapZ = y >> 6;
			int localX = x & 63;
			int localZ = y & 63;

			// Format: ::npcaddat duration name plane mapX mapZ localX localZ
			String command = String.format("npcaddat %d %s %d %d %d %d %d", duration, npc, plane, mapX, mapZ, localX, localZ);
			clientThread.invokeLater(() -> client.runScript(ScriptID.UNFORGE_CHEAT, command));
			status.setText("⚡ Lähetetty spawn: " + npc + " (" + x + ", " + y + ")");
		}
		catch (NumberFormatException exception)
		{
			status.setText("Virhe: Koordinaattien ja keston pitää olla numeroita.");
		}
	}
}

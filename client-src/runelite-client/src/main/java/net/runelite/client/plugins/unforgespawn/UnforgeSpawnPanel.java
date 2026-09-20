package net.runelite.client.plugins.unforgespawn;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.unforgecommon.AccordionColumn;
import net.runelite.client.plugins.unforgecommon.EntityPickerPanel;
import net.runelite.client.plugins.unforgecommon.PopoutWindow;
import net.runelite.client.plugins.unforgecommon.TableCopy;
import net.runelite.client.plugins.unforgecommon.UnforgeCheats;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

@Slf4j
public class UnforgeSpawnPanel extends PluginPanel
{
	private static final File KRONOS_DATA_ROOT = new File("C:/Users/HOST/Desktop/UNFORGE-239-STANDALONE-LAB/content/kronos-data");

	private final UnforgeSpawnPlugin plugin;
	private final Client client;
	private final ClientThread clientThread;
	private final PopoutWindow popout = new PopoutWindow();
	private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

	private int activeId = -1;
	private String activeName = "None";
	private boolean activeIsNpc = true;
	private int activeShape = -1;
	private int activeAngle = -1;
	private final JLabel entityLabel = new JLabel("No entity selected - search below");

	private final JTextField xField = new JTextField("3200");
	private final JTextField yField = new JTextField("3200");
	private final JComboBox<String> planeBox = new JComboBox<>(new String[]{"0", "1", "2", "3"});
	private final JTextField rangeField = new JTextField("3");
	private final JComboBox<String> dirBox = new JComboBox<>(new String[]{"S", "N", "E", "W"});
	private final JTextField durationField = new JTextField("perm");
	private final JLabel statusLabel = new JLabel(" ");

	private final DefaultTableModel spawnModel = new DefaultTableModel(new String[]{"Name", "ID", "X", "Y", "Z", "Dir", "Range"}, 0)
	{
		@Override
		public boolean isCellEditable(int row, int col)
		{
			return col != 0;
		}
	};
	private final JTable spawnTable = new JTable(spawnModel);
	private JButton popOutBtn;

	public UnforgeSpawnPanel(UnforgeSpawnPlugin plugin, Client client, ClientThread clientThread)
	{
		super(false);
		this.plugin = plugin;
		this.client = client;
		this.clientThread = clientThread;

		setLayout(new BorderLayout(0, 6));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setBorder(new EmptyBorder(10, 10, 10, 10));

		add(buildHeader(), BorderLayout.NORTH);
		add(buildEditor(), BorderLayout.CENTER);
		add(buildFooter(), BorderLayout.SOUTH);
	}

	private JPanel buildHeader()
	{
		JPanel header = new JPanel();
		header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
		header.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		header.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(new Color(0, 123, 255), 1),
			new EmptyBorder(8, 8, 8, 8)));

		JPanel titleRow = new JPanel(new BorderLayout());
		titleRow.setOpaque(false);
		JLabel title = new JLabel("⌖ UNFORGE SPAWN EDITOR");
		title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
		title.setForeground(new Color(90, 170, 255));
		titleRow.add(title, BorderLayout.WEST);
		popOutBtn = new JButton("⤢");
		popOutBtn.setToolTipText("Pop out into a resizable window");
		popOutBtn.setMargin(new java.awt.Insets(1, 6, 1, 6));
		popOutBtn.addActionListener(e -> togglePopOut());
		titleRow.add(popOutBtn, BorderLayout.EAST);
		header.add(titleRow);

		entityLabel.setForeground(Color.WHITE);
		entityLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
		entityLabel.setBorder(new EmptyBorder(4, 0, 2, 0));
		header.add(entityLabel);

		return header;
	}

	private JPanel buildEditor()
	{
		AccordionColumn editor = new AccordionColumn();
		Color accent = new Color(90, 170, 255);

		editor.addSection("Entity Search",
			new EntityPickerPanel(client, clientThread, false, false, r ->
			{
				activeId = r.id;
				activeName = r.name;
				activeIsNpc = r.npc;
				activeShape = -1;
				activeAngle = -1;
				entityLabel.setText((r.npc ? "NPC: " : "Object: ") + r.name + " (ID: " + r.id + ")");
			}), true, accent, true);

		JPanel coords = new JPanel(new GridLayout(0, 2, 4, 2));
		coords.setOpaque(false);
		coords.setBorder(BorderFactory.createTitledBorder(
			BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR), "Placement"));
		coords.add(new JLabel("X:"));
		coords.add(xField);
		coords.add(new JLabel("Y:"));
		coords.add(yField);
		coords.add(new JLabel("Plane:"));
		coords.add(planeBox);
		coords.add(new JLabel("Walk range:"));
		coords.add(rangeField);
		coords.add(new JLabel("Direction:"));
		coords.add(dirBox);
		coords.add(new JLabel("Duration (cycles or 'perm'):"));
		coords.add(durationField);

		JPanel posRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
		posRow.setOpaque(false);
		JButton myPos = new JButton("Use my position");
		myPos.addActionListener(e -> fillPlayerPosition());
		posRow.add(myPos);
		JButton addRow = new JButton("+ Add to list");
		addRow.addActionListener(e -> addSpawnRow());
		posRow.add(addRow);

		JPanel spawnRow = new JPanel(new GridLayout(1, 2, 4, 0));
		spawnRow.setOpaque(false);
		JButton atMe = new JButton("⚡ Spawn at me");
		atMe.setBackground(new Color(0, 123, 255));
		atMe.setForeground(Color.WHITE);
		atMe.addActionListener(e -> spawnAtMe());
		spawnRow.add(atMe);
		JButton atCoords = new JButton("⚡ Spawn at coords");
		atCoords.setBackground(new Color(90, 60, 200));
		atCoords.setForeground(Color.WHITE);
		atCoords.addActionListener(e -> spawnAtCoords());
		spawnRow.add(atCoords);

		statusLabel.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 10));
		statusLabel.setForeground(new Color(0, 255, 204));

		JPanel placement = new JPanel();
		placement.setLayout(new BoxLayout(placement, BoxLayout.Y_AXIS));
		placement.setOpaque(false);
		placement.add(coords);
		placement.add(posRow);
		placement.add(spawnRow);
		placement.add(statusLabel);
		editor.addSection("Placement", placement, true, accent, false);

		spawnTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		spawnTable.setFillsViewportHeight(true);
		spawnTable.getColumnModel().getColumn(1).setMaxWidth(60);
		spawnTable.getColumnModel().getColumn(4).setMaxWidth(30);
		spawnTable.getColumnModel().getColumn(5).setMaxWidth(40);
		spawnTable.getColumnModel().getColumn(6).setMaxWidth(50);
		TableCopy.install(spawnTable);
		JScrollPane tableScroll = new JScrollPane(spawnTable);
		tableScroll.setBorder(BorderFactory.createTitledBorder(
			BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR), "Spawn list (Ctrl+C copies rows)"));
		editor.addSection("Spawn List", tableScroll, true, accent, true);

		return editor;
	}

	private JPanel buildFooter()
	{
		JPanel footer = new JPanel(new GridLayout(1, 3, 5, 5));
		footer.setOpaque(false);

		JButton del = new JButton("Remove sel");
		del.addActionListener(e -> removeSelected());
		footer.add(del);

		JButton copyJson = new JButton("Copy JSON rows");
		copyJson.addActionListener(e -> copyToClipboard(generateSpawnsJson(false)));
		footer.add(copyJson);

		JButton save = new JButton("Save JSON");
		save.setBackground(new Color(0, 123, 255));
		save.setForeground(Color.WHITE);
		save.addActionListener(e -> saveSpawns());
		footer.add(save);

		return footer;
	}

	private void togglePopOut()
	{
		popout.toggle(this, "Unforge Spawn Editor", 540, 780, plugin::reopenPanel);
		popOutBtn.setText(popout.isOpen() ? "⤡" : "⤢");
	}

	void closePopout()
	{
		popout.dock(this, null);
	}

	void loadEntity(int id, String name, boolean npc, WorldPoint loc, int shape, int angle)
	{
		activeId = id;
		activeName = name;
		activeIsNpc = npc;
		activeShape = shape;
		activeAngle = angle;
		entityLabel.setText((npc ? "NPC: " : "Object: ") + name + " (ID: " + id + ")");
		if (loc != null)
		{
			xField.setText(String.valueOf(loc.getX()));
			yField.setText(String.valueOf(loc.getY()));
			planeBox.setSelectedItem(String.valueOf(loc.getPlane()));
		}
		statusLabel.setText("Copied " + name + " from world - set position and spawn.");
	}

	private void fillPlayerPosition()
	{
		clientThread.invokeLater(() ->
		{
			WorldPoint p = client.getLocalPlayer() != null ? client.getLocalPlayer().getWorldLocation() : null;
			SwingUtilities.invokeLater(() ->
			{
				if (p != null)
				{
					xField.setText(String.valueOf(p.getX()));
					yField.setText(String.valueOf(p.getY()));
					planeBox.setSelectedItem(String.valueOf(p.getPlane()));
					statusLabel.setText("Position: " + p.getX() + ", " + p.getY() + ", " + p.getPlane());
				}
			});
		});
	}

	private void spawnAtMe()
	{
		if (activeId < 0)
		{
			statusLabel.setText("Select an NPC/object first.");
			return;
		}
		int duration = parseDuration();
		String cmd = activeIsNpc
			? "npcadd " + duration + " " + activeId
			: String.format("locadd %d %d %d %d", duration, activeId,
				activeAngle >= 0 ? activeAngle : 0, activeShape >= 0 ? activeShape : 10);
		UnforgeCheats.send(clientThread, client, cmd);
		statusLabel.setText("⚡ Sent ::" + cmd + (duration == Integer.MAX_VALUE ? " (persists)" : ""));
	}

	private void spawnAtCoords()
	{
		if (activeId < 0)
		{
			statusLabel.setText("Select an entity first.");
			return;
		}
		int duration = parseDuration();
		int x = parseInt(xField.getText(), -1);
		int y = parseInt(yField.getText(), -1);
		int plane = Integer.parseInt((String) planeBox.getSelectedItem());
		if (x < 0 || y < 0)
		{
			statusLabel.setText("Invalid coords.");
			return;
		}
		// npcaddat / locaddat duration id plane mapX mapZ localX localZ [angle] [shape]
		String cmd = activeIsNpc
			? String.format("npcaddat %d %d %d %d %d %d %d",
				duration, activeId, plane, x >> 6, y >> 6, x & 63, y & 63)
			: String.format("locaddat %d %d %d %d %d %d %d %d %d",
				duration, activeId, plane, x >> 6, y >> 6, x & 63, y & 63,
				activeAngle >= 0 ? activeAngle : 0, activeShape >= 0 ? activeShape : 10);
		UnforgeCheats.send(clientThread, client, cmd);
		statusLabel.setText("⚡ Sent ::" + cmd + (duration == Integer.MAX_VALUE ? " (persists)" : ""));
	}

	private int parseDuration()
	{
		String raw = durationField.getText().trim().toLowerCase();
		if (raw.isEmpty() || raw.equals("perm") || raw.equals("p") || raw.equals("forever"))
		{
			return Integer.MAX_VALUE;
		}
		return parseInt(raw, 6000);
	}

	private void addSpawnRow()
	{
		if (activeId < 0)
		{
			statusLabel.setText("Select an entity first.");
			return;
		}
		spawnModel.addRow(new Object[]{
			activeName, activeId,
			parseInt(xField.getText(), 0), parseInt(yField.getText(), 0),
			planeBox.getSelectedItem(), dirBox.getSelectedItem(), parseInt(rangeField.getText(), 3)});
		statusLabel.setText("Added " + activeName + " to spawn list.");
	}

	private void removeSelected()
	{
		int[] rows = spawnTable.getSelectedRows();
		for (int i = rows.length - 1; i >= 0; i--)
		{
			spawnModel.removeRow(spawnTable.convertRowIndexToModel(rows[i]));
		}
	}

	private String generateSpawnsJson(boolean pretty)
	{
		JsonArray arr = new JsonArray();
		for (int i = 0; i < spawnModel.getRowCount(); i++)
		{
			JsonObject o = new JsonObject();
			o.addProperty("name", String.valueOf(spawnModel.getValueAt(i, 0)));
			o.addProperty("id", parseInt(String.valueOf(spawnModel.getValueAt(i, 1)), 0));
			o.addProperty("x", parseInt(String.valueOf(spawnModel.getValueAt(i, 2)), 0));
			o.addProperty("y", parseInt(String.valueOf(spawnModel.getValueAt(i, 3)), 0));
			o.addProperty("z", parseInt(String.valueOf(spawnModel.getValueAt(i, 4)), 0));
			o.addProperty("direction", String.valueOf(spawnModel.getValueAt(i, 5)));
			o.addProperty("walkRange", parseInt(String.valueOf(spawnModel.getValueAt(i, 6)), 3));
			arr.add(o);
		}
		return gson.toJson(arr);
	}

	private void saveSpawns()
	{
		if (spawnModel.getRowCount() == 0)
		{
			statusLabel.setText("Spawn list is empty.");
			return;
		}
		String json = generateSpawnsJson(true);
		// Append entries into a JSON array-safe way: write a standalone custom file
		File target = new File(KRONOS_DATA_ROOT, "npcs/spawns/custom_spawns_editor.json");
		try
		{
			target.getParentFile().mkdirs();
			Files.write(target.toPath(), (json + "\n").getBytes(StandardCharsets.UTF_8),
				StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
			JOptionPane.showMessageDialog(this, "Saved spawns to:\n" + target.getAbsolutePath(), "Spawns Saved", JOptionPane.INFORMATION_MESSAGE);
		}
		catch (IOException ex)
		{
			copyToClipboard(json);
			JOptionPane.showMessageDialog(this, "Could not write file, JSON copied to clipboard instead:\n" + ex.getMessage());
		}
	}

	private static int parseInt(String s, int fallback)
	{
		try
		{
			return Integer.parseInt(s.trim());
		}
		catch (Exception e)
		{
			return fallback;
		}
	}

	private void copyToClipboard(String text)
	{
		if (text != null && !text.isEmpty())
		{
			java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
				.setContents(new java.awt.datatransfer.StringSelection(text), null);
		}
	}
}

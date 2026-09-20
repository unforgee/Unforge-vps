package net.runelite.client.plugins.unforgedrops;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
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
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.unforgecommon.AccordionColumn;
import net.runelite.client.plugins.unforgecommon.EntityPickerPanel;
import net.runelite.client.plugins.unforgecommon.ItemPickerPanel;
import net.runelite.client.plugins.unforgecommon.PopoutWindow;
import net.runelite.client.plugins.unforgecommon.TableCopy;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

@Slf4j
public class UnforgeDropsPanel extends PluginPanel
{
	private static final File KRONOS_DATA_ROOT = new File("C:/Users/HOST/Desktop/UNFORGE-239-STANDALONE-LAB/content/kronos-data");

	private final UnforgeDropsPlugin plugin;
	private final Client client;
	private final ClientThread clientThread;
	private final PopoutWindow popout = new PopoutWindow();
	private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

	private int activeNpcId = -1;
	private String activeNpcName = "None";
	private final JLabel npcLabel = new JLabel("No NPC selected - search below");

	private final JTextField hpField = new JTextField("150");
	private final JTextField maxHitField = new JTextField("12");
	private final JComboBox<String> styleBox = new JComboBox<>(new String[]{"MELEE", "STAB", "SLASH", "CRUSH", "RANGED", "MAGIC"});
	private final JTextField speedField = new JTextField("4");
	private final JTextField respawnField = new JTextField("50");

	private final JTextField tableNameField = new JTextField("Drops");
	private final JComboBox<String> dropTierBox = new JComboBox<>(new String[]{"eco", "pvp"});
	private final DefaultTableModel dropModel = new DefaultTableModel(new String[]{"ID", "Item", "Min", "Max", "Weight", "Table", "Guar"}, 0)
	{
		@Override
		public boolean isCellEditable(int row, int col)
		{
			return col != 1;
		}

		@Override
		public Class<?> getColumnClass(int col)
		{
			return col == 6 ? Boolean.class : Object.class;
		}
	};
	private final JTable dropTable = new JTable(dropModel);
	private final JTextField bulkMin = new JTextField("1", 3);
	private final JTextField bulkMax = new JTextField("1", 3);
	private final JTextField bulkWeight = new JTextField("10", 4);
	private JButton popOutBtn;

	public UnforgeDropsPanel(UnforgeDropsPlugin plugin, Client client, ClientThread clientThread)
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
			BorderFactory.createLineBorder(new Color(220, 53, 69), 1),
			new EmptyBorder(8, 8, 8, 8)));

		JPanel titleRow = new JPanel(new BorderLayout());
		titleRow.setOpaque(false);
		JLabel title = new JLabel("☠ UNFORGE DROP EDITOR");
		title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
		title.setForeground(new Color(255, 110, 130));
		titleRow.add(title, BorderLayout.WEST);
		popOutBtn = new JButton("⤢");
		popOutBtn.setToolTipText("Pop out into a resizable window");
		popOutBtn.setMargin(new java.awt.Insets(1, 6, 1, 6));
		popOutBtn.addActionListener(e -> togglePopOut());
		titleRow.add(popOutBtn, BorderLayout.EAST);
		header.add(titleRow);

		npcLabel.setForeground(Color.WHITE);
		npcLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
		npcLabel.setBorder(new EmptyBorder(4, 0, 2, 0));
		header.add(npcLabel);

		dropTierBox.addActionListener(e -> loadNpcData());

		return header;
	}

	private JPanel buildEditor()
	{
		AccordionColumn editor = new AccordionColumn();
		Color accent = new Color(255, 110, 130);

		EntityPickerPanel npcPicker = new EntityPickerPanel(client, clientThread, false, true, r ->
		{
			activeNpcId = r.id;
			activeNpcName = r.name;
			npcLabel.setText("NPC: " + r.name + " (ID: " + r.id + ", Combat: " + r.combatLevel + ")");
			loadNpcData();
		});
		editor.addSection("NPC Search", npcPicker, true, accent, true);

		JPanel combat = new JPanel(new GridLayout(0, 2, 4, 2));
		combat.setOpaque(false);
		combat.setBorder(BorderFactory.createTitledBorder(
			BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR), "Combat"));
		combat.add(new JLabel("Hitpoints:"));
		combat.add(hpField);
		combat.add(new JLabel("Max hit:"));
		combat.add(maxHitField);
		combat.add(new JLabel("Style:"));
		combat.add(styleBox);
		combat.add(new JLabel("Atk speed (t):"));
		combat.add(speedField);
		combat.add(new JLabel("Respawn (t):"));
		combat.add(respawnField);
		editor.addSection("Combat", combat, false, accent, false);

		ItemPickerPanel picker = new ItemPickerPanel(client, clientThread, items ->
		{
			for (net.runelite.client.plugins.unforgecommon.UnforgeSearch.ItemResult r : items)
			{
				dropModel.addRow(new Object[]{r.id, r.name, parseInt(bulkMin.getText(), 1), parseInt(bulkMax.getText(), 1), parseInt(bulkWeight.getText(), 10), tableNameField.getText(), false});
			}
		}, 4);
		editor.addSection("Item Search", picker, true, accent, true);

		dropTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		dropTable.setFillsViewportHeight(true);
		dropTable.getColumnModel().getColumn(0).setMaxWidth(60);
		dropTable.getColumnModel().getColumn(2).setMaxWidth(45);
		dropTable.getColumnModel().getColumn(3).setMaxWidth(45);
		dropTable.getColumnModel().getColumn(4).setMaxWidth(55);
		dropTable.getColumnModel().getColumn(5).setMaxWidth(70);
		dropTable.getColumnModel().getColumn(6).setMaxWidth(35);
		TableCopy.install(dropTable);

		JPanel dropSection = new JPanel(new BorderLayout(0, 4));
		dropSection.setOpaque(false);

		JScrollPane tableScroll = new JScrollPane(dropTable);
		tableScroll.setBorder(BorderFactory.createTitledBorder(
			BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR), "Drop table (Ctrl+C copies rows)"));
		dropSection.add(tableScroll, BorderLayout.CENTER);

		JPanel dropBottom = new JPanel();
		dropBottom.setLayout(new BoxLayout(dropBottom, BoxLayout.Y_AXIS));
		dropBottom.setOpaque(false);

		JPanel bulk = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 2));
		bulk.setOpaque(false);
		bulk.add(new JLabel("Min:"));
		bulk.add(bulkMin);
		JButton setMin = new JButton("Set");
		setMin.addActionListener(e -> applyBulk(2, bulkMin.getText()));
		bulk.add(setMin);
		bulk.add(new JLabel("Max:"));
		bulk.add(bulkMax);
		JButton setMax = new JButton("Set");
		setMax.addActionListener(e -> applyBulk(3, bulkMax.getText()));
		bulk.add(setMax);
		bulk.add(new JLabel("Wt:"));
		bulk.add(bulkWeight);
		JButton setWt = new JButton("Set");
		setWt.addActionListener(e -> applyBulk(4, bulkWeight.getText()));
		bulk.add(setWt);
		JCheckBox guar = new JCheckBox("Guar");
		guar.setOpaque(false);
		guar.addActionListener(e ->
		{
			for (int row : dropTable.getSelectedRows())
			{
				dropModel.setValueAt(guar.isSelected(), dropTable.convertRowIndexToModel(row), 6);
			}
		});
		bulk.add(guar);
		JButton setTbl = new JButton("Tbl");
		setTbl.setToolTipText("Assign selected rows to the table named in the Table field");
		setTbl.addActionListener(e ->
		{
			for (int row : dropTable.getSelectedRows())
			{
				dropModel.setValueAt(tableNameField.getText(), dropTable.convertRowIndexToModel(row), 5);
			}
		});
		bulk.add(setTbl);
		JButton removeBtn = new JButton("Del");
		removeBtn.addActionListener(e -> removeSelected());
		bulk.add(removeBtn);
		JButton copyBtn = new JButton("Copy");
		copyBtn.addActionListener(e -> TableCopy.copyRows(dropTable, false));
		bulk.add(copyBtn);
		dropBottom.add(bulk);

		JPanel tableMeta = new JPanel(new GridLayout(1, 2, 4, 0));
		tableMeta.setOpaque(false);
		JPanel tn = new JPanel(new BorderLayout(4, 0));
		tn.setOpaque(false);
		tn.add(new JLabel("Table:"), BorderLayout.WEST);
		tn.add(tableNameField, BorderLayout.CENTER);
		tableMeta.add(tn);
		JPanel tier = new JPanel(new BorderLayout(4, 0));
		tier.setOpaque(false);
		tier.add(new JLabel("Tier:"), BorderLayout.WEST);
		tier.add(dropTierBox, BorderLayout.CENTER);
		tableMeta.add(tier);
		dropBottom.add(tableMeta);
		dropSection.add(dropBottom, BorderLayout.SOUTH);

		editor.addSection("Drop Table", dropSection, true, accent, true);

		return editor;
	}

	private JPanel buildFooter()
	{
		JPanel footer = new JPanel(new GridLayout(2, 2, 5, 5));
		footer.setOpaque(false);

		JButton saveDrops = new JButton("Save Drops JSON");
		saveDrops.setBackground(new Color(220, 53, 69));
		saveDrops.setForeground(Color.WHITE);
		saveDrops.addActionListener(e -> saveDrops());
		footer.add(saveDrops);

		JButton copyDrops = new JButton("Copy Drops JSON");
		copyDrops.addActionListener(e -> copyToClipboard(gson.toJson(buildDropsJson())));
		footer.add(copyDrops);

		JButton saveCombat = new JButton("Save Combat JSON");
		saveCombat.setBackground(new Color(120, 60, 180));
		saveCombat.setForeground(Color.WHITE);
		saveCombat.addActionListener(e -> saveCombat());
		footer.add(saveCombat);

		JButton copyCombat = new JButton("Copy Combat JSON");
		copyCombat.addActionListener(e -> copyToClipboard(gson.toJson(buildCombatJson())));
		footer.add(copyCombat);

		return footer;
	}

	/** Loads an npc selected externally (e.g. right-click Edit NPC in game). */
	public void editNpc(int id, String name, int combatLevel)
	{
		activeNpcId = id;
		activeNpcName = name;
		npcLabel.setText("NPC: " + name + " (ID: " + id + ", Combat: " + combatLevel + ")");
		loadNpcData();
	}

	// -------------------------------------------------------------
	// EXISTING DATA LOAD (level/base drops stay untouched - editor
	// rows merge into the npc's own entry / named tables on save)
	// -------------------------------------------------------------
	private void loadNpcData()
	{
		if (activeNpcId < 0)
		{
			return;
		}
		dropModel.setRowCount(0);

		File df = dropsFile();
		if (df.exists())
		{
			try
			{
				JsonElement root = parseJsonLenient(df);
				if (root.isJsonArray())
				{
					JsonObject entry = findEntry(root.getAsJsonArray(), activeNpcId);
					if (entry == null && root.getAsJsonArray().size() > 0)
					{
						entry = root.getAsJsonArray().get(0).getAsJsonObject();
					}
					if (entry != null)
					{
						if (entry.has("guaranteed"))
						{
							for (JsonElement el : entry.getAsJsonArray("guaranteed"))
							{
								JsonObject it = el.getAsJsonObject();
								dropModel.addRow(new Object[]{it.get("id").getAsInt(), "item " + it.get("id").getAsInt(),
									it.has("min") ? it.get("min").getAsInt() : 1, it.has("max") ? it.get("max").getAsInt() : 1, 0, "", true});
							}
						}
						if (entry.has("tables"))
						{
							for (JsonElement te : entry.getAsJsonArray("tables"))
							{
								JsonObject t = te.getAsJsonObject();
								String tname = t.has("name") ? t.get("name").getAsString() : "Drops";
								for (JsonElement ie : t.getAsJsonArray("items"))
								{
									JsonObject it = ie.getAsJsonObject();
									dropModel.addRow(new Object[]{it.get("id").getAsInt(), "item " + it.get("id").getAsInt(),
										it.has("min") ? it.get("min").getAsInt() : 1, it.has("max") ? it.get("max").getAsInt() : 1,
										it.has("weight") ? it.get("weight").getAsInt() : 1, tname, false});
								}
							}
						}
					}
				}
			}
			catch (Exception e)
			{
				log.warn("Failed to load drops for {}: {}", activeNpcName, e.getMessage());
			}
		}
		resolveRowNames();

		File cf = combatFile();
		if (cf.exists())
		{
			try
			{
				JsonElement root = parseJsonLenient(cf);
				if (root.isJsonArray())
				{
					JsonObject entry = findEntry(root.getAsJsonArray(), activeNpcId);
					if (entry == null && root.getAsJsonArray().size() > 0)
					{
						entry = root.getAsJsonArray().get(0).getAsJsonObject();
					}
					if (entry != null)
					{
						if (entry.has("hitpoints")) hpField.setText(entry.get("hitpoints").getAsString());
						if (entry.has("max_damage")) maxHitField.setText(entry.get("max_damage").getAsString());
						if (entry.has("attack_style")) styleBox.setSelectedItem(entry.get("attack_style").getAsString());
						if (entry.has("attack_ticks")) speedField.setText(entry.get("attack_ticks").getAsString());
						if (entry.has("respawn_ticks")) respawnField.setText(entry.get("respawn_ticks").getAsString());
					}
				}
			}
			catch (Exception e)
			{
				log.warn("Failed to load combat def for {}: {}", activeNpcName, e.getMessage());
			}
		}
	}

	private void resolveRowNames()
	{
		clientThread.invokeLater(() ->
		{
			java.util.List<Object[]> updates = new java.util.ArrayList<>();
			for (int i = 0; i < dropModel.getRowCount(); i++)
			{
				try
				{
					int id = Integer.parseInt(String.valueOf(dropModel.getValueAt(i, 0)).trim());
					net.runelite.api.ItemComposition def = client.getItemDefinition(id);
					if (def != null && def.getName() != null && !def.getName().equalsIgnoreCase("null"))
					{
						updates.add(new Object[]{i, def.getName().replace(" (Members)", "")});
					}
				}
				catch (Throwable ignored)
				{
				}
			}
			SwingUtilities.invokeLater(() ->
			{
				for (Object[] u : updates)
				{
					int row = (Integer) u[0];
					if (row < dropModel.getRowCount())
					{
						dropModel.setValueAt(u[1], row, 1);
					}
				}
			});
		});
	}

	private File dropsFile()
	{
		String fname = activeNpcName.replaceAll("[^a-zA-Z0-9_-]", "_") + ".json";
		String tier = (String) dropTierBox.getSelectedItem();
		File f = new File(KRONOS_DATA_ROOT, "npcs/drops/" + tier + "/" + fname);
		if (!f.exists())
		{
			String other = "eco".equals(tier) ? "pvp" : "eco";
			File alt = new File(KRONOS_DATA_ROOT, "npcs/drops/" + other + "/" + fname);
			if (alt.exists())
			{
				dropTierBox.setSelectedItem(other);
				return alt;
			}
		}
		return f;
	}

	private File combatFile()
	{
		String fname = activeNpcName.replaceAll("[^a-zA-Z0-9_-]", "_") + ".json";
		return new File(KRONOS_DATA_ROOT, "npcs/combat/" + fname);
	}

	private static JsonElement parseJsonLenient(File f) throws IOException
	{
		String text = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
		// Strip // line comments (kronos data files use them for item names)
		text = text.replaceAll("//[^\\r\\n]*", "");
		return new JsonParser().parse(text);
	}

	private static JsonObject findEntry(JsonArray arr, int npcId)
	{
		for (JsonElement el : arr)
		{
			if (!el.isJsonObject())
			{
				continue;
			}
			JsonObject o = el.getAsJsonObject();
			if (o.has("ids") && o.get("ids").isJsonArray())
			{
				for (JsonElement i : o.getAsJsonArray("ids"))
				{
					if (i.getAsInt() == npcId)
					{
						return o;
					}
				}
			}
		}
		return null;
	}

	private void togglePopOut()
	{
		popout.toggle(this, "Unforge Drop Editor", 540, 780, plugin::reopenPanel);
		popOutBtn.setText(popout.isOpen() ? "⤡" : "⤢");
	}

	void closePopout()
	{
		popout.dock(this, null);
	}

	private void applyBulk(int col, String value)
	{
		int v = parseInt(value, -1);
		if (v < 0)
		{
			return;
		}
		for (int row : dropTable.getSelectedRows())
		{
			dropModel.setValueAt(v, dropTable.convertRowIndexToModel(row), col);
		}
	}

	private void removeSelected()
	{
		int[] rows = dropTable.getSelectedRows();
		for (int i = rows.length - 1; i >= 0; i--)
		{
			dropModel.removeRow(dropTable.convertRowIndexToModel(rows[i]));
		}
	}

	/**
	 * Merges editor rows into the existing drops file: the npc's entry keeps its
	 * other tables (level-base drops) and rows are written into the named tables
	 * they carry. Guaranteed rows replace the entry's guaranteed list.
	 */
	private JsonArray buildDropsJson()
	{
		JsonArray root = new JsonArray();
		File f = dropsFile();
		if (f.exists())
		{
			try
			{
				JsonElement parsed = parseJsonLenient(f);
				if (parsed.isJsonArray())
				{
					root = parsed.getAsJsonArray();
				}
			}
			catch (Exception e)
			{
				log.warn("Could not read existing drops file, writing fresh: {}", e.getMessage());
			}
		}

		JsonArray guaranteed = new JsonArray();
		Map<String, JsonArray> tableItems = new LinkedHashMap<>();
		for (int i = 0; i < dropModel.getRowCount(); i++)
		{
			try
			{
				JsonObject item = new JsonObject();
				item.addProperty("id", Integer.parseInt(String.valueOf(dropModel.getValueAt(i, 0)).trim()));
				item.addProperty("min", Integer.parseInt(String.valueOf(dropModel.getValueAt(i, 2)).trim()));
				item.addProperty("max", Integer.parseInt(String.valueOf(dropModel.getValueAt(i, 3)).trim()));
				boolean guar = Boolean.TRUE.equals(dropModel.getValueAt(i, 6));
				if (guar)
				{
					guaranteed.add(item);
				}
				else
				{
					item.addProperty("weight", Integer.parseInt(String.valueOf(dropModel.getValueAt(i, 4)).trim()));
					String tname = String.valueOf(dropModel.getValueAt(i, 5)).trim();
					if (tname.isEmpty())
					{
						tname = "Drops";
					}
					tableItems.computeIfAbsent(tname, k -> new JsonArray()).add(item);
				}
			}
			catch (NumberFormatException ignored)
			{
			}
		}

		JsonObject entry = findEntry(root, activeNpcId);
		if (entry == null)
		{
			entry = new JsonObject();
			JsonArray ids = new JsonArray();
			ids.add(activeNpcId);
			entry.add("ids", ids);
			root.add(entry);
		}
		if (guaranteed.size() > 0 || entry.has("guaranteed"))
		{
			entry.add("guaranteed", guaranteed);
		}

		JsonArray newTables = new JsonArray();
		Set<String> consumed = new HashSet<>();
		if (entry.has("tables") && entry.get("tables").isJsonArray())
		{
			for (JsonElement te : entry.getAsJsonArray("tables"))
			{
				JsonObject t = te.getAsJsonObject();
				String tn = t.has("name") ? t.get("name").getAsString() : "Drops";
				if (tableItems.containsKey(tn))
				{
					t.add("items", tableItems.get(tn));
					consumed.add(tn);
				}
				newTables.add(t);
			}
		}
		for (Map.Entry<String, JsonArray> e : tableItems.entrySet())
		{
			if (!consumed.contains(e.getKey()))
			{
				JsonObject t = new JsonObject();
				t.addProperty("name", e.getKey());
				t.addProperty("weight", 1);
				t.add("items", e.getValue());
				newTables.add(t);
			}
		}
		entry.add("tables", newTables);
		return root;
	}

	private void saveDrops()
	{
		String json = gson.toJson(buildDropsJson());
		File target = dropsFile();
		try
		{
			target.getParentFile().mkdirs();
			Files.write(target.toPath(), json.getBytes(StandardCharsets.UTF_8));
			JOptionPane.showMessageDialog(this, "Saved drops to:\n" + target.getAbsolutePath(), "Drops Saved", JOptionPane.INFORMATION_MESSAGE);
		}
		catch (IOException ex)
		{
			copyToClipboard(json);
			JOptionPane.showMessageDialog(this, "Could not write file, JSON copied to clipboard instead:\n" + ex.getMessage());
		}
	}

	/** Merges combat fields into the npc's entry in the existing combat file. */
	private JsonArray buildCombatJson()
	{
		JsonArray root = new JsonArray();
		File f = combatFile();
		if (f.exists())
		{
			try
			{
				JsonElement parsed = parseJsonLenient(f);
				if (parsed.isJsonArray())
				{
					root = parsed.getAsJsonArray();
				}
			}
			catch (Exception e)
			{
				log.warn("Could not read existing combat file, writing fresh: {}", e.getMessage());
			}
		}
		JsonObject entry = findEntry(root, activeNpcId);
		if (entry == null)
		{
			entry = new JsonObject();
			JsonArray ids = new JsonArray();
			ids.add(activeNpcId);
			entry.add("ids", ids);
			root.add(entry);
		}
		entry.addProperty("hitpoints", parseInt(hpField.getText(), 100));
		entry.addProperty("max_damage", parseInt(maxHitField.getText(), 10));
		entry.addProperty("attack_style", (String) styleBox.getSelectedItem());
		entry.addProperty("attack_ticks", parseInt(speedField.getText(), 4));
		entry.addProperty("respawn_ticks", parseInt(respawnField.getText(), 50));
		return root;
	}

	private void saveCombat()
	{
		String json = gson.toJson(buildCombatJson());
		File target = combatFile();
		try
		{
			target.getParentFile().mkdirs();
			Files.write(target.toPath(), json.getBytes(StandardCharsets.UTF_8));
			JOptionPane.showMessageDialog(this, "Saved combat def to:\n" + target.getAbsolutePath(), "Combat Saved", JOptionPane.INFORMATION_MESSAGE);
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

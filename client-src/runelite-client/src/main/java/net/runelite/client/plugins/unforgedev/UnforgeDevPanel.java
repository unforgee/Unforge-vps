package net.runelite.client.plugins.unforgedev;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import javax.swing.Timer;
import javax.swing.JTable;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.NPCComposition;
import net.runelite.api.ObjectComposition;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

@Slf4j
public class UnforgeDevPanel extends PluginPanel
{
	private static final File KRONOS_DATA_ROOT = new File("C:/Users/HOST/Desktop/UNFORGE-239-STANDALONE-LAB/content/kronos-data");

	private final UnforgeDevToolsPlugin plugin;
	private final Client client;
	private final ClientThread clientThread;
	private final UnforgeDevConfig config;
	private final UnforgeAiDispatcher aiDispatcher;
	private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

	// Selected Entity Info
	private JLabel entityTitleLabel;
	private JLabel entityDetailsLabel;
	private int activeNpcId = -1;
	private String activeNpcName = "None";
	private WorldPoint activeNpcPoint;
	private boolean activeIsNpc;

	// Entity Search
	private JTextField entitySearchField;
	private JComboBox<String> entityTypeBox;
	private JComboBox<String> searchModeBox;
	private JButton entitySearchBtn;
	private DefaultListModel<EntityResult> resultModel;
	private JList<EntityResult> resultList;

	// Pop-out window
	private JFrame popOutFrame;
	private JButton popOutBtn;

	// Tabs & Role Selectors
	private JComboBox<String> roleSelector;
	private JPanel roleCardsContainer;
	private JPanel shopCard;
	private JPanel combatCard;
	private JPanel questCard;
	private JPanel spawnCard;

	// Shop Fields
	private JTextField shopTitleField;
	private JComboBox<String> shopCurrencyBox;
	private JFrame shopBuilderFrame;
	private final DefaultTableModel shopItemModel = new DefaultTableModel(new String[]{"ID", "Item", "Amount", "Price"}, 0)
	{
		@Override
		public boolean isCellEditable(int row, int col)
		{
			return col != 1;
		}
	};

	// Combat Fields
	private JTextField combatHpField;
	private JTextField combatMaxHitField;
	private JComboBox<String> combatAttackStyleBox;
	private JTextField combatAttackSpeedField;
	private JTextField combatRespawnTicksField;
	private JTextArea combatDropTableArea;

	// Quest Fields
	private JTextField questNameField;
	private JComboBox<String> questStageBox;
	private JTextArea questDialogueArea;

	// Spawn Fields
	private JTextField spawnCoordsField;
	private JTextField spawnRangeField;
	private JComboBox<String> spawnDirectionBox;
	private JTextField spawnDurationField;

	// AI Assistant Bar
	private JComboBox<UnforgeAiDispatcher.ProviderEntry> aiProviderBox;
	private JTextField aiPromptField;
	private JButton aiGenerateBtn;
	private JLabel aiStatusLabel;

	public UnforgeDevPanel(UnforgeDevToolsPlugin plugin, Client client, ClientThread clientThread, UnforgeDevConfig config, UnforgeAiDispatcher aiDispatcher)
	{
		super(false);
		this.plugin = plugin;
		this.client = client;
		this.clientThread = clientThread;
		this.config = config;
		this.aiDispatcher = aiDispatcher;

		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setBorder(new EmptyBorder(10, 10, 10, 10));

		add(buildHeaderPanel(), BorderLayout.NORTH);
		add(buildCenterPanel(), BorderLayout.CENTER);
		add(buildFooterPanel(), BorderLayout.SOUTH);

		int[][] defaults = {{590, 100, 10}, {1755, 100, 15}, {2347, 100, 20}, {952, 100, 25}, {1265, 50, 50}};
		for (int[] d : defaults)
		{
			shopItemModel.addRow(new Object[]{d[0], "item " + d[0], d[1], d[2]});
		}
		resolveShopItemNames();
	}

	private void resolveShopItemNames()
	{
		clientThread.invokeLater(() ->
		{
			List<Object[]> updates = new ArrayList<>();
			for (int i = 0; i < shopItemModel.getRowCount(); i++)
			{
				try
				{
					int id = Integer.parseInt(String.valueOf(shopItemModel.getValueAt(i, 0)).trim());
					ItemComposition def = client.getItemDefinition(id);
					if (def != null && nameMatches(def.getName(), ""))
					{
						updates.add(new Object[]{i, def.getName()});
					}
				}
				catch (Throwable ignored) {}
			}
			SwingUtilities.invokeLater(() ->
			{
				for (Object[] u : updates)
				{
					int row = (Integer) u[0];
					if (row < shopItemModel.getRowCount())
					{
						shopItemModel.setValueAt(u[1], row, 1);
					}
				}
			});
		});
	}

	private JPanel buildHeaderPanel()
	{
		JPanel header = new JPanel();
		header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
		header.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		header.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(new Color(0, 188, 212), 1),
			new EmptyBorder(8, 8, 8, 8)
		));

		JPanel titleRow = new JPanel(new BorderLayout());
		titleRow.setOpaque(false);

		JLabel title = new JLabel("⚡ UNFORGE DEV STUDIO");
		title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
		title.setForeground(new Color(0, 255, 255));
		titleRow.add(title, BorderLayout.WEST);

		popOutBtn = new JButton("⤢");
		popOutBtn.setToolTipText("Pop out into a resizable window");
		popOutBtn.setMargin(new java.awt.Insets(1, 6, 1, 6));
		popOutBtn.addActionListener(e -> togglePopOut());
		titleRow.add(popOutBtn, BorderLayout.EAST);
		header.add(titleRow);

		entityTitleLabel = new JLabel("No Entity Selected");
		entityTitleLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
		entityTitleLabel.setForeground(Color.WHITE);
		header.add(entityTitleLabel);

		entityDetailsLabel = new JLabel("Right-click any NPC/Object in-game -> 'Edit'");
		entityDetailsLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
		entityDetailsLabel.setForeground(Color.LIGHT_GRAY);
		header.add(entityDetailsLabel);

		// Action buttons (Copy coords, copy ID)
		JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
		btnRow.setOpaque(false);

		JButton copyIdBtn = new JButton("Copy ID");
		copyIdBtn.addActionListener(e -> copyToClipboard(String.valueOf(activeNpcId)));
		btnRow.add(copyIdBtn);

		JButton copyCoordsBtn = new JButton("Copy Coords");
		copyCoordsBtn.addActionListener(e -> {
			if (activeNpcPoint != null) {
				copyToClipboard(activeNpcPoint.getX() + ", " + activeNpcPoint.getY() + ", " + activeNpcPoint.getPlane());
			}
		});
		btnRow.add(copyCoordsBtn);

		header.add(btnRow);
		header.add(buildSearchPanel());
		return header;
	}

	// -------------------------------------------------------------
	// ENTITY SEARCH (by name or right-click action)
	// -------------------------------------------------------------
	private JPanel buildSearchPanel()
	{
		JPanel search = new JPanel();
		search.setLayout(new BoxLayout(search, BoxLayout.Y_AXIS));
		search.setOpaque(false);
		search.setBorder(new EmptyBorder(6, 0, 0, 0));

		JLabel searchLbl = new JLabel("Entity Search (name or action, e.g. \"bank\"):");
		searchLbl.setForeground(new Color(0, 200, 255));
		searchLbl.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
		search.add(searchLbl);

		JPanel filterRow = new JPanel(new GridLayout(1, 2, 4, 0));
		filterRow.setOpaque(false);
		entityTypeBox = new JComboBox<>(new String[]{"Objects", "NPCs"});
		searchModeBox = new JComboBox<>(new String[]{"by Name", "by Action"});
		filterRow.add(entityTypeBox);
		filterRow.add(searchModeBox);
		search.add(filterRow);

		JPanel fieldRow = new JPanel(new BorderLayout(4, 0));
		fieldRow.setOpaque(false);
		fieldRow.setBorder(new EmptyBorder(4, 0, 4, 0));
		entitySearchField = new JTextField();
		entitySearchField.setToolTipText("Search object/NPC names or actions - e.g. bank booth, Bank, Talk-to");
		entitySearchField.addActionListener(e -> runEntitySearch());
		fieldRow.add(entitySearchField, BorderLayout.CENTER);

		entitySearchBtn = new JButton("Search");
		entitySearchBtn.addActionListener(e -> runEntitySearch());
		fieldRow.add(entitySearchBtn, BorderLayout.EAST);
		search.add(fieldRow);

		resultModel = new DefaultListModel<>();
		resultList = new JList<EntityResult>(resultModel)
		{
			@Override
			public String getToolTipText(MouseEvent e)
			{
				int i = locationToIndex(e.getPoint());
				if (i < 0 || i >= getModel().getSize())
				{
					return null;
				}
				EntityResult r = getModel().getElementAt(i);
				return "<html><b>" + r.name + "</b> (id " + r.id + ")<br/>Actions: "
					+ (r.actions.isEmpty() ? "-" : r.actions) + "</html>";
			}
		};
		resultList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		resultList.setVisibleRowCount(4);
		resultList.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		resultList.setForeground(Color.WHITE);
		ToolTipManager.sharedInstance().registerComponent(resultList);
		resultList.addListSelectionListener(e ->
		{
			if (!e.getValueIsAdjusting())
			{
				selectSearchResult(resultList.getSelectedValue());
			}
		});
		search.add(new JScrollPane(resultList));

		return search;
	}

	private static boolean nameMatches(String name, String query)
	{
		return name != null && !name.equalsIgnoreCase("null") && name.toLowerCase().contains(query);
	}

	private static boolean actionsMatch(String[] actions, String query)
	{
		if (actions == null)
		{
			return false;
		}
		for (String action : actions)
		{
			if (action != null && !action.equalsIgnoreCase("null") && action.toLowerCase().contains(query))
			{
				return true;
			}
		}
		return false;
	}

	private void runEntitySearch()
	{
		String query = entitySearchField.getText().trim().toLowerCase();
		if (query.isEmpty())
		{
			return;
		}
		boolean searchNpcs = entityTypeBox.getSelectedIndex() == 1;
		boolean byAction = searchModeBox.getSelectedIndex() == 1;

		entitySearchBtn.setEnabled(false);
		entitySearchBtn.setText("...");

		// Composition lookups must run on the client thread
		clientThread.invokeLater(() ->
		{
			List<EntityResult> results = new ArrayList<>(256);
			int misses = 0;
			for (int id = 0; id < 65536 && results.size() < 250; id++)
			{
				try
				{
					if (searchNpcs)
					{
						NPCComposition def = client.getNpcDefinition(id);
						if (def == null)
						{
							if (++misses > 2048)
							{
								break;
							}
							continue;
						}
						misses = 0;
						String[] actions = def.getActions();
						if (byAction ? actionsMatch(actions, query) : nameMatches(def.getName(), query))
						{
							results.add(new EntityResult(id, def.getName(), actions, def.getCombatLevel(), true));
						}
					}
					else
					{
						ObjectComposition def = client.getObjectDefinition(id);
						if (def == null)
						{
							if (++misses > 2048)
							{
								break;
							}
							continue;
						}
						misses = 0;
						String[] actions = def.getActions();
						if (byAction ? actionsMatch(actions, query) : nameMatches(def.getName(), query))
						{
							results.add(new EntityResult(id, def.getName(), actions, -1, false));
						}
					}
				}
				catch (Throwable t)
				{
					if (++misses > 2048)
					{
						break;
					}
				}
			}

			SwingUtilities.invokeLater(() ->
			{
				resultModel.clear();
				for (EntityResult r : results)
				{
					resultModel.addElement(r);
				}
				entitySearchBtn.setEnabled(true);
				entitySearchBtn.setText("Search");
				entityDetailsLabel.setText(results.isEmpty()
					? "No matches for '" + query + "'"
					: results.size() + " match(es) - click to load");
			});
		});
	}

	private void selectSearchResult(EntityResult r)
	{
		if (r == null)
		{
			return;
		}
		clientThread.invokeLater(() ->
		{
			WorldPoint point = client.getLocalPlayer() != null
				? client.getLocalPlayer().getWorldLocation()
				: new WorldPoint(3200, 3200, 0);
			SwingUtilities.invokeLater(() ->
			{
				if (r.npc)
				{
					loadNpc(r.id, r.name, point, r.combatLevel);
				}
				else
				{
					loadObject(r.id, r.name, point);
				}
			});
		});
	}

	private void togglePopOut()
	{
		if (popOutFrame == null)
		{
			popOutFrame = new JFrame("Unforge Dev Studio");
			popOutFrame.setAlwaysOnTop(true);
			popOutFrame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
			popOutFrame.addWindowListener(new WindowAdapter()
			{
				@Override
				public void windowClosing(WindowEvent e)
				{
					dockPanel();
				}
			});
			popOutFrame.getContentPane().add(this);
			popOutFrame.setSize(460, 800);
			popOutFrame.setLocationByPlatform(true);
			popOutFrame.setVisible(true);
			popOutBtn.setText("⤡");
			popOutBtn.setToolTipText("Dock back into the sidebar");
		}
		else
		{
			dockPanel();
		}
	}

	private void dockPanel()
	{
		if (popOutFrame != null)
		{
			popOutFrame.getContentPane().remove(this);
			popOutFrame.dispose();
			popOutFrame = null;
		}
		popOutBtn.setText("⤢");
		popOutBtn.setToolTipText("Pop out into a resizable window");
		plugin.reopenPanel();
	}

	private static final class EntityResult
	{
		final int id;
		final String name;
		final String actions;
		final int combatLevel;
		final boolean npc;

		EntityResult(int id, String name, String[] actions, int combatLevel, boolean npc)
		{
			this.id = id;
			this.name = name != null && !name.equalsIgnoreCase("null") ? name : "(unnamed)";
			this.actions = actions == null ? "" : String.join(", ", Arrays.stream(actions).filter(a -> a != null && !a.equalsIgnoreCase("null")).toArray(String[]::new));
			this.combatLevel = combatLevel;
			this.npc = npc;
		}

		@Override
		public String toString()
		{
			String lvl = npc && combatLevel > 0 ? " • lvl " + combatLevel : "";
			return name + "  (id " + id + lvl + ")";
		}
	}

	// -------------------------------------------------------------
	// SHOP ITEM PICKER (live name search, multi-select, bulk edit)
	// -------------------------------------------------------------
	private final class ItemResult
	{
		final int id;
		final String name;
		final int storePrice;

		ItemResult(int id, String name, int storePrice)
		{
			this.id = id;
			this.name = name;
			this.storePrice = storePrice;
		}

		@Override
		public String toString()
		{
			return name + "  (id " + id + ")";
		}
	}

	private final class ShopItemEditor extends JPanel
	{
		private final JTextField searchField = new JTextField();
		private final DefaultListModel<ItemResult> resultModel = new DefaultListModel<>();
		private final JList<ItemResult> results = new JList<>(resultModel);
		private final JTable table = new JTable(shopItemModel);
		private final JTextField bulkAmount = new JTextField("1", 4);
		private final JTextField bulkPrice = new JTextField(4);
		private final Timer debounce;

		ShopItemEditor(boolean compact)
		{
			super(new BorderLayout(0, 4));
			setOpaque(false);

			JPanel searchRow = new JPanel(new BorderLayout(4, 0));
			searchRow.setOpaque(false);
			searchField.setToolTipText("Type to search items, e.g. whip / armadyl - Shift/Ctrl multi-select, Enter adds");
			searchRow.add(searchField, BorderLayout.CENTER);
			JButton addBtn = new JButton("Add");
			addBtn.addActionListener(e -> addSelectedItems());
			searchRow.add(addBtn, BorderLayout.EAST);
			add(searchRow, BorderLayout.NORTH);

			results.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
			results.setVisibleRowCount(compact ? 4 : 8);
			results.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			results.setForeground(Color.WHITE);
			results.addKeyListener(new KeyAdapter()
			{
				@Override
				public void keyPressed(KeyEvent e)
				{
					if (e.getKeyCode() == KeyEvent.VK_ENTER)
					{
						addSelectedItems();
					}
				}
			});
			results.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseClicked(MouseEvent e)
				{
					if (e.getClickCount() == 2)
					{
						addSelectedItems();
					}
				}
			});

			table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
			table.setFillsViewportHeight(true);
			table.getColumnModel().getColumn(0).setMaxWidth(60);
			table.getColumnModel().getColumn(2).setMaxWidth(60);
			table.getColumnModel().getColumn(3).setMaxWidth(70);

			JScrollPane resultsScroll = new JScrollPane(results);
			resultsScroll.setBorder(BorderFactory.createTitledBorder(
				BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR), "Matches (Shift+Enter adds all selected)"));
			JScrollPane tableScroll = new JScrollPane(table);
			tableScroll.setBorder(BorderFactory.createTitledBorder(
				BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR), "Shop stock (Shift-select rows for bulk edit)"));

			JPanel center = new JPanel(new GridLayout(2, 1, 0, 4));
			center.setOpaque(false);
			center.add(resultsScroll);
			center.add(tableScroll);
			add(center, BorderLayout.CENTER);

			JPanel bulk = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
			bulk.setOpaque(false);
			bulk.add(new JLabel("Amount:"));
			bulk.add(bulkAmount);
			JButton setAmount = new JButton("Set");
			setAmount.addActionListener(e -> applyBulk(2, bulkAmount.getText()));
			bulk.add(setAmount);
			bulk.add(new JLabel("Price:"));
			bulk.add(bulkPrice);
			JButton setPrice = new JButton("Set");
			setPrice.addActionListener(e -> applyBulk(3, bulkPrice.getText()));
			bulk.add(setPrice);
			JButton removeBtn = new JButton("Remove");
			removeBtn.addActionListener(e -> removeSelectedRows());
			bulk.add(removeBtn);
			add(bulk, BorderLayout.SOUTH);

			debounce = new Timer(300, e -> runItemSearch());
			debounce.setRepeats(false);
			searchField.getDocument().addDocumentListener(new DocumentListener()
			{
				public void insertUpdate(DocumentEvent e) { debounce.restart(); }
				public void removeUpdate(DocumentEvent e) { debounce.restart(); }
				public void changedUpdate(DocumentEvent e) { debounce.restart(); }
			});
			searchField.addActionListener(e -> runItemSearch());
		}

		private void addSelectedItems()
		{
			int amount;
			try
			{
				amount = Math.max(1, Integer.parseInt(bulkAmount.getText().trim()));
			}
			catch (NumberFormatException e)
			{
				amount = 1;
			}
			for (ItemResult r : results.getSelectedValuesList())
			{
				shopItemModel.addRow(new Object[]{r.id, r.name, amount, Math.max(0, r.storePrice)});
			}
			if (!results.isSelectionEmpty())
			{
				searchField.setText("");
				resultModel.clear();
			}
		}

		private void applyBulk(int col, String value)
		{
			int v;
			try
			{
				v = Integer.parseInt(value.trim());
			}
			catch (NumberFormatException e)
			{
				return;
			}
			for (int row : table.getSelectedRows())
			{
				shopItemModel.setValueAt(v, table.convertRowIndexToModel(row), col);
			}
		}

		private void removeSelectedRows()
		{
			int[] rows = table.getSelectedRows();
			for (int i = rows.length - 1; i >= 0; i--)
			{
				shopItemModel.removeRow(table.convertRowIndexToModel(rows[i]));
			}
		}

		private void runItemSearch()
		{
			String query = searchField.getText().trim().toLowerCase();
			if (query.length() < 2)
			{
				return;
			}
			clientThread.invokeLater(() ->
			{
				int bound = client.getItemCount();
				if (bound <= 0)
				{
					bound = 65536;
				}
				List<ItemResult> found = new ArrayList<>(128);
				for (int id = 0; id < bound && found.size() < 250; id++)
				{
					try
					{
						ItemComposition def = client.getItemDefinition(id);
						if (def != null && nameMatches(def.getName(), query))
						{
							found.add(new ItemResult(id, def.getName(), def.getPrice()));
						}
					}
					catch (Throwable ignored) {}
				}
				SwingUtilities.invokeLater(() ->
				{
					resultModel.clear();
					for (ItemResult r : found)
					{
						resultModel.addElement(r);
					}
				});
			});
		}
	}

	private void openShopBuilder()
	{
		if (shopBuilderFrame != null)
		{
			shopBuilderFrame.toFront();
			shopBuilderFrame.requestFocus();
			return;
		}

		shopBuilderFrame = new JFrame("Unforge Shop Builder");
		shopBuilderFrame.setAlwaysOnTop(true);
		shopBuilderFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		shopBuilderFrame.addWindowListener(new WindowAdapter()
		{
			@Override
			public void windowClosed(WindowEvent e)
			{
				shopBuilderFrame = null;
			}
		});

		JPanel root = new JPanel(new BorderLayout(8, 8));
		root.setBorder(new EmptyBorder(10, 10, 10, 10));
		root.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel top = new JPanel(new GridLayout(1, 2, 8, 0));
		top.setOpaque(false);
		JPanel titleWrap = new JPanel(new BorderLayout(4, 0));
		titleWrap.setOpaque(false);
		titleWrap.add(new JLabel("Shop title:"), BorderLayout.WEST);
		JTextField builderTitle = new JTextField(shopTitleField.getText());
		titleWrap.add(builderTitle, BorderLayout.CENTER);
		top.add(titleWrap);
		JPanel currWrap = new JPanel(new BorderLayout(4, 0));
		currWrap.setOpaque(false);
		currWrap.add(new JLabel("Currency:"), BorderLayout.WEST);
		JComboBox<String> builderCurrency = new JComboBox<>(new String[]{"COINS", "BLOOD_MONEY", "TOKKUL", "MOLCH_PEARLS", "MARK_OF_GRACE", "VOTE_TICKETS"});
		builderCurrency.setSelectedItem(shopCurrencyBox.getSelectedItem());
		currWrap.add(builderCurrency, BorderLayout.CENTER);
		top.add(currWrap);
		root.add(top, BorderLayout.NORTH);

		root.add(new ShopItemEditor(false), BorderLayout.CENTER);

		JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
		actions.setOpaque(false);
		JButton copyBtn = new JButton("Copy YAML");
		copyBtn.addActionListener(e -> copyToClipboard(generateShopYaml(builderTitle.getText(), (String) builderCurrency.getSelectedItem())));
		actions.add(copyBtn);
		JButton saveBtn = new JButton("Save Shop YAML");
		saveBtn.setBackground(new Color(40, 167, 69));
		saveBtn.setForeground(Color.WHITE);
		saveBtn.addActionListener(e ->
		{
			String yaml = generateShopYaml(builderTitle.getText(), (String) builderCurrency.getSelectedItem());
			String filename = activeNpcName.replaceAll("[^a-zA-Z0-9_-]", "_") + "_Shop.yaml";
			File target = new File(KRONOS_DATA_ROOT, "shops/" + filename);
			try
			{
				target.getParentFile().mkdirs();
				Files.write(target.toPath(), yaml.getBytes(StandardCharsets.UTF_8));
				JOptionPane.showMessageDialog(shopBuilderFrame, "Saved shop directly to:\n" + target.getAbsolutePath(), "Shop Saved", JOptionPane.INFORMATION_MESSAGE);
			}
			catch (IOException ex)
			{
				copyToClipboard(yaml);
				JOptionPane.showMessageDialog(shopBuilderFrame, "Could not write file, YAML copied to clipboard instead:\n" + ex.getMessage());
			}
		});
		actions.add(saveBtn);
		root.add(actions, BorderLayout.SOUTH);

		shopBuilderFrame.setContentPane(root);
		shopBuilderFrame.setSize(560, 700);
		shopBuilderFrame.setLocationByPlatform(true);
		shopBuilderFrame.setVisible(true);
	}

	private JPanel buildCenterPanel()
	{
		JPanel center = new JPanel(new BorderLayout());
		center.setOpaque(false);

		// Role selector dropdown
		JPanel roleSelectPanel = new JPanel(new BorderLayout(5, 5));
		roleSelectPanel.setOpaque(false);
		roleSelectPanel.setBorder(new EmptyBorder(8, 0, 8, 0));

		JLabel roleLbl = new JLabel("Assign / Edit Role:");
		roleLbl.setForeground(Color.YELLOW);
		roleLbl.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
		roleSelectPanel.add(roleLbl, BorderLayout.WEST);

		roleSelector = new JComboBox<>(new String[]{"🛒 Shopkeeper (Store)", "⚔️ Combat & Drops", "📜 Quest & Dialogue", "📍 World Spawner"});
		roleSelector.addActionListener(e -> switchRoleCard(roleSelector.getSelectedIndex()));
		roleSelectPanel.add(roleSelector, BorderLayout.CENTER);

		center.add(roleSelectPanel, BorderLayout.NORTH);

		// Role Cards container
		roleCardsContainer = new JPanel(new BorderLayout());
		roleCardsContainer.setOpaque(false);

		shopCard = buildShopCard();
		combatCard = buildCombatCard();
		questCard = buildQuestCard();
		spawnCard = buildSpawnCard();

		roleCardsContainer.add(shopCard, BorderLayout.CENTER);
		center.add(new JScrollPane(roleCardsContainer), BorderLayout.CENTER);

		return center;
	}

	private void switchRoleCard(int index)
	{
		roleCardsContainer.removeAll();
		switch (index)
		{
			case 0:
				roleCardsContainer.add(shopCard, BorderLayout.CENTER);
				break;
			case 1:
				roleCardsContainer.add(combatCard, BorderLayout.CENTER);
				break;
			case 2:
				roleCardsContainer.add(questCard, BorderLayout.CENTER);
				break;
			case 3:
				roleCardsContainer.add(spawnCard, BorderLayout.CENTER);
				break;
		}
		roleCardsContainer.revalidate();
		roleCardsContainer.repaint();
	}

	// -------------------------------------------------------------
	// 1. SHOP CARD
	// -------------------------------------------------------------
	private JPanel buildShopCard()
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setOpaque(false);
		panel.setBorder(new EmptyBorder(5, 5, 5, 5));

		panel.add(new JLabel("Shop Title / Name:"));
		shopTitleField = new JTextField("General Supplies");
		panel.add(shopTitleField);

		panel.add(new JLabel("Currency:"));
		shopCurrencyBox = new JComboBox<>(new String[]{"COINS", "BLOOD_MONEY", "TOKKUL", "MOLCH_PEARLS", "MARK_OF_GRACE", "VOTE_TICKETS"});
		panel.add(shopCurrencyBox);

		JPanel itemsLabelRow = new JPanel(new BorderLayout());
		itemsLabelRow.setOpaque(false);
		JLabel itemsLbl = new JLabel("Shop Items:");
		itemsLabelRow.add(itemsLbl, BorderLayout.WEST);
		JButton builderBtn = new JButton("+");
		builderBtn.setToolTipText("Open standalone Shop Builder window");
		builderBtn.setMargin(new java.awt.Insets(1, 8, 1, 8));
		builderBtn.addActionListener(e -> openShopBuilder());
		itemsLabelRow.add(builderBtn, BorderLayout.EAST);
		panel.add(itemsLabelRow);

		panel.add(new ShopItemEditor(true));

		JPanel btnRow = new JPanel(new GridLayout(1, 2, 5, 5));
		btnRow.setOpaque(false);
		JButton saveShopBtn = new JButton("Save Shop YAML");
		saveShopBtn.setBackground(new Color(40, 167, 69));
		saveShopBtn.setForeground(Color.WHITE);
		saveShopBtn.addActionListener(e -> saveShopYaml());
		btnRow.add(saveShopBtn);

		JButton copyYamlBtn = new JButton("Copy YAML");
		copyYamlBtn.addActionListener(e -> copyToClipboard(generateShopYaml(shopTitleField.getText(), (String) shopCurrencyBox.getSelectedItem())));
		btnRow.add(copyYamlBtn);

		panel.add(btnRow);
		return panel;
	}

	// -------------------------------------------------------------
	// 2. COMBAT & DROPS CARD
	// -------------------------------------------------------------
	private JPanel buildCombatCard()
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setOpaque(false);
		panel.setBorder(new EmptyBorder(5, 5, 5, 5));

		panel.add(new JLabel("Hitpoints (HP):"));
		combatHpField = new JTextField("150");
		panel.add(combatHpField);

		panel.add(new JLabel("Max Hit / Damage:"));
		combatMaxHitField = new JTextField("12");
		panel.add(combatMaxHitField);

		panel.add(new JLabel("Attack Style:"));
		combatAttackStyleBox = new JComboBox<>(new String[]{"MELEE", "STAB", "SLASH", "CRUSH", "RANGED", "MAGIC"});
		panel.add(combatAttackStyleBox);

		panel.add(new JLabel("Attack Speed (Ticks):"));
		combatAttackSpeedField = new JTextField("4");
		panel.add(combatAttackSpeedField);

		panel.add(new JLabel("Respawn Ticks:"));
		combatRespawnTicksField = new JTextField("50");
		panel.add(combatRespawnTicksField);

		panel.add(new JLabel("Drop Table (Format: itemId, min, max, weight):"));
		combatDropTableArea = new JTextArea(6, 20);
		combatDropTableArea.setText("592, 1, 1, 1000\n995, 500, 2500, 800\n4151, 1, 1, 10\n13265, 1, 1, 1");
		panel.add(new JScrollPane(combatDropTableArea));

		JPanel btnRow = new JPanel(new GridLayout(1, 2, 5, 5));
		btnRow.setOpaque(false);
		JButton saveCombatBtn = new JButton("Save Combat JSON");
		saveCombatBtn.setBackground(new Color(220, 53, 69));
		saveCombatBtn.setForeground(Color.WHITE);
		saveCombatBtn.addActionListener(e -> saveCombatJson());
		btnRow.add(saveCombatBtn);

		JButton copyJsonBtn = new JButton("Copy JSON");
		copyJsonBtn.addActionListener(e -> copyToClipboard(generateCombatJson()));
		btnRow.add(copyJsonBtn);

		panel.add(btnRow);
		return panel;
	}

	// -------------------------------------------------------------
	// 3. QUEST CARD
	// -------------------------------------------------------------
	private JPanel buildQuestCard()
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setOpaque(false);
		panel.setBorder(new EmptyBorder(5, 5, 5, 5));

		panel.add(new JLabel("Quest Name:"));
		questNameField = new JTextField("Cook's Assistant");
		panel.add(questNameField);

		panel.add(new JLabel("Role / Stage:"));
		questStageBox = new JComboBox<>(new String[]{"Start Quest (Intro)", "In Progress (Reminder)", "Checkpoint Completed", "Quest Finished (Reward)"});
		panel.add(questStageBox);

		panel.add(new JLabel("Dialogue Lines (One line per message):"));
		questDialogueArea = new JTextArea(8, 20);
		questDialogueArea.setText("Hello adventurer! Can you help me?\nI need milk, flour, and an egg for the Duke's cake!\nPlease hurry back once you have gathered them.");
		panel.add(new JScrollPane(questDialogueArea));

		JButton saveQuestBtn = new JButton("Generate Quest Flow");
		saveQuestBtn.setBackground(new Color(255, 152, 0));
		saveQuestBtn.setForeground(Color.BLACK);
		saveQuestBtn.addActionListener(e -> copyToClipboard(questDialogueArea.getText()));
		panel.add(saveQuestBtn);

		return panel;
	}

	// -------------------------------------------------------------
	// 4. SPAWN CARD
	// -------------------------------------------------------------
	private JPanel buildSpawnCard()
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setOpaque(false);
		panel.setBorder(new EmptyBorder(5, 5, 5, 5));

		panel.add(new JLabel("Coordinates (X, Y, Z):"));
		spawnCoordsField = new JTextField("3200, 3200, 0");
		panel.add(spawnCoordsField);

		panel.add(new JLabel("Walk Range (Tiles):"));
		spawnRangeField = new JTextField("3");
		panel.add(spawnRangeField);

		panel.add(new JLabel("Facing Direction:"));
		spawnDirectionBox = new JComboBox<>(new String[]{"S", "N", "E", "W"});
		panel.add(spawnDirectionBox);

		panel.add(new JLabel("Duration (cycles, in-game spawn):"));
		spawnDurationField = new JTextField("6000");
		panel.add(spawnDurationField);

		JButton spawnHereBtn = new JButton("⚡ Spawn at Me (::npcadd / ::locadd)");
		spawnHereBtn.setBackground(new Color(156, 39, 176));
		spawnHereBtn.setForeground(Color.WHITE);
		spawnHereBtn.addActionListener(e -> spawnSelectedAtPlayer());
		panel.add(spawnHereBtn);

		JPanel btnRow = new JPanel(new GridLayout(1, 2, 5, 5));
		btnRow.setOpaque(false);
		JButton addSpawnBtn = new JButton("Save Spawn");
		addSpawnBtn.setBackground(new Color(0, 123, 255));
		addSpawnBtn.setForeground(Color.WHITE);
		addSpawnBtn.addActionListener(e -> saveSpawnJson());
		btnRow.add(addSpawnBtn);

		JButton copySpawnBtn = new JButton("Copy Spawn JSON");
		copySpawnBtn.addActionListener(e -> copyToClipboard(generateSpawnJson()));
		btnRow.add(copySpawnBtn);

		panel.add(btnRow);
		return panel;
	}

	// -------------------------------------------------------------
	// FOOTER: UNIVERSAL AI PROMPT ASSISTANT
	// -------------------------------------------------------------
	private JPanel buildFooterPanel()
	{
		JPanel footer = new JPanel();
		footer.setLayout(new BoxLayout(footer, BoxLayout.Y_AXIS));
		footer.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		footer.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(new Color(156, 39, 176), 1),
			new EmptyBorder(8, 8, 8, 8)
		));

		JLabel aiTitle = new JLabel("🤖 AI DEV COPILOT (Multi-Provider)");
		aiTitle.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
		aiTitle.setForeground(new Color(225, 112, 255));
		footer.add(aiTitle);

		// Provider selector
		JPanel provRow = new JPanel(new BorderLayout(5, 0));
		provRow.setOpaque(false);
		provRow.add(new JLabel("Provider: "), BorderLayout.WEST);

		aiProviderBox = new JComboBox<>();
		refreshProviders();
		provRow.add(aiProviderBox, BorderLayout.CENTER);
		footer.add(provRow);

		// Prompt input & button
		JPanel promptRow = new JPanel(new BorderLayout(5, 0));
		promptRow.setOpaque(false);
		promptRow.setBorder(new EmptyBorder(4, 0, 4, 0));

		aiPromptField = new JTextField();
		aiPromptField.setToolTipText("e.g. 'Tee tästä Abyssal demonista bossi jolla 500hp ja harvinainen whip drop'");
		promptRow.add(aiPromptField, BorderLayout.CENTER);

		aiGenerateBtn = new JButton("Prompt AI");
		aiGenerateBtn.setBackground(new Color(156, 39, 176));
		aiGenerateBtn.setForeground(Color.WHITE);
		aiGenerateBtn.addActionListener(e -> executeAiPrompt());
		promptRow.add(aiGenerateBtn, BorderLayout.EAST);

		footer.add(promptRow);

		aiStatusLabel = new JLabel("Ready. Choose prompt or direct edit.");
		aiStatusLabel.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 10));
		aiStatusLabel.setForeground(Color.LIGHT_GRAY);
		footer.add(aiStatusLabel);

		return footer;
	}

	private void refreshProviders()
	{
		DefaultComboBoxModel<UnforgeAiDispatcher.ProviderEntry> model = new DefaultComboBoxModel<>();
		// Auto entry
		model.addElement(new UnforgeAiDispatcher.ProviderEntry("auto", "⚡ Auto (Free -> Paid Fallback)", "", "", "", true));
		for (UnforgeAiDispatcher.ProviderEntry p : aiDispatcher.getAvailableProviders())
		{
			model.addElement(p);
		}
		aiProviderBox.setModel(model);
	}

	private void executeAiPrompt()
	{
		String prompt = aiPromptField.getText().trim();
		if (prompt.isEmpty())
		{
			JOptionPane.showMessageDialog(this, "Please enter a prompt for the AI assistant.");
			return;
		}

		UnforgeAiDispatcher.ProviderEntry selected = (UnforgeAiDispatcher.ProviderEntry) aiProviderBox.getSelectedItem();
		String providerId = selected != null ? selected.getId() : "auto";

		aiStatusLabel.setText("Thinking... Contacting " + (selected != null ? selected.getDisplayName() : "AI") + "...");
		aiGenerateBtn.setEnabled(false);

		String systemPrompt = "You are an expert RuneScape / Kronos server game designer assistant. " +
			"The user wants to configure an NPC/Object. Target entity: ID=" + activeNpcId + ", Name='" + activeNpcName + "'. " +
			"Respond ONLY with a valid JSON object matching this schema: " +
			"{\"shopTitle\":\"...\", \"currency\":\"COINS\", \"shopItems\":\"id,amount,price\\n...\", " +
			"\"hp\":150, \"maxHit\":12, \"attackStyle\":\"MELEE\", \"attackSpeed\":4, \"respawnTicks\":50, " +
			"\"drops\":\"id,min,max,weight\\n...\", \"dialogue\":\"...\"}";

		aiDispatcher.askAi(providerId, systemPrompt, prompt).thenAccept(reply -> {
			SwingUtilities.invokeLater(() -> {
				aiGenerateBtn.setEnabled(true);
				aiStatusLabel.setText("AI update applied!");
				applyAiJson(reply);
			});
		}).exceptionally(err -> {
			SwingUtilities.invokeLater(() -> {
				aiGenerateBtn.setEnabled(true);
				aiStatusLabel.setText("AI Error: " + err.getMessage());
				log.error("AI Assistant error", err);
				JOptionPane.showMessageDialog(this, "AI Request Failed: " + err.getMessage(), "AI Error", JOptionPane.ERROR_MESSAGE);
			});
			return null;
		});
	}

	private void applyAiJson(String rawReply)
	{
		try
		{
			// Extract JSON substring if wrapped in markdown
			String clean = rawReply.trim();
			if (clean.contains("{") && clean.contains("}"))
			{
				clean = clean.substring(clean.indexOf('{'), clean.lastIndexOf('}') + 1);
			}

			JsonObject obj = gson.fromJson(clean, JsonObject.class);
			if (obj.has("hp")) combatHpField.setText(obj.get("hp").getAsString());
			if (obj.has("maxHit")) combatMaxHitField.setText(obj.get("maxHit").getAsString());
			if (obj.has("attackStyle")) combatAttackStyleBox.setSelectedItem(obj.get("attackStyle").getAsString());
			if (obj.has("drops")) combatDropTableArea.setText(obj.get("drops").getAsString());
			if (obj.has("shopTitle")) shopTitleField.setText(obj.get("shopTitle").getAsString());
			if (obj.has("currency")) shopCurrencyBox.setSelectedItem(obj.get("currency").getAsString());
			if (obj.has("shopItems"))
		{
			shopItemModel.setRowCount(0);
			for (String line : obj.get("shopItems").getAsString().split("\n"))
			{
				String[] p = line.split("[,; ]+");
				if (p.length >= 3)
				{
					try
					{
						shopItemModel.addRow(new Object[]{Integer.parseInt(p[0].trim()), "item " + p[0].trim(), Integer.parseInt(p[1].trim()), Integer.parseInt(p[2].trim())});
					}
					catch (NumberFormatException ignored) {}
				}
			}
			resolveShopItemNames();
		}
			if (obj.has("dialogue")) questDialogueArea.setText(obj.get("dialogue").getAsString());

			JOptionPane.showMessageDialog(this, "AI filled parameters successfully! Review tabs and click Save.", "AI Applied", JOptionPane.INFORMATION_MESSAGE);
		}
		catch (Exception e)
		{
			log.warn("Failed to parse AI response as JSON: {}", rawReply, e);
			// Put raw reply in dialogue or notification
			questDialogueArea.setText(rawReply);
		}
	}

	// -------------------------------------------------------------
	// PUBLIC LOADER
	// -------------------------------------------------------------
	private void spawnSelectedAtPlayer()
	{
		if (activeNpcId < 0)
		{
			aiStatusLabel.setText("Select an NPC/object first (search or right-click Edit).");
			return;
		}
		int duration;
		try
		{
			duration = Integer.parseInt(spawnDurationField.getText().trim());
		}
		catch (NumberFormatException e)
		{
			aiStatusLabel.setText("Invalid duration: " + spawnDurationField.getText());
			return;
		}
		String command = (activeIsNpc ? "npcadd " : "locadd ") + duration + " " + activeNpcId;
		plugin.sendCheat(command);
		aiStatusLabel.setText("⚡ Sent ::" + command);
	}

	public void loadNpc(int npcId, String name, WorldPoint point, int combatLevel)
	{
		this.activeNpcId = npcId;
		this.activeNpcName = name != null ? name : "NPC #" + npcId;
		this.activeNpcPoint = point;
		this.activeIsNpc = true;

		entityTitleLabel.setText("NPC: " + activeNpcName + " (ID: " + activeNpcId + ")");
		entityDetailsLabel.setText("Combat: " + combatLevel + " | Coords: " + activeNpcPoint.getX() + ", " + activeNpcPoint.getY() + ", " + activeNpcPoint.getPlane());

		shopTitleField.setText(activeNpcName + "'s Store");
		questNameField.setText(activeNpcName + "'s Quest");
		spawnCoordsField.setText(activeNpcPoint.getX() + ", " + activeNpcPoint.getY() + ", " + activeNpcPoint.getPlane());

		revalidate();
		repaint();
	}

	public void loadObject(int objectId, String name, WorldPoint point)
	{
		this.activeNpcId = objectId;
		this.activeNpcName = (name != null && !name.isEmpty()) ? name : "Object #" + objectId;
		this.activeNpcPoint = point;
		this.activeIsNpc = false;

		entityTitleLabel.setText("Object: " + activeNpcName + " (ID: " + activeNpcId + ")");
		entityDetailsLabel.setText("Coords: " + point.getX() + ", " + point.getY() + ", " + point.getPlane());

		spawnCoordsField.setText(point.getX() + ", " + point.getY() + ", " + point.getPlane());
		revalidate();
		repaint();
	}

	// -------------------------------------------------------------
	// CODE GENERATORS & PERSISTENCE
	// -------------------------------------------------------------
	private String generateShopYaml(String title, String currency)
	{
		StringBuilder sb = new StringBuilder();
		sb.append("---\n");
		sb.append("identifier: \"").append(UUID.randomUUID().toString()).append("\"\n");
		sb.append("title: \"").append(title.replace("\"", "\\\"")).append("\"\n");
		sb.append("currency: \"").append(currency).append("\"\n");
		sb.append("accessibleByIronMan: true\n");
		sb.append("canSellToStore: false\n");
		sb.append("restockRules:\n");
		sb.append("  restockTicks: 50\n");
		sb.append("  restockPerTick: 1\n");
		sb.append("defaultStock:\n");

		for (int i = 0; i < shopItemModel.getRowCount(); i++)
		{
			try
			{
				int id = Integer.parseInt(String.valueOf(shopItemModel.getValueAt(i, 0)).trim());
				int amount = Integer.parseInt(String.valueOf(shopItemModel.getValueAt(i, 2)).trim());
				int price = Integer.parseInt(String.valueOf(shopItemModel.getValueAt(i, 3)).trim());
				sb.append("- id: ").append(id).append("\n");
				sb.append("  amount: ").append(amount).append("\n");
				sb.append("  price: ").append(price).append("\n");
				sb.append("  placeholderId: 0\n");
			}
			catch (NumberFormatException ignored) {}
		}
		return sb.toString();
	}

	private void saveShopYaml()
	{
		String yaml = generateShopYaml(shopTitleField.getText(), (String) shopCurrencyBox.getSelectedItem());
		String filename = activeNpcName.replaceAll("[^a-zA-Z0-9_-]", "_") + "_Shop.yaml";
		File target = new File(KRONOS_DATA_ROOT, "shops/" + filename);
		try
		{
			target.getParentFile().mkdirs();
			Files.write(target.toPath(), yaml.getBytes(StandardCharsets.UTF_8));
			JOptionPane.showMessageDialog(this, "Saved shop directly to:\n" + target.getAbsolutePath(), "Shop Saved", JOptionPane.INFORMATION_MESSAGE);
		}
		catch (IOException ex)
		{
			copyToClipboard(yaml);
			JOptionPane.showMessageDialog(this, "Could not write file, YAML copied to clipboard instead:\n" + ex.getMessage());
		}
	}

	private String generateCombatJson()
	{
		JsonArray arr = new JsonArray();
		JsonObject obj = new JsonObject();
		JsonArray ids = new JsonArray();
		ids.add(activeNpcId > 0 ? activeNpcId : 9999);
		obj.add("ids", ids);
		try { obj.addProperty("hitpoints", Integer.parseInt(combatHpField.getText().trim())); } catch (Exception e) { obj.addProperty("hitpoints", 100); }
		try { obj.addProperty("max_damage", Integer.parseInt(combatMaxHitField.getText().trim())); } catch (Exception e) { obj.addProperty("max_damage", 10); }
		obj.addProperty("attack_style", (String) combatAttackStyleBox.getSelectedItem());
		try { obj.addProperty("attack_ticks", Integer.parseInt(combatAttackSpeedField.getText().trim())); } catch (Exception e) { obj.addProperty("attack_ticks", 4); }
		try { obj.addProperty("respawn_ticks", Integer.parseInt(combatRespawnTicksField.getText().trim())); } catch (Exception e) { obj.addProperty("respawn_ticks", 50); }
		arr.add(obj);
		return gson.toJson(arr);
	}

	private void saveCombatJson()
	{
		String json = generateCombatJson();
		String filename = activeNpcName.replaceAll("[^a-zA-Z0-9_-]", "_") + ".json";
		File target = new File(KRONOS_DATA_ROOT, "npcs/combat/" + filename);
		try
		{
			target.getParentFile().mkdirs();
			Files.write(target.toPath(), json.getBytes(StandardCharsets.UTF_8));
			JOptionPane.showMessageDialog(this, "Saved combat def directly to:\n" + target.getAbsolutePath(), "Combat Def Saved", JOptionPane.INFORMATION_MESSAGE);
		}
		catch (IOException ex)
		{
			copyToClipboard(json);
			JOptionPane.showMessageDialog(this, "Could not write file, JSON copied to clipboard instead:\n" + ex.getMessage());
		}
	}

	private String generateSpawnJson()
	{
		JsonObject obj = new JsonObject();
		obj.addProperty("name", activeNpcName);
		obj.addProperty("id", activeNpcId);
		String[] coords = spawnCoordsField.getText().split("[, ]+");
		if (coords.length >= 3)
		{
			try
			{
				obj.addProperty("x", Integer.parseInt(coords[0]));
				obj.addProperty("y", Integer.parseInt(coords[1]));
				obj.addProperty("z", Integer.parseInt(coords[2]));
			}
			catch (Exception ignored) {}
		}
		obj.addProperty("direction", (String) spawnDirectionBox.getSelectedItem());
		try { obj.addProperty("walkRange", Integer.parseInt(spawnRangeField.getText().trim())); } catch (Exception e) { obj.addProperty("walkRange", 3); }
		return gson.toJson(obj);
	}

	private void saveSpawnJson()
	{
		String json = generateSpawnJson();
		File target = new File(KRONOS_DATA_ROOT, "npcs/spawns/custom_spawns.json");
		try
		{
			target.getParentFile().mkdirs();
			Files.write(target.toPath(), (json + "\n").getBytes(StandardCharsets.UTF_8), java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
			JOptionPane.showMessageDialog(this, "Appended spawn directly to:\n" + target.getAbsolutePath(), "Spawn Appended", JOptionPane.INFORMATION_MESSAGE);
		}
		catch (IOException ex)
		{
			copyToClipboard(json);
			JOptionPane.showMessageDialog(this, "Could not write file, JSON copied to clipboard instead:\n" + ex.getMessage());
		}
	}

	private void copyToClipboard(String text)
	{
		if (text == null || text.isEmpty()) return;
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
	}
}

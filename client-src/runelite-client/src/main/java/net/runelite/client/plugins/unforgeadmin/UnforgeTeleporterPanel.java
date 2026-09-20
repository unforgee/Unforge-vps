package net.runelite.client.plugins.unforgeadmin;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import net.runelite.api.Client;
import net.runelite.api.ScriptID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

final class UnforgeTeleporterPanel extends PluginPanel
{
	private static final List<Destination> DESTINATIONS = Arrays.asList(
		// General / Cities
		new Destination("Cities", "Al Kharid", 3299, 3197, 0, "Desert Gateway, Furnace & Tanner"),
		new Destination("Cities", "Ardougne", 2662, 3307, 0, "Market stalls, East Ardougne Bank"),
		new Destination("Cities", "Ape Atoll", 2805, 2794, 0, "Monkey Temple, Agility Course"),
		new Destination("Cities", "Barbarian Village", 3085, 3421, 0, "Fly Fishing & Helmet shop"),
		new Destination("Cities", "Burthorpe", 2899, 3544, 0, "Warriors' Guild & Games Room"),
		new Destination("Cities", "Canifis", 3495, 3490, 0, "Morytania entrance, Slayer Tower access"),
		new Destination("Cities", "Catherby", 2804, 3435, 0, "Ocean Fishing, Farm patch & Ranges"),
		new Destination("Cities", "Crash Island", 2894, 2728, 0, "Glider access & remote beach"),
		new Destination("Cities", "Draynor Village", 3103, 3249, 0, "Willow trees, Wise Old Man"),
		new Destination("Cities", "Falador", 2965, 3379, 0, "White Knights' Castle, Mining Guild"),
		new Destination("Cities", "Karamja", 2914, 3151, 0, "Brimhaven docks & Musa Point"),
		new Destination("Cities", "Lumbridge", 3225, 3219, 0, "Lumbridge Castle Courtyard"),
		new Destination("Cities", "Lunar Isle", 2152, 3856, 0, "Astral altar & Moonclan crafters"),
		new Destination("Cities", "Miscellania", 2564, 3847, 0, "Kingdom management & Teak trees"),
		new Destination("Cities", "Port Sarim", 3027, 3241, 0, "Charter ships, fishing store"),
		new Destination("Cities", "Rellekka", 2631, 3676, 0, "Fremennik town & rock crabs"),
		new Destination("Cities", "Seers' Village", 2710, 3485, 0, "Maple trees, bank rooftop run"),
		new Destination("Cities", "Taverley", 2898, 3454, 0, "Druid sanctuary & Taverley Dungeon"),
		new Destination("Cities", "TzHaar", 2441, 5170, 0, "Fight Caves, Tokkul exchange, Inferno"),
		new Destination("Cities", "Varrock", 3214, 3424, 0, "Varrock Square & Grand Exchange path"),
		new Destination("Cities", "Grand Exchange", 3164, 3487, 0, "Trading Hub & Clerk counters"),
		new Destination("Cities", "Yanille", 2605, 3094, 0, "Wizards' Guild & Sandpit"),
		new Destination("Cities", "Prifddinas", 3264, 6065, 0, "Elven Capital & Zalcano entrance"),

		// Guilds
		new Destination("Guilds", "Cooking Guild", 3143, 3442, 0, "Chefs' range & water pump"),
		new Destination("Guilds", "Crafting Guild", 2933, 3291, 0, "Clay rocks, potter's wheel & kiln"),
		new Destination("Guilds", "Fishing Guild", 2611, 3390, 0, "Deep sea docks, Harpoon & Lobster"),
		new Destination("Guilds", "Mining Guild", 3022, 3337, 0, "Coal, Mithril, Adamantite & Amethyst"),
		new Destination("Guilds", "Woodcutting Guild", 1661, 3505, 0, "Hosidius Redwood & Magic trees"),
		new Destination("Guilds", "Warriors' Guild", 2843, 3543, 0, "Cyclopes, Defender room & armour animator"),

		// Skilling / Agility
		new Destination("Agility", "Gnome Stronghold Course", 2471, 3437, 0, "Lvl 1 - Tree branch & net"),
		new Destination("Agility", "Draynor Rooftop Course", 3105, 3272, 0, "Lvl 10 - Village roof climb"),
		new Destination("Agility", "Al Kharid Rooftop Course", 3281, 3197, 0, "Lvl 20 - Market rooftop circuit"),
		new Destination("Agility", "Varrock Rooftop Course", 3224, 3415, 0, "Lvl 30 - Square rooftop route"),
		new Destination("Agility", "Canifis Rooftop Course", 3505, 3486, 0, "Lvl 40 - Swamp city rooftop loop"),
		new Destination("Agility", "Falador Rooftop Course", 3037, 3339, 0, "Lvl 50 - Castle wall roofs"),
		new Destination("Agility", "Seers' Rooftop Course", 2728, 3485, 0, "Lvl 60 - Fast Marks of Grace"),
		new Destination("Agility", "Pollnivneach Rooftop", 3351, 2961, 0, "Lvl 70 - Desert rooftop"),
		new Destination("Agility", "Rellekka Rooftop Course", 2629, 3677, 0, "Lvl 80 - Frozen wooden roofs"),
		new Destination("Agility", "Ardougne Rooftop Course", 2673, 3296, 0, "Lvl 90 - Master rooftop circuit"),

		// Skilling / Others
		new Destination("Skilling", "Flax Field", 2741, 3442, 0, "Seers flax & spinning wheel"),
		new Destination("Skilling", "Aerial Fishing", 1368, 3633, 0, "Lake Molch bird fishing"),
		new Destination("Skilling", "Barbarian Fishing", 2503, 3488, 0, "Otto's Grotto leaping trout/salmon"),
		new Destination("Skilling", "Catherby Shores", 2809, 3435, 0, "Lobster, swordfish & shark fishing"),
		new Destination("Skilling", "Piscatoris Colony", 2341, 3666, 0, "Monkfish hotspots"),
		new Destination("Skilling", "Wilderness Chinchompas", 3145, 3771, 0, "Black chins hunting hill (32 Wildy)"),
		new Destination("Skilling", "Feldip Hills Chins", 2603, 2932, 0, "Red chinchompa box traps"),
		new Destination("Skilling", "Dwarven Mine", 3043, 9817, 0, "Underground mining & carts"),
		new Destination("Skilling", "Dense Essence Mine", 1769, 3849, 0, "Arceuus runecrafting quarry"),
		new Destination("Skilling", "Rogues' Den", 3046, 4969, 1, "Rogue equipment maze & emerald tanner"),

		// Monsters / Beginner
		new Destination("Monsters", "Lumbridge Cows", 3246, 3274, 0, "Low level combat & raw hides"),
		new Destination("Monsters", "Rock Crabs", 2676, 3711, 0, "Rellekka beach AFK training"),
		new Destination("Monsters", "Sand Crabs", 1723, 3464, 0, "Hosidius coast AFK spots"),
		new Destination("Monsters", "Ammonite Crabs", 3704, 3880, 0, "Fossil Island 100 HP crabs"),
		new Destination("Monsters", "Experiments", 3574, 9928, 0, "Fenkenstrain creature dungeon"),
		new Destination("Monsters", "Edgeville Dungeon", 3096, 3468, 0, "Chaos druids, skeletons, hill giants"),
		new Destination("Monsters", "Stronghold of Security", 3081, 3421, 0, "Flesh crawlers, minotaurs, ankou"),

		// Monsters / Advanced & Slayer
		new Destination("Slayer", "Slayer Tower", 3428, 3534, 0, "Bloodvelds, Gargoyles, Nechryael, Abyssal demons"),
		new Destination("Slayer", "Catacombs of Kourend", 1665, 10049, 0, "Skotizo totem pieces & ancient shards"),
		new Destination("Slayer", "Karuulm Dungeon", 1311, 10188, 0, "Wyrms, Drakes, Hydras & Alchemical Hydra"),
		new Destination("Slayer", "Fremennik Slayer Cave", 2805, 10001, 0, "Kurasks, Cave horrors, Turoths, Basilisks"),
		new Destination("Slayer", "Chasm of Fire", 1435, 3671, 0, "Greater demons & Black demons"),
		new Destination("Slayer", "Kraken Cove", 2278, 3609, 0, "Cave kraken & Whirlpool entrance"),
		new Destination("Slayer", "Smoke Devil Dungeon", 2412, 3060, 0, "Thermonuclear smoke devil & devils"),
		new Destination("Slayer", "Taverley Dungeon", 2884, 9799, 0, "Blue/Black dragons, Hellhounds, Cerberus"),
		new Destination("Slayer", "Brimhaven Dungeon", 2710, 9564, 0, "Metal dragons, Fire giants & Vines"),

		// Bosses
		new Destination("Bosses", "God Wars Dungeon", 2916, 3746, 0, "General Graardor, Kree, K'ril, Zilyana"),
		new Destination("Bosses", "Corporeal Beast", 2966, 4382, 2, "Sigils, Spirit shields & Elixir"),
		new Destination("Bosses", "Zulrah Shrine", 2200, 3056, 0, "Toxic blowpipe, Serpentine helm, Fang"),
		new Destination("Bosses", "Vorkath", 2272, 4050, 0, "Ungael dragon lair & Skeletal visage"),
		new Destination("Bosses", "Cerberus Lair", 1310, 1252, 0, "Hellhound boss & Primordial/Pegasian crystals"),
		new Destination("Bosses", "Abyssal Sire", 3038, 4771, 0, "Respiratory systems & Abyssal bludgeon"),
		new Destination("Bosses", "Kalphite Queen", 3226, 3108, 0, "Kalphite lair & Dragon 2h/chain"),
		new Destination("Bosses", "Dagannoth Kings", 1912, 4367, 0, "Rex, Prime, Supreme in Waterbirth depths"),
		new Destination("Bosses", "Demonic Gorillas", 2026, 5610, 0, "Crash site cavern & Zenyte shards"),
		new Destination("Bosses", "Barrows", 3565, 3314, 0, "Six Barrows brothers & Crypts chest"),

		// Raids & Minigames
		new Destination("Raids", "Chambers of Xeric (CoX)", 1245, 3557, 0, "Great Olm & Mount Quidamortem"),
		new Destination("Raids", "Theatre of Blood (ToB)", 3667, 3219, 0, "Verzik Vitur & Meiyerditch lab"),
		new Destination("Raids", "Tombs of Amascut (ToA)", 3356, 9150, 0, "Kharidian desert necropolis"),
		new Destination("Raids", "Fight Caves / Inferno", 2439, 5169, 0, "TzHaar-Mej-Jal fire capes"),
		new Destination("Raids", "Pest Control", 2658, 2660, 0, "Void Knight outpost & landers"),

		// Wilderness
		new Destination("Wilderness", "Edgeville Ditch (Lvl 1)", 3087, 3520, 0, "Wilderness wall leap"),
		new Destination("Wilderness", "Mage Arena (Lvl 52)", 3105, 3956, 0, "God spells & Imbued God Capes"),
		new Destination("Wilderness", "Revenant Caves", 3127, 3832, 0, "Lvl 40 - Revenants & Wilderness weapons"),
		new Destination("Wilderness", "Chaos Altar (Lvl 38)", 2948, 3821, 0, "700% prayer bone altar"),
		new Destination("Wilderness", "Chaos Fanatic (Lvl 42)", 2981, 3848, 0, "Odium/Malediction shard 1"),
		new Destination("Wilderness", "Crazy Archaeologist (Lvl 23)", 2977, 3700, 0, "Forgotten cemetery ruins"),
		new Destination("Wilderness", "Scorpia Cave (Lvl 54)", 3233, 3945, 0, "Scorpia off-hand shards"),
		new Destination("Wilderness", "Callisto / Artio", 3325, 3845, 0, "Bear boss & Voidwaker hilt"),
		new Destination("Wilderness", "Venenatis / Spindel", 3318, 3747, 0, "Spider boss & Voidwaker gem"),
		new Destination("Wilderness", "Vet'ion / Calvar'ion", 3184, 3788, 0, "Skeleton boss & Voidwaker blade"),
		new Destination("Wilderness", "Lava Dragon Isle", 3202, 3858, 0, "Lvl 43 - Lava dragon bones & scales")
	);

	private final Client client;
	private final ClientThread clientThread;

	private final JTextField searchField = new JTextField();
	private final DefaultListModel<String> categoryModel = new DefaultListModel<>();
	private final JList<String> categoryList = new JList<>(categoryModel);
	private final DefaultListModel<Destination> listModel = new DefaultListModel<>();
	private final JList<Destination> destinationList = new JList<>(listModel);

	private final JLabel selectedNameLabel = new JLabel("No Location Selected");
	private final JLabel selectedDescLabel = new JLabel("Select a destination to view details and teleport.");
	private final JLabel coordsLabel = new JLabel("X: -  Y: -  Plane: -");
	private final JLabel statusLabel = new JLabel("Ready");
	private final JButton teleportButton = new JButton("⚡ TELEPORT NOW");

	private List<Destination> allFiltered = new ArrayList<>();

	@Inject
	UnforgeTeleporterPanel(Client client, ClientThread clientThread)
	{
		this.client = client;
		this.clientThread = clientThread;

		setLayout(new BorderLayout(0, 8));
		setBorder(new EmptyBorder(10, 10, 10, 10));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		add(buildHeaderPanel(), BorderLayout.NORTH);
		add(buildCategoryPanel(), BorderLayout.WEST);
		add(buildListPanel(), BorderLayout.CENTER);
		add(buildActionPanel(), BorderLayout.SOUTH);

		refreshCategories();
		filterDestinations();
	}

	private JPanel buildHeaderPanel()
	{
		JPanel header = new JPanel();
		header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
		header.setOpaque(false);

		// Compact title bar, matching the old game interface rather than a modern card header.
		JPanel titleCard = new JPanel(new BorderLayout());
		titleCard.setBackground(new Color(61, 54, 43));
		titleCard.setBorder(new CompoundBorder(
			new LineBorder(new Color(117, 104, 77), 1),
			new EmptyBorder(5, 8, 5, 8)
		));

		JLabel title = new JLabel("Teleport destinations", JLabel.CENTER);
		title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
		title.setForeground(new Color(232, 153, 36));
		titleCard.add(title, BorderLayout.NORTH);

		header.add(titleCard);
		header.add(buildSpacer(4));

		// Search bar
		JPanel searchRow = new JPanel(new BorderLayout(5, 0));
		searchRow.setOpaque(false);
		JLabel searchIcon = new JLabel("Find:");
		searchIcon.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
		searchIcon.setForeground(new Color(224, 165, 53));
		searchRow.add(searchIcon, BorderLayout.WEST);

		searchField.setToolTipText("Filter by name, description or category...");
		searchField.addKeyListener(new KeyAdapter()
		{
			@Override
			public void keyReleased(KeyEvent e)
			{
				if (e.getKeyCode() == KeyEvent.VK_ENTER)
				{
					teleportSelected();
				}
				else
				{
					filterDestinations();
				}
			}
		});
		searchRow.add(searchField, BorderLayout.CENTER);

		JButton clearBtn = new JButton("✕");
		clearBtn.setMargin(new java.awt.Insets(1, 4, 1, 4));
		clearBtn.addActionListener(e -> {
			searchField.setText("");
			filterDestinations();
		});
		searchRow.add(clearBtn, BorderLayout.EAST);

		header.add(searchRow);
		return header;
	}

	private JPanel buildCategoryPanel()
	{
		JPanel sidebar = new JPanel(new BorderLayout());
		sidebar.setBackground(new Color(57, 52, 42));
		sidebar.setBorder(new CompoundBorder(
			new LineBorder(new Color(112, 98, 71), 1),
			new EmptyBorder(3, 3, 3, 3)
		));
		sidebar.setPreferredSize(new Dimension(93, 0));

		categoryList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		categoryList.setBackground(new Color(57, 52, 42));
		categoryList.setFixedCellHeight(27);
		categoryList.setCellRenderer(new CategoryRenderer());
		categoryList.addListSelectionListener(e -> {
			if (!e.getValueIsAdjusting())
			{
				filterDestinations();
			}
		});
		sidebar.add(categoryList, BorderLayout.CENTER);
		return sidebar;
	}

	private JPanel buildListPanel()
	{
		JPanel panel = new JPanel(new BorderLayout());
		panel.setOpaque(false);

		destinationList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		destinationList.setFixedCellHeight(40);
		destinationList.setBackground(new Color(61, 55, 45));
		destinationList.setBorder(new EmptyBorder(2, 2, 2, 2));
		destinationList.setCellRenderer(new DestinationRenderer());
		destinationList.addListSelectionListener(e -> updateDetails());

		// Double-click to instantly teleport
		destinationList.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (e.getClickCount() == 2)
				{
					teleportSelected();
				}
			}
		});

		JScrollPane scroll = new JScrollPane(destinationList);
		scroll.setBorder(new LineBorder(new Color(112, 98, 71), 1));
		scroll.getViewport().setBackground(new Color(61, 55, 45));
		panel.add(scroll, BorderLayout.CENTER);

		return panel;
	}

	private JPanel buildActionPanel()
	{
		JPanel actionPanel = new JPanel();
		actionPanel.setLayout(new BoxLayout(actionPanel, BoxLayout.Y_AXIS));
		actionPanel.setBackground(new Color(49, 37, 27));
		actionPanel.setBorder(new CompoundBorder(
			new LineBorder(new Color(164, 126, 62), 1),
			new EmptyBorder(8, 8, 8, 8)
		));

		selectedNameLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
		selectedNameLabel.setForeground(new Color(255, 210, 112));
		actionPanel.add(selectedNameLabel);

		selectedDescLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
		selectedDescLabel.setForeground(new Color(211, 190, 157));
		actionPanel.add(selectedDescLabel);

		coordsLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 10));
		coordsLabel.setForeground(new Color(181, 221, 133));
		actionPanel.add(coordsLabel);

		actionPanel.add(buildSpacer(6));

		// Action buttons
		JPanel btnRow = new JPanel(new GridLayout(1, 2, 6, 0));
		btnRow.setOpaque(false);

		teleportButton.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
		teleportButton.setBackground(new Color(119, 81, 35));
		teleportButton.setForeground(new Color(255, 230, 164));
		teleportButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
		teleportButton.addActionListener(e -> teleportSelected());
		btnRow.add(teleportButton);

		JButton copyBtn = new JButton("Copy Command");
		copyBtn.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
		copyBtn.addActionListener(e -> copyCommandToClipboard());
		btnRow.add(copyBtn);

		actionPanel.add(btnRow);
		actionPanel.add(buildSpacer(4));

		statusLabel.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 10));
		statusLabel.setForeground(Color.GREEN);
		actionPanel.add(statusLabel);

		return actionPanel;
	}

	private Component buildSpacer(int height)
	{
		JPanel p = new JPanel();
		p.setPreferredSize(new Dimension(1, height));
		p.setOpaque(false);
		return p;
	}

	private void refreshCategories()
	{
		categoryModel.clear();
		categoryModel.addElement("All destinations");
		categoryModel.addElement("Cities");
		categoryModel.addElement("Training");
		categoryModel.addElement("Slayer");
		categoryModel.addElement("Bosses");
		categoryModel.addElement("Skilling");
		categoryModel.addElement("Minigames");
		categoryModel.addElement("Wilderness");
		categoryList.setSelectedIndex(0);
	}

	private void filterDestinations()
	{
		String query = searchField.getText().trim().toLowerCase();
		String cat = categoryList.getSelectedValue();
		boolean allCats = cat == null || cat.equals("All destinations");

		allFiltered = DESTINATIONS.stream()
			.filter(d -> (allCats || categoryMatches(d, cat)) &&
				(query.isEmpty() || d.name.toLowerCase().contains(query) || d.desc.toLowerCase().contains(query) || d.category.toLowerCase().contains(query)))
			.collect(Collectors.toList());

		listModel.clear();
		for (Destination d : allFiltered)
		{
			listModel.addElement(d);
		}

		if (!allFiltered.isEmpty())
		{
			destinationList.setSelectedIndex(0);
		}
		else
		{
			selectedNameLabel.setText("No Results Found");
			selectedDescLabel.setText("Try a different search query or category.");
			coordsLabel.setText("");
			teleportButton.setEnabled(false);
		}
	}

	private static boolean categoryMatches(Destination destination, String category)
	{
		if (category.equalsIgnoreCase("Training"))
		{
			return destination.category.equals("Agility")
				|| destination.category.equals("Guilds")
				|| destination.category.equals("Monsters");
		}
		if (category.equalsIgnoreCase("Minigames"))
		{
			return destination.category.equals("Raids");
		}
		return destination.category.equalsIgnoreCase(category);
	}

	private void updateDetails()
	{
		Destination sel = destinationList.getSelectedValue();
		if (sel == null)
		{
			return;
		}

		selectedNameLabel.setText(sel.name + " (" + sel.category + ")");
		selectedDescLabel.setText(sel.desc);
		coordsLabel.setText(String.format("X: %d | Y: %d | Plane: %d", sel.x, sel.y, sel.plane));
		teleportButton.setEnabled(true);
		statusLabel.setText("Double-click or press Enter to teleport.");
	}

	private void teleportSelected()
	{
		Destination sel = destinationList.getSelectedValue();
		if (sel == null)
		{
			statusLabel.setText("Select a destination first!");
			return;
		}

		int mapX = sel.x >> 6;
		int mapZ = sel.y >> 6;
		int localX = sel.x & 63;
		int localZ = sel.y & 63;

		// Format: ::tele level mx mz lx lz
		String command = String.format("tele %d %d %d %d %d", sel.plane, mapX, mapZ, localX, localZ);

		clientThread.invokeLater(() -> client.runScript(ScriptID.UNFORGE_CHEAT, command));
		statusLabel.setText("⚡ Sent: ::" + command);
	}

	private void copyCommandToClipboard()
	{
		Destination sel = destinationList.getSelectedValue();
		if (sel == null) return;
		int mapX = sel.x >> 6;
		int mapZ = sel.y >> 6;
		int localX = sel.x & 63;
		int localZ = sel.y & 63;
		String cmd = String.format("::tele %d %d %d %d %d", sel.plane, mapX, mapZ, localX, localZ);
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(cmd), null);
		statusLabel.setText("Copied command to clipboard!");
	}

	// ------------------------------------------------------------------------
	// Small, old-school renderers: the left navigation and the selectable destination rows.
	// ------------------------------------------------------------------------
	private static class CategoryRenderer extends DefaultListCellRenderer
	{
		@Override
		public Component getListCellRendererComponent(JList<?> list, Object value, int index,
			boolean isSelected, boolean cellHasFocus)
		{
			super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
			String label = String.valueOf(value);
			String icon = label.equals("All destinations") ? "◆" : label.equals("Bosses") ? "☠"
				: label.equals("Slayer") ? "⚔" : label.equals("Wilderness") ? "♠" : "✦";
			setText(icon + "  " + label);
			setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
			setForeground(isSelected ? new Color(255, 190, 61) : new Color(226, 207, 169));
			setBackground(isSelected ? new Color(91, 82, 66) : new Color(57, 52, 42));
			setBorder(new EmptyBorder(4, 5, 4, 3));
			return this;
		}
	}

	private static class DestinationRenderer extends JPanel implements ListCellRenderer<Destination>
	{
		private final JLabel emblem = new JLabel("✦", JLabel.CENTER);
		private final JLabel name = new JLabel();
		private final JLabel category = new JLabel();
		private final JLabel coordinates = new JLabel();
		private final JLabel favorite = new JLabel("☆", JLabel.CENTER);

		DestinationRenderer()
		{
			setLayout(new BorderLayout(5, 0));
			setBorder(new EmptyBorder(6, 6, 6, 6));
			setOpaque(true);

			emblem.setPreferredSize(new Dimension(30, 30));
			emblem.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 17));
			emblem.setOpaque(true);
			add(emblem, BorderLayout.WEST);

			JPanel copy = new JPanel();
			copy.setOpaque(false);
			copy.setLayout(new BoxLayout(copy, BoxLayout.Y_AXIS));
			name.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
			category.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 8));
			coordinates.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 8));
			copy.add(name);
			copy.add(category);
			copy.add(Box.createVerticalGlue());
			copy.add(coordinates);
			add(copy, BorderLayout.CENTER);
			favorite.setPreferredSize(new Dimension(24, 30));
			favorite.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 17));
			add(favorite, BorderLayout.EAST);
		}

		@Override
		public Component getListCellRendererComponent(JList<? extends Destination> list, Destination value,
			int index, boolean isSelected, boolean cellHasFocus)
		{
			Color accent = categoryColor(value.category);
			setBackground(isSelected ? new Color(91, 66, 32) : new Color(55, 42, 30));
			setBorder(new CompoundBorder(
				new LineBorder(isSelected ? new Color(255, 212, 105) : new Color(112, 82, 43), isSelected ? 2 : 1),
				new EmptyBorder(5, 5, 5, 5)
			));
			emblem.setBackground(isSelected ? accent.brighter() : accent);
			emblem.setForeground(new Color(38, 27, 18));
			name.setText(value.name);
			name.setForeground(isSelected ? Color.WHITE : new Color(244, 222, 177));
			category.setText(value.category.toUpperCase());
			category.setForeground(accent.brighter());
			coordinates.setText(value.x + ", " + value.y + "  P" + value.plane);
			coordinates.setForeground(isSelected ? new Color(220, 238, 176) : new Color(180, 160, 130));
			favorite.setForeground(isSelected ? new Color(255, 221, 130) : new Color(219, 213, 193));
			return this;
		}

		private static Color categoryColor(String category)
		{
			switch (category)
			{
				case "Bosses": return new Color(184, 67, 52);
				case "Wilderness": return new Color(126, 155, 61);
				case "Raids": return new Color(131, 82, 176);
				case "Slayer": return new Color(174, 75, 119);
				case "Skilling":
				case "Agility": return new Color(57, 145, 116);
				case "Guilds": return new Color(66, 126, 177);
				case "Monsters": return new Color(177, 111, 53);
				default: return new Color(170, 128, 54);
			}
		}
	}

	private static final class Destination
	{
		private final String category;
		private final String name;
		private final int x;
		private final int y;
		private final int plane;
		private final String desc;

		private Destination(String category, String name, int x, int y, int plane, String desc)
		{
			this.category = category;
			this.name = name;
			this.x = x;
			this.y = y;
			this.plane = plane;
			this.desc = desc;
		}
	}
}

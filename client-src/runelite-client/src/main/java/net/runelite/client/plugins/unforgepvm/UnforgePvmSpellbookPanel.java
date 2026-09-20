package net.runelite.client.plugins.unforgepvm;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import javax.inject.Inject;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ScriptID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.unforgecommon.PopoutWindow;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

@Slf4j
public class UnforgePvmSpellbookPanel extends PluginPanel
{
	private static final String TITLE = "PvM Spellbook";
	private static final int POPOUT_WIDTH = 460;
	private static final int POPOUT_HEIGHT = 720;

	private final Client client;
	private final ClientThread clientThread;
	private final PopoutWindow popout = new PopoutWindow();

	private JButton popoutButton;
	private JTextField searchInput;
	private JComboBox<String> categoryFilter;
	private JPanel spellListContainer;
	private JLabel statusLabel;
	private Runnable onDock;

	private final Map<String, ImageIcon> iconCache = new HashMap<>();

	static class SpellEntry
	{
		final String id;
		final String name;
		final String category;
		final int level;
		final int cooldownSec;
		final String iconName;
		final String description;

		SpellEntry(String id, String name, String category, int level, int cooldownSec, String iconName, String description)
		{
			this.id = id;
			this.name = name;
			this.category = category;
			this.level = level;
			this.cooldownSec = cooldownSec;
			this.iconName = iconName;
			this.description = description;
		}
	}

	private static final List<SpellEntry> SPELLS = Arrays.asList(
		// Healing & Recovery
		new SpellEntry("EmergencyMend", "Emergency Mend", "Healing & Recovery", 60, 15, "human_support_02.png", "Quick emergency self-heal restoring 12% max HP."),
		new SpellEntry("MendingLight", "Mending Light", "Healing & Recovery", 80, 30, "undead_support_09.png", "Substantial self-heal restoring 35% max HP."),
		new SpellEntry("SanctuaryPulse", "Sanctuary Pulse", "Healing & Recovery", 85, 45, "arcane_support_08.png", "Wave heal healing caster and up to 8 nearby allies for 20% max HP."),
		new SpellEntry("GuardiansGrace", "Guardian's Grace", "Healing & Recovery", 90, 90, "elf_support_05.png", "Restores 25% max HP and grants a 10% max HP absorption shield for 10s."),
		new SpellEntry("RadiantRenewal", "Radiant Renewal", "Healing & Recovery", 88, 60, "human_magic_03.png", "Surges holy energy, restoring 15% of your base prayer points."),
		new SpellEntry("NaturesGrace", "Nature's Grace", "Healing & Recovery", 81, 40, "elf_defense_03.png", "Soothes wounds with nature's pulse, restoring 20% max HP."),
		new SpellEntry("SpiritBond", "Spirit Bond", "Healing & Recovery", 86, 50, "darkelf_support_03.png", "Links vitality with nearby allies, healing them for 15% max HP."),

		// Combat & Offense
		new SpellEntry("BattleHymn", "Battle Hymn", "Combat & Offense", 92, 60, "human_combat_01.png", "Buffs PvM damage (+15% self, +8% party) and accuracy (+10%) for 30s."),
		new SpellEntry("ArcaneSurge", "Arcane Surge", "Combat & Offense", 94, 75, "arcane_magic_06.png", "Unleashes raw arcane energy, increasing magical spell damage by 20%."),
		new SpellEntry("Stonebreaker", "Stonebreaker", "Combat & Offense", 78, 30, "dwarf_combat_01.png", "Crushing blow that shatters enemy armor for 15 seconds."),
		new SpellEntry("CelestialArrow", "Celestial Arrow", "Combat & Offense", 83, 25, "elf_combat_10.png", "Long-range astral shot guaranteeing high accuracy against bosses."),
		new SpellEntry("BloodFrenzy", "Blood Frenzy", "Combat & Offense", 95, 80, "orc_combat_02.png", "Channels pure orcish fury, boosting melee attack speed and hit chance."),
		new SpellEntry("BloodSiphon", "Blood Siphon", "Combat & Offense", 90, 50, "undead_magic_01.png", "Drains monster vitality, restoring hitpoints equal to damage dealt."),

		// Defence & Wards
		new SpellEntry("IronSanctuary", "Iron Sanctuary", "Defence & Wards", 92, 60, "human_defense_07.png", "Reduces incoming PvM damage (-12% self, -8% party) and boosts Def/Mage/Range."),
		new SpellEntry("ArcaneBarrier", "Arcane Barrier", "Defence & Wards", 85, 55, "arcane_defense_01.png", "Conjures a shimmering barrier absorbing magic and ranged projectiles."),
		new SpellEntry("ShadowVeil", "Shadow Veil", "Defence & Wards", 86, 45, "darkelf_defense_04.png", "Envelops caster in mist, granting a 25% chance to evade monster hits."),
		new SpellEntry("DemonicWard", "Demonic Ward", "Defence & Wards", 89, 65, "demon_defense_03.png", "Wreathes in spiked demonic armor, reflecting incoming melee damage back."),
		new SpellEntry("DwarvenBulwark", "Dwarven Bulwark", "Defence & Wards", 90, 60, "dwarf_defense_10.png", "Hardens skin into solid stone, negating monster stuns and knockbacks."),
		new SpellEntry("Juggernaut", "Juggernaut", "Defence & Wards", 84, 50, "orc_defense_08.png", "Unstoppable momentum granting massive defence and immunity to slowing."),
		new SpellEntry("BoneArmor", "Bone Armor", "Defence & Wards", 82, 45, "undead_defense_01.png", "Surrounds caster in rotating bone shards that absorb physical strikes.")
	);

	@Inject
	UnforgePvmSpellbookPanel(Client client, ClientThread clientThread)
	{
		this.client = client;
		this.clientThread = clientThread;
		buildUi();
	}

	void init(Runnable onDock)
	{
		this.onDock = onDock;
	}

	void onShutDown()
	{
		popout.dock(this, null);
	}

	private void buildUi()
	{
		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel headerPanel = new JPanel();
		headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.Y_AXIS));
		headerPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		headerPanel.setBorder(new EmptyBorder(10, 10, 8, 10));

		// Top title row with pop-out button
		JPanel titleRow = new JPanel(new BorderLayout());
		titleRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		JLabel titleLabel = new JLabel(TITLE);
		titleLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
		titleLabel.setForeground(new Color(255, 175, 50));
		titleRow.add(titleLabel, BorderLayout.WEST);

		popoutButton = new JButton("⤢");
		popoutButton.setToolTipText("Pop out into floating window");
		popoutButton.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
		popoutButton.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		popoutButton.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		popoutButton.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
		popoutButton.setFocusable(false);
		popoutButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		popoutButton.addActionListener(e -> togglePopout());
		titleRow.add(popoutButton, BorderLayout.EAST);

		headerPanel.add(titleRow);
		headerPanel.add(Box.createVerticalStrut(8));

		// Quick action buttons (HUD, Codex, Clear)
		JPanel actionsRow = new JPanel(new GridLayout(1, 3, 5, 0));
		actionsRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		JButton hudBtn = createSmallButton("HUD", "Toggle in-game HUD overlay (::support)", e -> runCheat("support"));
		JButton codexBtn = createSmallButton("PvM Book", "Switch active spellbook to PvM (::codex pvm)", e -> runCheat("codex pvm"));
		JButton clearBtn = createSmallButton("Reset CD", "Reset support cooldowns (::supportclear)", e -> runCheat("supportclear"));

		actionsRow.add(hudBtn);
		actionsRow.add(codexBtn);
		actionsRow.add(clearBtn);
		headerPanel.add(actionsRow);
		headerPanel.add(Box.createVerticalStrut(8));

		// Category filter combo box
		categoryFilter = new JComboBox<>();
		categoryFilter.setModel(new DefaultComboBoxModel<>(new String[]{
			"All Categories",
			"Healing & Recovery",
			"Combat & Offense",
			"Defence & Wards"
		}));
		categoryFilter.setBackground(ColorScheme.DARK_GRAY_COLOR);
		categoryFilter.setForeground(Color.WHITE);
		categoryFilter.setFocusable(false);
		categoryFilter.addActionListener(e -> filterSpells());
		headerPanel.add(categoryFilter);
		headerPanel.add(Box.createVerticalStrut(6));

		// Search input
		searchInput = new JTextField();
		searchInput.setToolTipText("Search spells by name or effect...");
		searchInput.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		searchInput.setForeground(Color.WHITE);
		searchInput.setCaretColor(Color.WHITE);
		searchInput.setBorder(new CompoundBorder(new LineBorder(ColorScheme.MEDIUM_GRAY_COLOR, 1), new EmptyBorder(4, 6, 4, 6)));
		searchInput.addKeyListener(new KeyAdapter()
		{
			@Override
			public void keyReleased(KeyEvent e)
			{
				filterSpells();
			}
		});
		headerPanel.add(searchInput);
		headerPanel.add(Box.createVerticalStrut(4));

		// Status readout
		statusLabel = new JLabel("Click any spell card to cast.");
		statusLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
		statusLabel.setForeground(new Color(170, 170, 170));
		headerPanel.add(statusLabel);

		add(headerPanel, BorderLayout.NORTH);

		// Scrollable spell list
		spellListContainer = new JPanel();
		spellListContainer.setLayout(new BoxLayout(spellListContainer, BoxLayout.Y_AXIS));
		spellListContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);
		spellListContainer.setBorder(new EmptyBorder(6, 6, 6, 6));

		JScrollPane scrollPane = new JScrollPane(spellListContainer);
		scrollPane.setBackground(ColorScheme.DARK_GRAY_COLOR);
		scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scrollPane.getVerticalScrollBar().setUnitIncrement(16);
		scrollPane.setBorder(null);

		add(scrollPane, BorderLayout.CENTER);

		filterSpells();
	}

	private JButton createSmallButton(String text, String tooltip, java.awt.event.ActionListener listener)
	{
		JButton btn = new JButton(text);
		btn.setToolTipText(tooltip);
		btn.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
		btn.setBackground(ColorScheme.DARK_GRAY_COLOR);
		btn.setForeground(Color.WHITE);
		btn.setBorder(BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR));
		btn.setFocusable(false);
		btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		btn.addActionListener(listener);
		return btn;
	}

	private void togglePopout()
	{
		if (popout.isOpen())
		{
			popout.dock(this, onDock);
			popoutButton.setText("⤢");
			popoutButton.setToolTipText("Pop out into floating window");
		}
		else
		{
			popout.toggle(this, TITLE, POPOUT_WIDTH, POPOUT_HEIGHT, onDock);
			popoutButton.setText("⤡");
			popoutButton.setToolTipText("Dock back to sidebar");
		}
	}

	private void filterSpells()
	{
		String query = searchInput.getText().trim().toLowerCase();
		String selectedCat = (String) categoryFilter.getSelectedItem();
		boolean allCats = selectedCat == null || selectedCat.equals("All Categories");

		spellListContainer.removeAll();

		List<SpellEntry> filtered = SPELLS.stream()
			.filter(s -> allCats || s.category.equalsIgnoreCase(selectedCat))
			.filter(s -> query.isEmpty() || s.name.toLowerCase().contains(query) || s.description.toLowerCase().contains(query))
			.collect(Collectors.toList());

		for (SpellEntry spell : filtered)
		{
			spellListContainer.add(createSpellCard(spell));
			spellListContainer.add(Box.createVerticalStrut(6));
		}

		if (filtered.isEmpty())
		{
			JLabel emptyLabel = new JLabel("No spells found matching filter.");
			emptyLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			emptyLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
			spellListContainer.add(Box.createVerticalStrut(20));
			spellListContainer.add(emptyLabel);
		}

		spellListContainer.revalidate();
		spellListContainer.repaint();
	}

	private JPanel createSpellCard(SpellEntry spell)
	{
		JPanel card = new JPanel(new BorderLayout(8, 0));
		card.setBackground(new Color(45, 42, 38));
		card.setBorder(new CompoundBorder(
			new LineBorder(new Color(70, 65, 58), 1),
			new EmptyBorder(6, 6, 6, 6)
		));
		card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 74));
		card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

		// Icon thumbnail on the left
		JLabel iconLabel = new JLabel();
		ImageIcon icon = getOrLoadIcon(spell.iconName);
		if (icon != null)
		{
			iconLabel.setIcon(icon);
		}
		iconLabel.setPreferredSize(new Dimension(48, 48));
		card.add(iconLabel, BorderLayout.WEST);

		// Middle info (Name, Level, CD, Description)
		JPanel infoPanel = new JPanel();
		infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
		infoPanel.setOpaque(false);

		JPanel topRow = new JPanel(new BorderLayout());
		topRow.setOpaque(false);

		JLabel nameLabel = new JLabel(spell.name);
		nameLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
		nameLabel.setForeground(new Color(255, 175, 50));
		topRow.add(nameLabel, BorderLayout.WEST);

		JLabel badgeLabel = new JLabel(String.format("Lvl %d | %ds", spell.level, spell.cooldownSec));
		badgeLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
		badgeLabel.setForeground(new Color(150, 210, 255));
		topRow.add(badgeLabel, BorderLayout.EAST);

		infoPanel.add(topRow);
		infoPanel.add(Box.createVerticalStrut(2));

		JLabel descLabel = new JLabel("<html><body style='width: 170px; color: #d0cbc2; font-size: 9px;'>" + spell.description + "</body></html>");
		infoPanel.add(descLabel);

		card.add(infoPanel, BorderLayout.CENTER);

		// Cast button on the right
		JButton castBtn = new JButton("⚡");
		castBtn.setToolTipText("Cast " + spell.name);
		castBtn.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
		castBtn.setBackground(new Color(60, 52, 42));
		castBtn.setForeground(new Color(255, 200, 80));
		castBtn.setBorder(new LineBorder(new Color(110, 95, 75)));
		castBtn.setFocusable(false);
		castBtn.setPreferredSize(new Dimension(32, 48));
		castBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		castBtn.addActionListener(e -> castSpell(spell));

		card.add(castBtn, BorderLayout.EAST);

		// Clicking anywhere on card casts the spell
		card.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				castSpell(spell);
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				card.setBackground(new Color(58, 54, 48));
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				card.setBackground(new Color(45, 42, 38));
			}
		});

		return card;
	}

	private ImageIcon getOrLoadIcon(String fileName)
	{
		if (iconCache.containsKey(fileName))
		{
			return iconCache.get(fileName);
		}

		String path = "/net/runelite/client/plugins/unforgepvm/icons/" + fileName;
		try (InputStream in = getClass().getResourceAsStream(path))
		{
			if (in != null)
			{
				BufferedImage raw = ImageIO.read(in);
				if (raw != null)
				{
					Image scaled = raw.getScaledInstance(48, 48, Image.SCALE_SMOOTH);
					ImageIcon icon = new ImageIcon(scaled);
					iconCache.put(fileName, icon);
					return icon;
				}
			}
		}
		catch (Exception e)
		{
			log.warn("Could not load icon {}: {}", fileName, e.getMessage());
		}

		iconCache.put(fileName, null);
		return null;
	}

	private void castSpell(SpellEntry spell)
	{
		runCheat("cast " + spell.id);
		statusLabel.setText("⚡ Cast: " + spell.name);
	}

	private void runCheat(String command)
	{
		clientThread.invokeLater(() -> client.runScript(ScriptID.UNFORGE_CHEAT, command));
	}
}

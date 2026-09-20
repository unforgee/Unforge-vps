package net.runelite.client.plugins.unforgecompanion;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JToggleButton;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

/**
 * Right-sidebar companion management panel fed by the `UNFORGE_COMPANION`
 * snapshot. Shows the squad list, per-companion HP / combat mode / ability
 * cooldowns, and sends only `playerCommand` cheats - the server owns all state.
 */
public class UnforgeCompanionPanel extends PluginPanel
{
	interface CommandSender
	{
		void send(String command);
	}

	private static final Color NAME_COLOR = new Color(0xff, 0x98, 0x1f);
	private static final Color SUBTLE = new Color(0x9a, 0x8b, 0x76);
	private static final Color GOOD = new Color(0x00, 0xb3, 0x3c);
	private static final Color WARN = new Color(0xff, 0xb8, 0x4d);
	private static final Color BAD = new Color(0xff, 0x33, 0x33);

	private final CommandSender sender;

	private final JComboBox<UnforgeCompanionProtocol.Pet> selector = new JComboBox<>();
	private final JLabel nameLabel = new JLabel();
	private final JLabel infoLabel = new JLabel();
	private final JLabel stateLabel = new JLabel();
	private final JProgressBar hpBar = new JProgressBar();
	private final JLabel abilityHeader = new JLabel("Abilities");
	private final JPanel abilityList = new JPanel();
	private final JLabel statusLabel = new JLabel();

	private final JToggleButton defButton = new JToggleButton("Defensive");
	private final JToggleButton aggButton = new JToggleButton("Aggressive");
	private final JToggleButton pasButton = new JToggleButton("Passive");
	private final JCheckBox lootBox = new JCheckBox("Auto-Loot");
	private final JCheckBox healBox = new JCheckBox("Emergency Heal");

	private final List<UnforgeCompanionProtocol.Pet> stagingPets = new ArrayList<>();
	private final Map<Long, List<UnforgeCompanionProtocol.Ability>> stagingAbilities =
		new LinkedHashMap<>();
	private boolean stagingAutoLoot;
	private boolean stagingEmergencyHeal;
	private long stagingSelectedId = -1;

	private final List<UnforgeCompanionProtocol.Pet> pets = new ArrayList<>();
	private final Map<Long, List<UnforgeCompanionProtocol.Ability>> abilities =
		new LinkedHashMap<>();
	private boolean autoLoot;
	private boolean emergencyHeal;
	private long selectedId = -1;

	/** True while the sidebar tab is open - gates the plugin's status polling. */
	private volatile boolean panelActive;

	/** True while the UI is being rebuilt from a snapshot - suppresses listeners. */
	private boolean rebuilding;

	public UnforgeCompanionPanel(CommandSender sender)
	{
		this.sender = sender;

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

		nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 14f));
		nameLabel.setForeground(NAME_COLOR);
		infoLabel.setForeground(SUBTLE);
		stateLabel.setForeground(SUBTLE);
		statusLabel.setForeground(SUBTLE);
		statusLabel.setBorder(new EmptyBorder(4, 2, 4, 2));
		abilityHeader.setForeground(WARN);
		abilityHeader.setBorder(new EmptyBorder(6, 0, 2, 0));

		selector.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		selector.addActionListener(e -> onSelect());

		hpBar.setStringPainted(true);
		hpBar.setForeground(GOOD);
		hpBar.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		abilityList.setLayout(new BoxLayout(abilityList, BoxLayout.Y_AXIS));
		abilityList.setBackground(ColorScheme.DARK_GRAY_COLOR);

		add(selector);
		add(Box.createVerticalStrut(4));
		add(nameLabel);
		add(infoLabel);
		add(stateLabel);
		add(hpBar);
		add(Box.createVerticalStrut(6));
		add(modeRow());
		add(togglesRow());
		add(actionsPanel());
		add(abilityHeader);
		add(abilityList);
		add(statusLabel);

		render();
	}

	private JPanel modeRow()
	{
		JPanel row = new JPanel(new GridLayout(1, 3, 4, 0));
		row.setBorder(BorderFactory.createTitledBorder(
			BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR), "Combat mode"));
		ButtonGroup group = new ButtonGroup();
		group.add(defButton);
		group.add(aggButton);
		group.add(pasButton);
		defButton.addActionListener(e -> onMode("defensive"));
		aggButton.addActionListener(e -> onMode("aggressive"));
		pasButton.addActionListener(e -> onMode("passive"));
		row.add(defButton);
		row.add(aggButton);
		row.add(pasButton);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 54));
		return row;
	}

	private JPanel togglesRow()
	{
		JPanel row = new JPanel(new GridLayout(1, 2, 4, 0));
		row.setBackground(ColorScheme.DARK_GRAY_COLOR);
		lootBox.addActionListener(e ->
		{
			if (!rebuilding)
			{
				send("petloot " + (lootBox.isSelected() ? "on" : "off"));
			}
		});
		healBox.addActionListener(e ->
		{
			if (!rebuilding)
			{
				send("petheal " + (healBox.isSelected() ? "on" : "off"));
			}
		});
		row.add(lootBox);
		row.add(healBox);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		return row;
	}

	private JPanel actionsPanel()
	{
		JPanel panel = new JPanel(new GridLayout(0, 2, 4, 4));
		panel.setBorder(new EmptyBorder(6, 0, 0, 0));
		panel.add(action("Hub", "pet"));
		panel.add(action("Gear", "petgear"));
		panel.add(action("Talents", "pettalents"));
		panel.add(action("Pack", "petpack"));
		panel.add(action("Style", "petspellbook"));
		panel.add(action("Spells", "petspells"));
		panel.add(action("Inspect", "petinspect"));
		panel.add(action("Behaviour", "petbehaviour"));
		panel.add(action("Summon", "petsummon"));
		panel.add(action("Dismiss", "petdismiss"));
		panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 210));
		return panel;
	}

	private JButton action(String label, String command)
	{
		JButton button = new JButton(label);
		button.addActionListener(e ->
		{
			if (rebuilding)
			{
				return;
			}
			sendSelect();
			send(command);
			send("companionstatus");
		});
		return button;
	}

	@Override
	public void onActivate()
	{
		panelActive = true;
		send("companionstatus");
	}

	@Override
	public void onDeactivate()
	{
		panelActive = false;
	}

	boolean isPanelActive()
	{
		return panelActive;
	}

	// ------------------------------------------------------------------ snapshot in

	void beginSnapshot(int count, long selectedId)
	{
		stagingPets.clear();
		stagingAbilities.clear();
		stagingSelectedId = selectedId;
	}

	void addPet(UnforgeCompanionProtocol.Pet pet)
	{
		stagingPets.add(pet);
	}

	void addAbility(UnforgeCompanionProtocol.Ability ability)
	{
		stagingAbilities.computeIfAbsent(ability.petId, k -> new ArrayList<>()).add(ability);
	}

	void setFlags(boolean autoLoot, boolean emergencyHeal)
	{
		stagingAutoLoot = autoLoot;
		stagingEmergencyHeal = emergencyHeal;
	}

	void endSnapshot()
	{
		pets.clear();
		pets.addAll(stagingPets);
		abilities.clear();
		abilities.putAll(stagingAbilities);
		autoLoot = stagingAutoLoot;
		emergencyHeal = stagingEmergencyHeal;
		if (stagingSelectedId >= 0)
		{
			selectedId = stagingSelectedId;
		}
		render();
	}

	// ------------------------------------------------------------------ actions out

	private void send(String command)
	{
		sender.send(command);
	}

	private void sendSelect()
	{
		UnforgeCompanionProtocol.Pet pet = selectedPet();
		if (pet != null)
		{
			send("petselect " + pet.id);
		}
	}

	private void onSelect()
	{
		if (rebuilding)
		{
			return;
		}
		UnforgeCompanionProtocol.Pet pet =
			(UnforgeCompanionProtocol.Pet) selector.getSelectedItem();
		if (pet != null)
		{
			selectedId = pet.id;
			send("petselect " + pet.id);
			render();
		}
	}

	private void onMode(String mode)
	{
		if (rebuilding)
		{
			return;
		}
		sendSelect();
		send("petmode " + mode);
		send("companionstatus");
	}

	// ------------------------------------------------------------------ render

	private UnforgeCompanionProtocol.Pet selectedPet()
	{
		for (UnforgeCompanionProtocol.Pet pet : pets)
		{
			if (pet.id == selectedId)
			{
				return pet;
			}
		}
		return pets.isEmpty() ? null : pets.get(0);
	}

	private void render()
	{
		rebuilding = true;
		try
		{
			long comboSelected = selectedId;
			selector.removeAllItems();
			for (UnforgeCompanionProtocol.Pet pet : pets)
			{
				selector.addItem(pet);
				if (pet.id == comboSelected)
				{
					selector.setSelectedItem(pet);
				}
			}

			UnforgeCompanionProtocol.Pet pet = selectedPet();
			if (pet == null)
			{
				nameLabel.setText("No companion");
				infoLabel.setText("Summon one with a contract scroll.");
				stateLabel.setText(" ");
				hpBar.setValue(0);
				hpBar.setMaximum(1);
				hpBar.setString("0/0");
				setControlsEnabled(false);
				abilityList.removeAll();
				statusLabel.setText(" ");
				revalidate();
				repaint();
				return;
			}

			selectedId = pet.id;
			nameLabel.setText(pet.name);
			infoLabel.setText(pet.companionClass + "  Lv " + pet.level + "  " + pet.style);
			String stateText = pet.state + (pet.active ? " - active" : " - stabled");
			if (pet.incapacitatedMs > 0)
			{
				stateText += " (recovers in " + formatMs(pet.incapacitatedMs) + ")";
			}
			stateLabel.setText(stateText);

			hpBar.setMaximum(Math.max(1, pet.maxHp));
			hpBar.setValue(pet.hp);
			hpBar.setString(pet.hp + "/" + pet.maxHp + " HP");
			double fraction = pet.maxHp > 0 ? (double) pet.hp / pet.maxHp : 0;
			hpBar.setForeground(fraction > 0.5 ? GOOD : fraction > 0.25 ? WARN : BAD);

			defButton.setSelected("DEFENSIVE".equals(pet.mode));
			aggButton.setSelected("AGGRESSIVE".equals(pet.mode));
			pasButton.setSelected("PASSIVE".equals(pet.mode));

			lootBox.setSelected(autoLoot);
			healBox.setSelected(emergencyHeal);

			abilityList.removeAll();
			List<UnforgeCompanionProtocol.Ability> rows =
				abilities.getOrDefault(pet.id, List.of());
			if (rows.isEmpty())
			{
				JLabel empty = new JLabel("No abilities equipped");
				empty.setForeground(SUBTLE);
				abilityList.add(empty);
			}
			else
			{
				for (UnforgeCompanionProtocol.Ability ability : rows)
				{
					boolean ready = ability.remainingMs <= 0;
					JLabel label = new JLabel(
						ability.name + (ready ? "  READY" : "  " + formatMs(ability.remainingMs)));
					label.setForeground(ready ? GOOD : WARN);
					abilityList.add(label);
				}
			}
			setControlsEnabled(true);
			revalidate();
			repaint();
		}
		finally
		{
			rebuilding = false;
		}
	}

	private void setControlsEnabled(boolean enabled)
	{
		defButton.setEnabled(enabled);
		aggButton.setEnabled(enabled);
		pasButton.setEnabled(enabled);
		selector.setEnabled(enabled && !pets.isEmpty());
	}

	private static String formatMs(long ms)
	{
		long seconds = (ms + 999) / 1000;
		return seconds >= 60 ? (seconds / 60) + "m " + (seconds % 60) + "s" : seconds + "s";
	}
}

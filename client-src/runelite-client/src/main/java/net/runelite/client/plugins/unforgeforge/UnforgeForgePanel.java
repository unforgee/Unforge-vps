package net.runelite.client.plugins.unforgeforge;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.unforgeforge.UnforgeForgeProtocol.ForgeAffix;
import net.runelite.client.plugins.unforgeforge.UnforgeForgeProtocol.ForgeCosts;
import net.runelite.client.plugins.unforgeforge.UnforgeForgeProtocol.ForgeDiff;
import net.runelite.client.plugins.unforgeforge.UnforgeForgeProtocol.ForgeEnchant;
import net.runelite.client.plugins.unforgeforge.UnforgeForgeProtocol.ForgeItem;
import net.runelite.client.plugins.unforgeforge.UnforgeForgeProtocol.ForgeSkillAffix;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

/**
 * Right-sidebar Forge panel. Renders the authoritative `UNFORGE_FORGE` snapshot:
 * item header, affix rolls with their possible range and roll-quality grading,
 * the three operations (upgrade / reforge all / reforge selected) and the
 * server-defined costs. Every button only sends `forgeop` - the server owns
 * validation, currency and the mutation itself.
 */
public class UnforgeForgePanel extends PluginPanel
{
	interface CommandSender
	{
		void send(String command);
	}

	private static final Color NAME_COLOR = new Color(0xff, 0x98, 0x1f);
	private static final Color SUBTLE = new Color(0x9a, 0x8b, 0x76);
	private static final Color GOOD = new Color(0x00, 0xb3, 0x3c);
	private static final Color BAD = new Color(0xff, 0x33, 0x33);
	private static final Color LOW_ROLL = new Color(0x80, 0x80, 0x80);
	private static final Color NORMAL_ROLL = new Color(0xd0, 0xd0, 0xd0);
	private static final Color GOOD_ROLL = new Color(0x1e, 0xff, 0x00);
	private static final Color EXCELLENT_ROLL = new Color(0x00, 0x70, 0xdd);
	private static final Color PERFECT_ROLL = new Color(0xff, 0x80, 0x00);
	private static final Color TICKET = new Color(0xff, 0xb8, 0x4d);

	private final ItemManager itemManager;
	private final CommandSender sender;

	private final JLabel itemIcon = new JLabel();
	private final JLabel nameLabel = new JLabel();
	private final JLabel subtitleLabel = new JLabel();
	private final JLabel balanceLabel = new JLabel();
	private final JLabel statusLabel = new JLabel();
	private final JPanel body = new JPanel();

	private ForgeItem item;
	private final Set<Integer> selected = new LinkedHashSet<>();
	private final List<ForgeDiff> lastDiffs = new ArrayList<>();
	private String lastResultOp;
	private boolean selectMode;
	private boolean pendingResult;

	public UnforgeForgePanel(ItemManager itemManager, CommandSender sender)
	{
		super();
		this.itemManager = itemManager;
		this.sender = sender;

		JPanel header = new JPanel(new BorderLayout(8, 0));
		header.setBackground(ColorScheme.DARK_GRAY_COLOR);
		header.setBorder(new EmptyBorder(2, 2, 8, 2));

		itemIcon.setBorder(BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR));
		header.add(itemIcon, BorderLayout.WEST);

		JPanel titles = new JPanel();
		titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
		titles.setBackground(ColorScheme.DARK_GRAY_COLOR);
		nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 14f));
		nameLabel.setForeground(NAME_COLOR);
		subtitleLabel.setFont(subtitleLabel.getFont().deriveFont(Font.PLAIN, 12f));
		subtitleLabel.setForeground(SUBTLE);
		titles.add(nameLabel);
		titles.add(Box.createVerticalStrut(2));
		titles.add(subtitleLabel);
		header.add(titles, BorderLayout.CENTER);

		balanceLabel.setForeground(TICKET);
		balanceLabel.setBorder(new EmptyBorder(0, 2, 4, 2));

		statusLabel.setBorder(new EmptyBorder(4, 4, 4, 4));
		statusLabel.setForeground(SUBTLE);

		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.setBackground(ColorScheme.DARK_GRAY_COLOR);

		add(header);
		add(balanceLabel);
		add(statusLabel);
		add(body);

		showEmpty();
	}

	void showEmpty()
	{
		item = null;
		selected.clear();
		selectMode = false;
		setHeader(null, null, null);
		balanceLabel.setText("");
		statusLabel.setText("");
		body.removeAll();
		JLabel hint = new JLabel(
			"<html>Right-click an equipment item and choose "
				+ "<b><col=ffb84d>Forge</col></b> to upgrade or reforge it here.</html>");
		hint.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		hint.setBorder(new EmptyBorder(8, 4, 8, 4));
		body.add(hint);
		revalidate();
		repaint();
	}

	// ------------------------------------------------------------------ protocol

	/** A fresh snapshot started streaming - replace the current item. */
	void beginItem(ForgeItem next)
	{
		boolean sameItem = item != null && item.instanceId == next.instanceId;
		if (!sameItem)
		{
			selected.clear();
			selectMode = false;
			lastDiffs.clear();
			lastResultOp = null;
		}
		item = next;
		pendingResult = false;
		statusLabel.setText("Loading...");
	}

	void addAffix(ForgeAffix affix)
	{
		if (item != null)
		{
			item.affixes.add(affix);
		}
	}

	void addSkillAffix(ForgeSkillAffix affix)
	{
		if (item != null)
		{
			item.skillAffixes.add(affix);
		}
	}

	void setCosts(ForgeCosts costs)
	{
		if (item != null)
		{
			item.costs = costs;
		}
	}

	void setEnchant(ForgeEnchant enchant)
	{
		if (item != null)
		{
			item.enchant = enchant;
		}
	}

	void setBalance(int tickets, int gold)
	{
		if (item != null)
		{
			item.tickets = tickets;
			item.gold = gold;
		}
	}

	/** END arrived - the snapshot is complete; render it. */
	void endItem()
	{
		if (item == null)
		{
			return;
		}
		// "resync"/"result" scopes refresh the same item; render what we have.
		render();
	}

	void onResult(String operation, long instanceId, long revision, int upgradeLevel)
	{
		if (item != null && item.instanceId == instanceId)
		{
			item.revision = revision;
		}
		lastResultOp = operation;
		lastDiffs.clear();
		pendingResult = true;
	}

	void addDiff(ForgeDiff diff)
	{
		lastDiffs.add(diff);
	}

	void onError(String code, String message)
	{
		pendingResult = false;
		statusLabel.setText(message);
		statusLabel.setForeground(BAD);
	}

	// ------------------------------------------------------------------ render

	private void render()
	{
		ForgeItem current = item;
		if (current == null)
		{
			return;
		}

		setHeader(current.objectId, current.name, subtitle(current));
		balanceLabel.setText(String.format(
			Locale.ROOT, "Forge Tickets: %d   Coins: %,d", current.tickets, current.gold));
		if (!pendingResult)
		{
			statusLabel.setText("");
		}

		body.removeAll();

		if (!lastDiffs.isEmpty() && lastResultOp != null)
		{
			body.add(resultSection());
		}

		if (selectMode)
		{
			renderSelection(current);
		}
		else
		{
			renderMain(current);
		}

		revalidate();
		repaint();
	}

	private void renderMain(ForgeItem current)
	{
		for (ForgeAffix affix : current.affixes)
		{
			body.add(affixRow(affix, null));
		}
		for (ForgeSkillAffix affix : current.skillAffixes)
		{
			JPanel row = new JPanel(new BorderLayout());
			row.setBackground(ColorScheme.DARK_GRAY_COLOR);
			row.setBorder(new EmptyBorder(1, 4, 1, 4));
			JLabel label = new JLabel(prettify(affix.effect) + " " + formatMagnitude(affix.unit, affix.magnitude));
			label.setForeground(SUBTLE);
			row.add(label, BorderLayout.CENTER);
			body.add(row);
		}
		if (current.enchant != null)
		{
			JPanel enchant = new JPanel(new BorderLayout());
			enchant.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			enchant.setBorder(new EmptyBorder(4, 4, 4, 4));
			String label = current.enchant.active
				? "Mystery Enchant: " + prettify(current.enchant.id) + " [" + current.enchant.tier + "] "
					+ current.enchant.remaining + "/" + current.enchant.max + " kills"
				: "Mystery Enchant: none";
			JLabel enchantLabel = new JLabel(label);
			enchantLabel.setForeground(current.enchant.active ? GOOD : SUBTLE);
			enchant.add(enchantLabel, BorderLayout.CENTER);
			body.add(enchant);
		}

		body.add(Box.createVerticalStrut(6));

		ForgeCosts costs = current.costs;
		JPanel buttons = new JPanel(new GridLayout(0, 1, 0, 4));
		buttons.setBackground(ColorScheme.DARK_GRAY_COLOR);
		buttons.setBorder(new EmptyBorder(0, 4, 4, 4));

		JButton upgrade = new JButton(costs == null
			? "UPGRADE"
			: String.format(Locale.ROOT, "UPGRADE  +%d -> +%d   (%dt / %,dg)",
				current.upgradeLevel, current.upgradeLevel + 1,
				costs.upgradeTickets, costs.upgradeGold));
		upgrade.addActionListener(e -> sendOp("upgrade", null));
		buttons.add(upgrade);

		JButton reforgeAll = new JButton(costs == null
			? "REFORGE ALL"
			: String.format(Locale.ROOT, "REFORGE ALL   (%dt / %,dg)",
				costs.reforgeAllTickets, costs.reforgeAllGold));
		reforgeAll.addActionListener(e -> reforgeAllClicked(current));
		buttons.add(reforgeAll);

		JButton reforgeSelected = new JButton("REFORGE SELECTED...");
		reforgeSelected.addActionListener(e ->
		{
			selectMode = true;
			render();
		});
		buttons.add(reforgeSelected);

		JButton mystery = new JButton(costs == null
			? "MYSTERY ENCHANT"
			: String.format(Locale.ROOT, "MYSTERY ENCHANT   (%dt)", costs.mysteryTickets));
		mystery.addActionListener(e -> mysteryEnchantClicked(current));
		buttons.add(mystery);

		body.add(buttons);
	}

	private void renderSelection(ForgeItem current)
	{
		JLabel title = new JLabel("SELECT AFFIXES TO REFORGE");
		title.setForeground(NAME_COLOR);
		title.setBorder(new EmptyBorder(2, 4, 4, 4));
		body.add(title);

		for (ForgeAffix affix : current.affixes)
		{
			body.add(affixRow(affix, current));
		}

		body.add(Box.createVerticalStrut(4));

		JPanel quick = new JPanel(new GridLayout(1, 2, 4, 0));
		quick.setBackground(ColorScheme.DARK_GRAY_COLOR);
		quick.setBorder(new EmptyBorder(0, 4, 4, 4));
		JButton selectAll = new JButton("SELECT ALL");
		selectAll.addActionListener(e ->
		{
			for (ForgeAffix affix : current.affixes)
			{
				if (affix.rerollable)
				{
					selected.add(affix.slot);
				}
			}
			render();
		});
		JButton clear = new JButton("CLEAR");
		clear.addActionListener(e ->
		{
			selected.clear();
			render();
		});
		quick.add(selectAll);
		quick.add(clear);
		body.add(quick);

		ForgeCosts costs = current.costs;
		JLabel costLine = new JLabel(costs == null
			? "Selected: " + selected.size()
			: String.format(Locale.ROOT, "Selected: %d   Cost: %d ticket(s) + %,d coins",
				selected.size(), costs.selectedTickets(selected.size()),
				costs.selectedGold(selected.size())));
		costLine.setForeground(TICKET);
		costLine.setBorder(new EmptyBorder(0, 4, 4, 4));
		body.add(costLine);

		JPanel actions = new JPanel(new GridLayout(1, 2, 4, 0));
		actions.setBackground(ColorScheme.DARK_GRAY_COLOR);
		actions.setBorder(new EmptyBorder(0, 4, 4, 4));
		JButton confirm = new JButton("REFORGE SELECTED");
		confirm.addActionListener(e ->
		{
			if (selected.isEmpty())
			{
				statusLabel.setText("Select at least one affix to reforge.");
				statusLabel.setForeground(BAD);
				return;
			}
			sendOp("sel", selected);
		});
		JButton back = new JButton("BACK");
		back.addActionListener(e ->
		{
			selectMode = false;
			render();
		});
		actions.add(confirm);
		actions.add(back);
		body.add(actions);
	}

	/** Result block: one line per affix with old -> new and a direction indicator. */
	private JPanel resultSection()
	{
		JPanel section = new JPanel();
		section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
		section.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		section.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
			new EmptyBorder(4, 6, 4, 6)));

		String resultTitle = "UPGRADE".equals(lastResultOp)
			? "UPGRADE RESULT"
			: "MYSTERY_ENCHANT".equals(lastResultOp)
				? "MYSTERY ENCHANT RESULT"
				: "REFORGE RESULT";
		JLabel title = new JLabel(resultTitle);
		title.setForeground(NAME_COLOR);
		section.add(title);
		section.add(Box.createVerticalStrut(2));

		for (ForgeDiff diff : lastDiffs)
		{
			String text;
			Color color;
			if (diff.newMagnitude > diff.oldMagnitude)
			{
				text = String.format(Locale.ROOT, "%s  %s -> %s  UP",
					prettify(diff.stat), formatMagnitude(diff.unit, diff.oldMagnitude),
					formatMagnitude(diff.unit, diff.newMagnitude));
				color = GOOD;
			}
			else if (diff.newMagnitude < diff.oldMagnitude)
			{
				text = String.format(Locale.ROOT, "%s  %s -> %s  DOWN",
					prettify(diff.stat), formatMagnitude(diff.unit, diff.oldMagnitude),
					formatMagnitude(diff.unit, diff.newMagnitude));
				color = BAD;
			}
			else
			{
				text = String.format(Locale.ROOT, "%s  %s  (unchanged)",
					prettify(diff.stat), formatMagnitude(diff.unit, diff.newMagnitude));
				color = SUBTLE;
			}
			JLabel line = new JLabel(text);
			line.setForeground(color);
			section.add(line);
		}

		section.add(Box.createVerticalStrut(4));
		JButton again = new JButton(
			"UPGRADE".equals(lastResultOp) ? "UPGRADE AGAIN" : "REFORGE AGAIN");
		again.addActionListener(e -> sendOp(
			"REFORGE_SELECTED".equals(lastResultOp) ? "sel"
				: "REFORGE_ALL".equals(lastResultOp) ? "all" : "upgrade",
			"REFORGE_SELECTED".equals(lastResultOp) ? selected : null));
		section.add(again);
		return section;
	}

	/**
	 * One affix row. In selection mode [container] non-null means a checkbox for
	 * rerollable affixes and a LOCKED badge otherwise; unchecked affixes are what
	 * Reforge Selected keeps byte-for-byte unchanged.
	 */
	private JPanel affixRow(ForgeAffix affix, ForgeItem current)
	{
		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setBackground(ColorScheme.DARK_GRAY_COLOR);
		row.setBorder(new EmptyBorder(2, 4, 2, 4));

		JPanel text = new JPanel();
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		text.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JLabel main = new JLabel(prettify(affix.family) + "  " +
			formatMagnitude(affix.unit, affix.magnitude));
		main.setForeground(qualityColor(affix.qualityBps));
		text.add(main);

		StringBuilder sub = new StringBuilder();
		if (affix.rangeMin != null && affix.rangeMax != null)
		{
			sub.append("Possible: ")
				.append(formatMagnitude(affix.unit, affix.rangeMin))
				.append(" - ")
				.append(formatMagnitude(affix.unit, affix.rangeMax));
		}
		if (affix.qualityBps >= 0)
		{
			if (sub.length() > 0)
			{
				sub.append("   ");
			}
			sub.append(String.format(Locale.ROOT, "%d%% roll", affix.qualityBps / 100));
		}
		if (sub.length() > 0)
		{
			JLabel range = new JLabel(sub.toString());
			range.setForeground(SUBTLE);
			range.setFont(range.getFont().deriveFont(Font.PLAIN, 11f));
			text.add(range);
		}
		row.add(text, BorderLayout.CENTER);

		if (current != null)
		{
			if (affix.rerollable)
			{
				JCheckBox box = new JCheckBox();
				box.setBackground(ColorScheme.DARK_GRAY_COLOR);
				box.setSelected(selected.contains(affix.slot));
				box.setToolTipText(tooltip(affix));
				box.addActionListener(e ->
				{
					if (box.isSelected())
					{
						selected.add(affix.slot);
					}
					else
					{
						selected.remove(affix.slot);
					}
					render();
				});
				row.add(box, BorderLayout.EAST);
			}
			else
			{
				JLabel locked = new JLabel("LOCKED");
				locked.setForeground(SUBTLE);
				locked.setFont(locked.getFont().deriveFont(Font.PLAIN, 10f));
				locked.setToolTipText("This affix cannot be rerolled.");
				row.add(locked, BorderLayout.EAST);
			}
		}
		else
		{
			row.setToolTipText(tooltip(affix));
		}
		return row;
	}

	// ------------------------------------------------------------------ actions

	private void reforgeAllClicked(ForgeItem current)
	{
		if (current.costs != null && current.costs.warnsHighRoll)
		{
			int choice = JOptionPane.showConfirmDialog(this,
				"This item contains one or more high-roll affixes.\n" +
					"Reforge All will replace them.",
				"Forge", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
			if (choice != JOptionPane.OK_OPTION)
			{
				return;
			}
		}
		sendOp("all", null);
	}

	private void mysteryEnchantClicked(ForgeItem current)
	{
		if (current.costs != null && current.enchant != null && current.enchant.active)
		{
			int choice = JOptionPane.showConfirmDialog(this,
				"This replaces the active Mystery Enchant. Continue?",
				"Mystery Forge", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
			if (choice != JOptionPane.OK_OPTION)
			{
				return;
			}
		}
		sendOp("mystery", null);
	}

	private void sendOp(String operation, Set<Integer> slots)
	{
		ForgeItem current = item;
		if (current == null)
		{
			return;
		}
		StringBuilder command = new StringBuilder("forgeop ")
			.append(current.instanceId).append(' ')
			.append(current.revision).append(' ')
			.append(operation);
		if (slots != null && !slots.isEmpty())
		{
			StringBuilder csv = new StringBuilder();
			for (int slot : slots)
			{
				if (csv.length() > 0)
				{
					csv.append(',');
				}
				csv.append(slot);
			}
			command.append(' ').append(csv);
		}
		statusLabel.setText("Working...");
		statusLabel.setForeground(SUBTLE);
		sender.send(command.toString());
	}

	// ------------------------------------------------------------------ format

	private void setHeader(Integer objectId, String name, String subtitle)
	{
		nameLabel.setText(name == null ? "Forge" : name);
		subtitleLabel.setText(subtitle == null ? "" : subtitle);
		if (objectId == null || objectId < 0)
		{
			itemIcon.setIcon(null);
		}
		else
		{
			itemManager.getImage(objectId).addTo(itemIcon);
		}
	}

	private static String subtitle(ForgeItem item)
	{
		return item.rarity + "   " + item.tier + "   ilvl " + item.itemLevel +
			"   Upgrade +" + item.upgradeLevel;
	}

	private static String tooltip(ForgeAffix affix)
	{
		StringBuilder tip = new StringBuilder("<html>")
			.append(prettify(affix.family))
			.append("<br>Current: ").append(formatMagnitude(affix.unit, affix.magnitude));
		if (affix.rangeMin != null && affix.rangeMax != null)
		{
			tip.append("<br>Possible: ")
				.append(formatMagnitude(affix.unit, affix.rangeMin))
				.append(" - ")
				.append(formatMagnitude(affix.unit, affix.rangeMax));
		}
		return tip.append("</html>").toString();
	}

	private static Color qualityColor(int qualityBps)
	{
		if (qualityBps < 0)
		{
			return NORMAL_ROLL;
		}
		if (qualityBps >= 9_000)
		{
			return PERFECT_ROLL;
		}
		if (qualityBps >= 7_500)
		{
			return EXCELLENT_ROLL;
		}
		if (qualityBps >= 5_000)
		{
			return GOOD_ROLL;
		}
		if (qualityBps >= 2_500)
		{
			return NORMAL_ROLL;
		}
		return LOW_ROLL;
	}

	static String formatMagnitude(String unit, int magnitude)
	{
		if ("BasisPoints".equals(unit) || "ProcBasisPoints".equals(unit))
		{
			String sign = magnitude > 0 ? "+" : magnitude < 0 ? "-" : "";
			return sign + String.format(Locale.ROOT, "%.2f%%", Math.abs(magnitude) / 100.0)
				+ ("ProcBasisPoints".equals(unit) ? " proc" : "");
		}
		if ("Ticks".equals(unit))
		{
			return (magnitude > 0 ? "+" : "") + magnitude + "t";
		}
		return (magnitude > 0 ? "+" : "") + magnitude;
	}

	/** "healing-power" -> "Healing Power"; camelCase stat names also split. */
	static String prettify(String raw)
	{
		String spaced = raw.replace('-', ' ').replace('_', ' ');
		StringBuilder out = new StringBuilder();
		boolean wordStart = true;
		for (int i = 0; i < spaced.length(); i++)
		{
			char c = spaced.charAt(i);
			if (!wordStart && Character.isUpperCase(c)
				&& (Character.isLowerCase(spaced.charAt(i - 1))))
			{
				out.append(' ');
			}
			out.append(wordStart ? Character.toUpperCase(c) : c);
			wordStart = c == ' ';
		}
		return out.toString();
	}

	private static Color rarityColor(String rarity)
	{
		switch (rarity)
		{
			case "Rare":
				return new Color(0x00, 0x70, 0xDD);
			case "Epic":
				return new Color(0xA3, 0x35, 0xEE);
			case "Legendary":
				return new Color(0xFF, 0x80, 0x00);
			case "Mythic":
				return new Color(0xE6, 0xCC, 0x80);
			case "Jackpot":
				return new Color(0xFF, 0x00, 0x00);
			case "Uncommon":
			default:
				return new Color(0x1E, 0xFF, 0x00);
		}
	}
}

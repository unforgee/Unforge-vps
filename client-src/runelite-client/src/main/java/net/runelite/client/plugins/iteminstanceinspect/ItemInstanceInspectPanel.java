package net.runelite.client.plugins.iteminstanceinspect;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.iteminstancehover.ItemInstanceMetadata;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

/**
 * Right-sidebar panel for the `Item Instance Inspect` plugin. The header shows the
 * examined item (icon, name, instance identity); the body renders the authoritative
 * `UNFORGE_INSPECT_LINE` stream from the server - the same post-affix stats, affix
 * rolls, sockets, unique-effect mechanics and skilling bonuses combat rolls against.
 * Before a server payload lands, structured metadata synced by the hover plugin is
 * shown as a preview when available.
 */
public class ItemInstanceInspectPanel extends PluginPanel
{
	private static final Pattern COL_TAG = Pattern.compile("<col=([0-9a-fA-F]{6})>");
	private static final Color NAME_COLOR = new Color(0xff, 0x98, 0x1f);
	private static final Color SUBTITLE_COLOR = new Color(0x9a, 0x8b, 0x76);

	private final ItemManager itemManager;

	private final JLabel itemIcon = new JLabel();
	private final JLabel nameLabel = new JLabel();
	private final JLabel subtitleLabel = new JLabel();
	private final JPanel linesPanel = new JPanel();
	private final JLabel emptyLabel = new JLabel();
	private boolean inspecting;

	public ItemInstanceInspectPanel(ItemManager itemManager)
	{
		super();
		this.itemManager = itemManager;

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
		subtitleLabel.setForeground(SUBTITLE_COLOR);
		titles.add(nameLabel);
		titles.add(Box.createVerticalStrut(2));
		titles.add(subtitleLabel);
		header.add(titles, BorderLayout.CENTER);

		linesPanel.setLayout(new BoxLayout(linesPanel, BoxLayout.Y_AXIS));
		linesPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

		emptyLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		emptyLabel.setBorder(new EmptyBorder(8, 4, 8, 4));
		emptyLabel.setText("<html>Right-click an equipment item and choose "
			+ "<b>Examine</b> to inspect it here.</html>");

		add(header);
		add(linesPanel);
		add(emptyLabel);
	}

	/** Empty state shown before anything has been examined. */
	void showEmpty()
	{
		setHeader(-1, null, null);
		rebuildLines(java.util.Collections.emptyList());
		emptyLabel.setVisible(true);
		revalidate();
		repaint();
	}

	/** Shows all active custom item-instance stats and their aggregated totals. */
	void showCatalog(List<ItemInstanceMetadata> items)
	{
		if (items == null || inspecting)
		{
			return;
		}
		setHeader(-1, "Active Item Instance Stats", items.size() + " active instance(s)");
		emptyLabel.setVisible(false);
		linesPanel.removeAll();
		Map<String, Integer> totals = new LinkedHashMap<>();
		for (ItemInstanceMetadata item : items)
		{
			for (ItemInstanceMetadata.Affix affix : item.getAffixes())
			{
				addTotal(totals, affix.getStat(), affix.getMagnitude());
			}
			for (ItemInstanceMetadata.SkillAffix affix : item.getSkillAffixes())
			{
				addTotal(totals, affix.getSkill() + " " + affix.getEffect(), affix.getMagnitude());
			}
		}
		if (totals.isEmpty())
		{
			appendTextLine("<col=9a8b76>No active custom item-instance stats.</col>");
		}
		else
		{
			List<Map.Entry<String, Integer>> sorted = new ArrayList<>(totals.entrySet());
			sorted.sort(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER));
			for (Map.Entry<String, Integer> total : sorted)
			{
				appendTextLine("<col=aaaaaa>" + total.getKey() + ":</col> <col=33cc33>"
					+ formatSigned(total.getValue()) + " total</col>");
			}
		}
		appendTextLine("<col=9a8b76>Totals include every active inventory/equipment instance synced by the server.</col>");
		revalidate();
		repaint();
	}

	private static void addTotal(Map<String, Integer> totals, String name, int magnitude)
	{
		if (name != null && !name.isEmpty())
		{
			totals.merge(name, magnitude, Integer::sum);
		}
	}

	private static String formatSigned(int value)
	{
		return value > 0 ? "+" + value : Integer.toString(value);
	}

	/**
	 * Immediate feedback on the examine click: header plus whatever structured
	 * metadata the hover plugin has already synced for this item (may be null).
	 * The server's inspect stream replaces the body when it lands.
	 */
	void showItem(int objectId, String name, ItemInstanceMetadata metadata)
	{
		inspecting = true;
		String subtitle = metadata != null && metadata.getInstanceId() > 0
			? "Instance #" + metadata.getInstanceId() + " - " + metadata.getRarity()
				+ " tier " + metadata.getTier() + ", ilvl " + metadata.getItemLevel()
			: metadata != null ? "Base item" : null;
		setHeader(objectId, name, subtitle);

		emptyLabel.setVisible(false);
		linesPanel.removeAll();
		if (metadata == null)
		{
			appendTextLine("<col=9a8b76>No instance data - examine sends details when available.</col>");
		}
		else
		{
			appendMetadataPreview(metadata);
		}
		revalidate();
		repaint();
	}

	/** `UNFORGE_INSPECT_BEGIN` - reset the body for the incoming authoritative lines. */
	void beginInspect(int objectId, long instanceId, String name, String subtitle)
	{
		inspecting = true;
		setHeader(objectId, name, subtitle);
		emptyLabel.setVisible(false);
		linesPanel.removeAll();
		revalidate();
		repaint();
	}

	/** `UNFORGE_INSPECT_LINE` - append one rendered line. */
	void appendLine(String text)
	{
		emptyLabel.setVisible(false);
		appendTextLine(text);
		revalidate();
		repaint();
	}

	/** `UNFORGE_INSPECT_END` - payload complete. */
	void endInspect()
	{
		inspecting = false;
		revalidate();
		repaint();
	}

	private void setHeader(int objectId, String name, String subtitle)
	{
		if (objectId >= 0)
		{
			itemManager.getImage(objectId).addTo(itemIcon);
		}
		else
		{
			itemIcon.setIcon(null);
		}
		nameLabel.setText(name != null ? name : "Item Instance Inspect");
		subtitleLabel.setText(subtitle != null ? subtitle : "");
	}

	private void appendMetadataPreview(ItemInstanceMetadata meta)
	{
		appendTextLine("<col=aaaaaa>tier " + meta.getTier() + ", ilvl " + meta.getItemLevel()
			+ ", quality " + meta.getQuality() + "%</col>");
		for (ItemInstanceMetadata.Affix affix : meta.getAffixes())
		{
			appendTextLine("<col=00b4ff>Affix " + affix.getFamily() + ":</col> "
				+ affix.getStat() + " <col=ffdc00>" + affix.getMagnitude()
				+ " " + affix.getUnit() + "</col>");
		}
		if (!meta.getSockets().isEmpty())
		{
			appendTextLine("<col=9090ff>Sockets:</col> " + meta.getSockets().size() + " slot(s)");
		}
		for (ItemInstanceMetadata.Ability ability : meta.getAbilities())
		{
			appendTextLine("<col=ffdc00>" + ability.getName() + "</col>"
				+ (ability.getDescription().isEmpty()
					? ""
					: " <col=aaaaaa>" + ability.getDescription() + "</col>"));
		}
		for (ItemInstanceMetadata.SkillAffix skill : meta.getSkillAffixes())
		{
			appendTextLine("<col=4dc3ff>" + skill.getSkill() + " " + skill.getEffect()
				+ ":</col> <col=33cc33>+" + skill.getMagnitude() + "</col>");
		}
		appendTextLine("<col=9a8b76>Loading full details...</col>");
	}

	private void appendTextLine(String text)
	{
		if (text == null || text.isEmpty())
		{
			linesPanel.add(Box.createVerticalStrut(6));
			return;
		}
		JLabel label = new JLabel(toHtml(text));
		label.setFont(label.getFont().deriveFont(Font.PLAIN, 13f));
		label.setForeground(ColorScheme.TEXT_COLOR);
		label.setAlignmentX(LEFT_ALIGNMENT);
		linesPanel.add(label);
	}

	private void rebuildLines(java.util.List<String> lines)
	{
		linesPanel.removeAll();
		for (String line : lines)
		{
			appendTextLine(line);
		}
	}

	/** Converts server `<col=rrggbb>` spans into Swing HTML. */
	private static String toHtml(String text)
	{
		String html = COL_TAG.matcher(text).replaceAll("<font color='#$1'>");
		html = html.replace("</col>", "</font>");
		return "<html>" + html + "</html>";
	}
}

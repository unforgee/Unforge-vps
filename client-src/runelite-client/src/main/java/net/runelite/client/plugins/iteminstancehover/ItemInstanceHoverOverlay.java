package net.runelite.client.plugins.iteminstancehover;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.util.Locale;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.MenuEntry;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.ComponentConstants;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/**
 * Dedicated stats panel for instanced items. Unlike the generic tooltip this
 * renders a structured, color-coded panel anchored to the mouse and clamped to
 * the canvas, so the full stat block stays visible even inside the bank.
 *
 * The server payload remains the only authoritative source - nothing here
 * re-rolls or reconstructs instance values.
 */
@Singleton
final class ItemInstanceHoverOverlay extends Overlay
{
	private static final int OFFSET_X = 16;
	private static final int OFFSET_Y = 16;

	private static final Color SECTION = new Color(0xFF, 0x9B, 0x2F);
	private static final Color SPEED = new Color(0x8F, 0xE0, 0x5A);
	private static final Color LABEL = new Color(0xC8, 0xC8, 0xC8);
	private static final Color VALUE_POSITIVE = new Color(0x30, 0xD8, 0x30);
	private static final Color VALUE_NEGATIVE = new Color(0xEF, 0x40, 0x40);
	private static final Color VALUE_NEUTRAL = Color.WHITE;
	private static final Color DIM = new Color(0x90, 0x90, 0x90);
	private static final Color TAGGED = new Color(0xFF, 0xD1, 0x00);

	/**
	 * Labels for the server's fixed-order total-stat list (index 12 is magic damage,
	 * carried in 0.1% units). Indices must match `EquipmentInstanceDescribe.totalStats`.
	 */
	private static final String[] STAT_LABELS = {
		"Attack stab", "Attack slash", "Attack crush", "Attack magic", "Attack ranged",
		"Defence stab", "Defence slash", "Defence crush", "Defence magic", "Defence ranged",
		"Melee strength", "Ranged strength", "Magic damage", "Prayer"
	};
	private static final int MAGIC_DAMAGE_INDEX = 12;

	private final Client client;
	private final ItemInstanceMetadataStore metadataStore;
	private final ItemInstanceTagStore tagStore;
	private final PanelComponent panel = new PanelComponent();

	// The previous frame's rendered size, used to clamp the panel into the canvas
	// without needing to measure the children twice.
	private Dimension lastSize = new Dimension();

	@Inject
	private ItemInstanceHoverOverlay(
		Client client,
		ItemInstanceMetadataStore metadataStore,
		ItemInstanceTagStore tagStore)
	{
		this.client = client;
		this.metadataStore = metadataStore;
		this.tagStore = tagStore;
		setPriority(PRIORITY_HIGHEST);
		setPosition(OverlayPosition.DYNAMIC);
		// Default is UNDER_WIDGETS, which lets the vanilla action text/menu draw
		// over the panel. The hover must sit above everything else on screen.
		setLayer(OverlayLayer.ALWAYS_ON_TOP);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (client.isMenuOpen())
		{
			return null;
		}

		HoverTarget target = resolveTarget();
		if (target == null)
		{
			return null;
		}

		ItemInstanceMetadata metadata = metadataStore.get(target.scope, target.slot);
		if (metadata == null || metadata.getObjectId() != target.objectId)
		{
			// Do not fall back to a re-roll or to cache stats here. The server payload is
			// the only authoritative source for instance-specific values.
			return null;
		}

		net.runelite.api.Point mouse = client.getMouseCanvasPosition();
		if (mouse == null)
		{
			return null;
		}

		buildPanel(metadata);

		int canvasWidth = client.getCanvasWidth();
		int canvasHeight = client.getCanvasHeight();
		int x = clamp(mouse.getX() + OFFSET_X, 0, Math.max(0, canvasWidth - lastSize.width));
		int y = clamp(mouse.getY() + OFFSET_Y, 0, Math.max(0, canvasHeight - lastSize.height));

		panel.setPreferredLocation(new Point(x, y));
		lastSize = panel.render(graphics);
		return null;
	}

	private HoverTarget resolveTarget()
	{
		MenuEntry[] menu = client.getMenuEntries();
		if (menu.length == 0)
		{
			return null;
		}

		MenuEntry entry = menu[menu.length - 1];
		Widget widget = entry.getWidget();
		if (widget == null)
		{
			widget = client.getWidget(entry.getParam1());
		}
		if (widget == null)
		{
			return null;
		}

		Widget item = itemWidgetOf(widget);
		if (item == null)
		{
			return null;
		}

		int scope = ItemInstanceScopes.scopeFor(item);
		if (scope < 0)
		{
			return null;
		}

		int slot = item.getIndex();
		if (slot < 0)
		{
			slot = entry.getParam0();
		}

		int objectId = item.getItemId();
		if (objectId < 0)
		{
			objectId = entry.getItemId();
		}

		return slot < 0 || objectId < 0 ? null : new HoverTarget(scope, slot, objectId);
	}

	/**
	 * Worn-item menu entries point at the slot container; the item itself lives in
	 * a child widget. Scan for the child that actually carries an item id.
	 */
	private static Widget itemWidgetOf(Widget widget)
	{
		if (widget.getItemId() >= 0)
		{
			return widget;
		}
		Widget[] children = widget.getChildren();
		if (children != null)
		{
			for (Widget child : children)
			{
				if (child.getItemId() >= 0)
				{
					return child;
				}
			}
		}
		return widget.getItemId() < 0 && widget.getIndex() >= 0 ? widget : null;
	}

	private void buildPanel(ItemInstanceMetadata metadata)
	{
		panel.getChildren().clear();
		panel.setPreferredSize(new Dimension(ComponentConstants.STANDARD_WIDTH, 0));
		panel.setGap(new Point(0, 3));

		// A plain wearable carries no instance id - only its template gear speed is meaningful.
		boolean instanced = metadata.getInstanceId() > 0;
		Color rarityColor = instanced ? rarityColor(metadata.getRarity()) : VALUE_NEUTRAL;
		boolean tagged = instanced && tagStore.isTagged(metadata.getInstanceId());

		ItemComposition definition = client.getItemDefinition(metadata.getObjectId());
		String name = definition != null && definition.getName() != null
			? definition.getName()
			: "Item " + metadata.getObjectId();

		panel.getChildren().add(TitleComponent.builder()
			.text(name + (tagged ? "  \u2605" : ""))
			.color(rarityColor)
			.build());

		if (instanced)
		{
			panel.getChildren().add(LineComponent.builder()
				.left(displayName(metadata.getRarity()) + " " + displayName(metadata.getTier()))
				.leftColor(rarityColor)
				.right("ilvl " + metadata.getItemLevel())
				.rightColor(VALUE_NEUTRAL)
				.build());

			panel.getChildren().add(LineComponent.builder()
				.left("Quality")
				.leftColor(LABEL)
				.right(metadata.getQuality() + "%")
				.rightColor(VALUE_NEUTRAL)
				.build());
		}

		if (metadata.getSpeedBps() != 0)
		{
			panel.getChildren().add(TitleComponent.builder()
				.text("Speed")
				.color(SPEED)
				.build());
			panel.getChildren().add(LineComponent.builder()
				.left("Attack speed")
				.leftColor(LABEL)
				.right(formatBps(metadata.getSpeedBps()))
				.rightColor(metadata.getSpeedBps() > 0 ? VALUE_POSITIVE : VALUE_NEGATIVE)
				.build());
		}

		if (!metadata.getStats().isEmpty())
		{
			panel.getChildren().add(TitleComponent.builder()
				.text("Total stats")
				.color(SECTION)
				.build());
			java.util.List<Integer> stats = metadata.getStats();
			for (int i = 0; i < stats.size() && i < STAT_LABELS.length; i++)
			{
				int value = stats.get(i);
				if (value == 0)
				{
					continue;
				}
				String text = i == MAGIC_DAMAGE_INDEX
					? String.format(Locale.ROOT, "%s%.1f%%", value > 0 ? "+" : "", value / 10.0)
					: formatSigned(value);
				panel.getChildren().add(LineComponent.builder()
					.left(STAT_LABELS[i])
					.leftColor(LABEL)
					.right(text)
					.rightColor(value > 0 ? VALUE_POSITIVE : VALUE_NEGATIVE)
					.build());
			}
		}

		if (!metadata.getAffixes().isEmpty())
		{
			panel.getChildren().add(TitleComponent.builder()
				.text("Stats")
				.color(SECTION)
				.build());
			for (ItemInstanceMetadata.Affix affix : metadata.getAffixes())
			{
				int signed = "Curse".equalsIgnoreCase(affix.getPolarity())
					? -Math.abs(affix.getMagnitude())
					: affix.getMagnitude();
				panel.getChildren().add(LineComponent.builder()
					.left(displayName(affix.getStat()))
					.leftColor(LABEL)
					.right(formatMagnitude(affix, signed))
					.rightColor(signed > 0 ? VALUE_POSITIVE : signed < 0 ? VALUE_NEGATIVE : DIM)
					.build());
			}
		}

		if (!metadata.getSkillAffixes().isEmpty())
		{
			panel.getChildren().add(TitleComponent.builder()
				.text("Skilling Stats")
				.color(new Color(0x4D, 0xC3, 0xFF))
				.build());
			for (ItemInstanceMetadata.SkillAffix affix : metadata.getSkillAffixes())
			{
				String value = formatSkillMagnitude(affix);
				panel.getChildren().add(LineComponent.builder()
					.left(displayName(affix.getSkill()) + " " + displayName(affix.getEffect()))
					.leftColor(LABEL)
					.right(value)
					.rightColor(VALUE_POSITIVE)
					.build());
			}
		}

		if (!metadata.getAbilities().isEmpty())
		{
			String chance = String.format(Locale.ROOT, "Proc %.1f%%", procChanceBps(metadata) / 100.0);
			panel.getChildren().add(TitleComponent.builder()
				.text("Abilities")
				.color(SECTION)
				.build());
			for (ItemInstanceMetadata.Ability ability : metadata.getAbilities())
			{
				panel.getChildren().add(LineComponent.builder()
					.left(displayName(ability.getName()))
					.leftColor(VALUE_NEUTRAL)
					.right(chance)
					.rightColor(DIM)
					.build());
				if (!ability.getDescription().isEmpty())
				{
					panel.getChildren().add(LineComponent.builder()
						.left("  " + ability.getDescription())
						.leftColor(DIM)
						.build());
				}
			}
		}

		if (!metadata.getSockets().isEmpty())
		{
			panel.getChildren().add(TitleComponent.builder()
				.text("Sockets")
				.color(SECTION)
				.build());
			for (ItemInstanceMetadata.Socket socket : metadata.getSockets())
			{
				String value = displayName(socket.getType());
				if (socket.getObjectId() != null)
				{
					ItemComposition socketed = client.getItemDefinition(socket.getObjectId());
					value = socketed != null && socketed.getName() != null
						? socketed.getName()
						: "object " + socket.getObjectId();
				}
				if (socket.getMagnitude() != 0)
				{
					value += " " + formatSigned(socket.getMagnitude());
				}
				panel.getChildren().add(LineComponent.builder()
					.left("Slot " + (socket.getSlot() + 1))
					.leftColor(LABEL)
					.right(value)
					.rightColor(VALUE_NEUTRAL)
					.build());
			}
		}
	}

	/**
	 * Mirrors the server-side proc formula so the displayed chance always matches
	 * what {@code StandardNpcHitProcessor} actually rolls:
	 * {@code (500 + rarityOrdinal * 1200 + itemLevel * 30).coerceIn(100, 9000)}.
	 */
	private static int procChanceBps(ItemInstanceMetadata metadata)
	{
		int chance = 500 + rarityOrdinal(metadata.getRarity()) * 1_200 + metadata.getItemLevel() * 30;
		return Math.max(100, Math.min(9_000, chance));
	}

	private static int rarityOrdinal(String rarity)
	{
		switch (rarity)
		{
			case "Rare":
				return 1;
			case "Epic":
				return 2;
			case "Legendary":
				return 3;
			case "Mythic":
				return 4;
			case "Jackpot":
				return 5;
			case "Uncommon":
			default:
				return 0;
		}
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

	private static String formatMagnitude(ItemInstanceMetadata.Affix affix, int signedMagnitude)
	{
		String unit = affix.getUnit();
		if ("BasisPoints".equals(unit) || "ProcBasisPoints".equals(unit))
		{
			String sign = signedMagnitude > 0 ? "+" : signedMagnitude < 0 ? "-" : "";
			String value = String.format(Locale.ROOT, "%.2f%%", Math.abs(signedMagnitude) / 100.0);
			return sign + value + ("ProcBasisPoints".equals(unit) ? " proc" : "");
		}

		if ("Ticks".equals(unit))
		{
			return formatSigned(signedMagnitude) + "t"
				+ ("Mixed".equalsIgnoreCase(affix.getPolarity()) ? " (mixed)" : "");
		}

		return formatSigned(signedMagnitude);
	}

	private static String formatSkillMagnitude(ItemInstanceMetadata.SkillAffix affix)
	{
		if ("BasisPoints".equals(affix.getUnit()) || "ProcBasisPoints".equals(affix.getUnit()))
		{
			return String.format(Locale.ROOT, "+%.2f%%", affix.getMagnitude() / 100.0);
		}
		return "+" + affix.getMagnitude();
	}

	private static String formatSigned(int value)
	{
		return value > 0 ? "+" + value : Integer.toString(value);
	}

	private static String formatBps(int basisPoints)
	{
		String sign = basisPoints > 0 ? "+" : basisPoints < 0 ? "-" : "";
		return sign + String.format(Locale.ROOT, "%.2f%%", Math.abs(basisPoints) / 100.0);
	}

	private static String displayName(String value)
	{
		String normalized = value.replace('-', ' ').replace('_', ' ');
		StringBuilder result = new StringBuilder(normalized.length());
		for (int i = 0; i < normalized.length(); i++)
		{
			char current = normalized.charAt(i);
			if (i > 0 && Character.isUpperCase(current) && Character.isLowerCase(normalized.charAt(i - 1)))
			{
				result.append(' ');
			}
			result.append(i == 0 ? Character.toUpperCase(current) : current);
		}
		return result.toString();
	}

	private static int clamp(int value, int min, int max)
	{
		return Math.max(min, Math.min(max, value));
	}

	static final class HoverTarget
	{
		final int scope;
		final int slot;
		final int objectId;

		HoverTarget(int scope, int slot, int objectId)
		{
			this.scope = scope;
			this.slot = slot;
			this.objectId = objectId;
		}
	}
}

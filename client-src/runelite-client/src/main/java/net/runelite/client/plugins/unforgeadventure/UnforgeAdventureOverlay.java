package net.runelite.client.plugins.unforgeadventure;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Point;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.ui.overlay.components.ComponentConstants;
import net.runelite.client.ui.overlay.components.LineComponent;

/**
 * Adventure Path tracker overlay: a translucent, freely-movable panel that lists the
 * quest chain with the next required step highlighted. The step table and progress are
 * pushed by the server (`UNFORGE_ADV|*` console messages) - the client never guesses.
 */
@Singleton
final class UnforgeAdventureOverlay extends OverlayPanel
{
	private static final Color HEADER = new Color(0xFF, 0xB8, 0x4D);
	private static final Color NEXT = new Color(0xFF, 0xD1, 0x00);
	private static final Color LABEL = new Color(0x9A, 0x8B, 0x76);
	private static final Color DONE = new Color(0x33, 0xB5, 0x32);
	private static final Color TEXT = new Color(0xD8, 0xD8, 0xD8);
	private static final Color DIM = new Color(0x8A, 0x8A, 0x8A);

	/** Steps shown after the highlighted "next" one when the panel is not expanded. */
	private static final int COLLAPSED_EXTRA_STEPS = 2;

	private final Client client;
	private final AdventureTrackerModel model;
	private final UnforgeAdventureConfig config;

	@Inject
	private UnforgeAdventureOverlay(
		Client client,
		AdventureTrackerModel model,
		UnforgeAdventureConfig config)
	{
		this.client = client;
		this.model = model;
		this.config = config;
		setPosition(OverlayPosition.TOP_LEFT);
		setPriority(OverlayPriority.LOW);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (client.getGameState() != GameState.LOGGED_IN || !model.hasData()
			|| model.hidden() || !config.showTracker())
		{
			return null;
		}

		Font font = new Font(Font.SANS_SERIF, Font.PLAIN, config.fontSize());
		Font bold = font.deriveFont(Font.BOLD);
		panelComponent.setBackgroundColor(new Color(10, 14, 26, config.backgroundAlpha()));
		panelComponent.getChildren().clear();
		panelComponent.setGap(new Point(0, 2));

		panelComponent.getChildren().add(LineComponent.builder()
			.left("Adventure Path")
			.leftColor(HEADER)
			.leftFont(bold)
			.right(model.completedCount() + "/" + model.stepCount())
			.rightColor(TEXT)
			.rightFont(font)
			.build());

		int next = model.nextOrdinal();
		if (next < 0)
		{
			LineComponent complete = LineComponent.builder()
				.left("ADVENTURE PATH COMPLETE")
				.leftColor(DONE)
				.leftFont(bold)
				.build();
			panelComponent.getChildren().add(complete);
		}
		else
		{
			AdventureTrackerModel.Step nextStep = model.step(next);
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Next: " + nextStep.title)
				.leftColor(NEXT)
				.leftFont(bold)
				.build());
			if (!model.minimized())
			{
				panelComponent.getChildren().add(LineComponent.builder()
					.left("  " + nextStep.requirement)
					.leftColor(TEXT)
					.leftFont(font)
					.build());
			}

			int shown = next + 1;
			int limit = model.minimized() ? shown
				: config.expanded() ? model.stepCount()
				: Math.min(model.stepCount(), shown + COLLAPSED_EXTRA_STEPS);
			for (; shown < limit; shown++)
			{
				AdventureTrackerModel.Step step = model.step(shown);
				if (step == null)
				{
					continue;
				}
				panelComponent.getChildren().add(LineComponent.builder()
					.left("  " + step.title)
					.leftColor(DIM)
					.leftFont(font)
					.right(step.phase)
					.rightColor(LABEL)
					.rightFont(font)
					.build());
			}
			if (shown < model.stepCount())
			{
				int remaining = (int) java.util.stream.IntStream.range(shown, model.stepCount())
					.filter(model::isDone)
					.count();
				int left = model.stepCount() - shown - remaining;
				if (left > 0)
				{
					panelComponent.getChildren().add(LineComponent.builder()
						.left("  + " + left + " more steps")
						.leftColor(DIM)
						.leftFont(font)
						.build());
				}
			}
		}

		if (!model.minimized())
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Relic")
				.leftColor(LABEL)
				.leftFont(font)
				.right(model.relic())
				.rightColor(TEXT)
				.rightFont(font)
				.build());
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Discoveries")
				.leftColor(LABEL)
				.leftFont(font)
				.right(model.discoveries() + " (" + model.discoveryPoints() + " pts)")
				.rightColor(TEXT)
				.rightFont(font)
				.build());
			panelComponent.getChildren().add(LineComponent.builder()
				.left("First Companion")
				.leftColor(LABEL)
				.leftFont(font)
				.right(model.companion())
				.rightColor("LOCKED".equals(model.companion()) ? DIM
					: "READY".equals(model.companion()) ? NEXT : DONE)
				.rightFont(font)
				.build());
		}

		panelComponent.setPreferredSize(new Dimension(ComponentConstants.STANDARD_WIDTH, 0));
		return super.render(graphics);
	}
}

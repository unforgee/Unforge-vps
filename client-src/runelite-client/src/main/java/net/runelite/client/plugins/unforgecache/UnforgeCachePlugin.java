package net.runelite.client.plugins.unforgecache;

import com.google.gson.Gson;
import com.google.inject.Provides;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.SpritePixels;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;

/**
 * Live sprite editor inside the game client.
 *
 * <p>Sprites are replaced through the client's own override map, which the renderer
 * consults instead of the cache. Nothing is ever written to the cache, so there is no
 * way to corrupt it and no backup to restore: reverting is simply removing the entry,
 * and closing the client restores everything by itself.
 *
 * <p>Because the override is live, a replacement shows up while you are playing — no
 * restart, no cache rebuild, no relog.
 */
@Slf4j
@PluginDescriptor(
	name = "Unforge Cache",
	configName = "UnforgeCachePlugin",
	description = "Replace sprites live while playing, without touching the game cache",
	tags = {"cache", "sprite", "interface", "unforge", "editor", "dev"},
	enabledByDefault = false
)
public class UnforgeCachePlugin extends Plugin implements UnforgeCachePanel.Actions
{
	private static final BufferedImage ICON = createIcon();

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private SpriteManager spriteManager;

	@Inject
	private Gson gson;

	@Inject
	private UnforgeCacheConfig config;

	private UnforgeCachePanel panel;
	private NavigationButton navigationButton;
	private SpriteOverrideStore store;

	@Provides
	UnforgeCacheConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(UnforgeCacheConfig.class);
	}

	@Override
	protected void startUp()
	{
		store = new SpriteOverrideStore(gson);

		panel = new UnforgeCachePanel();
		panel.init(this);

		navigationButton = NavigationButton.builder()
			.icon(ICON)
			.tooltip("Unforge Cache")
			.priority(12)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navigationButton);

		if (config.autoApply())
		{
			clientThread.invokeLater(this::applyStoredReplacements);
		}
	}

	@Override
	protected void shutDown()
	{
		// Leaving overrides behind would leak into a later session under a different cache.
		clientThread.invokeLater(() -> client.getSpriteOverrides().clear());

		// The panel may be living in its own window; dispose it so no orphan is left behind.
		final UnforgeCachePanel currentPanel = panel;
		if (currentPanel != null)
		{
			SwingUtilities.invokeLater(currentPanel::disposePopOut);
		}

		if (navigationButton != null)
		{
			clientToolbar.removeNavigation(navigationButton);
			navigationButton = null;
		}
		panel = null;
		store = null;
	}

	/* ------------------------------------------------- panel actions */

	@Override
	public void loadSprite(int spriteId, int frame, Consumer<BufferedImage> onImage)
	{
		clientThread.invokeLater(() ->
		{
			BufferedImage image = null;
			try
			{
				image = spriteManager.getSprite(spriteId, frame);
			}
			catch (RuntimeException e)
			{
				log.debug("Unforge Cache: sprite {}/{} is not readable", spriteId, frame, e);
			}
			onImage.accept(image);
		});
	}

	@Override
	public void applyReplacement(int spriteId, int frame, File png)
	{
		BufferedImage image;
		try
		{
			image = ImageIO.read(png);
		}
		catch (Exception e)
		{
			log.warn("Unforge Cache: could not read {}", png, e);
			return;
		}

		if (image == null)
		{
			log.warn("Unforge Cache: {} is not an image the JDK can read", png);
			return;
		}

		try
		{
			store.store(spriteId, frame, image);
		}
		catch (Exception e)
		{
			log.warn("Unforge Cache: could not save the replacement", e);
		}

		final BufferedImage stored = image;
		clientThread.invokeLater(() -> client.getSpriteOverrides().put(spriteId, toSpritePixels(stored)));
	}

	@Override
	public void revertReplacement(int spriteId, int frame)
	{
		if (!config.keepOriginalOnRevert())
		{
			store.clear(spriteId, frame);
		}
		clientThread.invokeLater(() -> client.getSpriteOverrides().remove(spriteId));
	}

	@Override
	public boolean isApplied(int spriteId, int frame)
	{
		return client.getSpriteOverrides().containsKey(spriteId);
	}

	@Override
	public boolean hasStoredPng(int spriteId, int frame)
	{
		SpriteOverrideStore current = store;
		return current != null && current.hasStoredPng(spriteId, frame);
	}

	@Override
	public Path overrideFolder()
	{
		SpriteOverrideStore current = store;
		return current == null ? null : current.spritesDir();
	}

	@Override
	public void reopenPanel()
	{
		final NavigationButton button = navigationButton;
		if (button != null)
		{
			SwingUtilities.invokeLater(() -> clientToolbar.openPanel(button));
		}
	}

	/* ------------------------------------------------------- internals */

	/** Re-applies every stored replacement, skipping any whose PNG has gone missing. */
	private void applyStoredReplacements()
	{
		if (store == null)
		{
			return;
		}

		int applied = 0;
		for (Map.Entry<String, BufferedImage> entry : store.loadAllImages().entrySet())
		{
			String[] parts = entry.getKey().split("/");
			if (parts.length != 2)
			{
				continue;
			}
			try
			{
				int spriteId = Integer.parseInt(parts[0]);
				client.getSpriteOverrides().put(spriteId, toSpritePixels(entry.getValue()));
				applied++;
			}
			catch (RuntimeException e)
			{
				log.warn("Unforge Cache: skipping stored replacement {}", entry.getKey(), e);
			}
		}

		if (applied > 0)
		{
			log.info("Unforge Cache: re-applied {} sprite replacement(s)", applied);
		}
	}

	/**
	 * Converts a PNG to the client's sprite format.
	 *
	 * <p>The client's own factory does the conversion, so padding, alpha and the
	 * internal colour layout match what the renderer expects rather than what this
	 * plugin guesses.
	 */
	private SpritePixels toSpritePixels(BufferedImage image)
	{
		int width = image.getWidth();
		int height = image.getHeight();
		int[] pixels = new int[width * height];
		image.getRGB(0, 0, width, height, pixels, 0, width);
		return client.createSpritePixels(pixels, width, height);
	}

	private static BufferedImage createIcon()
	{
		BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		try
		{
			graphics.setColor(new Color(150, 106, 34));
			graphics.fillRoundRect(0, 0, 15, 15, 4, 4);
			graphics.setColor(Color.WHITE);
			graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 8));
			graphics.drawString("SC", 2, 11);
		}
		finally
		{
			graphics.dispose();
		}
		return image;
	}
}

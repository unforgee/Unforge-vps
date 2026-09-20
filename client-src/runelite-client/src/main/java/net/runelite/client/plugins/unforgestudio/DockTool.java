package net.runelite.client.plugins.unforgestudio;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;

/**
 * Shared plumbing for every tool in the development dock.
 *
 * <p>Provides the services each tool needs (client, symbols, local API), a status
 * line for reporting outcomes, and two helpers that remove the two easy mistakes:
 * blocking the EDT on HTTP, and reading the game state off the client thread.</p>
 */
abstract class DockTool extends JPanel
{
	protected final Client client;
	protected final ClientThread clientThread;
	protected final SymbolIndex symbols;
	protected final StudioApiClient api;
	protected final UnforgeStudioDock dock;

	private final JLabel statusLabel = new JLabel(" ");

	protected DockTool(Client client, ClientThread clientThread, SymbolIndex symbols,
		StudioApiClient api, UnforgeStudioDock dock)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.symbols = symbols;
		this.api = api;
		this.dock = dock;

		setLayout(new BorderLayout(0, 4));
		setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
		add(buildStatusBar(), BorderLayout.SOUTH);
	}

	private JComponent buildStatusBar()
	{
		statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, statusLabel.getFont().getSize2D() - 1f));
		statusLabel.setForeground(Color.LIGHT_GRAY);
		return statusLabel;
	}

	protected void setStatus(String message)
	{
		SwingUtilities.invokeLater(() -> statusLabel.setText(message == null ? " " : message));
	}

	protected void setStatus(String message, boolean error)
	{
		SwingUtilities.invokeLater(() ->
		{
			statusLabel.setText(message == null ? " " : message);
			statusLabel.setForeground(error ? new Color(232, 120, 120) : Color.LIGHT_GRAY);
		});
	}

	/**
	 * Runs a blocking call on a worker thread, then reports the outcome in the
	 * status line. The supplier returns a human-readable result message; throwing
	 * is reported as an error.
	 */
	protected void background(String busyMessage, java.util.concurrent.Callable<String> work)
	{
		setStatus(busyMessage);
		final String[] result = new String[1];
		final RuntimeException[] failure = new RuntimeException[1];

		Thread worker = new Thread(() ->
		{
			try
			{
				result[0] = work.call();
			}
			catch (Exception ex)
			{
				failure[0] = ex instanceof RuntimeException ? (RuntimeException) ex : new RuntimeException(ex);
			}

			SwingUtilities.invokeLater(() ->
			{
				if (failure[0] != null)
				{
					String message = failure[0].getMessage();
					setStatus(message == null ? failure[0].getClass().getSimpleName() : message, true);
				}
				else
				{
					setStatus(result[0], false);
				}
			});
		}, "unforge-dock");

		worker.setDaemon(true);
		worker.start();
	}

	/** The local player's world position, read on the client thread. */
	protected void withPosition(Consumer<Position> consumer)
	{
		clientThread.invokeLater(() ->
		{
			if (client.getLocalPlayer() == null)
			{
				consumer.accept(null);
				return;
			}

			WorldPoint location = client.getLocalPlayer().getWorldLocation();
			if (location == null)
			{
				consumer.accept(null);
				return;
			}

			consumer.accept(new Position(location.getX(), location.getY(), location.getPlane()));
		});
	}

	/** A world tile. */
	protected static final class Position
	{
		final int x;
		final int y;
		final int z;

		Position(int x, int y, int z)
		{
			this.x = x;
			this.y = y;
			this.z = z;
		}

		/** `x y z` as the content formats expect. */
		String asFields()
		{
			return x + " " + y + " " + z;
		}

		String regionId()
		{
			return String.valueOf(((x >> 6) << 8) | (y >> 6));
		}

		@Override
		public String toString()
		{
			return x + ", " + y + ", plane " + z + " (region " + regionId() + ")";
		}
	}
}

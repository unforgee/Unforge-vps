package net.runelite.client.plugins.unforgestudio;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import net.runelite.client.plugins.unforgecommon.PopoutWindow;
import net.runelite.client.ui.ColorScheme;

/**
 * The bottom development dock: a tab strip of content tools with a draggable
 * divider along its top edge.
 *
 * <p>Deliberately a plain {@link JTabbedPane} container, so adding a tool is one
 * {@link #addTool} call and nothing else needs to change. A tool can be popped out
 * into an always-on-top window using the shared {@link PopoutWindow} helper, the
 * same pattern the other Unforge plugins use.</p>
 */
public class UnforgeStudioDock extends JPanel
{
	private static final int DIVIDER_HEIGHT = 5;
	private static final int MIN_HEIGHT = 120;
	private static final int MAX_HEIGHT = 900;
	private static final int POPOUT_WIDTH = 560;
	private static final int POPOUT_HEIGHT = 760;

	public interface Actions
	{
		/** The user is dragging; apply immediately but do not persist yet. */
		void onHeightChanged(int height);

		/** The drag finished; persist the new height. */
		void onHeightCommitted(int height);

		/** The user asked for the dock to be hidden. */
		void onHideRequested();
	}

	private final Actions actions;
	private final JTabbedPane tabs = new JTabbedPane();
	private final JLabel statusLabel = new JLabel();
	private final PopoutWindow popout = new PopoutWindow();

	private JComponent poppedComponent;
	private String poppedTitle;

	private int dragStartY;
	private int dragStartHeight;

	public UnforgeStudioDock(Actions actions)
	{
		this.actions = actions;

		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, ColorScheme.DARK_GRAY_COLOR));

		add(buildDivider(), BorderLayout.NORTH);
		add(buildBody(), BorderLayout.CENTER);
	}

	// ------------------------------------------------------------------ layout

	private JComponent buildDivider()
	{
		JPanel divider = new JPanel();
		divider.setPreferredSize(new Dimension(0, DIVIDER_HEIGHT));
		divider.setBackground(ColorScheme.DARK_GRAY_COLOR);
		divider.setCursor(Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR));
		divider.setToolTipText("Drag to resize the development dock");

		divider.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				dragStartY = e.getYOnScreen();
				dragStartHeight = getHeight();
			}

			@Override
			public void mouseReleased(MouseEvent e)
			{
				if (dragStartHeight > 0)
				{
					actions.onHeightCommitted(getHeight());
					dragStartHeight = 0;
				}
			}
		});

		divider.addMouseMotionListener(new MouseAdapter()
		{
			@Override
			public void mouseDragged(MouseEvent e)
			{
				// Dragging upwards grows the dock, so the delta is inverted.
				int delta = dragStartY - e.getYOnScreen();
				int height = Math.max(MIN_HEIGHT, Math.min(dragStartHeight + delta, MAX_HEIGHT));
				actions.onHeightChanged(height);
			}
		});

		return divider;
	}

	private JComponent buildBody()
	{
		JPanel body = new JPanel(new BorderLayout());
		body.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		body.add(buildHeader(), BorderLayout.NORTH);

		tabs.setTabPlacement(JTabbedPane.TOP);
		tabs.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		body.add(tabs, BorderLayout.CENTER);

		return body;
	}

	private JComponent buildHeader()
	{
		JPanel header = new JPanel(new BorderLayout());
		header.setBackground(ColorScheme.DARK_GRAY_COLOR);
		header.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 6));

		JLabel title = new JLabel("UNFORGE DEV");
		title.setFont(title.getFont().deriveFont(Font.BOLD, title.getFont().getSize2D()));
		title.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

		statusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		statusLabel.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 0));

		JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		left.setOpaque(false);
		left.add(title);
		left.add(statusLabel);

		JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
		right.setOpaque(false);
		right.add(toolButton("\u2922", "Pop the selected tool out into its own window", this::togglePopout));
		right.add(toolButton("\u2013", "Hide the development dock", () -> actions.onHideRequested()));

		header.add(left, BorderLayout.WEST);
		header.add(right, BorderLayout.EAST);
		return header;
	}

	private JButton toolButton(String text, String tooltip, Runnable onClick)
	{
		JButton button = new JButton(text);
		button.setToolTipText(tooltip);
		button.setFocusable(false);
		button.setMargin(new Insets(0, 4, 0, 4));
		button.addActionListener(e -> onClick.run());
		return button;
	}

	// ------------------------------------------------------------------- tools

	/** Registers one development tool as a tab. */
	public void addTool(String title, JComponent component)
	{
		tabs.addTab(title, component);
	}

	public JTabbedPane tabs()
	{
		return tabs;
	}

	public void setStatus(String text)
	{
		SwingUtilities.invokeLater(() -> statusLabel.setText(text == null ? "" : text));
	}

	// ----------------------------------------------------------------- pop-out

	private void togglePopout()
	{
		if (popout.isOpen())
		{
			// A second toggle with the same component docks it back and runs the callback.
			popout.toggle(poppedComponent, poppedTitle, POPOUT_WIDTH, POPOUT_HEIGHT, this::dockBack);
			return;
		}

		int index = tabs.getSelectedIndex();
		if (index < 0)
		{
			return;
		}

		Component selected = tabs.getComponentAt(index);
		if (!(selected instanceof JComponent))
		{
			return;
		}

		poppedTitle = tabs.getTitleAt(index);
		poppedComponent = (JComponent) selected;
		tabs.remove(index);

		popout.toggle(poppedComponent, "Unforge Dev - " + poppedTitle, POPOUT_WIDTH, POPOUT_HEIGHT, this::dockBack);
	}

	private void dockBack()
	{
		if (poppedComponent == null)
		{
			return;
		}

		tabs.addTab(poppedTitle, poppedComponent);
		tabs.setSelectedComponent(poppedComponent);
		poppedComponent = null;
		poppedTitle = null;
	}

	/** Docks any popped-out tool and clears the tabs, ready for shutdown. */
	public void dispose()
	{
		if (popout.isOpen() && poppedComponent != null)
		{
			popout.toggle(poppedComponent, poppedTitle, POPOUT_WIDTH, POPOUT_HEIGHT, null);
		}

		poppedComponent = null;
		poppedTitle = null;
		tabs.removeAll();
	}
}

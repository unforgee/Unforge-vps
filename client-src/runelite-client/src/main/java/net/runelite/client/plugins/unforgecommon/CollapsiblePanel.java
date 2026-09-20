package net.runelite.client.plugins.unforgecommon;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;

/**
 * A section that collapses to a thin titled bar. Click the bar to expand/collapse.
 */
public class CollapsiblePanel extends JPanel
{
	private final String title;
	private final JComponent content;
	private final JLabel label;
	private boolean expanded;
	private Runnable onToggle;

	public CollapsiblePanel(String title, JComponent content, boolean expanded, Color accent)
	{
		super(new BorderLayout());
		this.title = title;
		this.content = content;
		setOpaque(false);

		JPanel bar = new JPanel(new BorderLayout());
		bar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		bar.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(1, 0, 1, 0, ColorScheme.MEDIUM_GRAY_COLOR),
			new EmptyBorder(3, 6, 3, 6)));
		bar.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		label = new JLabel();
		label.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
		label.setForeground(accent);
		bar.add(label, BorderLayout.WEST);
		bar.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				setExpanded(!CollapsiblePanel.this.expanded);
			}
		});
		add(bar, BorderLayout.NORTH);
		add(content, BorderLayout.CENTER);
		setExpanded(expanded);
	}

	public boolean isExpanded()
	{
		return expanded;
	}

	/** Fired after every expand/collapse - used by AccordionColumn to redistribute space. */
	public void setOnToggle(Runnable onToggle)
	{
		this.onToggle = onToggle;
	}

	public void setExpanded(boolean expand)
	{
		expanded = expand;
		content.setVisible(expand);
		label.setText((expand ? "▾ " : "▸ ") + title);
		revalidate();
		repaint();
		if (onToggle != null)
		{
			onToggle.run();
		}
	}
}

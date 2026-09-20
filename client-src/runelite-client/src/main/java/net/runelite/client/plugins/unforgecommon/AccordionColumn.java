package net.runelite.client.plugins.unforgecommon;

import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JPanel;

/**
 * A vertical column of CollapsiblePanels where leftover space is handed to the sections that
 * are open, instead of pooling as dead space at the bottom.
 *
 * Sections added with growable=true (lists/tables) share all spare height between themselves
 * while expanded; growable=false sections (fixed field rows) always keep their preferred size.
 * When a growable section is collapsed its weight drops to 0 and the space is redistributed to
 * the remaining open growable sections.
 */
public class AccordionColumn extends JPanel
{
	private final List<Entry> entries = new ArrayList<>();

	public AccordionColumn()
	{
		super(new GridBagLayout());
		setOpaque(false);
	}

	public CollapsiblePanel addSection(String title, JComponent content, boolean expanded, Color accent, boolean growable)
	{
		CollapsiblePanel panel = new CollapsiblePanel(title, content, expanded, accent);
		GridBagConstraints c = new GridBagConstraints();
		c.gridx = 0;
		c.weightx = 1;
		c.fill = GridBagConstraints.BOTH;
		c.weighty = growable && expanded ? 1 : 0;
		add(panel, c);
		entries.add(new Entry(panel, growable));
		panel.setOnToggle(this::rebalance);
		return panel;
	}

	private void rebalance()
	{
		GridBagLayout layout = (GridBagLayout) getLayout();
		for (Entry e : entries)
		{
			GridBagConstraints c = layout.getConstraints(e.panel);
			c.weighty = e.growable && e.panel.isExpanded() ? 1 : 0;
			layout.setConstraints(e.panel, c);
		}
		revalidate();
		repaint();
	}

	private static class Entry
	{
		final CollapsiblePanel panel;
		final boolean growable;

		Entry(CollapsiblePanel panel, boolean growable)
		{
			this.panel = panel;
			this.growable = growable;
		}
	}
}

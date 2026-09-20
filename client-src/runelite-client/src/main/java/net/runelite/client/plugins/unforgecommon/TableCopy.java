package net.runelite.client.plugins.unforgecommon;

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.KeyStroke;

public final class TableCopy
{
	private TableCopy()
	{
	}

	/** Binds Ctrl+C on the table to copy the selected rows as comma-separated lines. */
	public static void install(JTable table)
	{
		table.getInputMap(JComponent.WHEN_FOCUSED)
			.put(KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK), "unforgeCopyRows");
		table.getActionMap().put("unforgeCopyRows", new AbstractAction()
		{
			@Override
			public void actionPerformed(java.awt.event.ActionEvent e)
			{
				copyRows(table, false);
			}
		});
	}

	/** Copies selected rows (or all rows when none selected) as CSV lines. Skips a "name" column heuristically by keeping it. */
	public static void copyRows(JTable table, boolean allRows)
	{
		int[] rows = allRows || table.getSelectedRowCount() == 0
			? allIndices(table.getRowCount())
			: table.getSelectedRows();
		StringBuilder sb = new StringBuilder();
		for (int viewRow : rows)
		{
			int row = table.convertRowIndexToModel(viewRow);
			for (int c = 0; c < table.getModel().getColumnCount(); c++)
			{
				if (c > 0)
				{
					sb.append(',');
				}
				Object v = table.getModel().getValueAt(row, c);
				sb.append(v == null ? "" : String.valueOf(v).trim());
			}
			sb.append('\n');
		}
		if (sb.length() > 0)
		{
			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(sb.toString()), null);
		}
	}

	private static int[] allIndices(int n)
	{
		int[] idx = new int[n];
		for (int i = 0; i < n; i++)
		{
			idx[i] = i;
		}
		return idx;
	}
}

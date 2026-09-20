package net.runelite.client.plugins.unforgecommon;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.ui.ColorScheme;

/**
 * Live item-name search with a multi-select result list. Shift/Ctrl select +
 * Enter or double-click fires {@link Listener#onPick} with all selected items.
 */
public class ItemPickerPanel extends JPanel
{
	public interface Listener
	{
		void onPick(List<UnforgeSearch.ItemResult> items);
	}

	private final JTextField searchField = new JTextField();
	private final DefaultListModel<UnforgeSearch.ItemResult> resultModel = new DefaultListModel<>();
	private final JList<UnforgeSearch.ItemResult> results = new JList<>(resultModel);

	public ItemPickerPanel(Client client, ClientThread clientThread, Listener listener, int visibleRows)
	{
		super(new BorderLayout(0, 4));
		setOpaque(false);

		JPanel searchRow = new JPanel(new BorderLayout(4, 0));
		searchRow.setOpaque(false);
		searchField.setToolTipText("Type an item name, e.g. whip / armadyl - Shift/Ctrl multi-select, Enter adds");
		searchRow.add(searchField, BorderLayout.CENTER);
		JButton addBtn = new JButton("Add");
		addBtn.addActionListener(e -> pick(listener));
		searchRow.add(addBtn, BorderLayout.EAST);
		add(searchRow, BorderLayout.NORTH);

		results.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		results.setVisibleRowCount(visibleRows);
		results.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		results.setForeground(Color.WHITE);
		results.addKeyListener(new KeyAdapter()
		{
			@Override
			public void keyPressed(KeyEvent e)
			{
				if (e.getKeyCode() == KeyEvent.VK_ENTER)
				{
					pick(listener);
				}
			}
		});
		results.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (e.getClickCount() == 2)
				{
					pick(listener);
				}
			}
		});

		JScrollPane scroll = new JScrollPane(results);
		scroll.setBorder(BorderFactory.createTitledBorder(
			BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR), "Matches (Shift+Enter adds selected)"));
		add(scroll, BorderLayout.CENTER);

		Timer debounce = new Timer(300, e -> search(client, clientThread));
		debounce.setRepeats(false);
		searchField.getDocument().addDocumentListener(new DocumentListener()
		{
			public void insertUpdate(DocumentEvent e)
			{
				debounce.restart();
			}

			public void removeUpdate(DocumentEvent e)
			{
				debounce.restart();
			}

			public void changedUpdate(DocumentEvent e)
			{
				debounce.restart();
			}
		});
		searchField.addActionListener(e -> search(client, clientThread));
	}

	private void pick(Listener listener)
	{
		List<UnforgeSearch.ItemResult> picked = results.getSelectedValuesList();
		if (!picked.isEmpty())
		{
			listener.onPick(picked);
			searchField.setText("");
			resultModel.clear();
		}
	}

	private void search(Client client, ClientThread clientThread)
	{
		String query = searchField.getText().trim().toLowerCase();
		if (query.length() < 2)
		{
			return;
		}
		clientThread.invokeLater(() ->
		{
			List<UnforgeSearch.ItemResult> found = UnforgeSearch.items(client, query);
			SwingUtilities.invokeLater(() ->
			{
				resultModel.clear();
				for (UnforgeSearch.ItemResult r : found)
				{
					resultModel.addElement(r);
				}
			});
		});
	}
}

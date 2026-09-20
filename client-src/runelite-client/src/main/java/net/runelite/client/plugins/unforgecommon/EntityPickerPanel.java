package net.runelite.client.plugins.unforgecommon;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridLayout;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import javax.swing.border.EmptyBorder;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.ui.ColorScheme;

/**
 * NPC/object search by name or by right-click action (e.g. "Bank", "Talk-to").
 * Selecting a result fires {@link Listener#onPick}.
 */
public class EntityPickerPanel extends JPanel
{
	public interface Listener
	{
		void onPick(UnforgeSearch.EntityResult entity);
	}

	private final JComboBox<String> typeBox;
	private final JComboBox<String> modeBox;
	private final JTextField searchField = new JTextField();
	private final DefaultListModel<UnforgeSearch.EntityResult> resultModel = new DefaultListModel<>();
	private final JList<UnforgeSearch.EntityResult> results;

	public EntityPickerPanel(Client client, ClientThread clientThread, boolean objectsOnly, boolean npcsOnly, Listener listener)
	{
		super(new BorderLayout(0, 4));
		setOpaque(false);

		JPanel top = new JPanel(new BorderLayout(0, 4));
		top.setOpaque(false);

		String[] types = objectsOnly ? new String[]{"Objects"} : npcsOnly ? new String[]{"NPCs"} : new String[]{"Objects", "NPCs"};
		typeBox = new JComboBox<>(types);
		modeBox = new JComboBox<>(new String[]{"by Name", "by Action"});
		JPanel filterRow = new JPanel(new GridLayout(1, 2, 4, 0));
		filterRow.setOpaque(false);
		filterRow.add(typeBox);
		filterRow.add(modeBox);
		filterRow.setBorder(new EmptyBorder(0, 0, 4, 0));
		top.add(filterRow, BorderLayout.NORTH);

		JPanel fieldRow = new JPanel(new BorderLayout(4, 0));
		fieldRow.setOpaque(false);
		searchField.setToolTipText("e.g. bank booth / Bank / goblin");
		searchField.addActionListener(e -> search(client, clientThread));
		fieldRow.add(searchField, BorderLayout.CENTER);
		JButton searchBtn = new JButton("Search");
		searchBtn.addActionListener(e -> search(client, clientThread));
		fieldRow.add(searchBtn, BorderLayout.EAST);
		top.add(fieldRow, BorderLayout.CENTER);
		add(top, BorderLayout.NORTH);

		results = new JList<UnforgeSearch.EntityResult>(resultModel)
		{
			@Override
			public String getToolTipText(MouseEvent e)
			{
				int i = locationToIndex(e.getPoint());
				if (i < 0 || i >= getModel().getSize())
				{
					return null;
				}
				UnforgeSearch.EntityResult r = getModel().getElementAt(i);
				return "<html><b>" + r.name + "</b> (id " + r.id + ")<br/>Actions: "
					+ (r.actions.isEmpty() ? "-" : r.actions) + "</html>";
			}
		};
		results.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		results.setVisibleRowCount(5);
		results.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		results.setForeground(Color.WHITE);
		ToolTipManager.sharedInstance().registerComponent(results);
		results.addListSelectionListener(e ->
		{
			if (!e.getValueIsAdjusting() && results.getSelectedValue() != null)
			{
				listener.onPick(results.getSelectedValue());
			}
		});
		JScrollPane scroll = new JScrollPane(results);
		scroll.setBorder(BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR));
		add(scroll, BorderLayout.CENTER);
	}

	private void search(Client client, ClientThread clientThread)
	{
		String query = searchField.getText().trim().toLowerCase();
		if (query.isEmpty())
		{
			return;
		}
		boolean npcs = typeBox.getSelectedIndex() == 1 || "NPCs".equals(typeBox.getSelectedItem());
		boolean byAction = modeBox.getSelectedIndex() == 1;
		clientThread.invokeLater(() ->
		{
			java.util.List<UnforgeSearch.EntityResult> found = npcs
				? UnforgeSearch.npcs(client, query, byAction)
				: UnforgeSearch.objects(client, query, byAction);
			SwingUtilities.invokeLater(() ->
			{
				resultModel.clear();
				for (UnforgeSearch.EntityResult r : found)
				{
					resultModel.addElement(r);
				}
			});
		});
	}
}

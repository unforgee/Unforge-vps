package net.runelite.client.plugins.unforgeshop;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.unforgecommon.AccordionColumn;
import net.runelite.client.plugins.unforgecommon.ItemPickerPanel;
import net.runelite.client.plugins.unforgecommon.PopoutWindow;
import net.runelite.client.plugins.unforgecommon.TableCopy;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

@Slf4j
public class UnforgeShopPanel extends PluginPanel
{
	private static final File KRONOS_DATA_ROOT = new File("C:/Users/HOST/Desktop/UNFORGE-239-STANDALONE-LAB/content/kronos-data");
	private static final String[] CURRENCIES = {"COINS", "BLOOD_MONEY", "TOKKUL", "MOLCH_PEARLS", "MARK_OF_GRACE", "VOTE_TICKETS"};

	private final UnforgeShopPlugin plugin;
	private final Client client;
	private final ClientThread clientThread;
	private final PopoutWindow popout = new PopoutWindow();

	private final JTextField shopTitleField = new JTextField("General Supplies");
	private final JComboBox<String> currencyBox = new JComboBox<>(CURRENCIES);
	private final DefaultTableModel itemModel = new DefaultTableModel(new String[]{"ID", "Item", "Amount", "Price"}, 0)
	{
		@Override
		public boolean isCellEditable(int row, int col)
		{
			return col != 1;
		}
	};
	private final JTable itemTable = new JTable(itemModel);
	private final JTextField bulkAmount = new JTextField("1", 4);
	private final JTextField bulkPrice = new JTextField(4);
	private JButton popOutBtn;

	public UnforgeShopPanel(UnforgeShopPlugin plugin, Client client, ClientThread clientThread)
	{
		super(false);
		this.plugin = plugin;
		this.client = client;
		this.clientThread = clientThread;

		setLayout(new BorderLayout(0, 6));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setBorder(new EmptyBorder(10, 10, 10, 10));

		add(buildHeader(), BorderLayout.NORTH);
		add(buildEditor(), BorderLayout.CENTER);
		add(buildFooter(), BorderLayout.SOUTH);

		int[][] defaults = {{590, 100, 10}, {1755, 100, 15}, {2347, 100, 20}, {952, 100, 25}, {1265, 50, 50}};
		for (int[] d : defaults)
		{
			itemModel.addRow(new Object[]{d[0], "item " + d[0], d[1], d[2]});
		}
		resolveNames();
	}

	private JPanel buildHeader()
	{
		JPanel header = new JPanel();
		header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
		header.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		header.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(new Color(40, 167, 69), 1),
			new EmptyBorder(8, 8, 8, 8)));

		JPanel titleRow = new JPanel(new BorderLayout());
		titleRow.setOpaque(false);
		JLabel title = new JLabel("$ UNFORGE SHOP BUILDER");
		title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
		title.setForeground(new Color(80, 220, 100));
		titleRow.add(title, BorderLayout.WEST);
		popOutBtn = new JButton("⤢");
		popOutBtn.setToolTipText("Pop out into a resizable window");
		popOutBtn.setMargin(new java.awt.Insets(1, 6, 1, 6));
		popOutBtn.addActionListener(e -> togglePopOut());
		titleRow.add(popOutBtn, BorderLayout.EAST);
		header.add(titleRow);

		JPanel titleFieldRow = new JPanel(new BorderLayout(4, 0));
		titleFieldRow.setOpaque(false);
		titleFieldRow.add(new JLabel("Shop title:"), BorderLayout.WEST);
		titleFieldRow.add(shopTitleField, BorderLayout.CENTER);
		titleFieldRow.setBorder(new EmptyBorder(4, 0, 0, 0));
		header.add(titleFieldRow);

		JPanel currRow = new JPanel(new BorderLayout(4, 0));
		currRow.setOpaque(false);
		currRow.add(new JLabel("Currency:"), BorderLayout.WEST);
		currRow.add(currencyBox, BorderLayout.CENTER);
		currRow.setBorder(new EmptyBorder(4, 0, 0, 0));
		header.add(currRow);

		return header;
	}

	private JPanel buildEditor()
	{
		AccordionColumn editor = new AccordionColumn();
		Color accent = new Color(80, 220, 100);

		ItemPickerPanel picker = new ItemPickerPanel(client, clientThread, items ->
		{
			int amount = parseInt(bulkAmount.getText(), 1);
			for (net.runelite.client.plugins.unforgecommon.UnforgeSearch.ItemResult r : items)
			{
				itemModel.addRow(new Object[]{r.id, r.name, Math.max(1, amount), Math.max(0, r.storePrice)});
			}
		}, 5);
		editor.addSection("Item Search", picker, true, accent, true);

		itemTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		itemTable.setFillsViewportHeight(true);
		itemTable.getColumnModel().getColumn(0).setMaxWidth(60);
		itemTable.getColumnModel().getColumn(2).setMaxWidth(60);
		itemTable.getColumnModel().getColumn(3).setMaxWidth(70);
		TableCopy.install(itemTable);
		JScrollPane tableScroll = new JScrollPane(itemTable);
		tableScroll.setBorder(BorderFactory.createTitledBorder(
			BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR), "Stock (Ctrl+C copies rows, Shift-sel for bulk)"));

		JPanel bulk = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
		bulk.setOpaque(false);
		bulk.add(new JLabel("Amount:"));
		bulk.add(bulkAmount);
		JButton setAmount = new JButton("Set");
		setAmount.addActionListener(e -> applyBulk(2, bulkAmount.getText()));
		bulk.add(setAmount);
		bulk.add(new JLabel("Price:"));
		bulk.add(bulkPrice);
		JButton setPrice = new JButton("Set");
		setPrice.addActionListener(e -> applyBulk(3, bulkPrice.getText()));
		bulk.add(setPrice);
		JButton removeBtn = new JButton("Del");
		removeBtn.addActionListener(e -> removeSelected());
		bulk.add(removeBtn);
		JButton copyRowsBtn = new JButton("Copy");
		copyRowsBtn.addActionListener(e -> TableCopy.copyRows(itemTable, false));
		bulk.add(copyRowsBtn);

		JPanel stockSection = new JPanel(new BorderLayout(0, 4));
		stockSection.setOpaque(false);
		stockSection.add(tableScroll, BorderLayout.CENTER);
		stockSection.add(bulk, BorderLayout.SOUTH);
		editor.addSection("Stock", stockSection, true, accent, true);

		return editor;
	}

	private JPanel buildFooter()
	{
		JPanel footer = new JPanel(new GridLayout(1, 2, 5, 5));
		footer.setOpaque(false);

		JButton saveBtn = new JButton("Save Shop YAML");
		saveBtn.setBackground(new Color(40, 167, 69));
		saveBtn.setForeground(Color.WHITE);
		saveBtn.addActionListener(e -> saveYaml());
		footer.add(saveBtn);

		JButton copyBtn = new JButton("Copy YAML");
		copyBtn.addActionListener(e -> copyToClipboard(generateYaml()));
		footer.add(copyBtn);

		return footer;
	}

	private void togglePopOut()
	{
		popout.toggle(this, "Unforge Shop Builder", 520, 720, plugin::reopenPanel);
		popOutBtn.setText(popout.isOpen() ? "⤡" : "⤢");
	}

	void closePopout()
	{
		popout.dock(this, null);
	}

	private void applyBulk(int col, String value)
	{
		int v = parseInt(value, -1);
		if (v < 0)
		{
			return;
		}
		for (int row : itemTable.getSelectedRows())
		{
			itemModel.setValueAt(v, itemTable.convertRowIndexToModel(row), col);
		}
	}

	private void removeSelected()
	{
		int[] rows = itemTable.getSelectedRows();
		for (int i = rows.length - 1; i >= 0; i--)
		{
			itemModel.removeRow(itemTable.convertRowIndexToModel(rows[i]));
		}
	}

	private void resolveNames()
	{
		clientThread.invokeLater(() ->
		{
			java.util.List<Object[]> updates = new java.util.ArrayList<>();
			for (int i = 0; i < itemModel.getRowCount(); i++)
			{
				try
				{
					int id = Integer.parseInt(String.valueOf(itemModel.getValueAt(i, 0)).trim());
					net.runelite.api.ItemComposition def = client.getItemDefinition(id);
					if (def != null && def.getName() != null && !def.getName().equalsIgnoreCase("null"))
					{
						updates.add(new Object[]{i, def.getName()});
					}
				}
				catch (Throwable ignored)
				{
				}
			}
			SwingUtilities.invokeLater(() ->
			{
				for (Object[] u : updates)
				{
					int row = (Integer) u[0];
					if (row < itemModel.getRowCount())
					{
						itemModel.setValueAt(u[1], row, 1);
					}
				}
			});
		});
	}

	private String generateYaml()
	{
		StringBuilder sb = new StringBuilder();
		sb.append("---\n");
		sb.append("identifier: \"").append(UUID.randomUUID().toString()).append("\"\n");
		sb.append("title: \"").append(shopTitleField.getText().replace("\"", "\\\"")).append("\"\n");
		sb.append("currency: \"").append(currencyBox.getSelectedItem()).append("\"\n");
		sb.append("accessibleByIronMan: true\n");
		sb.append("canSellToStore: false\n");
		sb.append("restockRules:\n");
		sb.append("  restockTicks: 50\n");
		sb.append("  restockPerTick: 1\n");
		sb.append("defaultStock:\n");
		for (int i = 0; i < itemModel.getRowCount(); i++)
		{
			try
			{
				int id = Integer.parseInt(String.valueOf(itemModel.getValueAt(i, 0)).trim());
				int amount = Integer.parseInt(String.valueOf(itemModel.getValueAt(i, 2)).trim());
				int price = Integer.parseInt(String.valueOf(itemModel.getValueAt(i, 3)).trim());
				sb.append("- id: ").append(id).append("\n");
				sb.append("  amount: ").append(amount).append("\n");
				sb.append("  price: ").append(price).append("\n");
				sb.append("  placeholderId: 0\n");
			}
			catch (NumberFormatException ignored)
			{
			}
		}
		return sb.toString();
	}

	private void saveYaml()
	{
		String yaml = generateYaml();
		String filename = shopTitleField.getText().replaceAll("[^a-zA-Z0-9_-]", "_") + "_Shop.yaml";
		File target = new File(KRONOS_DATA_ROOT, "shops/" + filename);
		try
		{
			target.getParentFile().mkdirs();
			Files.write(target.toPath(), yaml.getBytes(StandardCharsets.UTF_8));
			JOptionPane.showMessageDialog(this, "Saved shop to:\n" + target.getAbsolutePath(), "Shop Saved", JOptionPane.INFORMATION_MESSAGE);
		}
		catch (IOException ex)
		{
			copyToClipboard(yaml);
			JOptionPane.showMessageDialog(this, "Could not write file, YAML copied to clipboard instead:\n" + ex.getMessage());
		}
	}

	private static int parseInt(String s, int fallback)
	{
		try
		{
			return Integer.parseInt(s.trim());
		}
		catch (Exception e)
		{
			return fallback;
		}
	}

	private void copyToClipboard(String text)
	{
		if (text != null && !text.isEmpty())
		{
			java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
				.setContents(new java.awt.datatransfer.StringSelection(text), null);
		}
	}
}

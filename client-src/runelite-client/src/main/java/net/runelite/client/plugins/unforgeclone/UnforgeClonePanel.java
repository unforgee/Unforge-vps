package net.runelite.client.plugins.unforgeclone;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.JagexColor;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.unforgecommon.AccordionColumn;
import net.runelite.client.plugins.unforgecommon.CloneColors;
import net.runelite.client.plugins.unforgecommon.ItemPickerPanel;
import net.runelite.client.plugins.unforgecommon.PopoutWindow;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

@Slf4j
public class UnforgeClonePanel extends PluginPanel
{
	private static final File CLONE_DIR = new File(
		"C:/Users/HOST/Desktop/UNFORGE-239-STANDALONE-LAB/.data/content/items/clones");
	private static final Color ACCENT = new Color(0, 174, 204);

	private final UnforgeClonePlugin plugin;
	private final Client client;
	private final ClientThread clientThread;
	private final PopoutWindow popout = new PopoutWindow();
	private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

	private int baseId = -1;
	private String baseName = "None";
	private short[] src = new short[0];
	private short[] dst = new short[0];
	private short[] originalDst = new short[0];

	private final JLabel itemLabel = new JLabel("No item selected - search below");
	private final JTextField idField = new JTextField();
	private final JTextField nameField = new JTextField();
	private final JTextField internalField = new JTextField();
	private final JTextField descField = new JTextField();

	private final DefaultTableModel colorModel = new DefaultTableModel(new String[]{"#", "Source", "New"}, 0)
	{
		@Override
		public boolean isCellEditable(int row, int col)
		{
			return false;
		}
	};
	private final JTable colorTable = new JTable(colorModel);
	private final JComboBox<CloneColors.Theme> themeBox = new JComboBox<>(CloneColors.Theme.values());
	private final JSlider hueSlider = new JSlider(0, 63, 0);
	private final JCheckBox previewBox = new JCheckBox("Live preview", true);
	private final JLabel statusLabel = new JLabel(" ");
	private JButton popOutBtn;

	public UnforgeClonePanel(UnforgeClonePlugin plugin, Client client, ClientThread clientThread)
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
	}

	private JPanel buildHeader()
	{
		JPanel header = new JPanel();
		header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
		header.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		header.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(ACCENT, 1),
			new EmptyBorder(8, 8, 8, 8)));

		JPanel titleRow = new JPanel(new BorderLayout());
		titleRow.setOpaque(false);
		JLabel title = new JLabel("✦ ITEM CLONE & RECOLOUR");
		title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
		title.setForeground(ACCENT);
		titleRow.add(title, BorderLayout.WEST);
		popOutBtn = new JButton("⤢");
		popOutBtn.setToolTipText("Pop out into a resizable window");
		popOutBtn.setMargin(new java.awt.Insets(1, 6, 1, 6));
		popOutBtn.addActionListener(e -> togglePopOut());
		titleRow.add(popOutBtn, BorderLayout.EAST);
		header.add(titleRow);

		itemLabel.setForeground(Color.WHITE);
		itemLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
		itemLabel.setBorder(new EmptyBorder(4, 0, 2, 0));
		header.add(itemLabel);

		return header;
	}

	private JPanel buildEditor()
	{
		AccordionColumn editor = new AccordionColumn();

		ItemPickerPanel picker = new ItemPickerPanel(client, clientThread, items ->
		{
			if (!items.isEmpty())
			{
				loadBase(items.get(0).id);
			}
		}, 4);
		editor.addSection("Item Search", picker, true, ACCENT, true);

		JPanel fields = new JPanel(new GridLayout(0, 2, 4, 2));
		fields.setOpaque(false);
		fields.add(new JLabel("New ID:"));
		fields.add(idField);
		fields.add(new JLabel("Name:"));
		fields.add(nameField);
		fields.add(new JLabel("Internal:"));
		fields.add(internalField);
		fields.add(new JLabel("Desc:"));
		fields.add(descField);
		idField.setToolTipText("Clone id - leave same as base to recolour the original item");
		internalField.setToolTipText("Symbol name (blank = auto clone_<id>)");
		editor.addSection("Clone", fields, true, ACCENT, false);

		colorTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		colorTable.setFillsViewportHeight(true);
		colorTable.getColumnModel().getColumn(0).setMaxWidth(30);
		DefaultTableCellRenderer swatch = new SwatchRenderer();
		colorTable.getColumnModel().getColumn(1).setCellRenderer(swatch);
		colorTable.getColumnModel().getColumn(2).setCellRenderer(swatch);
		colorTable.addMouseListener(new java.awt.event.MouseAdapter()
		{
			@Override
			public void mouseClicked(java.awt.event.MouseEvent e)
			{
				int row = colorTable.rowAtPoint(e.getPoint());
				int col = colorTable.columnAtPoint(e.getPoint());
				if (row >= 0 && col == 2)
				{
					pickDstColor(row);
				}
			}
		});

		JPanel colorSection = new JPanel(new BorderLayout(0, 4));
		colorSection.setOpaque(false);

		JScrollPane scroll = new JScrollPane(colorTable);
		scroll.setBorder(BorderFactory.createTitledBorder(
			BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
			"Colours (click New cell to recolour)"));
		scroll.setPreferredSize(new Dimension(0, 140));
		colorSection.add(scroll, BorderLayout.CENTER);

		JPanel colorBottom = new JPanel();
		colorBottom.setLayout(new BoxLayout(colorBottom, BoxLayout.Y_AXIS));
		colorBottom.setOpaque(false);

		JPanel themeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 2));
		themeRow.setOpaque(false);
		themeRow.add(new JLabel("Theme:"));
		themeRow.add(themeBox);
		JButton applyTheme = new JButton("Apply all");
		applyTheme.setToolTipText("Recolour every slot with the selected theme");
		applyTheme.addActionListener(e -> applyTheme());
		themeRow.add(applyTheme);
		colorBottom.add(themeRow);

		JPanel hueRow = new JPanel(new BorderLayout(4, 0));
		hueRow.setOpaque(false);
		hueSlider.setPaintTicks(true);
		hueSlider.setMajorTickSpacing(16);
		hueSlider.setToolTipText("Jagex hue 0-63");
		hueRow.add(hueSlider, BorderLayout.CENTER);
		JButton applyHue = new JButton("Set hue");
		applyHue.setToolTipText("Set all colours to this hue (keeps shading)");
		applyHue.addActionListener(e -> setDst(CloneColors.withHue(src, hueSlider.getValue())));
		hueRow.add(applyHue, BorderLayout.EAST);
		colorBottom.add(hueRow);

		JPanel miscRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 2));
		miscRow.setOpaque(false);
		JButton pickAll = new JButton("Pick colour...");
		pickAll.setToolTipText("Tint every slot with one chosen colour");
		pickAll.addActionListener(e -> pickAllColor());
		miscRow.add(pickAll);
		JButton reset = new JButton("Reset");
		reset.addActionListener(e -> setDst(src.clone()));
		miscRow.add(reset);
		miscRow.add(previewBox);
		previewBox.setOpaque(false);
		previewBox.setForeground(Color.WHITE);
		previewBox.addActionListener(e -> applyPreview());
		colorBottom.add(miscRow);
		colorSection.add(colorBottom, BorderLayout.SOUTH);

		editor.addSection("Recolour", colorSection, true, ACCENT, true);

		return editor;
	}

	private JPanel buildFooter()
	{
		JPanel footer = new JPanel();
		footer.setLayout(new BoxLayout(footer, BoxLayout.Y_AXIS));
		footer.setOpaque(false);

		JButton save = new JButton("💾 Save clone JSON");
		save.setToolTipText("Writes to .data/content/items/clones - server packs it on boot or ::reloadclones");
		save.addActionListener(e -> saveClone());
		footer.add(save);

		statusLabel.setForeground(new Color(120, 200, 120));
		statusLabel.setBorder(new EmptyBorder(4, 0, 0, 0));
		footer.add(statusLabel);
		return footer;
	}

	private void loadBase(int id)
	{
		clientThread.invokeLater(() ->
		{
			ItemComposition comp;
			try
			{
				comp = client.getItemDefinition(id);
			}
			catch (Exception ex)
			{
				SwingUtilities.invokeLater(() -> status("Item " + id + " not found"));
				return;
			}
			short[] s = comp.getColorToReplace();
			short[] d = comp.getColorToReplaceWith();
			String name = comp.getName();
			SwingUtilities.invokeLater(() ->
			{
				baseId = id;
				baseName = name;
				src = s != null ? s.clone() : new short[0];
				originalDst = d != null ? d.clone() : src.clone();
				dst = originalDst.clone();
				itemLabel.setText("Item: " + name + " (ID: " + id + ")");
				nameField.setText(name);
				internalField.setText("");
				if (idField.getText().isBlank() || !idField.getText().trim().equals(String.valueOf(id)))
				{
					idField.setText(String.valueOf(id));
				}
				refreshTable();
				if (src.length == 0)
				{
					status("Item has no recolourable palette - clone will only rename");
				}
				else
				{
					status(src.length + " colour slot(s) loaded");
				}
			});
		});
	}

	private void refreshTable()
	{
		colorModel.setRowCount(0);
		for (int i = 0; i < src.length; i++)
		{
			colorModel.addRow(new Object[]{i, src[i], i < dst.length ? dst[i] : src[i]});
		}
	}

	private void setDst(short[] newDst)
	{
		dst = newDst.clone();
		refreshTable();
		applyPreview();
	}

	private void applyTheme()
	{
		CloneColors.Theme theme = (CloneColors.Theme) themeBox.getSelectedItem();
		if (theme == null || src.length == 0)
		{
			return;
		}
		setDst(CloneColors.apply(theme, src));
	}

	private void pickAllColor()
	{
		Color c = JColorChooser.showDialog(this, "Tint all colours", Color.WHITE);
		if (c != null && src.length > 0)
		{
			setDst(CloneColors.withColor(src, c.getRGB() & 0xFFFFFF));
		}
	}

	private void pickDstColor(int row)
	{
		Color current = new Color(CloneColors.hslToRgb(dst[row]));
		Color c = JColorChooser.showDialog(this, "Colour slot " + row, current);
		if (c != null)
		{
			dst[row] = JagexColor.rgbToHSL(c.getRGB() & 0xFFFFFF, 1.0);
			refreshTable();
			applyPreview();
		}
	}

	private void applyPreview()
	{
		if (baseId < 0)
		{
			return;
		}
		boolean on = previewBox.isSelected();
		short[] colors = dst.clone();
		short[] restore = originalDst.clone();
		clientThread.invokeLater(() ->
		{
			try
			{
				ItemComposition comp = client.getItemDefinition(baseId);
				comp.setColorToReplaceWith(on ? colors : restore);
			}
			catch (Exception ignored)
			{
			}
		});
	}

	private void saveClone()
	{
		if (baseId < 0)
		{
			status("Select a base item first");
			return;
		}
		int id;
		try
		{
			id = Integer.parseInt(idField.getText().trim());
		}
		catch (NumberFormatException ex)
		{
			status("Invalid clone id");
			return;
		}
		String name = nameField.getText().trim();
		if (name.isEmpty())
		{
			name = baseName;
		}
		String internal = internalField.getText().trim();
		if (internal.isEmpty())
		{
			internal = "clone_" + id;
		}
		JsonObject json = new JsonObject();
		json.addProperty("id", id);
		json.addProperty("base", baseId);
		json.addProperty("name", name);
		json.addProperty("internal", internal);
		json.addProperty("desc", descField.getText().trim());
		JsonArray colors = new JsonArray();
		for (short s : dst)
		{
			colors.add(s & 0xFFFF);
		}
		json.add("colors", colors);

		File out = new File(CLONE_DIR, internal + ".json");
		try
		{
			Files.createDirectories(CLONE_DIR.toPath());
			Files.write(out.toPath(), gson.toJson(json).getBytes(StandardCharsets.UTF_8));
			status("Saved " + out.getName() + " - restart server or ::reloadclones");
		}
		catch (IOException ex)
		{
			log.warn("Failed to save clone", ex);
			status("Save failed: " + ex.getMessage());
		}
	}

	private void status(String msg)
	{
		statusLabel.setText(msg);
	}

	private void togglePopOut()
	{
		popout.toggle(this, "Unforge Item Clone", 500, 760, () ->
		{
			popOutBtn.setText("⤢");
			plugin.reopenPanel();
		});
		if (popout.isOpen())
		{
			popOutBtn.setText("⤡");
		}
	}

	void closePopout()
	{
		if (popout.isOpen())
		{
			popout.dock(this, null);
		}
	}

	private class SwatchRenderer extends DefaultTableCellRenderer
	{
		@Override
		public Component getTableCellRendererComponent(JTable table, Object value,
			boolean isSelected, boolean hasFocus, int row, int column)
		{
			JLabel label = (JLabel) super.getTableCellRendererComponent(
				table, value, isSelected, hasFocus, row, column);
			if (value instanceof Short)
			{
				short hsl = (Short) value;
				label.setText(JagexColor.formatHSL(hsl));
				label.setOpaque(true);
				label.setBackground(new Color(CloneColors.hslToRgb(hsl)));
				label.setForeground(contrast(CloneColors.hslToRgb(hsl)));
			}
			return label;
		}

		private Color contrast(int rgb)
		{
			int r = rgb >> 16 & 255, g = rgb >> 8 & 255, b = rgb & 255;
			return (r * 299 + g * 587 + b * 114) / 1000 > 140 ? Color.BLACK : Color.WHITE;
		}
	}
}

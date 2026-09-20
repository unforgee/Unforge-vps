package net.runelite.client.plugins.unforgecache;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

/**
 * Side panel for the Unforge Cache plugin.
 *
 * <p>Lists the sprites the client knows by name, previews the selected one, and
 * replaces it with a PNG. The panel is a plain Swing component inside the client's
 * sidebar, so widening the sidebar by dragging its edge widens the panel with it and
 * the sprite list and preview both grow.
 */
@Slf4j
class UnforgeCachePanel extends PluginPanel
{
	private static final int PREVIEW_SIZE = 96;

	/**
	 * The sidebar is locked to 225px by the client, which is too narrow to work in, so
	 * the panel can be popped out into its own resizable window instead.
	 */
	private static final int POP_OUT_WIDTH = 460;
	private static final int POP_OUT_HEIGHT = 800;
	private static final String POP_OUT_GLYPH = "\u23E2";
	private static final String DOCK_GLYPH = "\u23E1";
	private static final String POP_OUT_TOOLTIP = "Pop out into a resizable window";

	/** Everything the panel needs from the plugin; keeps client-thread work out of the UI. */
	interface Actions
	{
		void loadSprite(int spriteId, int frame, Consumer<BufferedImage> onImage);

		void applyReplacement(int spriteId, int frame, File png);

		void revertReplacement(int spriteId, int frame);

		boolean isApplied(int spriteId, int frame);

		boolean hasStoredPng(int spriteId, int frame);

		Path overrideFolder();

		/** Puts the panel back in the sidebar after it has been popped out. */
		void reopenPanel();
	}

	private final DefaultListModel<SpriteCatalog.NamedSprite> listModel = new DefaultListModel<>();
	private final JList<SpriteCatalog.NamedSprite> list = new JList<>(listModel);
	private final JTextField filter = new JTextField();
	private final JSpinner frameSpinner =
		new JSpinner(new SpinnerNumberModel(0, 0, 255, 1));
	private final JLabel preview = new JLabel();
	private final JLabel title = new JLabel("Pick a sprite");
	private final JLabel status = new JLabel(" ");
	private final JButton replace = new JButton("Replace from PNG…");
	private final JButton export = new JButton("Export PNG…");
	private final JButton revert = new JButton("Revert");

	private final List<SpriteCatalog.NamedSprite> all;

	private JButton popOutBtn;
	private JFrame popOutFrame;

	private Actions actions;

	UnforgeCachePanel()
	{
		super(false);
		this.all = SpriteCatalog.load();

		setLayout(new BorderLayout(0, 8));
		setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

		add(buildHeader(), BorderLayout.NORTH);
		add(buildList(), BorderLayout.CENTER);
		add(buildDetail(), BorderLayout.SOUTH);

		applyFilter();
		status.setText(all.isEmpty()
			? "No named sprites found — type an id below."
			: all.size() + " named sprites");
	}

	void init(Actions actions)
	{
		this.actions = actions;
	}

	/** Jumps the selection to a sprite id, adding it to the list if it is not named. */
	void select(int spriteId)
	{
		for (int i = 0; i < listModel.size(); i++)
		{
			if (listModel.get(i).id() == spriteId)
			{
				list.setSelectedIndex(i);
				list.ensureIndexIsVisible(i);
				return;
			}
		}

		SpriteCatalog.NamedSprite unnamed = new SpriteCatalog.NamedSprite(spriteId, "sprite " + spriteId);
		listModel.addElement(unnamed);
		list.setSelectedIndex(listModel.size() - 1);
		list.ensureIndexIsVisible(listModel.size() - 1);
	}

	/* --------------------------------------------------------------- layout */

	private JPanel buildHeader()
	{
		JPanel header = new JPanel(new BorderLayout(0, 6));

		JPanel titleRow = new JPanel(new BorderLayout());
		titleRow.setOpaque(false);

		JLabel heading = new JLabel("UNFORGE CACHE");
		heading.setForeground(ColorScheme.BRAND_ORANGE);
		titleRow.add(heading, BorderLayout.WEST);

		popOutBtn = new JButton(POP_OUT_GLYPH);
		popOutBtn.setToolTipText(POP_OUT_TOOLTIP);
		popOutBtn.setMargin(new Insets(1, 6, 1, 6));
		popOutBtn.addActionListener(e -> togglePopOut());
		titleRow.add(popOutBtn, BorderLayout.EAST);

		header.add(titleRow, BorderLayout.NORTH);

		filter.setToolTipText("Filter by sprite name or id");
		filter.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				applyFilter();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				applyFilter();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				applyFilter();
			}
		});
		header.add(filter, BorderLayout.CENTER);

		JLabel hint = new JLabel("Sidebar is fixed at 225px \u2014 " + POP_OUT_GLYPH + " for a bigger workspace.");
		hint.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		header.add(hint, BorderLayout.SOUTH);
		return header;
	}

	/* ------------------------------------------------------------- pop out */

	/**
	 * Moves the panel between the sidebar and its own window.
	 *
	 * <p>The panel component itself is what moves, so there is only ever one instance:
	 * the sprite list, filter and selection are the same in both places, and nothing has
	 * to be kept in sync between them.
	 */
	private void togglePopOut()
	{
		if (popOutFrame != null)
		{
			dockPanel();
			return;
		}

		popOutFrame = new JFrame("Unforge Cache");
		popOutFrame.setAlwaysOnTop(true);
		popOutFrame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		popOutFrame.setMinimumSize(new Dimension(340, 420));
		popOutFrame.addWindowListener(new WindowAdapter()
		{
			@Override
			public void windowClosing(WindowEvent e)
			{
				dockPanel();
			}
		});
		popOutFrame.getContentPane().add(this);
		popOutFrame.setSize(POP_OUT_WIDTH, POP_OUT_HEIGHT);
		popOutFrame.setLocationByPlatform(true);
		popOutFrame.setVisible(true);

		popOutBtn.setText(DOCK_GLYPH);
		popOutBtn.setToolTipText("Dock back into the sidebar");
		refreshSelection();
	}

	private void dockPanel()
	{
		boolean wasPoppedOut = popOutFrame != null;
		if (wasPoppedOut)
		{
			popOutFrame.getContentPane().remove(this);
			popOutFrame.dispose();
			popOutFrame = null;
		}

		popOutBtn.setText(POP_OUT_GLYPH);
		popOutBtn.setToolTipText(POP_OUT_TOOLTIP);

		if (wasPoppedOut && actions != null)
		{
			actions.reopenPanel();
		}
	}

	/** Closes the popped-out window, if there is one, without touching the sidebar. */
	void disposePopOut()
	{
		if (popOutFrame != null)
		{
			popOutFrame.getContentPane().remove(this);
			popOutFrame.dispose();
			popOutFrame = null;
		}
		if (popOutBtn != null)
		{
			popOutBtn.setText(POP_OUT_GLYPH);
			popOutBtn.setToolTipText(POP_OUT_TOOLTIP);
		}
	}

	private JScrollPane buildList()
	{
		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		list.setVisibleRowCount(12);
		list.addListSelectionListener(e ->
		{
			if (!e.getValueIsAdjusting())
			{
				refreshSelection();
			}
		});
		return new JScrollPane(list);
	}

	private JPanel buildDetail()
	{
		JPanel detail = new JPanel();
		detail.setLayout(new BoxLayout(detail, BoxLayout.Y_AXIS));

		title.setAlignmentX(LEFT_ALIGNMENT);
		detail.add(title);
		detail.add(Box.createVerticalStrut(6));

		preview.setPreferredSize(new Dimension(PREVIEW_SIZE, PREVIEW_SIZE));
		preview.setMinimumSize(new Dimension(PREVIEW_SIZE, PREVIEW_SIZE));
		preview.setHorizontalAlignment(JLabel.CENTER);
		preview.setVerticalAlignment(JLabel.CENTER);
		preview.setOpaque(true);
		preview.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		preview.setBorder(BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR));
		preview.setAlignmentX(LEFT_ALIGNMENT);
		detail.add(preview);
		detail.add(Box.createVerticalStrut(6));

		JPanel frameRow = new JPanel(new BorderLayout(6, 0));
		frameRow.setAlignmentX(LEFT_ALIGNMENT);
		frameRow.add(new JLabel("Frame"), BorderLayout.WEST);
		frameSpinner.addChangeListener(e -> refreshSelection());
		frameRow.add(frameSpinner, BorderLayout.CENTER);
		detail.add(frameRow);
		detail.add(Box.createVerticalStrut(6));

		JPanel buttons = new JPanel(new GridLayout(0, 1, 0, 4));
		buttons.setAlignmentX(LEFT_ALIGNMENT);
		replace.addActionListener(e -> replaceFromPng());
		export.addActionListener(e -> exportPng());
		revert.addActionListener(e -> revertSprite());
		buttons.add(replace);
		buttons.add(export);
		buttons.add(revert);
		detail.add(buttons);
		detail.add(Box.createVerticalStrut(6));

		JButton openFolder = new JButton("Open replacements folder");
		openFolder.setAlignmentX(LEFT_ALIGNMENT);
		openFolder.addActionListener(e -> openFolder());
		detail.add(openFolder);
		detail.add(Box.createVerticalStrut(6));

		status.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		status.setAlignmentX(LEFT_ALIGNMENT);
		detail.add(status);
		return detail;
	}

	/* -------------------------------------------------------------- actions */

	private void applyFilter()
	{
		String needle = filter.getText().trim().toLowerCase();
		SpriteCatalog.NamedSprite selected = list.getSelectedValue();

		listModel.clear();
		for (SpriteCatalog.NamedSprite sprite : all)
		{
			if (SpriteCatalog.matches(sprite, needle))
			{
				listModel.addElement(sprite);
			}
		}

		if (selected != null && listModel.contains(selected))
		{
			list.setSelectedValue(selected, true);
		}
		else if (!listModel.isEmpty())
		{
			list.setSelectedIndex(0);
		}
	}

	private void refreshSelection()
	{
		SpriteCatalog.NamedSprite sprite = list.getSelectedValue();
		if (sprite == null || actions == null)
		{
			title.setText("Pick a sprite");
			preview.setIcon(null);
			preview.setText(" ");
			replace.setEnabled(false);
			export.setEnabled(false);
			revert.setEnabled(false);
			return;
		}

		int frame = (Integer) frameSpinner.getValue();
		title.setText("<html><b>" + escape(sprite.name()) + "</b><br>id " + sprite.id()
			+ ", frame " + frame + "</html>");
		replace.setEnabled(true);
		export.setEnabled(true);
		revert.setEnabled(actions.isApplied(sprite.id(), frame));

		boolean stored = actions.hasStoredPng(sprite.id(), frame);
		status.setText(actions.isApplied(sprite.id(), frame)
			? "Replaced — live now, original untouched."
			: stored ? "Replacement saved but not applied." : "Original sprite.");

		preview.setIcon(null);
		preview.setText("loading…");
		actions.loadSprite(sprite.id(), frame, image -> SwingUtilities.invokeLater(() ->
		{
			SpriteCatalog.NamedSprite current = list.getSelectedValue();
			if (current == null || current.id() != sprite.id() || (Integer) frameSpinner.getValue() != frame)
			{
				return;
			}
			if (image == null)
			{
				preview.setText("not in cache");
				return;
			}
			preview.setText(null);
			preview.setIcon(new ImageIcon(scale(image, PREVIEW_SIZE)));
		}));
	}

	private void replaceFromPng()
	{
		SpriteCatalog.NamedSprite sprite = list.getSelectedValue();
		if (sprite == null || actions == null)
		{
			return;
		}

		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle("Choose a PNG to use for " + sprite.name());
		chooser.setFileFilter(new FileNameExtensionFilter("PNG images", "png"));
		chooser.setAcceptAllFileFilterUsed(false);
		if (actions.overrideFolder() != null)
		{
			chooser.setCurrentDirectory(actions.overrideFolder().toFile());
		}
		if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION)
		{
			return;
		}

		int frame = (Integer) frameSpinner.getValue();
		actions.applyReplacement(sprite.id(), frame, chooser.getSelectedFile());
		SwingUtilities.invokeLater(() ->
		{
			status.setText("Replaced — live now, original untouched.");
			refreshSelection();
		});
	}

	private void exportPng()
	{
		final SpriteCatalog.NamedSprite sprite = list.getSelectedValue();
		if (sprite == null || actions == null)
		{
			return;
		}
		final int frame = (Integer) frameSpinner.getValue();

		actions.loadSprite(sprite.id(), frame, image ->
		{
			if (image == null)
			{
				SwingUtilities.invokeLater(() -> status.setText("Nothing to export for that frame."));
				return;
			}
			SwingUtilities.invokeLater(() ->
			{
				JFileChooser chooser = new JFileChooser();
				chooser.setDialogTitle("Export " + sprite.name());
				chooser.setSelectedFile(new File(sprite.id() + "_" + frame + ".png"));
				if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION)
				{
					return;
				}
				try
				{
					File target = chooser.getSelectedFile();
					if (!target.getName().toLowerCase().endsWith(".png"))
					{
						target = new File(target.getParentFile(), target.getName() + ".png");
					}
					javax.imageio.ImageIO.write(image, "png", target);
					status.setText("Exported " + target.getName());
				}
				catch (Exception e)
				{
					log.warn("Unforge Cache: export failed", e);
					status.setText("Export failed: " + e.getMessage());
				}
			});
		});
	}

	private void revertSprite()
	{
		SpriteCatalog.NamedSprite sprite = list.getSelectedValue();
		if (sprite == null || actions == null)
		{
			return;
		}
		int frame = (Integer) frameSpinner.getValue();
		actions.revertReplacement(sprite.id(), frame);
		SwingUtilities.invokeLater(() ->
		{
			status.setText(actions.hasStoredPng(sprite.id(), frame)
				? "Reverted — showing the original again."
				: "Reverted and the stored PNG was removed.");
			refreshSelection();
		});
	}

	private void openFolder()
	{
		if (actions == null || actions.overrideFolder() == null)
		{
			return;
		}
		try
		{
			java.awt.Desktop.getDesktop().open(actions.overrideFolder().toFile());
		}
		catch (Exception e)
		{
			JOptionPane.showMessageDialog(this,
				"Replacements are in:\n" + actions.overrideFolder(),
				"Unforge Cache", JOptionPane.INFORMATION_MESSAGE);
		}
	}

	/* --------------------------------------------------------------- helpers */

	private static BufferedImage scale(BufferedImage source, int max)
	{
		int width = source.getWidth();
		int height = source.getHeight();
		if (width <= max && height <= max)
		{
			return source;
		}
		double factor = Math.min((double) max / width, (double) max / height);
		int targetWidth = Math.max(1, (int) Math.round(width * factor));
		int targetHeight = Math.max(1, (int) Math.round(height * factor));
		BufferedImage scaled = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
		java.awt.Graphics2D graphics = scaled.createGraphics();
		try
		{
			graphics.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
				java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
			graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
		}
		finally
		{
			graphics.dispose();
		}
		return scaled;
	}

	private static String escape(String text)
	{
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}

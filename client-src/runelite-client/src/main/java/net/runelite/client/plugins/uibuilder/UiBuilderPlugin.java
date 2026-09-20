package net.runelite.client.plugins.uibuilder;

import com.google.inject.Provides;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import javax.imageio.ImageIO;
import javax.inject.Inject;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.TransferHandler;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;

@PluginDescriptor(name = "RSPS2 UI Builder", description = "Build and arrange RSPS2 r239 interfaces.", tags = {"rsps2", "ui", "builder", "interface"}, enabledByDefault = false)
public class UiBuilderPlugin extends Plugin
{
	@Inject private ClientToolbar pluginToolbar;
	@Inject private UiBuilderConfig config;
	private NavigationButton navigationButton;

	@Provides
	UiBuilderConfig provideConfig(ConfigManager manager)
	{
		return manager.getConfig(UiBuilderConfig.class);
	}

	@Override
	protected void startUp()
	{
		UiBuilderPanel panel = new UiBuilderPanel();
		navigationButton = NavigationButton.builder().tooltip("RSPS2 UI Builder").panel(panel).build();
		pluginToolbar.addNavigation(navigationButton);
	}

	@Override
	protected void shutDown()
	{
		if (navigationButton != null)
		{
			pluginToolbar.removeNavigation(navigationButton);
			navigationButton = null;
		}
	}

	private static final class UiBuilderPanel extends PluginPanel
	{
		private final JPanel canvas = new JPanel(null);
		private final JTextField text = new JTextField("New text", 14);
		private int nextY = 16;

		UiBuilderPanel()
		{
			setLayout(new BorderLayout(4, 4));
			JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 3));
			JButton addText = new JButton("Text");
			JButton addButton = new JButton("Button");
			addText.addActionListener(e -> addLabel(text.getText()));
			addButton.addActionListener(e -> addLabel("Action button"));
			toolbar.add(text);
			toolbar.add(addText);
			toolbar.add(addButton);
			add(toolbar, BorderLayout.NORTH);
			canvas.setBackground(new Color(35, 35, 35));
			canvas.setPreferredSize(new Dimension(280, 520));
			canvas.setBorder(BorderFactory.createLineBorder(new Color(120, 120, 120)));
			canvas.setTransferHandler(new ImageDropHandler());
			add(new JScrollPane(canvas), BorderLayout.CENTER);
			addLabel("Drop an image here or add an element");
		}

		private void addLabel(String value)
		{
			JLabel label = new JLabel(value);
			label.setForeground(Color.WHITE);
			label.setFont(label.getFont().deriveFont(Font.PLAIN, 13f));
			label.setBounds(12, nextY, 250, 28);
			nextY += 34;
			DragHandler drag = new DragHandler(label);
			label.addMouseListener(drag);
			label.addMouseMotionListener(drag);
			canvas.add(label);
			canvas.revalidate();
			canvas.repaint();
		}

		private final class ImageDropHandler extends TransferHandler
		{
			@Override public boolean canImport(TransferSupport support)
			{
				return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
			}
			@Override public boolean importData(TransferSupport support)
			{
				try
				{
					Transferable data = support.getTransferable();
					@SuppressWarnings("unchecked") List<File> files = (List<File>) data.getTransferData(DataFlavor.javaFileListFlavor);
					if (files.isEmpty()) return false;
					BufferedImage image = ImageIO.read(files.get(0));
					if (image == null) return false;
					int max = 240;
					double scale = Math.min(1d, Math.min((double) max / image.getWidth(), (double) max / image.getHeight()));
					int width = Math.max(1, (int) Math.round(image.getWidth() * scale));
					int height = Math.max(1, (int) Math.round(image.getHeight() * scale));
					JLabel preview = new JLabel(new javax.swing.ImageIcon(image.getScaledInstance(width, height, java.awt.Image.SCALE_SMOOTH)));
					preview.setBounds(12, nextY, width, height);
					nextY += height + 8;
					DragHandler drag = new DragHandler(preview);
					preview.addMouseListener(drag);
					preview.addMouseMotionListener(drag);
					canvas.add(preview);
					canvas.revalidate(); canvas.repaint();
					return true;
				}
				catch (Exception ignored) { return false; }
			}
		}

		private static final class DragHandler extends MouseAdapter
		{
			private final JLabel component; private int x; private int y;
			DragHandler(JLabel component) { this.component = component; }
			@Override public void mousePressed(MouseEvent event) { x = event.getX(); y = event.getY(); }
			@Override public void mouseDragged(MouseEvent event)
			{
				component.setLocation(Math.max(0, component.getX() + event.getX() - x), Math.max(0, component.getY() + event.getY() - y));
			}
		}
	}
}

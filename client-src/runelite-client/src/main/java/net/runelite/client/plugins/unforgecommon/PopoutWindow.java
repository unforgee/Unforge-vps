package net.runelite.client.plugins.unforgecommon;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JComponent;
import javax.swing.JFrame;

/**
 * Pops a sidebar panel out into a resizable always-on-top JFrame and docks it
 * back via the supplied callback (typically clientToolbar.openPanel(navButton)).
 */
public class PopoutWindow
{
	private JFrame frame;

	public boolean isOpen()
	{
		return frame != null;
	}

	public void toggle(JComponent content, String title, int width, int height, Runnable onDock)
	{
		if (frame == null)
		{
			frame = new JFrame(title);
			frame.setAlwaysOnTop(true);
			frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
			frame.addWindowListener(new WindowAdapter()
			{
				@Override
				public void windowClosing(WindowEvent e)
				{
					dock(content, onDock);
				}
			});
			frame.getContentPane().add(content);
			frame.setSize(width, height);
			frame.setLocationByPlatform(true);
			frame.setVisible(true);
		}
		else
		{
			dock(content, onDock);
		}
	}

	public void dock(JComponent content, Runnable onDock)
	{
		if (frame != null)
		{
			frame.getContentPane().remove(content);
			frame.dispose();
			frame = null;
		}
		if (onDock != null)
		{
			onDock.run();
		}
	}
}

package net.runelite.client.plugins.unforgeadmin;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

final class UnforgePluginIcon
{
	private UnforgePluginIcon()
	{
	}

	static BufferedImage create(Color color, boolean pin)
	{
		BufferedImage image = new BufferedImage(24, 24, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		try
		{
			graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			graphics.setColor(new Color(35, 35, 35, 235));
			graphics.fillRoundRect(1, 1, 22, 22, 6, 6);
			graphics.setColor(color);
			graphics.setStroke(new BasicStroke(2.5f));
			if (pin)
			{
				graphics.drawOval(6, 4, 12, 12);
				graphics.drawLine(12, 16, 12, 21);
				graphics.drawLine(9, 19, 15, 19);
			}
			else
			{
				graphics.drawRect(5, 5, 14, 14);
				graphics.drawLine(8, 12, 16, 12);
				graphics.drawLine(12, 8, 12, 16);
			}
		}
		finally
		{
			graphics.dispose();
		}
		return image;
	}
}

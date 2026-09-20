package net.runelite.client.plugins.unforgecommon;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

public final class UnforgeIcons
{
	private UnforgeIcons()
	{
	}

	/** Simple generated sidebar icon: coloured rounded square with a letter. */
	public static BufferedImage letter(Color color, String letter)
	{
		BufferedImage img = new BufferedImage(18, 18, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(color);
		g.fillRoundRect(1, 1, 16, 16, 5, 5);
		g.setColor(Color.BLACK);
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
		int w = g.getFontMetrics().stringWidth(letter);
		g.drawString(letter, (18 - w) / 2, 13);
		g.dispose();
		return img;
	}
}

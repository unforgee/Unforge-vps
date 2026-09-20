package net.runelite.client.plugins.unforgecommon;

import net.runelite.api.JagexColor;

/**
 * Jagex-HSL colour math for item recolouring. Jagex packs colours as
 * hue (0-63) / saturation (0-7) / luminance (0-127) into a short.
 */
public final class CloneColors
{
	public enum Theme
	{
		ORIGINAL("Original"),
		BLOOD("Blood red"),
		GOLD("Gold"),
		FOREST("Forest green"),
		OCEAN("Ocean blue"),
		ICE("Ice cyan"),
		VOID("Void purple"),
		PINK("Pink"),
		SHADOW("Shadow (dark)"),
		BONE("Bone white"),
		RAINBOW("Rainbow");

		final String label;

		Theme(String label)
		{
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	private CloneColors()
	{
	}

	/** Jagex hue values for the fixed-hue themes (0-63 wraps the colour wheel). */
	private static final int HUE_RED = 0;
	private static final int HUE_GOLD = 9;
	private static final int HUE_GREEN = 21;
	private static final int HUE_CYAN = 31;
	private static final int HUE_BLUE = 42;
	private static final int HUE_PURPLE = 50;
	private static final int HUE_PINK = 55;

	/** Apply a theme to every source colour, preserving the luminance ramp so shading survives. */
	public static short[] apply(Theme theme, short[] src)
	{
		short[] out = src.clone();
		for (int i = 0; i < out.length; i++)
		{
			out[i] = apply(theme, src[i], i);
		}
		return out;
	}

	private static short apply(Theme theme, short src, int index)
	{
		switch (theme)
		{
			case ORIGINAL:
				return src;
			case BLOOD:
				return withHue(src, HUE_RED);
			case GOLD:
				return withHue(src, HUE_GOLD);
			case FOREST:
				return withHue(src, HUE_GREEN);
			case OCEAN:
				return withHue(src, HUE_BLUE);
			case ICE:
				return withHue(src, HUE_CYAN);
			case VOID:
				return withHue(src, HUE_PURPLE);
			case PINK:
				return withHue(src, HUE_PINK);
			case SHADOW:
				return JagexColor.packHSL(JagexColor.unpackHue(src), JagexColor.unpackSaturation(src),
					Math.max(4, JagexColor.unpackLuminance(src) * 40 / 100));
			case BONE:
				return JagexColor.packHSL(JagexColor.unpackHue(src), 0,
					Math.min(JagexColor.LUMINANCE_MAX, JagexColor.unpackLuminance(src) * 130 / 100 + 12));
			case RAINBOW:
				return withHue(src, index * 9 % 64);
			default:
				return src;
		}
	}

	/** Replace only the hue, keeping source saturation + luminance (keeps shading). */
	public static short withHue(short src, int hue)
	{
		return JagexColor.packHSL(hue, JagexColor.unpackSaturation(src), JagexColor.unpackLuminance(src));
	}

	public static short[] withHue(short[] src, int hue)
	{
		short[] out = src.clone();
		for (int i = 0; i < out.length; i++)
		{
			out[i] = withHue(out[i], hue);
		}
		return out;
	}

	/** Take hue+saturation from an RGB colour, keep each source luminance (tint). */
	public static short[] withColor(short[] src, int rgb)
	{
		short picked = JagexColor.rgbToHSL(rgb, 1.0);
		int h = JagexColor.unpackHue(picked);
		int s = JagexColor.unpackSaturation(picked);
		short[] out = src.clone();
		for (int i = 0; i < out.length; i++)
		{
			out[i] = JagexColor.packHSL(h, s, JagexColor.unpackLuminance(src[i]));
		}
		return out;
	}

	/** Approximate RGB for a Jagex-HSL value - used for UI swatches only. */
	public static int hslToRgb(short hsl)
	{
		float h = JagexColor.unpackHue(hsl) / 63f;
		float s = JagexColor.unpackSaturation(hsl) / 7f;
		float l = JagexColor.unpackLuminance(hsl) / 127f;
		if (s <= 0f)
		{
			int g = Math.round(Math.min(1f, l) * 255);
			return g << 16 | g << 8 | g;
		}
		float q = l < 0.5f ? l * (1f + s) : l + s - l * s;
		float p = 2f * l - q;
		int r = Math.round(hueToRgb(p, q, h + 1f / 3f) * 255);
		int g = Math.round(hueToRgb(p, q, h) * 255);
		int b = Math.round(hueToRgb(p, q, h - 1f / 3f) * 255);
		return clamp(r) << 16 | clamp(g) << 8 | clamp(b);
	}

	private static float hueToRgb(float p, float q, float t)
	{
		if (t < 0f)
		{
			t += 1f;
		}
		if (t > 1f)
		{
			t -= 1f;
		}
		if (t < 1f / 6f)
		{
			return p + (q - p) * 6f * t;
		}
		if (t < 1f / 2f)
		{
			return q;
		}
		if (t < 2f / 3f)
		{
			return p + (q - p) * (2f / 3f - t) * 6f;
		}
		return p;
	}

	private static int clamp(int v)
	{
		return Math.max(0, Math.min(255, v));
	}
}

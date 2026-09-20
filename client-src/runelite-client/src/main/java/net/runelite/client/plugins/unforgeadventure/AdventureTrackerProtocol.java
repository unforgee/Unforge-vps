package net.runelite.client.plugins.unforgeadventure;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Parser for the `UNFORGE_ADV|*` console side channel emitted by
 * `EarlyGameTrackerSync`. The envelope is `|`-delimited; free-text fields are
 * base64-url encoded (without padding).
 *
 * <pre>
 * UNFORGE_ADV|C
 * UNFORGE_ADV|D|&lt;ordinal&gt;|&lt;b64 title&gt;|&lt;b64 phase&gt;|&lt;b64 requirement&gt;
 * UNFORGE_ADV|S|&lt;doneMask&gt;|&lt;relic&gt;|&lt;discoveries&gt;|&lt;points&gt;|&lt;companion&gt;[|&lt;hidden&gt;|&lt;minimized&gt;]
 * </pre>
 */
final class AdventureTrackerProtocol
{
	static final String PREFIX = "UNFORGE_ADV";

	private AdventureTrackerProtocol()
	{
	}

	/** Returns true when the message belongs to this side channel. */
	static boolean isTrackerMessage(String message)
	{
		return message != null && message.startsWith(PREFIX + "|");
	}

	static void apply(String message, AdventureTrackerModel model)
	{
		String[] parts = message.split("\\|", -1);
		try
		{
			switch (parts[1])
			{
				case "C":
					model.reset();
					break;
				case "D":
					if (parts.length == 6)
					{
						model.defineStep(
							Integer.parseInt(parts[2]),
							decode(parts[3]),
							decode(parts[4]),
							decode(parts[5]));
					}
					break;
				case "S":
					if (parts.length >= 7)
					{
						model.applyStatus(
							Long.parseLong(parts[2]),
							parts[3],
							Integer.parseInt(parts[4]),
							Integer.parseInt(parts[5]),
							parts[6],
							parts.length > 7 && "1".equals(parts[7]),
							parts.length > 8 && "1".equals(parts[8]));
					}
					break;
				default:
					break;
			}
		}
		catch (RuntimeException ignored)
		{
			// A malformed frame must not break the overlay - skip it.
		}
	}

	private static String decode(String value)
	{
		return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
	}
}

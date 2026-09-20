package net.runelite.client.plugins.iteminstanceinspect;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Parser for the `UNFORGE_INSPECT_*` console side channel the server emits when
 * a player examines equipment. The envelope is `|`-delimited; free-text fields
 * are base64-url encoded so tags and separators can never corrupt the frame.
 *
 * <pre>
 * UNFORGE_INSPECT_BEGIN|&lt;objectId&gt;|&lt;instanceId&gt;|&lt;b64 name&gt;|&lt;b64 subtitle&gt;
 * UNFORGE_INSPECT_LINE|&lt;b64 text&gt;
 * UNFORGE_INSPECT_PART|&lt;b64 chunk&gt;   (repeated; raw encoded fragment, not decoded alone)
 * UNFORGE_INSPECT_PEND                (terminates a PART sequence)
 * UNFORGE_INSPECT_END
 * </pre>
 *
 * Lines whose encoded form would exceed the 255-byte game-message cap are streamed as
 * PART chunks followed by PEND; the chunks are concatenated and decoded as one value.
 */
final class ItemInstanceInspectProtocol
{
	static final String BEGIN = "UNFORGE_INSPECT_BEGIN";
	static final String LINE = "UNFORGE_INSPECT_LINE";
	static final String PART = "UNFORGE_INSPECT_PART";
	static final String PART_END = "UNFORGE_INSPECT_PEND";
	static final String END = "UNFORGE_INSPECT_END";

	private ItemInstanceInspectProtocol()
	{
	}

	static ParsedMessage parse(String message)
	{
		if (message == null || !message.startsWith("UNFORGE_INSPECT_"))
		{
			return null;
		}

		try
		{
			if (message.equals(END))
			{
				return ParsedMessage.end();
			}
			if (message.equals(PART_END))
			{
				return ParsedMessage.partEnd();
			}

			String[] parts = message.split("\\|", -1);
			switch (parts[0])
			{
				case BEGIN:
					return parts.length == 5
						? ParsedMessage.begin(
							Integer.parseInt(parts[1]),
							Long.parseLong(parts[2]),
							decodeText(parts[3]),
							decodeText(parts[4]))
						: null;
				case LINE:
					return parts.length == 2
						? ParsedMessage.line(decodeText(parts[1]))
						: null;
				case PART:
					// PART chunks are raw base64 fragments - decoding a partial stream is
					// invalid, so the chunk is carried through undecoded.
					return parts.length == 2
						? ParsedMessage.part(parts[1])
						: null;
				default:
					return null;
			}
		}
		catch (RuntimeException ignored)
		{
			// A malformed protocol message must remain a normal console line rather
			// than being swallowed and silently losing user-visible information.
			return null;
		}
	}

	private static String decodeText(String value)
	{
		return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
	}

	/** Decodes the concatenated PART chunk buffer once the terminating PEND arrives. */
	static String decodeJoined(String encoded)
	{
		return decodeText(encoded);
	}

	static final class ParsedMessage
	{
		enum Kind
		{
			BEGIN,
			LINE,
			PART,
			PART_END,
			END
		}

		private final Kind kind;
		private final int objectId;
		private final long instanceId;
		private final String name;
		private final String subtitle;
		private final String text;

		private ParsedMessage(Kind kind, int objectId, long instanceId, String name,
			String subtitle, String text)
		{
			this.kind = kind;
			this.objectId = objectId;
			this.instanceId = instanceId;
			this.name = name;
			this.subtitle = subtitle;
			this.text = text;
		}

		static ParsedMessage begin(int objectId, long instanceId, String name, String subtitle)
		{
			return new ParsedMessage(Kind.BEGIN, objectId, instanceId, name, subtitle, null);
		}

		static ParsedMessage line(String text)
		{
			return new ParsedMessage(Kind.LINE, -1, -1, null, null, text);
		}

		static ParsedMessage part(String encodedChunk)
		{
			return new ParsedMessage(Kind.PART, -1, -1, null, null, encodedChunk);
		}

		static ParsedMessage partEnd()
		{
			return new ParsedMessage(Kind.PART_END, -1, -1, null, null, null);
		}

		static ParsedMessage end()
		{
			return new ParsedMessage(Kind.END, -1, -1, null, null, null);
		}

		Kind getKind()
		{
			return kind;
		}

		int getObjectId()
		{
			return objectId;
		}

		long getInstanceId()
		{
			return instanceId;
		}

		String getName()
		{
			return name;
		}

		String getSubtitle()
		{
			return subtitle;
		}

		String getText()
		{
			return text;
		}
	}
}

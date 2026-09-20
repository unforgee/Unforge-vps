package net.runelite.client.plugins.unforgecache;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import lombok.extern.slf4j.Slf4j;

/**
 * The named sprite ids from the client's own {@code gameval.SpriteID} class.
 *
 * <p>The generated gameval classes carry the real names for every sprite the game
 * addresses by name, which turns "sprite 900" into
 * {@code SideIcons.INVENTORY}. Reading them reflectively means the list stays correct
 * when the gameval classes are regenerated for a new revision — there is no second
 * copy of the name table here to drift out of date.
 *
 * <p>Ids the gameval classes do not name are still reachable: the panel accepts a
 * plain number for those.
 */
@Slf4j
final class SpriteCatalog
{
	/**
	 * One named sprite id.
	 *
	 * <p>A plain class rather than a record: the client compiles with {@code -source 11}.
	 */
	static final class NamedSprite
	{
		private final int id;
		private final String name;

		NamedSprite(int id, String name)
		{
			this.id = id;
			this.name = name;
		}

		int id()
		{
			return id;
		}

		String name()
		{
			return name;
		}

		@Override
		public String toString()
		{
			return name + "  [" + id + "]";
		}

		@Override
		public boolean equals(Object other)
		{
			if (this == other)
			{
				return true;
			}
			if (!(other instanceof NamedSprite))
			{
				return false;
			}
			NamedSprite that = (NamedSprite) other;
			return id == that.id && name.equals(that.name);
		}

		@Override
		public int hashCode()
		{
			return 31 * id + name.hashCode();
		}
	}

	private SpriteCatalog()
	{
	}

	/**
	 * Every named sprite id, ordered by id.
	 *
	 * <p>Two names can point at the same id; the first one in class order wins so the
	 * result stays stable between runs.
	 */
	static List<NamedSprite> load()
	{
		Map<Integer, String> byId = new TreeMap<>();
		try
		{
			Class<?> root = Class.forName("net.runelite.api.gameval.SpriteID");
			collect(root, byId);
			for (Class<?> nested : root.getDeclaredClasses())
			{
				collect(nested, byId);
			}
		}
		catch (ClassNotFoundException e)
		{
			log.warn("Unforge Cache: gameval.SpriteID not found, named sprites unavailable", e);
		}

		List<NamedSprite> sprites = new ArrayList<>(byId.size());
		for (Map.Entry<Integer, String> entry : byId.entrySet())
		{
			sprites.add(new NamedSprite(entry.getKey(), entry.getValue()));
		}
		sprites.sort(Comparator.comparingInt(NamedSprite::id));
		return sprites;
	}

	private static void collect(Class<?> type, Map<Integer, String> byId)
	{
		String prefix = type.getSimpleName().isEmpty() ? type.getName() : type.getSimpleName();
		for (Field field : type.getDeclaredFields())
		{
			if (!Modifier.isStatic(field.getModifiers()) || field.getType() != int.class)
			{
				continue;
			}
			try
			{
				int value = field.getInt(null);
				byId.putIfAbsent(value, prefix + "." + field.getName());
			}
			catch (IllegalAccessException | RuntimeException e)
			{
				// A field we cannot read is simply not named; the id is still reachable by number.
			}
		}
	}

	/** Case-insensitive match against the id or the name. */
	static boolean matches(NamedSprite sprite, String needle)
	{
		if (needle.isEmpty())
		{
			return true;
		}
		return sprite.name().toLowerCase(Locale.ROOT).contains(needle)
			|| Integer.toString(sprite.id()).contains(needle);
	}
}

package net.runelite.client.plugins.unforgestudio;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.TreeMap;

/**
 * Name-to-id lookup built from the server's own symbol tables.
 *
 * <p>The lab writes {@code .data/symbols/*.sym} as {@code id<TAB>name} pairs, one per
 * entity. Those tables are the ground truth for what the running server actually
 * knows, so content created through the dock can be expressed as names
 * ("goblin", "varrock") instead of magic numbers.</p>
 *
 * <p>Loaded lazily and cached; {@link #reload()} re-reads after a server pack.</p>
 */
public class SymbolIndex
{
	/** Symbol tables the lab exposes, in the order they are shown in the UI. */
	public enum Kind
	{
		NPC("npc.sym", "NPC"),
		LOC("loc.sym", "Object"),
		INTERFACE("interface.sym", "Interface"),
		COMPONENT("component.sym", "Component"),
		VARP("varp.sym", "Varp"),
		INV("inv.sym", "Inventory"),
		WALKTRIGGER("walktrigger.sym", "Walk trigger");

		private final String fileName;
		private final String label;

		Kind(String fileName, String label)
		{
			this.fileName = fileName;
			this.label = label;
		}

		public String fileName()
		{
			return fileName;
		}

		public String label()
		{
			return label;
		}
	}

	private final Map<Kind, Map<String, Integer>> byName = new EnumMap<>(Kind.class);
	private final Map<Kind, Map<Integer, String>> byId = new EnumMap<>(Kind.class);
	private final Map<Kind, Integer> counts = new EnumMap<>(Kind.class);

	private Path symbolsDir;
	private String status = "not loaded";

	public SymbolIndex()
	{
		for (Kind kind : Kind.values())
		{
			byName.put(kind, new TreeMap<>());
			byId.put(kind, new TreeMap<>());
			counts.put(kind, 0);
		}
	}

	/**
	 * Resolves the symbols directory and loads every table.
	 *
	 * @param configuredLabRoot explicit lab root from settings, or null/empty to auto-detect
	 */
	public synchronized void reload(String configuredLabRoot)
	{
		for (Kind kind : Kind.values())
		{
			byName.get(kind).clear();
			byId.get(kind).clear();
			counts.put(kind, 0);
		}

		symbolsDir = resolveSymbolsDir(configuredLabRoot);
		if (symbolsDir == null)
		{
			status = "symbols directory not found - set Lab root in plugin settings";
			return;
		}

		int loaded = 0;
		for (Kind kind : Kind.values())
		{
			loaded += loadTable(kind, symbolsDir.resolve(kind.fileName()));
		}

		status = String.format(Locale.ROOT, "%d entries from %s", loaded, symbolsDir);
	}

	private static Path resolveSymbolsDir(String configuredLabRoot)
	{
		List<Path> roots = new ArrayList<>();

		if (configuredLabRoot != null && !configuredLabRoot.trim().isEmpty())
		{
			roots.add(Paths.get(configuredLabRoot.trim()));
		}

		String fromEnv = System.getenv("UNFORGE_LAB_ROOT");
		if (fromEnv != null && !fromEnv.trim().isEmpty())
		{
			roots.add(Paths.get(fromEnv.trim()));
		}

		// Relative guesses cover starting the client from the lab root or from client-src.
		Path cwd = Paths.get("").toAbsolutePath();
		roots.add(cwd);
		if (cwd.getParent() != null)
		{
			roots.add(cwd.getParent());
		}

		for (Path root : roots)
		{
			Path candidate = root.resolve(".data").resolve("symbols");
			if (Files.isDirectory(candidate))
			{
				return candidate;
			}
		}

		return null;
	}

	private int loadTable(Kind kind, Path file)
	{
		if (!Files.isRegularFile(file))
		{
			return 0;
		}

		Map<String, Integer> names = byName.get(kind);
		Map<Integer, String> ids = byId.get(kind);

		int count = 0;
		try
		{
			for (String line : Files.readAllLines(file, StandardCharsets.UTF_8))
			{
				if (line.isEmpty())
				{
					continue;
				}

				int tab = line.indexOf('\t');
				if (tab <= 0 || tab == line.length() - 1)
				{
					continue;
				}

				int id;
				try
				{
					id = Integer.parseInt(line.substring(0, tab).trim());
				}
				catch (NumberFormatException ex)
				{
					continue;
				}

				String name = line.substring(tab + 1).trim();
				if (name.isEmpty())
				{
					continue;
				}

				names.putIfAbsent(name.toLowerCase(Locale.ROOT), id);
				ids.putIfAbsent(id, name);
				count++;
			}
		}
		catch (IOException ex)
		{
			return 0;
		}

		counts.put(kind, count);
		return count;
	}

	/** Id for an exact, case-insensitive name. */
	public synchronized OptionalInt idFor(Kind kind, String name)
	{
		if (name == null)
		{
			return OptionalInt.empty();
		}

		Integer id = byName.get(kind).get(name.trim().toLowerCase(Locale.ROOT));
		return id == null ? OptionalInt.empty() : OptionalInt.of(id);
	}

	/** Canonical name for an id. */
	public synchronized Optional<String> nameFor(Kind kind, int id)
	{
		return Optional.ofNullable(byId.get(kind).get(id));
	}

	/**
	 * Prefix search over names, best-effort shortest-first so the most obviously
	 * relevant match is first.
	 *
	 * @param prefix case-insensitive prefix, or empty for the first entries
	 * @param limit  maximum number of results
	 */
	public synchronized List<String> search(Kind kind, String prefix, int limit)
	{
		Map<String, Integer> names = byName.get(kind);
		if (names.isEmpty() || limit <= 0)
		{
			return Collections.emptyList();
		}

		String needle = prefix == null ? "" : prefix.trim().toLowerCase(Locale.ROOT);
		List<String> startsWith = new ArrayList<>();
		List<String> contains = new ArrayList<>();

		for (String name : names.keySet())
		{
			if (needle.isEmpty() || name.startsWith(needle))
			{
				startsWith.add(name);
			}
			else if (name.contains(needle))
			{
				contains.add(name);
			}

			if (startsWith.size() >= limit && contains.size() >= limit)
			{
				break;
			}
		}

		List<String> out = new ArrayList<>(startsWith);
		if (out.size() < limit)
		{
			contains.sort((a, b) -> Integer.compare(a.length(), b.length()));
			for (String name : contains)
			{
				if (out.size() >= limit)
				{
					break;
				}
				out.add(name);
			}
		}

		return out.size() > limit ? out.subList(0, limit) : out;
	}

	/** Name plus id for display in a picker. */
	public synchronized Map<String, Integer> entries(Kind kind)
	{
		return new LinkedHashMap<>(byName.get(kind));
	}

	public synchronized int count(Kind kind)
	{
		return counts.get(kind);
	}

	public synchronized Path symbolsDir()
	{
		return symbolsDir;
	}

	public synchronized String status()
	{
		return status;
	}

	public synchronized boolean isLoaded()
	{
		return symbolsDir != null && counts.values().stream().anyMatch(c -> c > 0);
	}
}

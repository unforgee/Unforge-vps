package net.runelite.client.plugins.unforgecache;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;

/**
 * Storage for sprite replacements.
 *
 * <p>Replacements live in {@code ~/.runelite/unforge-cache/} as plain PNG files plus a
 * small manifest, so a replacement is something you can look at, edit in any image
 * editor, drop in from anywhere, and delete by hand. Nothing is hidden inside the game
 * cache, which means nothing here can break it.
 */
@Slf4j
class SpriteOverrideStore
{
	private static final TypeToken<Map<String, String>> MANIFEST_TYPE =
		new TypeToken<Map<String, String>>()
		{
		};

	private final Gson gson;
	private final Path root;
	private final Path spritesDir;
	private final Path manifestFile;

	/** key "{spriteId}/{frame}" -> file name inside the sprites directory */
	private final Map<String, String> manifest = new HashMap<>();

	SpriteOverrideStore(Gson gson)
	{
		this.gson = gson;
		this.root = RuneLite.RUNELITE_DIR.toPath().resolve("unforge-cache");
		this.spritesDir = root.resolve("sprites");
		this.manifestFile = root.resolve("manifest.json");
		load();
	}

	static String key(int spriteId, int frame)
	{
		return spriteId + "/" + frame;
	}

	Path root()
	{
		return root;
	}

	Path spritesDir()
	{
		return spritesDir;
	}

	private void load()
	{
		try
		{
			Files.createDirectories(spritesDir);
		}
		catch (IOException e)
		{
			log.warn("Unforge Cache: could not create {}", spritesDir, e);
		}

		if (!Files.isRegularFile(manifestFile))
		{
			return;
		}

		try
		{
			String json = Files.readString(manifestFile);
			Map<String, String> stored = gson.fromJson(json, MANIFEST_TYPE.getType());
			if (stored != null)
			{
				manifest.putAll(stored);
			}
		}
		catch (IOException | RuntimeException e)
		{
			log.warn("Unforge Cache: could not read the manifest, starting empty", e);
		}
	}

	/** Every stored replacement that still has its PNG on disk. */
	Map<String, BufferedImage> loadAllImages()
	{
		Map<String, BufferedImage> images = new HashMap<>();
		for (Map.Entry<String, String> entry : manifest.entrySet())
		{
			File file = spritesDir.resolve(entry.getValue()).toFile();
			if (!file.isFile())
			{
				continue;
			}
			try
			{
				BufferedImage image = ImageIO.read(file);
				if (image != null)
				{
					images.put(entry.getKey(), image);
				}
			}
			catch (IOException e)
			{
				log.warn("Unforge Cache: could not read {}", file, e);
			}
		}
		return images;
	}

	/** Copies a PNG in as the replacement for a sprite frame and records it. */
	boolean store(int spriteId, int frame, File source) throws IOException
	{
		BufferedImage image = ImageIO.read(source);
		if (image == null)
		{
			return false;
		}
		return store(spriteId, frame, image);
	}

	boolean store(int spriteId, int frame, BufferedImage image) throws IOException
	{
		Files.createDirectories(spritesDir);
		String fileName = spriteId + "_" + frame + ".png";
		ImageIO.write(image, "png", spritesDir.resolve(fileName).toFile());
		manifest.put(key(spriteId, frame), fileName);
		save();
		return true;
	}

	/** Forgets a replacement; the PNG is kept so it can be re-enabled. */
	void clear(int spriteId, int frame)
	{
		if (manifest.remove(key(spriteId, frame)) != null)
		{
			save();
		}
	}

	boolean has(int spriteId, int frame)
	{
		return manifest.containsKey(key(spriteId, frame));
	}

	/** True when the sprite has a PNG stored, even if it is not currently applied. */
	boolean hasStoredPng(int spriteId, int frame)
	{
		String fileName = spriteId + "_" + frame + ".png";
		return Files.isRegularFile(spritesDir.resolve(fileName));
	}

	private void save()
	{
		try
		{
			Files.createDirectories(root);
			Files.writeString(manifestFile, gson.toJson(manifest, MANIFEST_TYPE.getType()));
		}
		catch (IOException e)
		{
			log.warn("Unforge Cache: could not write the manifest", e);
		}
	}
}

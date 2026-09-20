# Unforge Cache — sprite editor inside the client

A RuneLite plugin that lists the client's sprites, previews them and replaces them
**live while you are playing**. Nothing is written to the game cache.

- **Plugin name**: *Unforge Cache*
- **Panel**: sidebar button; **⤢** pops it out into a 460×800 resizable, always-on-top window
- **Replacements live in**: `%USERPROFILE%\.runelite\unforge-cache\sprites\*.png`
- **Source**: `client-src/runelite-client/src/main/java/net/runelite/client/plugins/unforgecache/`

## Pop-out

RuneLite locks the sidebar to **225 px** and it cannot be widened, which is far too
narrow for a sprite list next to a preview. The **⤢** button in the panel header
therefore moves the panel into its own window — the same pattern *Unforge Dev Studio*
uses:

| | |
|---|---|
| Open | **⤢** in the header |
| Size | 460×800 to start, resizable from the edges, minimum 340×420 |
| Behaviour | always on top, so it stays visible next to the game |
| Close | the window's X, or **⤡** in the header — both dock it back into the sidebar |

The panel **component itself** is what moves between the sidebar and the window, so
there is only ever one instance. The filter text, the selected sprite and the frame
number are the same in both places; nothing is duplicated and nothing has to be kept in
sync. The header button shows **⤢** when docked and **⤡** when popped out, so it always
offers the way back.


## Why this instead of rewriting the cache

The client already has an override map that the renderer consults **before** the cache:

```java
Map<Integer, SpritePixels> getSpriteOverrides();        // sprite id -> replacement
Map<Integer, SpritePixels> getWidgetSpriteOverrides();  // packed widget id -> replacement
net.runelite.api.Client#createSpritePixels(int[] pixels, int width, int height)
net.runelite.client.game.SpriteManager#getSprite(int archive, int file)
```

So a replacement is an in-memory entry, and the cache file is never opened for
writing. That has consequences worth being explicit about:

| | Cache rewrite | This plugin |
|---|---|---|
| Shows up while playing | needs a relog | immediately |
| Can corrupt the cache | yes | no |
| Revert | restore a backup | remove one entry |
| Closing the client | keeps the change | restores everything |
| Works on a live server | no | yes |

There is no failure mode here that leaves you with a broken cache, which is why it is
the default and the cache-rewrite path is not implemented.

## Using it

1. Start the client, open the **Unforge Cache** sidebar button.
2. Find a sprite — type in the filter to match a name or an id.
   Sprites are listed by the game's own names, read from
   `net.runelite.api.gameval.SpriteID` (`SideIcons.INVENTORY [1001]`, `COMPASS [54]`, …),
   so the list stays correct when the gameval classes are regenerated for a new revision.
3. The panel previews the sprite, scaled up with nearest-neighbour so pixels stay sharp.
   **Frame** steps through an animated sprite's frames.
4. **Replace from PNG…** — pick an image. It is copied into
   `unforge-cache/sprites/`, converted through the client's own
   `createSpritePixels`, and applied. The game shows it on the next frame.
5. **Revert** — removes the override; the client shows the original again.
6. **Export PNG…** — saves the sprite currently in the cache.

Replacements survive a restart when **Re-apply replacements on startup** is on (default),
so a sprite set you are working on stays applied.

## Notes

- A sprite not named by the gameval classes is still editable: selection by id works for
  anything, named or not.
- The plugin ships **disabled by default** (`enabledByDefault = false`) so it cannot
  surprise anyone; enable it in the plugin list.
- The plugin clears its overrides on shutdown, and disposes the popped-out window if it
  is open. Leaving either behind would leak into a later session — the overrides into a
  different cache, the window into the client's shutdown.

## Building

```
cd client-src
gradlew.bat :runelite-client:compileJava
```

Then run the client as usual. No extra dependency is needed: the plugin uses only the
client API, Swing and `javax.imageio`, all of which are already on the classpath.

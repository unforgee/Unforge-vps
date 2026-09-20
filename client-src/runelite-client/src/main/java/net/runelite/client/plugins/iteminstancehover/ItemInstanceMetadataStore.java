package net.runelite.client.plugins.iteminstancehover;

import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public final class ItemInstanceMetadataStore
{
	private final Map<Integer, Map<Integer, ItemInstanceMetadata>> values = new HashMap<>();

	/**
	 * Chunks of a split section that have arrived but are not complete yet, keyed by
	 * (scope, slot, objectId, section). The objectId is part of the key so chunks
	 * from a stale payload can never leak into a different item's metadata.
	 */
	private final Map<SectionBufferKey, SectionBuffer> sectionBuffers = new HashMap<>();

	/**
	 * Sections completed before their base upsert arrived. Server order is always
	 * upsert-first, so this is only a safety net; entries are consumed (or dropped
	 * on objectId mismatch) by the next upsert for the slot.
	 */
	private final Map<SlotKey, PendingSections> pendingSections = new HashMap<>();

	@Inject
	public ItemInstanceMetadataStore()
	{
	}

	public synchronized void upsert(ItemInstanceMetadata metadata)
	{
		SlotKey slotKey = new SlotKey(metadata.getScope(), metadata.getSlot());
		PendingSections pending = pendingSections.remove(slotKey);
		if (pending != null && pending.objectId == metadata.getObjectId())
		{
			for (Map.Entry<String, String> section : pending.sections.entrySet())
			{
				metadata = ItemInstanceProtocol.withSection(
					metadata, section.getKey(), section.getValue());
			}
		}
		values.computeIfAbsent(metadata.getScope(), ignored -> new HashMap<>())
			.put(metadata.getSlot(), metadata);
	}

	public synchronized void remove(int scope, int slot)
	{
		pendingSections.remove(new SlotKey(scope, slot));
		sectionBuffers.keySet().removeIf(key -> key.scope == scope && key.slot == slot);

		Map<Integer, ItemInstanceMetadata> scoped = values.get(scope);
		if (scoped == null)
		{
			return;
		}

		scoped.remove(slot);
		if (scoped.isEmpty())
		{
			values.remove(scope);
		}
	}

	public synchronized void clear(int scope)
	{
		pendingSections.keySet().removeIf(key -> key.scope == scope);
		sectionBuffers.keySet().removeIf(key -> key.scope == scope);
		values.remove(scope);
	}

	public synchronized void clearAll()
	{
		pendingSections.clear();
		sectionBuffers.clear();
		values.clear();
	}

	public synchronized ItemInstanceMetadata get(int scope, int slot)
	{
		Map<Integer, ItemInstanceMetadata> scoped = values.get(scope);
		return scoped == null ? null : scoped.get(slot);
	}

	/** Returns a stable snapshot of every currently active item instance. */
	public synchronized List<ItemInstanceMetadata> snapshot()
	{
		List<ItemInstanceMetadata> result = new ArrayList<>();
		for (Map<Integer, ItemInstanceMetadata> scoped : values.values())
		{
			result.addAll(scoped.values());
		}
		return Collections.unmodifiableList(result);
	}

	/**
	 * Accumulates a `sect` message chunk. Once all chunks of a section have
	 * arrived the joined data replaces that one section on the stored metadata -
	 * or is parked in [pendingSections] when the base upsert has not arrived yet.
	 */
	public synchronized void applySection(
		int scope,
		int slot,
		int objectId,
		String name,
		int sequence,
		int chunkCount,
		String data)
	{
		if (sequence >= chunkCount || chunkCount <= 0)
		{
			return;
		}

		SectionBufferKey key = new SectionBufferKey(scope, slot, objectId, name);
		SectionBuffer buffer = sectionBuffers.get(key);
		if (buffer == null || buffer.parts.length != chunkCount)
		{
			buffer = new SectionBuffer(chunkCount);
			sectionBuffers.put(key, buffer);
		}
		buffer.parts[sequence] = data;

		for (String part : buffer.parts)
		{
			if (part == null)
			{
				return;
			}
		}
		sectionBuffers.remove(key);
		try
		{
			mergeSection(scope, slot, objectId, name, String.join(",", buffer.parts));
		}
		catch (RuntimeException ignored)
		{
			// A malformed section must not kill the chat handler - the head metadata
			// stays valid and the section simply stays empty.
		}
	}

	private void mergeSection(int scope, int slot, int objectId, String name, String data)
	{
		ItemInstanceMetadata base = get(scope, slot);
		if (base == null || base.getObjectId() != objectId)
		{
			SlotKey slotKey = new SlotKey(scope, slot);
			PendingSections pending = pendingSections.get(slotKey);
			if (pending == null || pending.objectId != objectId)
			{
				pending = new PendingSections(objectId);
				pendingSections.put(slotKey, pending);
			}
			pending.sections.put(name, data);
			return;
		}
		upsert(ItemInstanceProtocol.withSection(base, name, data));
	}

	private static class SlotKey
	{
		final int scope;
		final int slot;

		SlotKey(int scope, int slot)
		{
			this.scope = scope;
			this.slot = slot;
		}

		@Override
		public boolean equals(Object other)
		{
			if (!(other instanceof SlotKey))
			{
				return false;
			}
			SlotKey key = (SlotKey) other;
			return scope == key.scope && slot == key.slot;
		}

		@Override
		public int hashCode()
		{
			return scope * 1_000_003 + slot;
		}
	}

	private static final class SectionBufferKey extends SlotKey
	{
		private final int objectId;
		private final String name;

		SectionBufferKey(int scope, int slot, int objectId, String name)
		{
			super(scope, slot);
			this.objectId = objectId;
			this.name = name;
		}

		@Override
		public boolean equals(Object other)
		{
			if (!(other instanceof SectionBufferKey))
			{
				return false;
			}
			SectionBufferKey key = (SectionBufferKey) other;
			return super.equals(other)
				&& objectId == key.objectId
				&& name.equals(key.name);
		}

		@Override
		public int hashCode()
		{
			return super.hashCode() * 31 + objectId * 31 + name.hashCode();
		}
	}

	private static final class SectionBuffer
	{
		private final String[] parts;

		SectionBuffer(int chunkCount)
		{
			parts = new String[chunkCount];
		}
	}

	private static final class PendingSections
	{
		private final int objectId;
		private final Map<String, String> sections = new HashMap<>();

		PendingSections(int objectId)
		{
			this.objectId = objectId;
		}
	}
}

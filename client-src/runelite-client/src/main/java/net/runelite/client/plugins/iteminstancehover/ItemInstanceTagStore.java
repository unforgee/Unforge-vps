package net.runelite.client.plugins.iteminstancehover;

import java.util.HashSet;
import java.util.Set;
import javax.inject.Singleton;

/**
 * Client-local set of tagged equipment instances. Tags are keyed by the
 * server-issued instance id - never by object id - so two copies of the same
 * base item stay independent.
 */
@Singleton
class ItemInstanceTagStore
{
	private final Set<Long> taggedInstanceIds = new HashSet<>();

	boolean toggle(long instanceId)
	{
		if (!taggedInstanceIds.add(instanceId))
		{
			taggedInstanceIds.remove(instanceId);
			return false;
		}
		return true;
	}

	boolean isTagged(long instanceId)
	{
		return taggedInstanceIds.contains(instanceId);
	}

	void clear()
	{
		taggedInstanceIds.clear();
	}
}

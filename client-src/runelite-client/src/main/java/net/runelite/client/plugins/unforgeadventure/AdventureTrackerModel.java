package net.runelite.client.plugins.unforgeadventure;

import java.util.ArrayList;
import java.util.List;
import javax.inject.Singleton;

/**
 * Client-side mirror of the server-driven Adventure Path. Step definitions arrive once
 * per login (`D` frames); the `S` frame carries a bitmask of completed step ordinals.
 */
@Singleton
final class AdventureTrackerModel
{
	static final class Step
	{
		final String title;
		final String phase;
		final String requirement;

		Step(String title, String phase, String requirement)
		{
			this.title = title;
			this.phase = phase;
			this.requirement = requirement;
		}
	}

	private final List<Step> steps = new ArrayList<>();
	private long doneMask;
	private String relic = "-";
	private int discoveries;
	private int discoveryPoints;
	private String companion = "LOCKED";
	private boolean hidden;
	private boolean minimized;
	private boolean receivedData;

	synchronized void reset()
	{
		steps.clear();
		doneMask = 0;
		relic = "-";
		discoveries = 0;
		discoveryPoints = 0;
		companion = "LOCKED";
		hidden = false;
		minimized = false;
		receivedData = false;
	}

	synchronized void defineStep(int ordinal, String title, String phase, String requirement)
	{
		while (steps.size() <= ordinal)
		{
			steps.add(null);
		}
		steps.set(ordinal, new Step(title, phase, requirement));
		receivedData = true;
	}

	synchronized void applyStatus(long mask, String relicName, int discoveryCount,
		int points, String companionState, boolean hiddenState, boolean minimizedState)
	{
		doneMask = mask;
		relic = relicName;
		discoveries = discoveryCount;
		discoveryPoints = points;
		companion = companionState;
		hidden = hiddenState;
		minimized = minimizedState;
		receivedData = true;
	}

	synchronized boolean hasData()
	{
		return receivedData && !steps.isEmpty();
	}

	synchronized int stepCount()
	{
		return steps.size();
	}

	synchronized int completedCount()
	{
		return Long.bitCount(doneMask);
	}

	synchronized boolean isDone(int ordinal)
	{
		return ordinal < steps.size() && (doneMask & (1L << ordinal)) != 0;
	}

	/** The first incomplete step in path order - "the next step" for the player. */
	synchronized int nextOrdinal()
	{
		for (int i = 0; i < steps.size(); i++)
		{
			if (steps.get(i) != null && !isDone(i))
			{
				return i;
			}
		}
		return -1;
	}

	synchronized Step step(int ordinal)
	{
		return ordinal >= 0 && ordinal < steps.size() ? steps.get(ordinal) : null;
	}

	synchronized String relic()
	{
		return relic;
	}

	synchronized int discoveries()
	{
		return discoveries;
	}

	synchronized int discoveryPoints()
	{
		return discoveryPoints;
	}

	synchronized String companion()
	{
		return companion;
	}

	synchronized boolean hidden()
	{
		return hidden;
	}

	synchronized boolean minimized()
	{
		return minimized;
	}
}

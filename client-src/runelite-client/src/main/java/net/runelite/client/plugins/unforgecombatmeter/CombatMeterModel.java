package net.runelite.client.plugins.unforgecombatmeter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class CombatMeterModel
{
	enum Kind { DAMAGE, HEALING, TAKEN }

	static final class ActorStats
	{
		final String name;
		long damage;
		long healing;
		long overhealing;
		long taken;
		int hits;
		int maxHit;
		long lastActive;
		final Map<String, Integer> breakdown = new LinkedHashMap<>();

		ActorStats(String name) { this.name = name; }
		void add(Kind kind, int amount, String ability)
		{
			if (kind == Kind.DAMAGE) damage += amount;
			if (kind == Kind.HEALING) healing += amount;
			if (kind == Kind.TAKEN) taken += amount;
			if (kind != Kind.HEALING) { hits++; maxHit = Math.max(maxHit, amount); }
			if (ability != null) breakdown.merge(ability, amount, Integer::sum);
			lastActive = System.currentTimeMillis();
		}
	}

	private final Map<String, ActorStats> actors = new LinkedHashMap<>();
	private long started;

	void reset() { actors.clear(); started = 0; }
	void ensureStarted() { if (started == 0) started = System.currentTimeMillis(); }
	ActorStats actor(String name) { ensureStarted(); return actors.computeIfAbsent(name, ActorStats::new); }
	long elapsedSeconds() { return started == 0 ? 0 : Math.max(1, (System.currentTimeMillis() - started) / 1000); }
	long total(Kind kind)
	{
		return actors.values().stream().mapToLong(a -> kind == Kind.DAMAGE ? a.damage : kind == Kind.HEALING ? a.healing : a.taken).sum();
	}
	List<ActorStats> sorted(Kind kind)
	{
		List<ActorStats> result = new ArrayList<>(actors.values());
		result.removeIf(a -> (kind == Kind.DAMAGE ? a.damage : kind == Kind.HEALING ? a.healing : a.taken) == 0);
		result.sort((a, b) -> Long.compare(value(b, kind), value(a, kind)));
		return Collections.unmodifiableList(result);
	}
	private long value(ActorStats a, Kind k) { return k == Kind.DAMAGE ? a.damage : k == Kind.HEALING ? a.healing : a.taken; }
}

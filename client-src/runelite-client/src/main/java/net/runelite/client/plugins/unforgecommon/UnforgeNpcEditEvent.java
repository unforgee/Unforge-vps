package net.runelite.client.plugins.unforgecommon;

/**
 * Posted on the shared EventBus when a right-click "Edit NPC" menu entry is clicked.
 * The Drop Editor plugin listens for this and loads the NPC's combat stats + drops.
 */
public class UnforgeNpcEditEvent
{
	private final int npcId;
	private final String name;
	private final int combatLevel;

	public UnforgeNpcEditEvent(int npcId, String name, int combatLevel)
	{
		this.npcId = npcId;
		this.name = name;
		this.combatLevel = combatLevel;
	}

	public int getNpcId()
	{
		return npcId;
	}

	public String getName()
	{
		return name;
	}

	public int getCombatLevel()
	{
		return combatLevel;
	}
}

package net.runelite.client.plugins.iteminstancehover;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import org.junit.Test;

public class ItemInstanceProtocolTest
{
	@Test
	public void parsesAndStoresAuthoritativeInstanceData()
	{
		String affix = String.join("~",
			"0",
			encode("stab-power"),
			encode("combat"),
			"AttackStab",
			"BasisPoints",
			"Boon",
			"250");
		String socket = String.join("~", "0", encode("ruby"), "1603", "3");
		String skillAffix = String.join("~", encode("ALL"), encode("NOTED_CHANCE"), "BasisPoints", "150");
		String message = String.join("|",
			"UNFORGE_ITEM_INSTANCE",
			"3",
			"upsert",
			"93",
			"2",
			"123456",
			"4151",
			"Weapon",
			"Jackpot",
			"Rune",
			"42",
			"87",
			affix,
			socket,
			encode("Jackpot Surge") + "~" + encode("+5% damage")
				+ "," + encode("executioner"),
			skillAffix,
			"700");

		ItemInstanceProtocol.ParsedMessage parsed = ItemInstanceProtocol.parse(message);
		assertNotNull(parsed);

		ItemInstanceMetadataStore store = new ItemInstanceMetadataStore();
		parsed.apply(store);
		ItemInstanceMetadata metadata = store.get(93, 2);
		assertNotNull(metadata);
		assertEquals(123456L, metadata.getInstanceId());
		assertEquals(4151, metadata.getObjectId());
		assertEquals("Jackpot", metadata.getRarity());
		assertEquals(42, metadata.getItemLevel());
		assertEquals(700, metadata.getSpeedBps());
		assertEquals(250, metadata.getAffixes().get(0).getMagnitude());
		assertEquals("stab-power", metadata.getAffixes().get(0).getDefinitionId());
		assertEquals("ruby", metadata.getSockets().get(0).getType());
		assertEquals("Jackpot Surge", metadata.getAbilities().get(0).getName());
		assertEquals("+5% damage", metadata.getAbilities().get(0).getDescription());
		assertEquals("executioner", metadata.getAbilities().get(1).getName());
		assertEquals("", metadata.getAbilities().get(1).getDescription());
		assertEquals("NOTED_CHANCE", metadata.getSkillAffixes().get(0).getEffect());

		ItemInstanceProtocol.parse("UNFORGE_ITEM_INSTANCE|3|remove|93|2").apply(store);
		assertNull(store.get(93, 2));
	}

	@Test
	public void parsesPlainWearableWithZeroInstanceId()
	{
		String message = String.join("|",
			"UNFORGE_ITEM_INSTANCE",
			"3",
			"upsert",
			"93",
			"5",
			"0",
			"11832",
			"-",
			"-",
			"-",
			"0",
			"0",
			"-",
			"-",
			"-",
			"-",
			"100");

		ItemInstanceMetadataStore store = new ItemInstanceMetadataStore();
		ItemInstanceProtocol.ParsedMessage parsed = ItemInstanceProtocol.parse(message);
		assertNotNull(parsed);
		parsed.apply(store);

		ItemInstanceMetadata metadata = store.get(93, 5);
		assertNotNull(metadata);
		assertEquals(0L, metadata.getInstanceId());
		assertEquals(11832, metadata.getObjectId());
		assertEquals(100, metadata.getSpeedBps());
		assertEquals(0, metadata.getAffixes().size());
	}

	@Test
	public void acceptsLegacyEnvelopes()
	{
		String legacy = String.join("|",
			"UNFORGE_ITEM_INSTANCE",
			"1",
			"upsert",
			"93",
			"9",
			"123456",
			"4151",
			"Weapon",
			"Rare",
			"Rune",
			"42",
			"87",
			"-",
			"-",
			"-");

		ItemInstanceMetadataStore store = new ItemInstanceMetadataStore();
		ItemInstanceProtocol.ParsedMessage parsed = ItemInstanceProtocol.parse(legacy);
		assertNotNull(parsed);
		parsed.apply(store);
		assertEquals(0, store.get(93, 9).getSpeedBps());
	}

	@Test
	public void mergesSplitSectionsIntoBaseUpsert()
	{
		String head = String.join("|",
			"UNFORGE_ITEM_INSTANCE", "3", "upsert", "93", "2", "123456", "4151",
			"Weapon", "Jackpot", "Rune", "42", "87", "-", "-", "-", "-", "700");

		ItemInstanceMetadataStore store = new ItemInstanceMetadataStore();
		ItemInstanceProtocol.parse(head).apply(store);
		assertEquals(0, store.get(93, 2).getAffixes().size());

		String affixA = String.join("~", "0", encode("stab-power"), encode("combat"),
			"AttackStab", "BasisPoints", "Boon", "250");
		String affixB = String.join("~", "1", encode("keen"), encode("keen"),
			"AttackSlash", "Flat", "Boon", "3");
		ItemInstanceProtocol.parse(String.join("|",
			"UNFORGE_ITEM_INSTANCE", "3", "sect", "93", "2", "4151",
			"affixes", "0", "2", affixA)).apply(store);
		// Incomplete chunk set must not merge yet.
		assertEquals(0, store.get(93, 2).getAffixes().size());
		ItemInstanceProtocol.parse(String.join("|",
			"UNFORGE_ITEM_INSTANCE", "3", "sect", "93", "2", "4151",
			"affixes", "1", "2", affixB)).apply(store);

		ItemInstanceMetadata metadata = store.get(93, 2);
		assertEquals(2, metadata.getAffixes().size());
		assertEquals("stab-power", metadata.getAffixes().get(0).getDefinitionId());
		assertEquals("keen", metadata.getAffixes().get(1).getDefinitionId());
		assertEquals("Jackpot", metadata.getRarity());
		assertEquals(700, metadata.getSpeedBps());
	}

	@Test
	public void dropsSectionForStaleObjectIdAndParksSectionBeforeUpsert()
	{
		ItemInstanceMetadataStore store = new ItemInstanceMetadataStore();

		// A section arriving before its upsert is parked, then merged by objectId.
		String affix = String.join("~", "0", encode("keen"), encode("keen"),
			"AttackSlash", "Flat", "Boon", "3");
		ItemInstanceProtocol.parse(String.join("|",
			"UNFORGE_ITEM_INSTANCE", "3", "sect", "93", "7", "4151",
			"affixes", "0", "1", affix)).apply(store);

		String head = String.join("|",
			"UNFORGE_ITEM_INSTANCE", "3", "upsert", "93", "7", "99", "4151",
			"Weapon", "Rare", "Rune", "10", "50", "-", "-", "-", "-", "0");
		ItemInstanceProtocol.parse(head).apply(store);
		assertEquals(1, store.get(93, 7).getAffixes().size());

		// A section for a different object id in the slot must be dropped.
		String stale = String.join("~", "0", encode("old"), encode("old"),
			"AttackStab", "Flat", "Boon", "9");
		ItemInstanceProtocol.parse(String.join("|",
			"UNFORGE_ITEM_INSTANCE", "3", "sect", "93", "7", "9999",
			"affixes", "0", "1", stale)).apply(store);
		assertEquals("keen", store.get(93, 7).getAffixes().get(0).getDefinitionId());

		// Removing the slot clears buffers so nothing stale merges afterwards.
		ItemInstanceProtocol.parse("UNFORGE_ITEM_INSTANCE|3|remove|93|7").apply(store);
		assertNull(store.get(93, 7));
	}

	@Test
	public void rejectsUnknownVersionMalformedAndNegativeScopeMessages()
	{
		assertNull(ItemInstanceProtocol.parse("UNFORGE_ITEM_INSTANCE|9|clear|93"));
		assertNull(ItemInstanceProtocol.parse("UNFORGE_ITEM_INSTANCE|3|remove|-1|2"));
		assertNull(ItemInstanceProtocol.parse("UNFORGE_ITEM_INSTANCE|3|upsert|93|2|bad"));
		assertNull(ItemInstanceProtocol.parse("UNFORGE_ITEM_INSTANCE|3|upsert|-1|2|-"));
		assertNull(ItemInstanceProtocol.parse("ordinary game message"));
	}

	private static String encode(String value)
	{
		return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
	}
}

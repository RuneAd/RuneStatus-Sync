package gg.runestatus.sync;

import gg.runestatus.sync.data.CombatAchievementData;
import gg.runestatus.sync.data.DiaryData;
import gg.runestatus.sync.data.SkillData;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.Varbits;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Singleton
public class DataCollector
{
	private final Client client;

	// Achievement Diary Varbits - completion status for each tier
	// These varbits track whether each diary tier is complete (1) or not (0)
	private static final int DIARY_ARDOUGNE_EASY = 4458;
	private static final int DIARY_ARDOUGNE_MEDIUM = 4459;
	private static final int DIARY_ARDOUGNE_HARD = 4460;
	private static final int DIARY_ARDOUGNE_ELITE = 4461;

	private static final int DIARY_DESERT_EASY = 4483;
	private static final int DIARY_DESERT_MEDIUM = 4484;
	private static final int DIARY_DESERT_HARD = 4485;
	private static final int DIARY_DESERT_ELITE = 4486;

	private static final int DIARY_FALADOR_EASY = 4462;
	private static final int DIARY_FALADOR_MEDIUM = 4463;
	private static final int DIARY_FALADOR_HARD = 4464;
	private static final int DIARY_FALADOR_ELITE = 4465;

	private static final int DIARY_FREMENNIK_EASY = 4491;
	private static final int DIARY_FREMENNIK_MEDIUM = 4492;
	private static final int DIARY_FREMENNIK_HARD = 4493;
	private static final int DIARY_FREMENNIK_ELITE = 4494;

	private static final int DIARY_KANDARIN_EASY = 4475;
	private static final int DIARY_KANDARIN_MEDIUM = 4476;
	private static final int DIARY_KANDARIN_HARD = 4477;
	private static final int DIARY_KANDARIN_ELITE = 4478;

	// Karamja diary uses different varbits - 3598 is the confirmed medium completion varbit
	private static final int DIARY_KARAMJA_EASY = 3578;
	private static final int DIARY_KARAMJA_MEDIUM = 3598;  // Was 3599, confirmed 3598 is correct for completion
	private static final int DIARY_KARAMJA_HARD = 3611;
	private static final int DIARY_KARAMJA_ELITE = 4566;

	private static final int DIARY_KOUREND_EASY = 7925;
	private static final int DIARY_KOUREND_MEDIUM = 7926;
	private static final int DIARY_KOUREND_HARD = 7927;
	private static final int DIARY_KOUREND_ELITE = 7928;

	private static final int DIARY_LUMBRIDGE_EASY = 4495;
	private static final int DIARY_LUMBRIDGE_MEDIUM = 4496;
	private static final int DIARY_LUMBRIDGE_HARD = 4497;
	private static final int DIARY_LUMBRIDGE_ELITE = 4498;

	private static final int DIARY_MORYTANIA_EASY = 4487;
	private static final int DIARY_MORYTANIA_MEDIUM = 4488;
	private static final int DIARY_MORYTANIA_HARD = 4489;
	private static final int DIARY_MORYTANIA_ELITE = 4490;

	private static final int DIARY_VARROCK_EASY = 4479;
	private static final int DIARY_VARROCK_MEDIUM = 4480;
	private static final int DIARY_VARROCK_HARD = 4481;
	private static final int DIARY_VARROCK_ELITE = 4482;

	private static final int DIARY_WESTERN_EASY = 4471;
	private static final int DIARY_WESTERN_MEDIUM = 4472;
	private static final int DIARY_WESTERN_HARD = 4473;
	private static final int DIARY_WESTERN_ELITE = 4474;

	private static final int DIARY_WILDERNESS_EASY = 4466;
	private static final int DIARY_WILDERNESS_MEDIUM = 4467;
	private static final int DIARY_WILDERNESS_HARD = 4468;
	private static final int DIARY_WILDERNESS_ELITE = 4469;

	// Combat Achievement Varbits
	private static final int CA_EASY_COMPLETE = 12855;
	private static final int CA_MEDIUM_COMPLETE = 12856;
	private static final int CA_HARD_COMPLETE = 12857;
	private static final int CA_ELITE_COMPLETE = 12858;
	private static final int CA_MASTER_COMPLETE = 12859;
	private static final int CA_GRANDMASTER_COMPLETE = 12860;

	@Inject
	public DataCollector(Client client)
	{
		this.client = client;
	}

	public String getUsername()
	{
		Player localPlayer = client.getLocalPlayer();
		if (localPlayer != null)
		{
			return localPlayer.getName();
		}
		return null;
	}

	public int getAccountType()
	{
		return client.getVarbitValue(Varbits.ACCOUNT_TYPE);
	}

	public int getWorld()
	{
		return client.getWorld();
	}

	public Map<String, SkillData> collectSkills()
	{
		Map<String, SkillData> skills = new HashMap<>();

		for (Skill skill : Skill.values())
		{
			if (skill == Skill.OVERALL)
			{
				continue;
			}

			skills.put(skill.getName(), SkillData.builder()
				.level(client.getRealSkillLevel(skill))
				.xp(client.getSkillExperience(skill))
				.build());
		}

		return skills;
	}

	public Map<String, String> collectQuests()
	{
		Map<String, String> quests = new HashMap<>();

		for (Quest quest : Quest.values())
		{
			QuestState state = quest.getState(client);
			quests.put(quest.getName(), state.name());
		}

		return quests;
	}

	public Map<String, DiaryData> collectAchievementDiaries()
	{
		Map<String, DiaryData> diaries = new HashMap<>();

		diaries.put("Ardougne", DiaryData.builder()
			.easy(client.getVarbitValue(DIARY_ARDOUGNE_EASY) == 1)
			.medium(client.getVarbitValue(DIARY_ARDOUGNE_MEDIUM) == 1)
			.hard(client.getVarbitValue(DIARY_ARDOUGNE_HARD) == 1)
			.elite(client.getVarbitValue(DIARY_ARDOUGNE_ELITE) == 1)
			.build());

		diaries.put("Desert", DiaryData.builder()
			.easy(client.getVarbitValue(DIARY_DESERT_EASY) == 1)
			.medium(client.getVarbitValue(DIARY_DESERT_MEDIUM) == 1)
			.hard(client.getVarbitValue(DIARY_DESERT_HARD) == 1)
			.elite(client.getVarbitValue(DIARY_DESERT_ELITE) == 1)
			.build());

		diaries.put("Falador", DiaryData.builder()
			.easy(client.getVarbitValue(DIARY_FALADOR_EASY) == 1)
			.medium(client.getVarbitValue(DIARY_FALADOR_MEDIUM) == 1)
			.hard(client.getVarbitValue(DIARY_FALADOR_HARD) == 1)
			.elite(client.getVarbitValue(DIARY_FALADOR_ELITE) == 1)
			.build());

		diaries.put("Fremennik", DiaryData.builder()
			.easy(client.getVarbitValue(DIARY_FREMENNIK_EASY) == 1)
			.medium(client.getVarbitValue(DIARY_FREMENNIK_MEDIUM) == 1)
			.hard(client.getVarbitValue(DIARY_FREMENNIK_HARD) == 1)
			.elite(client.getVarbitValue(DIARY_FREMENNIK_ELITE) == 1)
			.build());

		diaries.put("Kandarin", DiaryData.builder()
			.easy(client.getVarbitValue(DIARY_KANDARIN_EASY) == 1)
			.medium(client.getVarbitValue(DIARY_KANDARIN_MEDIUM) == 1)
			.hard(client.getVarbitValue(DIARY_KANDARIN_HARD) == 1)
			.elite(client.getVarbitValue(DIARY_KANDARIN_ELITE) == 1)
			.build());

		diaries.put("Karamja", DiaryData.builder()
			.easy(client.getVarbitValue(DIARY_KARAMJA_EASY) == 1)
			.medium(client.getVarbitValue(DIARY_KARAMJA_MEDIUM) == 1)
			.hard(client.getVarbitValue(DIARY_KARAMJA_HARD) == 1)
			.elite(client.getVarbitValue(DIARY_KARAMJA_ELITE) == 1)
			.build());

		diaries.put("Kourend & Kebos", DiaryData.builder()
			.easy(client.getVarbitValue(DIARY_KOUREND_EASY) == 1)
			.medium(client.getVarbitValue(DIARY_KOUREND_MEDIUM) == 1)
			.hard(client.getVarbitValue(DIARY_KOUREND_HARD) == 1)
			.elite(client.getVarbitValue(DIARY_KOUREND_ELITE) == 1)
			.build());

		diaries.put("Lumbridge & Draynor", DiaryData.builder()
			.easy(client.getVarbitValue(DIARY_LUMBRIDGE_EASY) == 1)
			.medium(client.getVarbitValue(DIARY_LUMBRIDGE_MEDIUM) == 1)
			.hard(client.getVarbitValue(DIARY_LUMBRIDGE_HARD) == 1)
			.elite(client.getVarbitValue(DIARY_LUMBRIDGE_ELITE) == 1)
			.build());

		diaries.put("Morytania", DiaryData.builder()
			.easy(client.getVarbitValue(DIARY_MORYTANIA_EASY) == 1)
			.medium(client.getVarbitValue(DIARY_MORYTANIA_MEDIUM) == 1)
			.hard(client.getVarbitValue(DIARY_MORYTANIA_HARD) == 1)
			.elite(client.getVarbitValue(DIARY_MORYTANIA_ELITE) == 1)
			.build());

		diaries.put("Varrock", DiaryData.builder()
			.easy(client.getVarbitValue(DIARY_VARROCK_EASY) == 1)
			.medium(client.getVarbitValue(DIARY_VARROCK_MEDIUM) == 1)
			.hard(client.getVarbitValue(DIARY_VARROCK_HARD) == 1)
			.elite(client.getVarbitValue(DIARY_VARROCK_ELITE) == 1)
			.build());

		diaries.put("Western Provinces", DiaryData.builder()
			.easy(client.getVarbitValue(DIARY_WESTERN_EASY) == 1)
			.medium(client.getVarbitValue(DIARY_WESTERN_MEDIUM) == 1)
			.hard(client.getVarbitValue(DIARY_WESTERN_HARD) == 1)
			.elite(client.getVarbitValue(DIARY_WESTERN_ELITE) == 1)
			.build());

		diaries.put("Wilderness", DiaryData.builder()
			.easy(client.getVarbitValue(DIARY_WILDERNESS_EASY) == 1)
			.medium(client.getVarbitValue(DIARY_WILDERNESS_MEDIUM) == 1)
			.hard(client.getVarbitValue(DIARY_WILDERNESS_HARD) == 1)
			.elite(client.getVarbitValue(DIARY_WILDERNESS_ELITE) == 1)
			.build());

		return diaries;
	}

	public CombatAchievementData collectCombatAchievements()
	{
		return CombatAchievementData.builder()
			.easy(client.getVarbitValue(CA_EASY_COMPLETE))
			.medium(client.getVarbitValue(CA_MEDIUM_COMPLETE))
			.hard(client.getVarbitValue(CA_HARD_COMPLETE))
			.elite(client.getVarbitValue(CA_ELITE_COMPLETE))
			.master(client.getVarbitValue(CA_MASTER_COMPLETE))
			.grandmaster(client.getVarbitValue(CA_GRANDMASTER_COMPLETE))
			.build();
	}

	public Map<String, Integer> collectEquipment()
	{
		Map<String, Integer> equipment = new HashMap<>();

		ItemContainer equipmentContainer = client.getItemContainer(InventoryID.EQUIPMENT);
		if (equipmentContainer == null)
		{
			return equipment;
		}

		Item[] items = equipmentContainer.getItems();
		String[] slotNames = {"Head", "Cape", "Amulet", "Weapon", "Body", "Shield", "Legs", "Gloves", "Boots", "Ring", "Ammo"};

		for (int i = 0; i < Math.min(items.length, slotNames.length); i++)
		{
			if (items[i] != null && items[i].getId() != -1)
			{
				equipment.put(slotNames[i], items[i].getId());
			}
		}

		return equipment;
	}
}

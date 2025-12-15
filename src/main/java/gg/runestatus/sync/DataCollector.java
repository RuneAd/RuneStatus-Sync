package gg.runestatus.sync;

import gg.runestatus.sync.data.CombatAchievementData;
import gg.runestatus.sync.data.DiaryData;
import gg.runestatus.sync.data.SkillData;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.Varbits;
import net.runelite.api.gameval.VarPlayerID;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Singleton
public class DataCollector
{
	private final Client client;

	// Achievement Diary Script - returns completion info for a diary
	// Script 2200 takes diary ID and returns completion data in intStack
	// Reference: https://github.com/RuneStar/cs2-scripts/blob/master/scripts/%5Bproc%2Cdiary_completion_info%5D.cs2
	private static final int DIARY_COMPLETION_SCRIPT = 2200;

	// Diary IDs for script 2200
	private static final int DIARY_ID_KARAMJA = 0;
	private static final int DIARY_ID_ARDOUGNE = 1;
	private static final int DIARY_ID_FALADOR = 2;
	private static final int DIARY_ID_FREMENNIK = 3;
	private static final int DIARY_ID_KANDARIN = 4;
	private static final int DIARY_ID_DESERT = 5;
	private static final int DIARY_ID_LUMBRIDGE = 6;
	private static final int DIARY_ID_MORYTANIA = 7;
	private static final int DIARY_ID_VARROCK = 8;
	private static final int DIARY_ID_WILDERNESS = 9;
	private static final int DIARY_ID_WESTERN = 10;
	private static final int DIARY_ID_KOUREND = 11;

	// Combat Achievement Script - returns completed task count for a tier
	// Script 4784 takes tier ID (1=Easy, 2=Medium, 3=Hard, 4=Elite, 5=Master, 6=Grandmaster)
	private static final int CA_COMPLETED_COUNT_SCRIPT = 4784;

	// Combat Achievement total tasks per tier (as of 2024)
	private static final int CA_EASY_TOTAL = 33;
	private static final int CA_MEDIUM_TOTAL = 41;
	private static final int CA_HARD_TOTAL = 129;
	private static final int CA_ELITE_TOTAL = 182;
	private static final int CA_MASTER_TOTAL = 150;
	private static final int CA_GRANDMASTER_TOTAL = 90;
	private static final int CA_TOTAL = CA_EASY_TOTAL + CA_MEDIUM_TOTAL + CA_HARD_TOTAL + CA_ELITE_TOTAL + CA_MASTER_TOTAL + CA_GRANDMASTER_TOTAL;

	// VarcInt for time played in minutes
	private static final int VARC_TIME_PLAYED = 526;

	// All diary IDs for iterating
	private static final int[] ALL_DIARY_IDS = {
		DIARY_ID_KARAMJA, DIARY_ID_ARDOUGNE, DIARY_ID_FALADOR, DIARY_ID_FREMENNIK,
		DIARY_ID_KANDARIN, DIARY_ID_DESERT, DIARY_ID_LUMBRIDGE, DIARY_ID_MORYTANIA,
		DIARY_ID_VARROCK, DIARY_ID_WILDERNESS, DIARY_ID_WESTERN, DIARY_ID_KOUREND
	};

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

	public int getCombatLevel()
	{
		Player localPlayer = client.getLocalPlayer();
		if (localPlayer != null)
		{
			return localPlayer.getCombatLevel();
		}
		return 0;
	}

	public int getTotalLevel()
	{
		int total = 0;
		for (Skill skill : Skill.values())
		{
			if (skill == Skill.OVERALL)
			{
				continue;
			}
			total += client.getRealSkillLevel(skill);
		}
		return total;
	}

	public long getTotalXp()
	{
		long total = 0;
		for (Skill skill : Skill.values())
		{
			if (skill == Skill.OVERALL)
			{
				continue;
			}
			total += client.getSkillExperience(skill);
		}
		return total;
	}

	public int getTimePlayedMinutes()
	{
		return client.getVarcIntValue(VARC_TIME_PLAYED);
	}

	/**
	 * Gets the collection log obtained count from VarPlayer.
	 * This is available anytime after login, no need to open collection log.
	 */
	public int getCollectionLogCount()
	{
		return client.getVarpValue(VarPlayerID.COLLECTION_COUNT);
	}

	/**
	 * Returns [completed, total] quest counts
	 */
	public int[] getQuestCounts()
	{
		int completed = 0;
		int total = 0;
		for (Quest quest : Quest.values())
		{
			total++;
			if (quest.getState(client) == QuestState.FINISHED)
			{
				completed++;
			}
		}
		return new int[] { completed, total };
	}

	/**
	 * Returns [completed, total] diary task counts across all regions
	 */
	public int[] getDiaryTaskCounts()
	{
		int completed = 0;
		int total = 0;

		for (int diaryId : ALL_DIARY_IDS)
		{
			client.runScript(DIARY_COMPLETION_SCRIPT, diaryId);
			int[] stack = client.getIntStack();

			// Easy
			completed += stack[0];
			total += stack[1];
			// Medium
			completed += stack[3];
			total += stack[4];
			// Hard
			completed += stack[6];
			total += stack[7];
			// Elite
			completed += stack[9];
			total += stack[10];
		}

		return new int[] { completed, total };
	}

	/**
	 * Returns [completed, total] combat achievement counts
	 */
	public int[] getCombatTaskCounts()
	{
		int completed = 0;
		for (int tier = 1; tier <= 6; tier++)
		{
			completed += getCompletedCombatAchievementCount(tier);
		}
		return new int[] { completed, CA_TOTAL };
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

		// Use script 2200 which is the authoritative source for diary completion
		// This is the same method used by RuneProfile and other reliable plugins
		diaries.put("Karamja", getDiaryDataFromScript(DIARY_ID_KARAMJA));
		diaries.put("Ardougne", getDiaryDataFromScript(DIARY_ID_ARDOUGNE));
		diaries.put("Falador", getDiaryDataFromScript(DIARY_ID_FALADOR));
		diaries.put("Fremennik", getDiaryDataFromScript(DIARY_ID_FREMENNIK));
		diaries.put("Kandarin", getDiaryDataFromScript(DIARY_ID_KANDARIN));
		diaries.put("Desert", getDiaryDataFromScript(DIARY_ID_DESERT));
		diaries.put("Lumbridge & Draynor", getDiaryDataFromScript(DIARY_ID_LUMBRIDGE));
		diaries.put("Morytania", getDiaryDataFromScript(DIARY_ID_MORYTANIA));
		diaries.put("Varrock", getDiaryDataFromScript(DIARY_ID_VARROCK));
		diaries.put("Wilderness", getDiaryDataFromScript(DIARY_ID_WILDERNESS));
		diaries.put("Western Provinces", getDiaryDataFromScript(DIARY_ID_WESTERN));
		diaries.put("Kourend & Kebos", getDiaryDataFromScript(DIARY_ID_KOUREND));

		return diaries;
	}

	/**
	 * Gets diary completion data using script 2200.
	 * The script returns completion info in intStack (12 values):
	 * - stack[0] = easy completed count
	 * - stack[1] = easy total count
	 * - stack[3] = medium completed count
	 * - stack[4] = medium total count
	 * - stack[6] = hard completed count
	 * - stack[7] = hard total count
	 * - stack[9] = elite completed count
	 * - stack[10] = elite total count
	 * Reference: https://github.com/RuneStar/cs2-scripts/blob/master/scripts/%5Bproc%2Cdiary_completion_info%5D.cs2
	 */
	private DiaryData getDiaryDataFromScript(int diaryId)
	{
		client.runScript(DIARY_COMPLETION_SCRIPT, diaryId);
		int[] stack = client.getIntStack();

		// A tier is complete when completed count >= total count
		int easyCompleted = stack[0];
		int easyTotal = stack[1];
		int mediumCompleted = stack[3];
		int mediumTotal = stack[4];
		int hardCompleted = stack[6];
		int hardTotal = stack[7];
		int eliteCompleted = stack[9];
		int eliteTotal = stack[10];

		boolean easy = easyTotal > 0 && easyCompleted >= easyTotal;
		boolean medium = mediumTotal > 0 && mediumCompleted >= mediumTotal;
		boolean hard = hardTotal > 0 && hardCompleted >= hardTotal;
		boolean elite = eliteTotal > 0 && eliteCompleted >= eliteTotal;

		log.debug("Diary {} - Easy: {}/{}, Medium: {}/{}, Hard: {}/{}, Elite: {}/{}",
			diaryId, easyCompleted, easyTotal, mediumCompleted, mediumTotal,
			hardCompleted, hardTotal, eliteCompleted, eliteTotal);

		return DiaryData.builder()
			.easy(easy)
			.medium(medium)
			.hard(hard)
			.elite(elite)
			.build();
	}

	public CombatAchievementData collectCombatAchievements()
	{
		// Script 4784 returns the completed task count for a given tier
		// Tier IDs: 1=Easy, 2=Medium, 3=Hard, 4=Elite, 5=Master, 6=Grandmaster
		return CombatAchievementData.builder()
			.easy(getCompletedCombatAchievementCount(1))
			.medium(getCompletedCombatAchievementCount(2))
			.hard(getCompletedCombatAchievementCount(3))
			.elite(getCompletedCombatAchievementCount(4))
			.master(getCompletedCombatAchievementCount(5))
			.grandmaster(getCompletedCombatAchievementCount(6))
			.build();
	}

	private int getCompletedCombatAchievementCount(int tierId)
	{
		client.runScript(CA_COMPLETED_COUNT_SCRIPT, tierId);
		return client.getIntStack()[0];
	}
}

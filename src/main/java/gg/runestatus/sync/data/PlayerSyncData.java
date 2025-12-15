package gg.runestatus.sync.data;

import lombok.Builder;
import lombok.Data;
import java.util.Map;

@Data
@Builder
public class PlayerSyncData
{
	private String username;
	private int accountType;
	private int world;
	private Map<String, SkillData> skills;
	private Map<String, String> quests;
	private Map<String, DiaryData> achievementDiaries;
	private CombatAchievementData combatAchievements;
	private Map<String, Integer> equipment;
	private Map<String, Map<String, CollectionLogItem>> collectionLog;
	private CollectionLogCounts collectionLogCounts;
	private long lastSyncedAt;
}

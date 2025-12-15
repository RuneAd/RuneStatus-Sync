package gg.runestatus.sync.data;

import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class PlayerSyncData
{
	private String username;
	private int accountType;
	private int world;

	// Summary stats (from Character Summary panel)
	private int combatLevel;
	private int totalLevel;
	private long totalXp;
	private int questsCompleted;
	private int questsTotal;
	private int diaryTasksCompleted;
	private int diaryTasksTotal;
	private int combatTasksCompleted;
	private int combatTasksTotal;
	private int collectionLogObtained;
	private int timePlayedMinutes;

	// Detailed data
	private Map<String, SkillData> skills;
	private Map<String, String> quests;
	private Map<String, DiaryData> achievementDiaries;
	private CombatAchievementData combatAchievements;

	// Recent collection log drops detected from chat messages
	private List<String> recentDrops;

	private long lastSyncedAt;
}

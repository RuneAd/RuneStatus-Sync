package gg.runestatus.sync.data;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CombatAchievementData
{
	private int easy;
	private int medium;
	private int hard;
	private int elite;
	private int master;
	private int grandmaster;
}

package gg.runestatus.sync.data;

import com.google.gson.annotations.SerializedName;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CombatAchievementData
{
	@SerializedName("Easy")
	private int easy;

	@SerializedName("Medium")
	private int medium;

	@SerializedName("Hard")
	private int hard;

	@SerializedName("Elite")
	private int elite;

	@SerializedName("Master")
	private int master;

	@SerializedName("Grandmaster")
	private int grandmaster;
}

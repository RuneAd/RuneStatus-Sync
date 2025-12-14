package gg.runestatus.sync.data;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SkillData
{
	private int level;
	private int xp;
}

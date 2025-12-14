package gg.runestatus.sync.data;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DiaryData
{
	private boolean easy;
	private boolean medium;
	private boolean hard;
	private boolean elite;
}

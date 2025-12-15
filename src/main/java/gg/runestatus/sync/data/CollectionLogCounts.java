package gg.runestatus.sync.data;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CollectionLogCounts
{
	private int obtained;
	private int total;
}

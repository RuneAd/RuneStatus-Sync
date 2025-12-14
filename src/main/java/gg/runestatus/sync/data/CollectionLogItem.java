package gg.runestatus.sync.data;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CollectionLogItem
{
	private boolean obtained;
	private int count;
}

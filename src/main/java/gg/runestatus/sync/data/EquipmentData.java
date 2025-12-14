package gg.runestatus.sync.data;

import lombok.Builder;
import lombok.Data;
import java.util.Map;

@Data
@Builder
public class EquipmentData
{
	private Map<String, Integer> slots;
}

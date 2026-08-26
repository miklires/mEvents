package io.github.miklires.mevents.event;
import java.util.List;
public record RewardDefinition(String id,double weight,List<String> commands){public RewardDefinition{if(id==null||id.isBlank())throw new IllegalArgumentException("Reward id is blank");if(!Double.isFinite(weight)||weight<=0)throw new IllegalArgumentException("Reward weight must be positive");commands=List.copyOf(commands);}}

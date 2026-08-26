package io.github.miklires.mevents.event;

import io.github.miklires.mevents.api.EventType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.util.*;

public final class EventCatalog {
    private volatile Map<String,EventDefinition> definitions=Map.of();
    public void load(File file){
        YamlConfiguration yaml=YamlConfiguration.loadConfiguration(file);ConfigurationSection root=yaml.getConfigurationSection("events");
        if(root==null)throw new IllegalArgumentException("events.yml has no events section");Map<String,EventDefinition> loaded=new LinkedHashMap<>();
        for(String id:root.getKeys(false)){ConfigurationSection s=Objects.requireNonNull(root.getConfigurationSection(id));List<RewardDefinition> rewards=new ArrayList<>();for(Map<?,?> raw:s.getMapList("rewards")){String reward=String.valueOf(raw.get("id"));Object rawWeight=raw.containsKey("weight")?raw.get("weight"):1;double weight=Double.parseDouble(String.valueOf(rawWeight));Object value=raw.get("commands");List<String> commands=value instanceof List<?> list?list.stream().map(String::valueOf).toList():List.of();rewards.add(new RewardDefinition(reward,weight,commands));}
            loaded.put(id,new EventDefinition(id,s.getString("name",id),EventType.valueOf(s.getString("type","AIRDROP").toUpperCase(Locale.ROOT)),s.getString("location.world","world"),s.getInt("location.x"),s.getInt("location.y",80),s.getInt("location.z"),s.getLong("preparation-ticks",100),s.getLong("duration-ticks",1200),s.getString("boss.entity","ZOMBIE"),s.getDouble("boss.health",100),rewards));}
        definitions=Map.copyOf(loaded);
    }
    public Optional<EventDefinition> find(String id){return Optional.ofNullable(definitions.get(id));}
    public Collection<EventDefinition> all(){return definitions.values();}
    public Optional<RewardDefinition> reward(String id){return definitions.values().stream().flatMap(d->d.rewards().stream()).filter(r->r.id().equals(id)).findFirst();}
}

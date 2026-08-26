package io.github.miklires.mevents.api;
import java.time.Instant;import java.util.UUID;
public record EventRunView(UUID runId,String templateId,EventType type,EventState state,String world,int x,int y,int z,Instant createdAt,Instant startedAt,Instant endsAt){}

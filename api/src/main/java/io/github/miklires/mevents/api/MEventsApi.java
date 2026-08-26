package io.github.miklires.mevents.api;
import java.util.Collection;import java.util.Optional;import java.util.UUID;import java.util.concurrent.CompletableFuture;
public interface MEventsApi {Collection<String> templateIds();CompletableFuture<Optional<EventRunView>> active(String templateId);CompletableFuture<Boolean> cancel(UUID runId,String reason);}

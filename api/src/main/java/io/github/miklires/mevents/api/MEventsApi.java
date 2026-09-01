package io.github.miklires.mevents.api;
import java.util.Collection;import java.util.Optional;import java.util.UUID;import java.util.concurrent.CompletableFuture;
public interface MEventsApi {Collection<String> templateIds();default CompletableFuture<EventRunView> start(String templateId){return CompletableFuture.failedFuture(new UnsupportedOperationException("Starting events is not supported by this provider"));}CompletableFuture<Optional<EventRunView>> active(String templateId);CompletableFuture<Boolean> cancel(UUID runId,String reason);}

package com.dpworld.fms.api;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class TrackItTelemetryEvents {
  private final Set<SseEmitter> clients = ConcurrentHashMap.newKeySet();

  public SseEmitter subscribe() {
    SseEmitter emitter = new SseEmitter(0L);
    clients.add(emitter);
    emitter.onCompletion(() -> clients.remove(emitter));
    emitter.onTimeout(() -> clients.remove(emitter));
    emitter.onError(error -> clients.remove(emitter));
    try {
      emitter.send(SseEmitter.event().name("connected").data("ready"));
    } catch (IOException exception) {
      clients.remove(emitter);
      emitter.completeWithError(exception);
    }
    return emitter;
  }

  public void publish(Object snapshot) {
    clients.removeIf(emitter -> {
      try {
        emitter.send(SseEmitter.event().name("telemetry").data(snapshot));
        return false;
      } catch (IOException exception) {
        emitter.complete();
        return true;
      }
    });
  }
}

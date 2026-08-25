package com.dpworld.fms.application.automation;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Atomic local implementation. Production adapters use the same semantics with Redis SET NX and DB uniqueness. */
public final class AutomationReservationService {
  private final ConcurrentMap<String, Reservation> byResource = new ConcurrentHashMap<>();

  public Optional<Reservation> reserve(String resourceType, String resourceId, UUID assetId,
                                       String idempotencyKey, Instant now, Duration ttl) {
    Reservation candidate = new Reservation(UUID.randomUUID(), resourceType, resourceId, assetId,
        idempotencyKey, now, now.plus(ttl), false, 0);
    Reservation result = byResource.compute(resourceId, (ignored, existing) ->
        existing == null || existing.released() || !existing.expiresAt().isAfter(now)
            ? candidate : existing);
    return result.id().equals(candidate.id()) ? Optional.of(result) : Optional.empty();
  }

  public Reservation extend(UUID id, Instant now, Duration extension) {
    for (String key : byResource.keySet()) {
      Reservation updated = byResource.computeIfPresent(key, (ignored, old) -> old.id().equals(id)
          && !old.released() ? new Reservation(old.id(), old.resourceType(), old.resourceId(),
          old.assetId(), old.idempotencyKey(), old.createdAt(), now.plus(extension), false,
          old.version() + 1) : old);
      if (updated != null && updated.id().equals(id)) return updated;
    }
    throw new IllegalArgumentException("active reservation not found");
  }

  public void release(UUID id) {
    byResource.replaceAll((key, old) -> old.id().equals(id)
        ? new Reservation(old.id(), old.resourceType(), old.resourceId(), old.assetId(),
            old.idempotencyKey(), old.createdAt(), old.expiresAt(), true, old.version() + 1) : old);
  }

  public List<Reservation> active(Instant now) {
    return byResource.values().stream().filter(r -> !r.released() && r.expiresAt().isAfter(now)).toList();
  }

  public record Reservation(UUID id, String resourceType, String resourceId, UUID assetId,
                            String idempotencyKey, Instant createdAt, Instant expiresAt,
                            boolean released, long version) {}
}

package com.dpworld.fms.api;

import java.time.Duration;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/** Owner-token Redis lock used to serialize multi-instance scheduler work. */
@Component
public class RedisAutomationLock {
  private static final DefaultRedisScript<Long> RELEASE = new DefaultRedisScript<>(
      "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
      Long.class);
  private final StringRedisTemplate redis;

  public RedisAutomationLock(StringRedisTemplate redis) { this.redis = redis; }

  public Optional<Lease> acquire(String name, Duration ttl) {
    String key = "dpwfms:automation:lock:" + name;
    String owner = UUID.randomUUID().toString();
    Boolean acquired = redis.opsForValue().setIfAbsent(key, owner, ttl);
    return Boolean.TRUE.equals(acquired) ? Optional.of(new Lease(key, owner)) : Optional.empty();
  }

  public final class Lease implements AutoCloseable {
    private final String key;
    private final String owner;
    private boolean closed;
    private Lease(String key, String owner) { this.key = key; this.owner = owner; }
    @Override public void close() {
      if (!closed) {
        redis.execute(RELEASE, Collections.singletonList(key), owner);
        closed = true;
      }
    }
  }
}

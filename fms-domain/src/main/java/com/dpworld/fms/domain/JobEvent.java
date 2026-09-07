package com.dpworld.fms.domain;
import java.time.Instant; import java.util.UUID;
public record JobEvent(UUID id, UUID jobId, JobStatus fromStatus, JobStatus toStatus, Instant occurredAt, String actor, String reason) {}

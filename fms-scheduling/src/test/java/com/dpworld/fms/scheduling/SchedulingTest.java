package com.dpworld.fms.scheduling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dpworld.fms.domain.Asset;
import com.dpworld.fms.domain.AssetStatus;
import com.dpworld.fms.domain.AssetType;
import com.dpworld.fms.domain.Availability;
import com.dpworld.fms.domain.FleetJob;
import com.dpworld.fms.domain.GeoPoint;
import com.dpworld.fms.domain.JobStatus;
import com.dpworld.fms.domain.JobType;
import com.dpworld.fms.domain.MaintenanceStatus;
import com.dpworld.fms.domain.VehicleEnvelope;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

class SchedulingTest {
  private static final Instant NOW = Instant.parse("2026-08-27T10:00:00Z");

  @Test
  void selectsEligibleCandidateAndRejectsLowFuel() {
    CandidateScorer scorer = scorer();
    CandidateScorer.Candidate lowFuel = candidate(asset(10, Availability.AVAILABLE), 1);
    CandidateScorer.Candidate eligible = candidate(asset(80, Availability.AVAILABLE), 2);

    assertEquals(eligible, scorer.select(job(), List.of(lowFuel, eligible), NOW).orElseThrow());
    assertTrue(scorer.select(job(), List.of(lowFuel), NOW).isEmpty());
  }

  @Test
  void unavailableAssetIsNotEligible() {
    CandidateScorer scorer = scorer();
    CandidateScorer.Candidate unavailable = candidate(asset(80, Availability.UNAVAILABLE), 1);

    assertTrue(scorer.select(job(), List.of(unavailable), NOW).isEmpty());
  }

  @Test
  void reservationIsAtomic() throws Exception {
    ResourceReservationService reservations = new ResourceReservationService();
    var executor = Executors.newFixedThreadPool(8);
    try {
      CountDownLatch start = new CountDownLatch(1);
      List<Future<Boolean>> attempts = new ArrayList<>();
      for (int index = 0; index < 20; index++) {
        UUID jobId = UUID.randomUUID();
        attempts.add(executor.submit(() -> {
          start.await();
          return reservations.reserve("BAY-1", jobId, Duration.ofMinutes(1)).isPresent();
        }));
      }

      start.countDown();
      long successfulReservations = 0;
      for (Future<Boolean> attempt : attempts) {
        if (attempt.get()) successfulReservations++;
      }
      assertEquals(1L, successfulReservations);
    } finally {
      executor.shutdownNow();
    }
  }

  private CandidateScorer scorer() {
    return new CandidateScorer(new CandidateScorer.Weights(1, 1, 1, 1, 1, 1, 1, 1), 15);
  }

  private CandidateScorer.Candidate candidate(Asset asset, double distanceMetres) {
    return new CandidateScorer.Candidate(asset, distanceMetres, distanceMetres, 0, 0);
  }

  private Asset asset(double energy, Availability availability) {
    return new Asset(UUID.randomUUID(), "ITV-1", AssetType.ITV, new GeoPoint(24.995, 55.04),
        0, 0, AssetStatus.IDLE, availability, energy, 0, 0, "YARD-A", null, NOW,
        null, "DEVICE-1", "TRACKIT-1", MaintenanceStatus.SERVICEABLE,
        Set.of("TWISTLOCK"), new VehicleEnvelope(3.2, 2.5, 7, 35));
  }

  private FleetJob job() {
    FleetJob job = new FleetJob(UUID.randomUUID(), "J-1", JobType.CONTAINER_TRANSPORT, 10,
        "A", "B", Set.of("TWISTLOCK"), NOW, NOW.plusSeconds(300), "test");
    job.transition(JobStatus.VALIDATED, "test", "validated");
    job.transition(JobStatus.SCHEDULED, "test", "scheduled");
    return job;
  }
}

package com.dpworld.fms.simulator;

import java.util.List;

public record AutomationScenario(String code, String name, String description, List<String> expectedEvents) {
  public AutomationScenario { expectedEvents = List.copyOf(expectedEvents); }

  public static List<AutomationScenario> demonstrations() {
    return List.of(
        new AutomationScenario("IDLE_PARK", "Idle asset automatically parked", "An idle ITV is scored and reserves the best compatible space.", List.of("DECISION", "RESERVATION", "PARKING_JOB")),
        new AutomationScenario("SPACE_OCCUPIED", "Destination occupied during travel", "The original reservation is retained until an alternative is secured.", List.of("OCCUPANCY_ALERT", "ALTERNATIVE_RESERVATION", "REROUTE")),
        new AutomationScenario("PARK_REROUTE", "Parking space reassignment", "A newly occupied destination causes an atomic alternative reservation and route replacement.", List.of("ALTERNATIVE_SELECTED", "OLD_RESERVATION_RELEASED", "DISPATCH_UPDATED")),
        new AutomationScenario("LOW_FUEL", "Low fuel station selection", "Queue cost makes the operationally best station win.", List.of("FUEL_DECISION", "BAY_RESERVATION", "FUELING_JOB")),
        new AutomationScenario("QUEUE_SELECTION", "Distant station beats nearby queue", "A long queue at the nearest station makes a farther available bay the lower-cost choice.", List.of("CANDIDATE_SCORES", "QUEUE_PENALTY", "DISTANT_STATION_SELECTED")),
        new AutomationScenario("CRITICAL_FUEL", "Critical fuel transport block", "Critical fuel prevents transport assignment.", List.of("CRITICAL_ALERT", "TRANSPORT_REJECTED", "FUELING_JOB")),
        new AutomationScenario("STATION_OUTAGE", "Fuel station outage", "A safe alternative bay is reserved before the old bay is released.", List.of("OUTAGE", "REASSIGNMENT", "REROUTE")),
        new AutomationScenario("FUEL_TO_PARK", "Fueling-to-parking chain", "Dependent parking remains blocked until fueling completes.", List.of("PARENT_FUEL_JOB", "CHILD_PARK_JOB", "DEPENDENCY")),
        new AutomationScenario("DUPLICATE_MESSAGE", "Duplicate telemetry", "Idempotency prevents a duplicate physical job.", List.of("FIRST_ACCEPTED", "DUPLICATE_IGNORED")),
        new AutomationScenario("CONCURRENT_RESERVATION", "Concurrent schedulers", "Only one scheduler wins the atomic reservation.", List.of("LOCK_CONTENTION", "SINGLE_WINNER")),
        new AutomationScenario("STALE_GPS", "Telemetry becomes stale", "Dispatch pauses and an alert is raised.", List.of("WAITING_FOR_ASSET", "STALE_ALERT")),
        new AutomationScenario("ROUTE_DEVIATION", "Asset route deviation", "The route is recalculated from the current node.", List.of("DEVIATION", "REROUTE")));
  }
}

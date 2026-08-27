package com.dpworld.fms.application.automation;

import static org.junit.jupiter.api.Assertions.*;
import com.dpworld.fms.domain.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class RuleConfigurationServiceTest {
  @Test void mostSpecificConfigurationWinsDeterministically() {
    Instant now = Instant.parse("2026-08-21T12:00:00Z");
    RuleConfigurationService service = RuleConfigurationService.withDefaults(now.minusSeconds(1));
    AssetAutomationSnapshot asset = snapshot(now);
    service.save(rule("ZONE", AutomationRule.ScopeType.OPERATIONAL_ZONE, "YARD-A", 1, now));
    service.save(rule("ASSET", AutomationRule.ScopeType.ASSET, asset.assetId().toString(), 1, now));
    assertEquals("ASSET", service.resolve(AutomationRule.RuleKind.PARKING, asset, now).orElseThrow().code());
  }

  @Test void unsafeFuelThresholdsCannotBeActivated() {
    assertThrows(IllegalArgumentException.class, () -> new AutomationRule(UUID.randomUUID(), "BAD", "bad", "bad",
        AutomationRule.RuleKind.FUELING, AutomationRule.ScopeType.GLOBAL, null, true, 1, null, null,
        Map.of("emergencyFuelPercent", 20d, "criticalFuelPercent", 10d, "lowFuelPercent", 35d),
        Map.of(), 0, 0, 0, 1, "test", "test", Instant.now()));
  }

  private AutomationRule rule(String code, AutomationRule.ScopeType scope, String scopeId, int version, Instant now) {
    return new AutomationRule(UUID.randomUUID(), code, code, code, AutomationRule.RuleKind.PARKING,
        scope, scopeId, true, 1, now.minusSeconds(1), null, Map.of(), Map.of(), 0, 1, 1,
        version, "test", "test", now);
  }
  private AssetAutomationSnapshot snapshot(Instant now) {
    return new AssetAutomationSnapshot(UUID.randomUUID(), "T", "YARD-A", AssetType.ITV, "ITV-DAY", true,
        new GeoPoint(24.995,55.04), "N-1", now, AssetStatus.IDLE, MaintenanceStatus.SERVICEABLE,
        false,false,false,true,true,true,false,false,false,false,null,now.minusSeconds(500),50,
        "DIESEL",10000,new VehicleEnvelope(3.2,2.5,7.0,35.0),Set.of("YARD-A","SERVICE"),now);
  }
}

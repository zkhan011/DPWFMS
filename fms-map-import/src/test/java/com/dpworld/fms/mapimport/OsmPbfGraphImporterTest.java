package com.dpworld.fms.mapimport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OsmPbfGraphImporterTest {
  @Test
  void generatesDirectedEdgesAndReportsDisconnectedResources() {
    var importer = new OsmPbfGraphImporter(Set.of("service"), 25);
    Map<Long, OsmPbfGraphImporter.RawNode> nodes = Map.of(
        1L, new OsmPbfGraphImporter.RawNode(1, 25, 55, Map.of()),
        2L, new OsmPbfGraphImporter.RawNode(2, 25, 55.001, Map.of()),
        3L, new OsmPbfGraphImporter.RawNode(3, 25.1, 55.1, Map.of()));
    var ways = List.of(new OsmPbfGraphImporter.RawWay(
        10, List.of(1L, 2L), Map.of("highway", "service", "oneway", "yes")));
    var overrides = new TerminalOverrides(null, null, null, null,
        List.of(new TerminalOverrides.PrivateNode("PRIVATE", 25.1, 55.1)), null,
        Map.of("PARK-1", "PRIVATE"), null, null);

    var graph = importer.build(nodes, ways, overrides);

    assertEquals(1, graph.segments().size());
    assertFalse(graph.report().valid());
    assertTrue(graph.report().errors().stream().anyMatch(error -> error.contains("unreachable")));
  }

  @Test
  void appliesSpeedDirectionAndAccessOverrides() {
    var importer = new OsmPbfGraphImporter(Set.of("service"), 25);
    Map<Long, OsmPbfGraphImporter.RawNode> nodes = Map.of(
        1L, new OsmPbfGraphImporter.RawNode(1, 25, 55, Map.of()),
        2L, new OsmPbfGraphImporter.RawNode(2, 25, 55.001, Map.of()));
    var ways = List.of(new OsmPbfGraphImporter.RawWay(
        10, List.of(1L, 2L), Map.of("highway", "service")));
    var overrides = new TerminalOverrides(null, Map.of(10L, "-1"), Map.of(10L, 12d),
        Map.of(10L, List.of("ITV")), null, null, null, null, null);

    var graph = importer.build(nodes, ways, overrides);

    assertEquals(1, graph.segments().size());
    assertEquals("REVERSE", graph.segments().getFirst().direction());
    assertEquals(12, graph.segments().getFirst().speedKph());
    assertEquals(List.of("ITV"), graph.segments().getFirst().allowedAssetTypes());
  }
}

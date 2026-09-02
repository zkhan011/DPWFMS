package com.dpworld.fms.api;
import com.dpworld.fms.routing.*;import org.springframework.context.annotation.*;
@Configuration public class RoutingConfiguration{@Bean ProductionRoutingService productionRoutingService(RoutingGraphRepository graphs,PlannedRouteRepository routes){return new ProductionRoutingService(graphs,routes);}}

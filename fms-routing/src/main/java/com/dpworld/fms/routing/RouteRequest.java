package com.dpworld.fms.routing;
import com.dpworld.fms.domain.*;
public record RouteRequest(String from,String to,AssetType assetType,VehicleEnvelope envelope,double availableRangeMetres,int jobPriority) {}

package com.dpworld.fms.domain;
public record VehicleEnvelope(double heightM, double widthM, double lengthM, double weightTonnes) { public static VehicleEnvelope unrestricted(){ return new VehicleEnvelope(0,0,0,0); } }

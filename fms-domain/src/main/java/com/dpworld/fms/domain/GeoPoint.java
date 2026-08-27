package com.dpworld.fms.domain;
public record GeoPoint(double latitude, double longitude) {
  public GeoPoint { if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) throw new IllegalArgumentException("invalid coordinates"); }
  public double distanceMetres(GeoPoint other) { double r=6_371_000, p1=Math.toRadians(latitude), p2=Math.toRadians(other.latitude), dp=Math.toRadians(other.latitude-latitude), dl=Math.toRadians(other.longitude-longitude); double a=Math.sin(dp/2)*Math.sin(dp/2)+Math.cos(p1)*Math.cos(p2)*Math.sin(dl/2)*Math.sin(dl/2); return 2*r*Math.atan2(Math.sqrt(a),Math.sqrt(1-a)); }
}

package com.graphhopper.resources;

public class EdgeUpdateRequest {
    private String lineString; // Holds the LineString in WKT (Well-Known Text) or GeoJSON format
    private double speed;      // Holds the speed value associated with the LineString

    // Getters and Setters
    public String getLineString() {
        return lineString;
    }

    public void setLineString(String lineString) {
        this.lineString = lineString;
    }

    public double getSpeed() {
        return speed;
    }

    public void setSpeed(double speed) {
        this.speed = speed;
    }
}

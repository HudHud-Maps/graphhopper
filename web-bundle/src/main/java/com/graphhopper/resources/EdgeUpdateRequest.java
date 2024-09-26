package com.graphhopper.resources;

import com.fasterxml.jackson.annotation.JsonProperty;

public class EdgeUpdateRequest {

    @JsonProperty("geometry")
    private String geometry;

    @JsonProperty("speed")
    private double speed;

    // Getter and Setter for lineString
    public String getGeometry() {
        return geometry;
    }

    public void setGeometry(String geometry) {
        this.geometry = geometry;
    }

    // Getter and Setter for speed
    public double getSpeed() {
        return speed;
    }

    public void setSpeed(double speed) {
        this.speed = speed;
    }
}

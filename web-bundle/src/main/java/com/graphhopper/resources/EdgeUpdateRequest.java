package com.graphhopper.resources;

import com.fasterxml.jackson.annotation.JsonProperty;

public class EdgeUpdateRequest {

    @JsonProperty("wayId")
    private int wayID;

    @JsonProperty("speed")
    private double speed;

    // Getter and Setter for lineString
    public int getWayID() {
        return wayID;
    }

    public void setWayID(int wayID) {
        this.wayID = wayID;
    }

    // Getter and Setter for speed
    public double getSpeed() {
        return speed;
    }

    public void setSpeed(double speed) {
        this.speed = speed;
    }
}

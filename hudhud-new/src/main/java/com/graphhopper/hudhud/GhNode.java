package com.graphhopper.hudhud;

public class GhNode {
    int nodeId;
    double lat;
    double lon;

    public GhNode(int nodeId, double lat, double lon) {
        this.nodeId = nodeId;
        this.lat = lat;
        this.lon = lon;
    }
}

package com.graphhopper.hudhud;

import java.util.Map;

import org.locationtech.jts.geom.LineString;

import com.graphhopper.search.KVStorage;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.FetchMode;

public class GhEdge {
    int edgeId;
    Long osmWayId;
    String highway;
    String junction;
    String construction;
    String oneway;
    LineString lineString;
    double distance;
    int srcNode;
    int targetNode;
    int fow;
    int frc;

    public GhEdge(EdgeIteratorState edge) {
        this.edgeId = edge.getEdge();
        this.lineString = edge.fetchWayGeometry(FetchMode.ALL).toLineString(false);
        this.distance = edge.getDistance();
        Map<String, KVStorage.KValue> map = edge.getKeyValues(); 
        this.osmWayId = map.containsKey("osm_way_id") ? Long.parseLong(map.get("osm_way_id").toString()) : 0;
        this.highway = map.containsKey("highway") ? map.get("highway").toString() : "";
        this.junction = map.containsKey("junction") ? map.get("junction").toString() : "";
        this.construction = map.containsKey("construction") ? map.get("construction").toString() : "";
        this.oneway = map.containsKey("oneway") ? map.get("oneway").toString() : "";
        this.srcNode = edge.getBaseNode();
        this.targetNode = edge.getAdjNode();
    }

    public void setFow(int fow) {
        this.fow = fow;
    }

    public void setFrc(int frc) {
        this.frc = frc;
    }

    public Long getOsmWayId() {
        return this.osmWayId;
    }
}

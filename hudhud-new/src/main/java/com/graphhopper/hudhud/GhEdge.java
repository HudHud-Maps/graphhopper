// package com.graphhopper.hudhud;

// import java.util.Map;

// import org.locationtech.jts.geom.LineString;

// import com.graphhopper.search.KVStorage;
// import com.graphhopper.storage.NodeAccess;
// import com.graphhopper.util.EdgeIteratorState;
// import com.graphhopper.util.FetchMode;

// public class GhEdge {
//     int edgeId;
//     long osmWayId;
//     String oneway;
//     LineString lineString;
//     double distance;
//     int srcNode;
//     int targetNode;
//     int fow;
//     int frc;
//     double srcNodeLat;
//     double srcNodeLon;
//     double targetNodeLat;
//     double targetNodeLon;

//     public GhEdge(EdgeIteratorState edge, NodeAccess na) {
//         this.edgeId = edge.getEdge();
//         this.lineString = edge.fetchWayGeometry(FetchMode.ALL).toLineString(false);
//         this.distance = edge.getDistance();
//         Map<String, KVStorage.KValue> map = edge.getKeyValues(); 
//         this.osmWayId = map.containsKey("osm_way_id") ? Long.parseLong(map.get("osm_way_id").toString()) : 0;
//         this.srcNode = edge.getBaseNode();
//         this.targetNode = edge.getAdjNode();
//         this.srcNodeLat = na.getLat(this.srcNode);
//         this.srcNodeLon = na.getLon(this.srcNode);
//         this.targetNodeLat = na.getLat(this.targetNode);
//         this.targetNodeLon = na.getLon(this.targetNode);
//     }

//     public void setFow(int fow) {
//         this.fow = fow;
//     }

//     public void setFrc(int frc) {
//         this.frc = frc;
//     }

//     public long getOsmWayId() {
//         return this.osmWayId;
//     }


//     public String getOneway() {
//         return this.oneway;
//     }

//     public int getEdgeId() {
//         return this.edgeId;
//     }

//     public int getSource() {
//         return this.srcNode;
//     }

//     public int getTarget() {
//         return this.targetNode;
//     }

//     public double getLengthM() {
//         return this.distance;
//     }

//     public String getWktGeometry() {
//         return this.lineString.toString();
//     }

//     public String getReverseWktGeometry() {
//         return this.lineString.reverse().toString();
//     }

//     public int getFrc() {
//         return this.frc;
//     }

//     public int getFow() {
//         return this.fow;
//     }

//     public 
// }

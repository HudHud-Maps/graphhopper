package com.graphhopper.hudhud;

import java.util.HashMap;
import java.util.Map;

import com.graphhopper.GraphHopper;
import com.graphhopper.config.Profile;
import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;

public class GhExporter {
    private  final Map<Integer, GhEdge> ghEdgeMap = new HashMap<>();
    private  final Map<Integer, GhNode> ghNodeMap = new HashMap<>();

    public void loadGraph(Map<Long, HudhudWay> wayMap) {
    // Initialize GraphHopper
        GraphHopper hopper = new GraphHopper();
        // Configure and load the graph (you'll need to set this up properly)
        
        hopper.setEncodedValuesString("car_access, car_average_speed");
        hopper.setProfiles(new Profile("car").setCustomModel(GHUtility.loadCustomModelFromJar("car.json")));
        hopper.setOSMFile("saudi_arabia-latest.osm.pbf");
        hopper.setGraphHopperLocation("graph-cache");
        hopper.importOrLoad();

        // Get the base graph
        BaseGraph graph = hopper.getBaseGraph();
        AllEdgesIterator allEdgeIterator = graph.getAllEdges();
        while (allEdgeIterator.next()) {
            EdgeIteratorState edgeState = graph.getEdgeIteratorState(allEdgeIterator.getEdge(), Integer.MIN_VALUE);
            int nodeA = edgeState.getBaseNode();
            int nodeB = edgeState.getAdjNode();
            NodeAccess na = graph.getNodeAccess();
            GhEdge ghEdge = new GhEdge(edgeState);
            ghEdgeMap.put(allEdgeIterator.getEdge(), ghEdge);
            ghNodeMap.put(nodeA, new GhNode(nodeA, na.getLat(nodeA), na.getLon(nodeA)));
            ghNodeMap.put(nodeB, new GhNode(nodeB, na.getLat(nodeB), na.getLon(nodeB)));
            System.out.println(ghEdge.getOsmWayId());
            System.out.println(wayMap.get(ghEdge.getOsmWayId()));
            ghEdge.setFow(wayMap.get(ghEdge.getOsmWayId()).getFow());
            ghEdge.setFrc(wayMap.get(ghEdge.getOsmWayId()).getFrc());
        }
        System.out.println("Graph loaded successfully " + ghEdgeMap.size() + " edges and " + ghNodeMap.size() + " nodes");
    }
}

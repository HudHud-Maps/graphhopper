package com.graphhopper.hudhud;


import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;
import java.util.Set;
import java.util.HashSet;

import org.locationtech.jts.algorithm.Angle;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.PrecisionModel;
import org.openstreetmap.osmosis.core.container.v0_6.EntityContainer;
import org.openstreetmap.osmosis.core.domain.v0_6.Entity;
import org.openstreetmap.osmosis.core.domain.v0_6.Node;
import org.openstreetmap.osmosis.core.domain.v0_6.Way;
import org.openstreetmap.osmosis.core.domain.v0_6.WayNode;
import org.openstreetmap.osmosis.core.task.v0_6.RunnableSource;
import org.openstreetmap.osmosis.core.task.v0_6.Sink;
import org.openstreetmap.osmosis.pbf2.v0_6.PbfReader;

import net.sf.geographiclib.Geodesic;
import net.sf.geographiclib.GeodesicData;

import java.io.File;

public class Annotator {
    private String osmFile;
    private Map<Long, HudhudWay> wayMap = new HashMap<>();
    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);
    private final Map<Long, Node> nodeMap = new HashMap<>();
    public Annotator(String osmFile) {
        this.osmFile = osmFile;
    }

    public void annotate() {
       File pbfFile = new File(osmFile);
        RunnableSource reader = new PbfReader(pbfFile, 4);
        reader.setSink(new Sink() {
            @Override
            public void process(EntityContainer entityContainer) {
                Entity entity = entityContainer.getEntity();
                if (entity instanceof Way) {
                    Way way = (Way) entity;
                    LineString ls = getLineStringFromWay(way);
                    String highwayTag = way.getTags().stream()
                        .filter(tag -> tag.getKey().equals("highway"))
                        .map(tag -> tag.getValue())
                        .findFirst()
                        .orElse("");
                    if (highwayTag.equals("footway") || highwayTag.equals("cycleway") || highwayTag.equals("path") || highwayTag.equals("pedestrian") || highwayTag.equals("steps") || highwayTag.equals("")) {
                        return;
                    }
                    String onewayTag = way.getTags().stream()
                        .filter(tag -> tag.getKey().equals("oneway"))
                        .map(tag -> tag.getValue())
                        .findFirst()
                        .orElse("");
                    String constructionTag = way.getTags().stream()
                        .filter(tag -> tag.getKey().equals("construction"))
                        .map(tag -> tag.getValue())
                        .findFirst()
                        .orElse("");
                    String junctionTag = way.getTags().stream() 
                        .filter(tag -> tag.getKey().equals("junction"))
                        .map(tag -> tag.getValue())
                        .findFirst()
                        .orElse("");
                    int frc = inferFrc(highwayTag, constructionTag);
                    int fow = inferFow(highwayTag, constructionTag, junctionTag);
                    if (ls != null) {
                        Coordinate centroid = ls.getCentroid().getCoordinate();
                        wayMap.put(way.getId(), new HudhudWay(way.getId(), ls, highwayTag, centroid, fow ,frc, onewayTag));
                    }
                }
                else if (entity instanceof Node) {
                    Node node = (Node) entity;
                    nodeMap.put(node.getId(), node);
                }
            }

            @Override
            public void complete() {
                System.out.println("Reader complete");
            }

            @Override
            public void initialize(Map<String, Object> map) {
                System.out.println("Reader initialized");
            }

            @Override
            public void close() {
                System.out.println("Reader closed");
            }
        });
        Thread thread = new Thread(reader);
        thread.start();
        try {
            thread.join();           
            List<HudhudWay> ways = new ArrayList<>();
            for (Map.Entry<Long, HudhudWay> entry : wayMap.entrySet()) {
                if (entry.getValue().getFow() == 3 && (entry.getValue().getOneway().equals("yes") || entry.getValue().getOneway().equals("-1"))) {
                    ways.add(entry.getValue());
                }
            }
            Map<String, List<HudhudWay>> waysByHighwayTag = ways.stream()
                .collect(Collectors.groupingBy(HudhudWay::getHighwayTag));
            for (Map.Entry<String, List<HudhudWay>> entry : waysByHighwayTag.entrySet()) {
                multiCarriageways(entry.getValue());
            }
            Map<Integer, List<HudhudWay>> waysByfow = wayMap.values().stream()
                .collect(Collectors.groupingBy(HudhudWay::getFrc));
            for (Map.Entry<Integer, List<HudhudWay>> entry : waysByfow.entrySet()) {
                System.out.println(entry.getKey() + ": " + entry.getValue().size());
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private LineString getLineStringFromWay(Way way) {
        List<WayNode> wayNodes = way.getWayNodes();
        List<Coordinate> coordinates = new ArrayList<>();

        for (WayNode wayNode : wayNodes) {
            Node node = nodeMap.get(wayNode.getNodeId());
            if (node != null) {
                double longitude = Math.round(node.getLongitude() * 1e7) / 1e7;
                double latitude = Math.round(node.getLatitude() * 1e7) / 1e7;
                coordinates.add(new Coordinate(longitude, latitude));
            }
        }

        if (coordinates.size() >= 2) {
            return geometryFactory.createLineString(coordinates.toArray(new Coordinate[0]));
        } else {
            return null;
        }
    }
    private int inferFrc(String highway, String construction){
        String type = highway;
        if (type.equals("construction")) {
            type = construction;
        }
        if (type.startsWith("motorway")) {
            return 0;
        }
        else if (type.startsWith("trunk")) {
            return 1;
        }
        else if (type.startsWith("primary")) {
            return 2;
        }
            else if (type.startsWith("secondary")) {
            return 3;
        }
            else if (type.startsWith("tertiary")) {
            return 4;
        }
        else if (type.startsWith("unclassified")) {
            return 6;
        }
        return 7;
    }
    private int inferFow(String highway, String construction, String junction){
        if (junction.equals("roundabout") || 
        junction.equals("circular") || 
        junction.equals("circle") || 
        junction.equals("ro") || 
        junction.equals("r")) {
            return 4;
        } else if (highway.equals("motorway") || construction.equals("motorway")) {
            return 1;
        }
        else if (highway.endsWith("_link") || construction.endsWith("_link")) {
            return 6;
        }
        String type = highway;
        if (type.equals(construction)) {
            type = construction;
        }
        else if (type.equals("residential") || type.equals("unclassified") || type.equals("tertiary") || type.equals("secondary") || type.equals("primary") || type.equals("trunk")) {
            return 3;
        } else if (type.equals("road")) {
            return 0;
        }
        return 7;
    }

    private  void multiCarriageways(List<HudhudWay> ways) {
        List<Map.Entry<Long, Double>> sortedEntries = sortByAngle(ways, "43.989207,23.986220");
        int i = 0;
        while (i < sortedEntries.size() - 1) {
            int j = i;
            List<Long> candidates = new ArrayList<>();
            double angleDiff = 0;
            while (true) {
                angleDiff = angleDiff + sortedEntries.get(j + 1).getValue() - sortedEntries.get(j).getValue();
                if (angleDiff <= 1) {
                    if (areParallel(wayMap.get(sortedEntries.get(i).getKey()), wayMap.get(sortedEntries.get(j + 1).getKey()))) {
                        double dist = calculateDistance(wayMap.get(sortedEntries.get(i).getKey()).getCentroid().y, wayMap.get(sortedEntries.get(i).getKey()).getCentroid().x, wayMap.get(sortedEntries.get(j + 1).getKey()).getCentroid().y, wayMap.get(sortedEntries.get(j + 1).getKey()).getCentroid().x);
                        if (dist < 300) {
                            candidates.add(sortedEntries.get(j + 1).getKey());
                        }
                    }
                } else {
                    break;
                }
                j++;
                if (j >= sortedEntries.size() - 2) {
                    break;
                }
            }
            if (candidates.size() > 0) {
                for (Long candidate : candidates) {
                    wayMap.get(candidate).setFow(2);
                }
                wayMap.get(sortedEntries.get(i).getKey()).setFow(2);
            }
            i++;
        }
    }

    private List<Map.Entry<Long, Double>> sortByAngle(List<HudhudWay> ways, String point) {
        Map<Long, Double> angles = new HashMap<>();
        String[] sp = point.split(",");
        double lat = Double.parseDouble(sp[1]);
        double lon = Double.parseDouble(sp[0]);
        for (HudhudWay way : ways) {
            angles.put(way.getId(), (Math.toDegrees(Angle.angle(new Coordinate(lon, lat), new Coordinate(way.getCentroid().x, way.getCentroid().y)) + 360) % 360));
        }
        List<Map.Entry<Long, Double>> sortedEntries = new ArrayList<>(angles.entrySet());
        sortedEntries.sort(Map.Entry.comparingByValue());
        return sortedEntries;
    }

    private boolean areParallel(HudhudWay way1, HudhudWay way2) {
        double angle1 = lineAngle(way1.getLineString());
        double angle2 = lineAngle(way2.getLineString());
        return Math.abs(angle1 - angle2) <= 200 && Math.abs(angle1 - angle2) >= 160;
    }

    private double lineAngle(LineString ls) {
        double azimuth1 = Angle.angle(ls.getStartPoint().getCoordinate(), ls.getEndPoint().getCoordinate());
        azimuth1 = Math.toDegrees(azimuth1);
        azimuth1 = (azimuth1 + 360) % 360;
        return azimuth1;
    }

    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        GeodesicData gd = Geodesic.WGS84.Inverse(lat1, lon1, lat2, lon2);
        return gd.s12;
    }

    public Map<Long, HudhudWay> getWayMap() {
        return wayMap;
    }
}

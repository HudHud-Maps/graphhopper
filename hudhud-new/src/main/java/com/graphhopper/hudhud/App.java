package com.graphhopper.hudhud;


import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.locationtech.jts.algorithm.Angle;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
// import org.locationtech.jts.geom.Point;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import org.locationtech.proj4j.CoordinateReferenceSystem;
import org.locationtech.proj4j.CoordinateTransform;
import org.locationtech.proj4j.CoordinateTransformFactory;
import org.locationtech.proj4j.CRSFactory;
import org.locationtech.proj4j.ProjCoordinate;
import org.openstreetmap.osmosis.core.container.v0_6.EntityContainer;
import org.openstreetmap.osmosis.core.domain.v0_6.Entity;
import org.openstreetmap.osmosis.core.domain.v0_6.Node;
import org.openstreetmap.osmosis.core.domain.v0_6.Way;
import org.openstreetmap.osmosis.core.domain.v0_6.WayNode;
import org.openstreetmap.osmosis.core.task.v0_6.RunnableSource;
import org.openstreetmap.osmosis.core.task.v0_6.Sink;
import org.openstreetmap.osmosis.pbf2.v0_6.PbfReader;

import org.locationtech.spatial4j.context.SpatialContext;
import org.locationtech.spatial4j.distance.DistanceCalculator;
import org.locationtech.spatial4j.distance.GeodesicSphereDistCalc;
import org.locationtech.spatial4j.distance.DistanceUtils;

import com.graphhopper.GraphHopper;
import com.graphhopper.config.Profile;
import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.search.KVStorage;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.FetchMode;
import com.graphhopper.util.GHUtility;
import org.locationtech.spatial4j.shape.Point ;

import net.sf.geographiclib.Geodesic;
import net.sf.geographiclib.GeodesicData;

import com.graphhopper.hudhud.HudhudWay;
import com.graphhopper.hudhud.Annotator;
public class App 
{
    private static final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);
    private static final WKTReader wktReader = new WKTReader(geometryFactory);
    private static final Map<Long, Node> nodeMap = new HashMap<>();
    private static final SpatialContext ctx = SpatialContext.GEO;
    private static final DistanceCalculator calculator = new GeodesicSphereDistCalc.Vincenty();
    public static void main( String[] args )
    {
    }
    public static void export() {
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
        String[] toExport = new String[graph.getEdges()]; 
        AllEdgesIterator allEdgeIterator = graph.getAllEdges();
        // // graph.getNodeAccess()
        try (FileWriter writer = new FileWriter("edge_data.csv")) {
            // Write CSV header
        writer.write("edgeid,srcnode,targetnode,srcnodelat,srcnodelon,targetnodelat,targetnodelon,distance,linestring,reverse_linestring,osmwayid\n");
        int i = 0;
         while (allEdgeIterator.next()) {
            EdgeIteratorState edgeState = graph.getEdgeIteratorState(allEdgeIterator.getEdge(), Integer.MIN_VALUE);
            Map<String, KVStorage.KValue> map = edgeState.getKeyValues();        
            LineString ls = edgeState.fetchWayGeometry(FetchMode.ALL).toLineString(false);
            int nodeA = edgeState.getBaseNode();
            int nodeB = edgeState.getAdjNode();
            NodeAccess na = graph.getNodeAccess();
            double latA = na.getLat(nodeA);
            double lonA = na.getLon(nodeA);
            double latB = na.getLat(nodeB);
            double lonB = na.getLon(nodeB);
            long edgeId = allEdgeIterator.getEdge();
            double distance = edgeState.getDistance();
            String lineString = ls.toString();
            String reverseLineString = ls.reverse().toString();
            String osmWayId = map.containsKey("osm_way_id") ? map.get("osm_way_id").toString() : "";
            String highway = map.containsKey("highway") ? map.get("highway").toString() : "";
            String junction = map.containsKey("junction") ? map.get("junction").toString() : "";
            String construction = map.containsKey("construction") ? map.get("construction").toString() : "";
            String oneway = map.containsKey("oneway") ? map.get("oneway").toString() : "";
            toExport[i] = String.format("%d\t%d\t%d\t%.7f\t%.7f\t%.7f\t%.7f\t%.2f\t\"%s\"\t\"%s\"\t%s\t\"%s\"\t\"%s\"\t\"%s\"\t%s\t%d\t%d",
                edgeId, nodeA, nodeB, latA, lonA, latB, lonB, distance, lineString, reverseLineString, osmWayId, highway, junction, construction, oneway, getFrc(highway, construction), getFow(highway, construction, junction));
            i++;
            System.out.println(String.format("Wrote: %d , remaining: %d", i, graph.getEdges() - i));
        }
        String csvContent = String.join("\n", toExport);
        writer.write(csvContent);
    } catch (IOException e) {
        System.err.println("Error writing to CSV file: " + e.getMessage());
    }
    }    
    public static void loadOSM() {
        File pbfFile = new File("saudi_arabia-latest.osm.pbf");

        RunnableSource reader = new PbfReader(pbfFile, 4);
        reader.setSink(new Sink() {
            @Override
            public void process(EntityContainer entityContainer) {
                Entity entity = entityContainer.getEntity();
                if (entity instanceof Way) {
                    Way way = (Way) entity;
                    try (FileWriter writer = new FileWriter("ways.csv", true)) { 
                        LineString ls = getLineStringFromWay(way);
                        String highwayTag = way.getTags().stream()
                            .filter(tag -> tag.getKey().equals("highway"))
                            .map(tag -> tag.getValue())
                            .findFirst()
                            .orElse("");
                        if (ls != null) {
                            Coordinate centroid = ls.getCentroid().getCoordinate();
                            writer.write(way.getId() + "\t" + ls.toString() + "\t" + highwayTag + "\t" + centroid.toString() + "\n");
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
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
    }

    private static LineString getLineStringFromWay(Way way) {
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
            System.out.println("Way " + way.getId() + " has fewer than 2 valid nodes");
            return null;
        }
    }
    public static double lineAngle(LineString ls) {
        double azimuth1 = Angle.angle(ls.getStartPoint().getCoordinate(), ls.getEndPoint().getCoordinate());
        azimuth1 = Math.toDegrees(azimuth1);
        azimuth1 = (azimuth1 + 360) % 360;
        return azimuth1;
    }
    public static void getAngle() {
        String ls1 = "LINESTRING (43.1546026 18.0804166, 43.1568718 18.0808296, 43.1601441 18.0813753, 43.1610668 18.0815742, 43.1625795 18.0820688, 43.1636471 18.0827318, 43.164838 18.0837211, 43.1656533 18.0846236, 43.166281 18.085669, 43.1680298 18.0884023, 43.1685233 18.089157, 43.1693172 18.0899168, 43.1701058 18.0904879, 43.1711787 18.0910386, 43.1725466 18.0914159, 43.1738716 18.0917219, 43.1747782 18.0918494, 43.1756087 18.091912, 43.1761301 18.0919514, 43.1770956 18.091829, 43.1783026 18.0914618, 43.1790858 18.0910284, 43.1796296 18.0907006, 43.1810653 18.0898352, 43.1853998 18.0872549, 43.191542 18.0835885, 43.1955707 18.0812274, 43.1965363 18.080539, 43.1977057 18.0798658, 43.1989288 18.0791009, 43.1997914 18.0786337, 43.2015842 18.0776628, 43.2025123 18.0772752, 43.2035261 18.0769489, 43.2045239 18.076648, 43.205983 18.076546, 43.2076406 18.0765919, 43.2108378 18.07675, 43.2128388 18.0768061, 43.2146144 18.0769387, 43.2168138 18.0771529, 43.2180583 18.0771631, 43.2199681 18.0769234, 43.221041 18.0766837)";
        String ls2 = "LINESTRING (43.2212448 18.0768163, 43.2200646 18.0771223, 43.2186484 18.0772651, 43.2170713 18.0772753, 43.2158589 18.0772141, 43.2129514 18.0769999, 43.211664 18.0768979, 43.2094324 18.0768163, 43.2066429 18.0766837, 43.204937 18.0767755, 43.2037032 18.0770509, 43.2023299 18.0775098, 43.2017398 18.0777342, 43.2007527 18.078285, 43.1969762 18.0804778, 43.1953561 18.0815181, 43.1892836 18.0851285, 43.1805289 18.0903604, 43.1795899 18.0908988, 43.1785011 18.091523, 43.1773531 18.0919208, 43.1762373 18.0920737, 43.1752932 18.0920329, 43.1741774 18.0919106, 43.1721496 18.0915128, 43.1705511 18.0909519, 43.1693172 18.0901054, 43.1683731 18.089259, 43.1674182 18.0878923, 43.1666694 18.086687, 43.1664741 18.0863727, 43.1663718 18.0862088, 43.1653154 18.0845166, 43.1644356 18.0835477, 43.1633305 18.0827114, 43.1626117 18.0823034, 43.161174 18.0817527, 43.1582987 18.0812631, 43.155005 18.080692, 43.1546026 18.0804166)";
        WKTReader reader = new WKTReader();
        try {
            LineString lineString1 = (LineString) reader.read(ls1);
            LineString lineString2 = (LineString) reader.read(ls2);
            double azimuth1 = Angle.angle(lineString1.getStartPoint().getCoordinate(), lineString1.getEndPoint().getCoordinate());
            azimuth1 = Math.toDegrees(azimuth1);
            azimuth1 = (azimuth1 + 360) % 360;
            System.out.println("Azimuth lineString1: " + azimuth1);
            double azimuth2 = Angle.angle(lineString2.getStartPoint().getCoordinate(), lineString2.getEndPoint().getCoordinate());
            azimuth2 = Math.toDegrees(azimuth2);
            azimuth2 = (azimuth2 + 360) % 360;
            System.out.println("Azimuth lineString2: " + azimuth2 + " degrees");
            System.out.println("Difference: " + Math.abs((azimuth2 - azimuth1)) + " degrees");
            
        } catch (ParseException e) {
            e.printStackTrace();
        }  
    }
    public static Set<String> getUniqueWays() {
        Set<String> uniqueWays = new HashSet<>();
        try (BufferedReader reader = new BufferedReader(new FileReader("unique_highways.csv"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                uniqueWays.add(line.strip());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return uniqueWays;
    }
    public static void generateUniqueHighwaysInfo() {
        try (BufferedReader reader = new BufferedReader(new FileReader("ways.csv"))) {
            String line;
            Set<String> uniqueWays = getUniqueWays();
            List<String> uniqueWaysList = new ArrayList<>(uniqueWays);
            int i = 1;
            while ((line = reader.readLine()) != null) {
                int index = line.indexOf("\t");
                String highway = line.substring(0, index);
                if (uniqueWaysList.contains(highway)) {
                    uniqueWaysList.add(line.trim().strip());
                }
                System.out.println("Wrote: " + i);
                i++;
            }
            try (FileWriter writer = new FileWriter("unique_highways_info.csv", true)) {
                writer.write(String.join("\n", uniqueWaysList));
            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static Map<String, List<HudhudWay>> annotateMultiCarriageways() {
        try (BufferedReader reader = new BufferedReader(new FileReader("unique_highways_all.csv"))) {
            String line;
            List<HudhudWay> ways = new ArrayList<>();
            while ((line = reader.readLine()) != null) {
                ways.add(new HudhudWay(wktReader, line));
            }
            Map<String, List<HudhudWay>> waysByHighwayTag = ways.stream()
                .collect(Collectors.groupingBy(HudhudWay::getHighwayTag));
            // Print the grouped ways for verification
            for (Map.Entry<String, List<HudhudWay>> entry : waysByHighwayTag.entrySet()) {
                System.out.println("Highway Tag: " + entry.getKey() + " Number of ways: " + entry.getValue().size());
            }
            return waysByHighwayTag;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static void DistanceOrientationFromPoint(List<HudhudWay> ways, String point) {
        CRSFactory crsFactory = new CRSFactory();
        CoordinateReferenceSystem src = crsFactory.createFromName("EPSG:4326");
        CoordinateReferenceSystem dst = crsFactory.createFromName("EPSG:3857");

        CoordinateTransformFactory ctFactory = new CoordinateTransformFactory();
        CoordinateTransform transform = ctFactory.createTransform(src, dst);
        String[] sp = point.split(",");
        double lat = Double.parseDouble(sp[1]);
        double lon = Double.parseDouble(sp[0]);
        ProjCoordinate pointCoord = new ProjCoordinate(lon, lat);
        ProjCoordinate dstCoordP = new ProjCoordinate();
        transform.transform(pointCoord, dstCoordP);
        // Point p3857 = geometryFactory.createPoint(new Coordinate(dstCoordP.x, dstCoordP.y));
        Map<Long, Double> distances = new HashMap<>();
        Map<Long, Double> angles = new HashMap<>();
        // transform onmly the centroid
        Map<Long, HudhudWay> waysMap = new HashMap<>();
        for (HudhudWay way : ways) {
                // ProjCoordinate srcCoord = new ProjCoordinate(way.getCentroid().x, way.getCentroid().y);
                // ProjCoordinate dstCoord = new ProjCoordinate();
                // transform.transform(srcCoord, dstCoord);
                // Point np3857 = geometryFactory.createPoint(new Coordinate(dstCoord.x, dstCoord.y));
                // distances.put(way.getId(), p3857.distance(np3857));
                angles.put(way.getId(), (Math.toDegrees(Angle.angle(new Coordinate(lon, lat), new Coordinate(way.getCentroid().x, way.getCentroid().y)) + 360) % 360));
                distances.put(way.getId(), calculateDistance(lat, lon, way.getCentroid().y, way.getCentroid().x));
            waysMap.put(way.getId(), way);
            }
            List<Map.Entry<Long, Double>> sortedEntries = new ArrayList<>(angles.entrySet());
            sortedEntries.sort(Map.Entry.comparingByValue());
            Entry<Long, Double> lastEntry = sortedEntries.get(sortedEntries.size() - 1);
            double lastAngle = lastEntry.getValue();
            double lastDistance = distances.get(lastEntry.getKey());
            Long lastWayId = lastEntry.getKey();
            try (FileWriter writer = new FileWriter("distances_angles.csv", true)) {
                writer.write("way_id,angle,distance\n");
                for (Map.Entry<Long, Double> entry : sortedEntries) {
                    Long wayId = entry.getKey();
                    Double distance = distances.get(wayId);
                    Double angle = entry.getValue();
                    double angPrev = (angle - lastAngle + 360) % 360;
                    double distPrev = Math.abs(distance - lastDistance);
                    double angDiff = direction(waysMap.get(wayId), waysMap.get(lastWayId));
                    writer.write(wayId + "," + angle + "," + distance + "," + angPrev + "," + distPrev + "," + angDiff + "\n");
                    lastAngle = angle;
                    lastDistance = distance;
                    lastWayId = wayId;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
    }

    public static List<Map.Entry<Long, Double>> sortByAngle(List<HudhudWay> ways, String point) {
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

    public static double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        GeodesicData gd = Geodesic.WGS84.Inverse(lat1, lon1, lat2, lon2);
        return gd.s12;
    }

    public static boolean areParallel(HudhudWay way1, HudhudWay way2) {
        double angle1 = lineAngle(way1.getLineString());
        double angle2 = lineAngle(way2.getLineString());
        return Math.abs(angle1 - angle2) <= 200 && Math.abs(angle1 - angle2) >= 160;
    }
    public static double direction(HudhudWay way1, HudhudWay way2) {
        double angle1 = lineAngle(way1.getLineString());
        double angle2 = lineAngle(way2.getLineString());
        return Math.abs(angle1 - angle2);
    }

    public static Set<Long> multiCarriageways(List<HudhudWay> ways) {
        Map<Long, HudhudWay> waysMap = new HashMap<>();
        for (HudhudWay w : ways) {
            waysMap.put(w.getId(), w);
        }
        Set<Long> result = new HashSet<>();
        List<Map.Entry<Long, Double>> sortedEntries = sortByAngle(ways, "43.989207,23.986220");
        int i = 0;
        while (i < sortedEntries.size() - 1) {
            int j = i;
            List<Long> candidates = new ArrayList<>();
            double angleDiff = 0;
            while (true) {
                angleDiff = angleDiff + sortedEntries.get(j + 1).getValue() - sortedEntries.get(j).getValue();
                if (angleDiff <= 1) {
                    if (areParallel(waysMap.get(sortedEntries.get(i).getKey()), waysMap.get(sortedEntries.get(j + 1).getKey()))) {
                        double dist = calculateDistance(waysMap.get(sortedEntries.get(i).getKey()).getCentroid().y, waysMap.get(sortedEntries.get(i).getKey()).getCentroid().x, waysMap.get(sortedEntries.get(j + 1).getKey()).getCentroid().y, waysMap.get(sortedEntries.get(j + 1).getKey()).getCentroid().x);
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
                result.add(sortedEntries.get(i).getKey());
                result.addAll(candidates);
            }
            i++;
        }
        return result;
    }

    // public static 
}

// 132578541
// 1080998621
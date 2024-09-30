package com.graphhopper.hudhud;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.search.KVStorage;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.FetchMode;

public class App 
{
    public static void main( String[] args ) {
        GhExporter ghExporter = new GhExporter();
        Annotator annotator = new Annotator("saudi_arabia-latest.osm.pbf");
        annotator.annotate();

        ghExporter.loadGraph();

        createDatabase();
        fillDatabase(ghExporter.getGraph(), annotator.getAnnotatedWays());
    }

    public static void createDatabase() {
        String url = "jdbc:postgresql://localhost:5435/postgres";
        String user = "postgres";
        String password = "postgres";

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            System.out.println("Connection to database successful");
            if (conn != null) {
                String createEdgeTableSql = """
                CREATE TABLE IF NOT EXISTS gh_edges (
                    id INT PRIMARY KEY,
                    source INT NOT NULL,
                    target INT NOT NULL,
                    frc SMALLINT NOT NULL,
                    fow SMALLINT NOT NULL,
                    length_m FLOAT NOT NULL,
                    the_geom geometry(LineString, 4326) NOT NULL,
                    geom_3857 geometry(LineString, 3857) NOT NULL,
                    osm_way_id BIGINT NOT NULL
                )""";
                String createNodeTableSql = """
                CREATE TABLE IF NOT EXISTS gh_nodes (
                    id INT PRIMARY KEY,
                    geom_3857 geometry(Point, 3857) NOT NULL,
                    the_geom geometry(Point, 4326) NOT NULL
                )""";
                Statement stmt = conn.createStatement();
                stmt.execute("CREATE EXTENSION IF NOT EXISTS postgis;");
                stmt.execute(createEdgeTableSql);
                stmt.execute(createNodeTableSql);
                System.out.println("Tables created successfully");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    public static void fillDatabase(BaseGraph graph, Map<Long, Integer> annotatedWay) {
        String url = "jdbc:postgresql://localhost:5435/postgres";
        String user = "postgres";
        String password = "postgres";

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            conn.setAutoCommit(false);

            String insertEdgeSql = "INSERT INTO gh_edges (id, source, target, frc, fow, length_m, the_geom, geom_3857, osm_way_id) VALUES (?, ?, ?, ?, ?, ?, ST_SetSRID(ST_GeomFromText(?), 4326), ST_Transform(ST_SetSRID(ST_GeomFromText(?), 4326), 3857), ? )";
            String insertNodeSql = "INSERT INTO gh_nodes (id, the_geom, geom_3857) VALUES (?, ST_SetSRID(ST_Point(?, ?), 4326), ST_Transform(ST_SetSRID(ST_Point(?, ?), 4326), 3857)) ON CONFLICT (id) DO NOTHING";

            try (PreparedStatement edgeStmt = conn.prepareStatement(insertEdgeSql);
                PreparedStatement nodeStmt = conn.prepareStatement(insertNodeSql)) {
                AllEdgesIterator allEdgesIterator = graph.getAllEdges();
                NodeAccess na = graph.getNodeAccess();
                System.out.println("Filling database");
                while (allEdgesIterator.next()) {
                    System.out.println("Processing edge " + allEdgesIterator.getEdge());
                    EdgeIteratorState edge = graph.getEdgeIteratorState(allEdgesIterator.getEdge(), Integer.MIN_VALUE);
                    Map<String, KVStorage.KValue> map = edge.getKeyValues(); 
                    long osmWayId = map.containsKey("osm_way_id") ? Long.parseLong(map.get("osm_way_id").toString()) : 0;
                    if (!annotatedWay.containsKey(osmWayId)) {
                        continue;
                    }
                    int srcNode = allEdgesIterator.getBaseNode();
                    int targetNode = allEdgesIterator.getAdjNode();
                    double srcLat = na.getLat(srcNode);
                    double srcLon = na.getLon(srcNode);
                    double targetLat = na.getLat(targetNode);
                    double targetLon = na.getLon(targetNode);
                    nodeStmt.setInt(1, srcNode);
                    nodeStmt.setDouble(2, srcLon);
                    nodeStmt.setDouble(3, srcLat);
                    nodeStmt.setDouble(4, srcLon);
                    nodeStmt.setDouble(5, srcLat);
                    nodeStmt.addBatch();
                    nodeStmt.setInt(1, targetNode);
                    nodeStmt.setDouble(2, targetLon);
                    nodeStmt.setDouble(3, targetLat);
                    nodeStmt.setDouble(4, targetLon);
                    nodeStmt.setDouble(5, targetLat);
                    nodeStmt.addBatch();
                    int frc = Annotator.getFrc(annotatedWay.get(osmWayId));
                    int fow = Annotator.getFow(annotatedWay.get(osmWayId));
                    String oneway = Annotator.getOneway(annotatedWay.get(osmWayId));
                    if (oneway.equals("yes")) {
                        edgeStmt.setInt(1, edge.getEdge() + 1);
                        edgeStmt.setInt(2, srcNode);
                        edgeStmt.setInt(3, targetNode);
                        edgeStmt.setInt(4, frc);
                        edgeStmt.setInt(5, fow);
                        edgeStmt.setDouble(6, edge.getDistance());
                        edgeStmt.setString(7, edge.fetchWayGeometry(FetchMode.ALL).toLineString(false).toString());
                        edgeStmt.setString(8, edge.fetchWayGeometry(FetchMode.ALL).toLineString(false).toString());
                        edgeStmt.setLong(9, osmWayId);
                        edgeStmt.addBatch();
                    } else if (oneway.equals("-1")) {
                        edgeStmt.setInt(1, edge.getEdge() + 1);
                        edgeStmt.setInt(2, targetNode);
                        edgeStmt.setInt(3, srcNode);
                        edgeStmt.setInt(4, frc);
                        edgeStmt.setInt(5, fow);
                        edgeStmt.setDouble(6, edge.getDistance());
                        edgeStmt.setString(7, edge.fetchWayGeometry(FetchMode.ALL).toLineString(false).reverse().toString());
                        edgeStmt.setString(8, edge.fetchWayGeometry(FetchMode.ALL).toLineString(false).reverse().toString());
                        edgeStmt.setLong(9, osmWayId);
                        edgeStmt.addBatch();
                    } else {
                        edgeStmt.setInt(1, edge.getEdge() + 1);
                        edgeStmt.setInt(2, srcNode);
                        edgeStmt.setInt(3, targetNode);
                        edgeStmt.setInt(4, frc);
                        edgeStmt.setInt(5, fow);
                        edgeStmt.setDouble(6, edge.getDistance());
                        edgeStmt.setString(7, edge.fetchWayGeometry(FetchMode.ALL).toLineString(false).toString());
                        edgeStmt.setString(8, edge.fetchWayGeometry(FetchMode.ALL).toLineString(false).toString());
                        edgeStmt.setLong(9, osmWayId);
                        edgeStmt.addBatch();
                        edgeStmt.setInt(1, -1 * (edge.getEdge() + 1));
                        edgeStmt.setInt(2, targetNode);
                        edgeStmt.setInt(3, srcNode);
                        edgeStmt.setInt(4, frc);
                        edgeStmt.setInt(5, fow);
                        edgeStmt.setDouble(6, edge.getDistance());
                        edgeStmt.setString(7, edge.fetchWayGeometry(FetchMode.ALL).toLineString(false).reverse().toString());
                        edgeStmt.setString(8, edge.fetchWayGeometry(FetchMode.ALL).toLineString(false).reverse().toString());
                        edgeStmt.setLong(9, osmWayId);
                        edgeStmt.addBatch();
                    }
                }
                edgeStmt.executeBatch();
                nodeStmt.executeBatch();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}

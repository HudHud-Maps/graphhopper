package com.graphhopper.resources;

import com.graphhopper.GraphHopper;
import com.graphhopper.matching.EdgeMatch;
import com.graphhopper.matching.MapMatching;
import com.graphhopper.matching.MatchResult;
import com.graphhopper.matching.Observation;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.VehicleSpeed;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.index.LocationIndexTree;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.shapes.GHPoint;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.io.WKTReader;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Path("datafeeder")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class EdgeUpdaterResource {

    private final GraphHopper graphHopper;
    private final EncodingManager encodingManager;
    private final ReadWriteLock graphLock = new ReentrantReadWriteLock();
    private final MapMatching mapMatching;

    public EdgeUpdaterResource(GraphHopper graphHopper, EncodingManager encodingManager, MapMatching mapMatching) {
        this.graphHopper = graphHopper;
        this.encodingManager = graphHopper.getEncodingManager();
        this.mapMatching = new MapMatching(graphHopper.getBaseGraph(), (LocationIndexTree) graphHopper.getLocationIndex(), null);
    }

    @POST
    public Response updateEdge(EdgeUpdateRequest request) {
        try {
            handleEdgeUpdate(request.getLineString(), request.getSpeed());

            return Response.ok().entity("Edges updated successfully").build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Error updating edges: " + e.getMessage())
                    .build();
        }
    }

    private void handleEdgeUpdate(String lineStringWKT, double speed) throws Exception {
        graphLock.writeLock().lock(); // Acquire write lock
        try {
            WKTReader reader = new WKTReader();
            LineString lineString = (LineString) reader.read(lineStringWKT);

            Coordinate[] coordinates = lineString.getCoordinates();

            List<Observation> observations = new ArrayList<>();
            for (Coordinate coord : coordinates) {
                observations.add(new Observation(new GHPoint(coord.y, coord.x)));
            }

            MatchResult matchResult = mapMatching.match(observations);

            for (EdgeMatch edgeMatch : matchResult.getEdgeMatches()) {
                EdgeIteratorState edge = edgeMatch.getEdgeState();
                updateEdgeSpeed(edge, speed);
            }
        } finally {
            graphLock.writeLock().unlock();
        }
    }

    private void updateEdgeSpeed(EdgeIteratorState edge, double speed) {
        DecimalEncodedValue speedEnc = encodingManager.getDecimalEncodedValue(VehicleSpeed.key("car"));
        edge.set(speedEnc, speed);
    }
}



package com.graphhopper.resources;

import com.graphhopper.GraphHopper;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.VehicleSpeed;
import com.graphhopper.util.EdgeIteratorState;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Path("datafeeder")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class EdgeUpdaterResource {

    private final GraphHopper graphHopper;
    private final ReadWriteLock graphLock = new ReentrantReadWriteLock();

    @Inject
    public EdgeUpdaterResource(GraphHopper graphHopper) {
        this.graphHopper = graphHopper;
    }

    @POST
    @Produces({MediaType.APPLICATION_JSON})
    public Response DoPost(List<EdgeUpdateRequest> requests) {
        try {
            for (EdgeUpdateRequest request : requests) {
                if (request.getWayID() < 0) {
                    throw new IllegalArgumentException("lineString cannot be null");
                }
                handleEdgeUpdates(request.getWayID(), request.getSpeed());
            }
            return Response.ok().entity("Edges updated successfully").build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Error updating edges: " + e.getMessage())
                    .build();
        }
    }

    private void handleEdgeUpdates(int wayID, double speed) throws Exception {
        graphLock.writeLock().lock(); // Acquire write lock
        try {
            EdgeIteratorState edge = graphHopper.getBaseGraph().getEdgeIteratorState(wayID, Integer.MIN_VALUE);
            DecimalEncodedValue speedEnc = graphHopper.getEncodingManager().getDecimalEncodedValue(VehicleSpeed.key("car"));
            edge.set(speedEnc, speed);
        } finally {
            graphLock.writeLock().unlock();
        }
    }
}


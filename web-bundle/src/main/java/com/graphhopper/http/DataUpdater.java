package com.graphhopper.http;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.graphhopper.GraphHopper;
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.EdgeIntAccess;
import com.graphhopper.routing.ev.VehicleAccess;
import com.graphhopper.routing.ev.VehicleSpeed;
import com.graphhopper.routing.util.AccessFilter;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.parsers.CarAverageSpeedParser;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.util.EdgeIteratorState;
import gnu.trove.set.hash.TIntHashSet;
import okhttp3.*;
// import com.graphhopper.storage.index;;
@Singleton
public class DataUpdater {
    private static final String ROAD_DATA_URL = "http://www.stadt-koeln.de/externe-dienste/open-data/traffic.php";
    private final Logger logger = LoggerFactory.getLogger(getClass());
    @Inject
    private GraphHopper graphHopper;

    private final Lock writeLock;
    private final OkHttpClient client;
    private final long seconds = 150;
    private ObjectMapper mapper;
    private RoadData currentRoads;
    public DataUpdater(Lock writeLock, GraphHopper graphHopper) {
        this.graphHopper = graphHopper;
        this.writeLock = writeLock;
        client = new OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS).build();
        this.mapper = new ObjectMapper();
        // json is underscore
        this.mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        SimpleModule pointModule = new SimpleModule("PointModule");
        pointModule.addSerializer(Point.class, new PointSerializer());
        pointModule.addDeserializer(Point.class, new PointDeserializer());
        mapper.registerModule(pointModule);
    }

    public void feed(RoadData data) {
        writeLock.lock();
        try {
            lockedFeed(data);
        } finally {
            writeLock.unlock();
        }
    }

    private final AtomicBoolean running = new AtomicBoolean(false);
    public void stop() {
        running.set(false);
    }
    public void start() {
        if (running.get()) {
            return;
        }

        running.set(true);
        new Thread("DataUpdater" + seconds) {
            @Override
            public void run() {
                logger.info("fetch new data every " + seconds + " seconds");
                while (running.get()) {
                    try {
                        logger.info("fetch new data");
                        RoadData data = fetchTrafficData(ROAD_DATA_URL);
                        feed(data);
                        try {
                            Thread.sleep(seconds * 1000);
                        } catch (InterruptedException ex) {
                            logger.info("update thread stopped");
                            break;
                        }
                    } catch (Exception ex) {
                        logger.error("Problem while fetching data", ex);
                    }
                }
            }
        }.start();
    }
    protected String fetchJSONString(String url) throws IOException {
        Request okRequest = new Request.Builder().url(url).build();
        return client.newCall(okRequest).execute().body().string();
    }
    public RoadData fetchTrafficData(String url) throws IOException {
        final String trafficJsonString = fetchJSONString(url);
        final OpenTrafficData trafficData = mapper.readValue(trafficJsonString, OpenTrafficData.class);
        RoadData data = new RoadData();

        for (final TrafficFeature trafficFeature : trafficData.features) {
            final String idStr = trafficFeature.attributes.identifier;
            final int streetUsage = trafficFeature.attributes.auslastung;

            // according to the docs http://www.offenedaten-koeln.de/dataset/verkehrskalender-der-stadt-k%C3%B6ln
            // there are only three indications 0='ok', 1='slow' and 2='traffic jam'
            if (streetUsage != 0 && streetUsage != 1 && streetUsage != 2) {
                continue;
            }

            final double speed;
            if (streetUsage == 1) {
                speed = 20;
            } else if (streetUsage == 2) {
                speed = 5;
            } else {
                // If there is a traffic jam we need to revert afterwards!
                speed = 45; // TODO getOldSpeed();
            }

            final List<List<List<Double>>> paths = trafficFeature.geometry.paths;
            for (int pathPointIndex = 0; pathPointIndex < paths.size(); pathPointIndex++) {
                final List<Point> points = new ArrayList<>();
                final List<List<Double>> path = paths.get(pathPointIndex);
                for (int pointIndex = 0; pointIndex < path.size(); pointIndex++) {
                    final List<Double> point = path.get(pointIndex);
                    points.add(new Point(point.get(1), point.get(0)));
                }

                if (!points.isEmpty()) {
                    data.add(new RoadEntry(idStr + "_" + pathPointIndex, points, speed, "speed", "replace"));
                }
            }

        }

        return data;
    }
    private void lockedFeed(RoadData data) {
        currentRoads = data;
        BaseGraph graph = graphHopper.getBaseGraph();
        EncodingManager encodingManager = graphHopper.getEncodingManager();
        LocationIndex locationIndex = graphHopper.getLocationIndex();

        int errors = 0;
        int updates = 0;
        TIntHashSet edgeIds = new TIntHashSet(data.size());
        for (RoadEntry entry : data) {
            BooleanEncodedValue val = graphHopper.getEncodingManager().getBooleanEncodedValue(VehicleAccess.key("car"));
            // TODO get more than one point -> our map matching component
            Point point = entry.getPoints().get(entry.getPoints().size() / 2);
            logger.info("POINT" + point);
            Snap qr = locationIndex.findClosest(point.lat, point.lon, AccessFilter.allEdges(val));
            if (!qr.isValid()) {
                logger.error("INVALID QR");
                errors++;
                continue;
            }
            int edgeId = qr.getClosestEdge().getEdge();
            logger.info("Edge ID " + edgeId);
            if (edgeIds.contains(edgeId)) {
                logger.error("Edge already updated");
                // TODO this wouldn't happen with our map matching component
                errors++;
                continue;
            }

            edgeIds.add(edgeId);
            EdgeIteratorState edge = graph.getEdgeIteratorState(edgeId, Integer.MIN_VALUE);
            double value = entry.getValue();
            if ("replace".equalsIgnoreCase(entry.getMode())) {
                if ("speed".equalsIgnoreCase(entry.getValueType())) {
                    DecimalEncodedValue decEnc =  encodingManager.getDecimalEncodedValue(VehicleSpeed.key("car"));
                    double oldSpeed = decEnc.getDecimal(false, edgeId, graph.getEdgeAccess());
                    if (oldSpeed != value) {
                        updates++;
                        logger.info("Speed change at " + entry.getId() + " (" + point + "). Old: " + oldSpeed + ", new:" + value);
                        edge.set(decEnc, value);
                    } 
                } else {
                    throw new IllegalStateException("currently no other value type than 'speed' is supported");
                }
            } else {
                throw new IllegalStateException("currently no other mode than 'replace' is supported");
            }
        }

        logger.info("Updated " + updates + " street elements of " + data.size() + ". Unchanged:" + (data.size() - updates) + ", errors:" + errors);
    }
    @JsonIgnoreProperties(ignoreUnknown=true)
    private static class OpenTrafficData {
        @JsonProperty("features")
        public List<TrafficFeature> features;
    }

    @JsonIgnoreProperties(ignoreUnknown=true)
    private static class TrafficFeature {
        @JsonProperty("attributes")
        public TrafficAttributes attributes;

        @JsonProperty("geometry")
        public TrafficGeometry geometry;
    }

    @JsonIgnoreProperties(ignoreUnknown=true)
    private static class TrafficAttributes {
        @JsonProperty("identifier")
        public String identifier;

        @JsonProperty("auslastung")
        public Integer auslastung;
    }

    @JsonIgnoreProperties(ignoreUnknown=true)
    private static class TrafficGeometry {
        @JsonProperty("paths")
        public List<List<List<Double>>> paths;
    }
    static class PointDeserializer extends JsonDeserializer<Point> {

        @Override
        public Point deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException, JsonProcessingException {
            JsonNode node = jp.getCodec().readTree(jp);
            Iterator<JsonNode> iter = node.elements();
            double lon = iter.next().asDouble();
            double lat = iter.next().asDouble();
            return new Point(lat, lon);
        }
    }

    static class PointSerializer extends JsonSerializer<Point> {

        @Override
        public void serialize(Point value, JsonGenerator jgen, SerializerProvider provider) throws IOException, JsonProcessingException {
            // geojson
            jgen.writeStartArray();
            jgen.writeNumber(value.lon);
            jgen.writeNumber(value.lat);
            jgen.writeEndArray();
        }
    }
}
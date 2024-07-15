package com.graphhopper.resources;
import java.io.IOException;
import java.util.Iterator;

import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.graphhopper.http.DataUpdater;
import com.graphhopper.http.Point;
import com.graphhopper.http.RoadData;

@Path("datafeed")
public class DatafeedResource {

    private final ObjectMapper mapper;

    private static final Logger logger = LoggerFactory.getLogger(DatafeedResource.class);

    @Inject
    private DataUpdater updater;

    @Inject
    public DatafeedResource() {
        this.mapper = new ObjectMapper();
        this.mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        SimpleModule pointModule = new SimpleModule("PointModule");
        pointModule.addSerializer(Point.class, new PointSerializer());
        pointModule.addDeserializer(Point.class, new PointDeserializer());
        mapper.registerModule(pointModule);
    }

    @POST
    @Consumes({MediaType.APPLICATION_JSON})
    @Produces({MediaType.APPLICATION_JSON})
    public Response doPost(
        @Context HttpServletRequest httpReq
    ) throws ServletException, IOException {
        logger.info("Datafeed request");
        RoadData data = mapper.readValue(httpReq.getInputStream(), RoadData.class);

        updater.feed(data);
        // read a request into an object
        return Response.ok().build();
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


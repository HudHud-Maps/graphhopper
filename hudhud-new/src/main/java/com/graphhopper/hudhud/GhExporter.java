package com.graphhopper.hudhud;

import java.io.FileInputStream;
import java.io.InputStream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.graphhopper.GraphHopperConfig;

import com.graphhopper.GraphHopper;
import com.graphhopper.storage.BaseGraph;


public class GhExporter {
    BaseGraph graph;
    public void loadGraph() {
        // Initialize GraphHopper
        try {
            GraphHopper hopper = new GraphHopper();
            // Configure and load the graph (you'll need to set this up properly)
            ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());
            objectMapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
            // Read the config file into an InputStream
            InputStream configInputStream = new FileInputStream("config-example.yml");
            GraphHopperConfig graphHopperConfig = objectMapper.readValue(configInputStream, GraphHopperConfig.class);
            hopper.init(graphHopperConfig);
            hopper.importOrLoad();
            this.graph = hopper.getBaseGraph(); 
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public BaseGraph getGraph() {
        return this.graph;
    }
}

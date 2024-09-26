package com.graphhopper.hudhud;

import com.graphhopper.hudhud.GhExporter;
import com.graphhopper.hudhud.Annotator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class App 
{

    public static void main( String[] args ) {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            GhExporter ghExporter = new GhExporter();
            Annotator annotator = new Annotator("saudi_arabia-latest.osm.pbf");
            annotator.annotate();

            ghExporter.loadGraph(annotator.getWayMap());
            // Future<Void> task1 = executor.submit(() -> {
            //     ghExporter.loadGraph();
            //     return null;
            // });
            // Future<Void> task2 = executor.submit(() -> {
            //     annotator.annotate();
            //     return null;
            // });

            // // Get results
            // Void result1 = task1.get();
            // Void result2 = task2.get();

            // System.out.println("Task 1 result: " + result1);
            // System.out.println("Task 2 result: " + result2);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            executor.shutdown();
        }
    }
}
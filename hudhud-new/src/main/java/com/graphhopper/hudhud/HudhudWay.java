package com.graphhopper.hudhud;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;

class HudhudWay {
    private long id;
    private LineString lineString;
    private String highwayTag;
    private Coordinate centroid;
    private int fow;
    private int frc;
    private String oneway;

    public HudhudWay(WKTReader reader, String line) {
        String[] data = line.split("\t");
        this.id = Long.parseLong(data[0]);
        try {
            this.lineString = (LineString) reader.read(data[1]);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        this.highwayTag = data[2];
        String centroidString = data[3];
        String[] centroidCoords = centroidString.substring(1).split(", ");
        this.centroid = new Coordinate(Double.parseDouble(centroidCoords[0]), Double.parseDouble(centroidCoords[1]));
    }

    public HudhudWay(long id, LineString lineString, String highwayTag, Coordinate centroid, int fow, int frc, String oneway) {
        this.id = id;
        this.lineString = lineString;
        this.highwayTag = highwayTag;
        this.centroid = centroid;
        this.fow = fow;
        this.frc = frc;
        this.oneway = oneway;
    }

    public long getId() {
        return this.id;
    }

    public LineString getLineString() {
        return this.lineString;
    }
    
    public String getHighwayTag() {
        return this.highwayTag;
    }

    public Coordinate getCentroid() {
        return this.centroid;
    }

    public int getFow() {
        return this.fow;
    }

    public void setFow(int fow) {
        this.fow = fow;
    }

    public int getFrc() {
        return this.frc;
    }

    public void setFrc(int frc) {
        this.frc = frc;
    }

    public String getOneway() {
        return this.oneway;
    }
}

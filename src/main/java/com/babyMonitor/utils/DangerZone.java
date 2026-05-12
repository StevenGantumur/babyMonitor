package com.babyMonitor.utils;

import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;
import java.util.List;
import java.util.ArrayList;
import org.opencv.core.Mat;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.MatOfPoint;


public class DangerZone {

    private ArrayList<Point> points;
    private String name;
    private int sensitivity;
    
    public DangerZone(String name, int sensitivity) {
        this.points = new ArrayList<>();
        this.name = name;
        this.sensitivity = sensitivity;
    }

    public void addPoint(Point p) {
        this.points.add(p);
    }

    public String getName(){
        return this.name;
    }

    public ArrayList<Point> getPoints() {
        return this.points;
    }

    public boolean checkDanger(Mat diff) {
        Mat mask = Mat.zeros(diff.size(), CvType.CV_8UC1);
        MatOfPoint polygon = new MatOfPoint();
        polygon.fromList(this.points);

        List<MatOfPoint> polygons = new ArrayList<>();
        polygons.add(polygon);

        Imgproc.fillPoly(mask, polygons, new Scalar(255));

        Mat masked = new Mat();
        Core.bitwise_and(diff, mask, masked);

        int motionPixels = Core.countNonZero(masked);  
        return motionPixels > this.sensitivity;
    }

}
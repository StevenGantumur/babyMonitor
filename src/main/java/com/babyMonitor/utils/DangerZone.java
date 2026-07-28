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
import org.opencv.core.Rect;
import org.opencv.core.MatOfPoint2f;


public class DangerZone {

    private ArrayList<Point> points;
    private String name;
    private int sensitivity;
    private boolean wasInDanger;
    
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

    /**
     * True if any corner of the given box falls inside this zone's polygon.
     * Deliberately sensitive: a body clipping the edge counts as overlapping,
     * because missing a real intrusion is worse than an occasional false alarm.
     */
    public boolean overlaps(Rect body) {
        // Fewer than 3 points isn't a polygon yet — nothing can be inside it.
        if (this.points.size() < 3) return false;

        MatOfPoint2f contour = new MatOfPoint2f();
        contour.fromList(this.points);

        Point[] corners = {
            new Point(body.x,              body.y),                 // top-left
            new Point(body.x + body.width, body.y),                 // top-right
            new Point(body.x + body.width, body.y + body.height),   // bottom-right
            new Point(body.x,              body.y + body.height)    // bottom-left
        };

        for (Point corner : corners) {
            // >= 0 means inside or exactly on the edge; negative means outside.
            if (Imgproc.pointPolygonTest(contour, corner, false) >= 0) {
                return true;
            }
        }
        return false;
    }

    public boolean isNewDanger(boolean nowInDanger) {
        boolean wasBefore = wasInDanger;
        wasInDanger = nowInDanger;
        return !wasBefore && nowInDanger;
    }

}
package com.babyMonitor;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.objdetect.CascadeClassifier;
import com.babyMonitor.utils.ResourceUtils;
import com.babyMonitor.utils.DangerZone;
import com.babyMonitor.utils.ImageUtils;
import com.babyMonitor.utils.MonitorUtils;

public class RoomMonitor implements Monitor {

    private static final int DIFF_THRESHOLD = 30;
    private static final int ZONE_SENSITIVITY = 100;

    private static final Scalar YELLOW = new Scalar(0, 255, 255);
    private static final Scalar GREEN  = new Scalar(0, 255, 0);
    private static final Scalar RED    = new Scalar(0, 0, 255);
    private static final Scalar GRAY   = new Scalar(128, 128, 128);

    private List<DangerZone> zones = new ArrayList<>();
    private DangerZone currentZone = new DangerZone("Zone 1", ZONE_SENSITIVITY);
    private boolean calibrated = false;
    private Point currentMouse = new Point(0, 0);

    private final Mat grayFrame = new Mat();
    private final Mat previousFrame = new Mat();
    private final Mat diff = new Mat();

    private CascadeClassifier bodyDetector;
    private final MatOfRect bodies = new MatOfRect();

    @Override
    public String getName() {
        return "Room Monitor";
    }

    @Override
    public void setActive(boolean active) {
        if (!active) return;

        if (bodyDetector == null) {
            try {
                bodyDetector = new CascadeClassifier(
                    ResourceUtils.extractResource("haarcascade_fullbody.xml"));
            } catch (Exception e) {
                System.out.println("Room Monitor: Could not load body cascade - " + e.getMessage());
            }
        }

        previousFrame.release();
        System.out.println(calibrated
            ? "Room Monitor active. Press 'r' to recalibrate."
            : "Room Monitor active. Click corners, ENTER closes a zone, ENTER again finishes.");
    }

    @Override
    public void onClick(int x, int y) {
        if (calibrated) return;
        currentZone.addPoint(new Point(x, y));
    }

    @Override
    public void onMouseMove(int x, int y) {
        currentMouse = new Point(x, y);
    }

    @Override
    public void onKey(char key) {
        if (key == 'r' || key == 'R') {
            zones = new ArrayList<>();
            currentZone = new DangerZone("Zone 1", ZONE_SENSITIVITY);
            calibrated = false;
            System.out.println("Room Monitor: zones reset.");
            return;
        }

        if (key != '\n' || calibrated) return;

        // Empty current zone means a second consecutive ENTER — that ends calibration.
        if (currentZone.getPoints().isEmpty()) {
            calibrated = true;
            System.out.println("Calibration complete! " + zones.size() + " zone(s) defined.");
        } else {
            zones.add(currentZone);
            currentZone = new DangerZone("Zone " + (zones.size() + 1), ZONE_SENSITIVITY);
        }
    }

    @Override
    public BufferedImage processFrame(Mat frame) {
        Imgproc.cvtColor(frame, grayFrame, Imgproc.COLOR_BGR2GRAY);

        if (!calibrated) {
            return ImageUtils.matToBufferedImage(drawCalibration(frame));
        }

        if (previousFrame.empty()) {
            grayFrame.copyTo(previousFrame);
            return ImageUtils.matToBufferedImage(frame);
        }

        Core.absdiff(previousFrame, grayFrame, diff);
        Imgproc.threshold(diff, diff, DIFF_THRESHOLD, 255, Imgproc.THRESH_BINARY);
        grayFrame.copyTo(previousFrame);

        MonitorUtils.currentMotionLevel = Core.countNonZero(diff);
        if(bodyDetector != null) {
            bodyDetector.detectMultiScale(grayFrame, bodies, 1.1, 3, 0, new Size(50, 100), new Size());
        }
        boolean anyDanger = false;

        for (DangerZone zone : zones) {
            boolean zoneDanger = zone.checkDanger(diff);
            boolean newDanger = zone.isNewDanger(zoneDanger);
            drawPolygon(frame, zone.getPoints(), zoneDanger ? RED : GREEN);

            if (zoneDanger) {
                boolean bodyInZone = false;
                Rect[] detected = bodies.toArray();
                for(Rect body: detected) {
                    if(zone.overlaps(body)) { 
                        bodyInZone = true;
                        break;
                    }
                }
                anyDanger = true;
                if (newDanger) {
                    MonitorUtils.logAlert(bodyInZone
                    ? "DANGER: person in " + zone.getName()
                    : "Watching: motion in " + zone.getName());
                }

                Imgproc.putText(frame, (bodyInZone ? "DANGER: " : "WATCHING: ") + zone.getName(), zone.getPoints().get(0),
                    Imgproc.FONT_HERSHEY_SIMPLEX, 0.8, new Scalar(0, 165, 255), 2);
            }
        }

        MonitorUtils.babyState = anyDanger ? "In danger zone" : "Clear";
        MonitorUtils.updateInfoPanel(anyDanger);
        return ImageUtils.matToBufferedImage(frame);
    }

    private Mat drawCalibration(Mat frame) {
        Mat display = frame.clone();

        Imgproc.putText(display,
            "Click corners. Press ENTER to close zone. Double ENTER when done.",
            new Point(10, 30), Imgproc.FONT_HERSHEY_SIMPLEX, 0.7, new Scalar(255, 255, 255), 2);

        for (DangerZone zone : zones) {
            drawPolygon(display, zone.getPoints(), GREEN);
            for (Point p : zone.getPoints()) {
                Imgproc.circle(display, p, 6, GREEN, -1);
            }
        }

        List<Point> pts = currentZone.getPoints();
        for (Point p : pts) {
            Imgproc.circle(display, p, 6, YELLOW, -1);
        }
        for (int i = 0; i < pts.size() - 1; i++) {
            Imgproc.line(display, pts.get(i), pts.get(i + 1), YELLOW, 2);
        }
        if (!pts.isEmpty()) {
            Imgproc.line(display, pts.get(pts.size() - 1), currentMouse, GRAY, 1);
        }
        return display;
    }

    private void drawPolygon(Mat target, List<Point> pts, Scalar color) {
        for (int i = 0; i < pts.size(); i++) {
            Imgproc.line(target, pts.get(i), pts.get((i + 1) % pts.size()), color, 2);
        }
    }
}

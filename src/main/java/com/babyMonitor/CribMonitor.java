package com.babyMonitor;

import java.awt.image.BufferedImage;
import java.util.List;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import com.babyMonitor.utils.DangerZone;
import com.babyMonitor.utils.ImageUtils;
import com.babyMonitor.utils.ResourceUtils;
import com.babyMonitor.utils.MonitorUtils;

public class CribMonitor implements Monitor {

    private static final int MOTION_THRESHOLD = 500;
    private static final int DIFF_THRESHOLD = 30;
    private static final int CORNERS_NEEDED = 4;

    private static final String[] INSTRUCTIONS = {
        "Click TOP-LEFT corner of crib",
        "Click TOP-RIGHT corner of crib",
        "Click BOTTOM-RIGHT corner of crib",
        "Click BOTTOM-LEFT corner of crib"
    };

    private DangerZone cribZone = new DangerZone("Crib", 0);
    private boolean calibrated = false;

    private final Mat grayFrame = new Mat();
    private final Mat previousFrame = new Mat();
    private final Mat diff = new Mat();
    private final MatOfRect faces = new MatOfRect();

    private CascadeClassifier faceDetector;

    @Override
    public String getName() {
        return "Crib Monitor";
    }

    @Override
    public void setActive(boolean active) {
        if (!active) return;

        if (faceDetector == null) {
            try {
                faceDetector = new CascadeClassifier(
                    ResourceUtils.extractResource("haarcascade_frontalface_default.xml"));
            } catch (Exception e) {
                System.out.println("Crib Monitor: could not load face cascade - " + e.getMessage());
            }
        }
        previousFrame.release();
        System.out.println(calibrated
            ? "Crib Monitor active. Press 'r' to recalibrate."
            : "Crib Monitor active. " + INSTRUCTIONS[cribZone.getPoints().size()]);
    }

    @Override
    public void onClick(int x, int y) {
        if (calibrated) return;
        cribZone.addPoint(new Point(x, y));
        if (cribZone.getPoints().size() == CORNERS_NEEDED) {
            calibrated = true;
            System.out.println("Calibration complete!");
        }
    }

    @Override
    public void onKey(char key) {
        if (key == 'r' || key == 'R') {
            cribZone = new DangerZone("Crib", 0);
            calibrated = false;
            System.out.println("Crib Monitor: calibration reset. " + INSTRUCTIONS[0]);
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
        boolean danger = MonitorUtils.currentMotionLevel > MOTION_THRESHOLD && checkDanger(frame, diff);
        if(cribZone.isNewDanger(danger)) {
            MonitorUtils.logAlert("DANGER: Baby near crib edge!");
        }
        MonitorUtils.updateInfoPanel(danger);

        List<Point> corners = cribZone.getPoints();
        for (int i = 0; i < corners.size(); i++) {
            Imgproc.line(frame, corners.get(i), corners.get((i + 1) % corners.size()),
                new Scalar(0, 255, 255), 2);
        }

        if (faceDetector != null) {
            faceDetector.detectMultiScale(grayFrame, faces, 1.1, 5, 0, new Size(30, 30), new Size());
            for (Rect face : faces.toArray()) {
                Imgproc.rectangle(frame, face, new Scalar(0, 255, 0), 2);
                MonitorUtils.babyState = "Face Detected!";
            }
        }

        if (faces.empty() || faces.toArray().length == 0) {
            if (MonitorUtils.currentMotionLevel > 5000) {
                MonitorUtils.babyState = "Active";
            } else if (MonitorUtils.currentMotionLevel > 2500) {
                MonitorUtils.babyState = "Stirring";
            } else {
                MonitorUtils.babyState = "Still";
            }
        }

        return ImageUtils.matToBufferedImage(frame);
    }

    private Mat drawCalibration(Mat frame) {
        Mat display = frame.clone();
        List<Point> corners = cribZone.getPoints();

        Imgproc.putText(display, INSTRUCTIONS[corners.size()], new Point(10, 30),
            Imgproc.FONT_HERSHEY_SIMPLEX, 1.2, new Scalar(255, 255, 255), 2);

        for (Point corner : corners) {
            Imgproc.circle(display, corner, 8, new Scalar(0, 255, 255), -1);
        }
        return display;
    }

    private boolean checkDanger(Mat frame, Mat diff) {
        Mat points = new Mat();
        Core.findNonZero(diff, points);
        if (points.empty()) return false;

        Rect motionArea = Imgproc.boundingRect(points);
        List<Point> corners = cribZone.getPoints();

        int left   = (int) Math.min(corners.get(0).x, corners.get(3).x);
        int top    = (int) Math.min(corners.get(0).y, corners.get(1).y);
        int right  = (int) Math.max(corners.get(1).x, corners.get(2).x);
        int bottom = (int) Math.max(corners.get(2).y, corners.get(3).y);

        int marginX = (right - left) / 10;
        int marginY = (bottom - top) / 10;

        boolean danger = motionArea.y < top + marginY
            || (motionArea.y + motionArea.height) > bottom - marginY
            || motionArea.x < left + marginX
            || (motionArea.x + motionArea.width) > right - marginX;

        if (danger) {
            Imgproc.putText(frame, "DANGER - Baby near edge!", new Point(10, 60),
                Imgproc.FONT_HERSHEY_SIMPLEX, 0.8, new Scalar(0, 0, 255), 2);
            Imgproc.rectangle(frame, new Point(left, top), new Point(right, bottom),
                new Scalar(0, 0, 255), 3);
        } else {
            Imgproc.rectangle(frame, new Point(left, top), new Point(right, bottom),
                new Scalar(0, 255, 0), 2);
        }
        return danger;
    }
}

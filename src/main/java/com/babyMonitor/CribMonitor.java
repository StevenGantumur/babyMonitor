package com.babyMonitor;

import java.awt.BorderLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.opencv.videoio.VideoCapture;
import com.babyMonitor.utils.ImageUtils;
import com.babyMonitor.utils.ResourceUtils;
import com.babyMonitor.utils.MonitorUtils;
import nu.pattern.OpenCV;


public class CribMonitor {
    // Crib setup and count
    static Point[] cribCorners = new Point[4];
    static int clickCount = 0;
    static boolean calibrated = false;
    // diff
    static BufferedImage currentImage = null;



    public static void start() {
        OpenCV.loadLocally();
        CascadeClassifier faceDetector;
        try {
            String facePath = ResourceUtils.extractResource("haarcascade_frontalface_default.xml");
            faceDetector = new CascadeClassifier(facePath);
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
            return;
        }

        VideoCapture camera = new VideoCapture(0);
        Mat frame = new Mat();
        Mat grayFrame = new Mat();
        Mat previousFrame = new Mat();
        Mat diff = new Mat();
        MatOfRect faces = new MatOfRect();

        JFrame window = new JFrame("Crib Monitor");
        window.setLayout(new BorderLayout());
        window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        JPanel panel = new JPanel() {
            @Override
            protected void paintComponent(java.awt.Graphics g) {
                super.paintComponent(g);
                if (currentImage != null) {
                    g.drawImage(currentImage, 0, 0, getWidth(), getHeight(), null);
                }
            }
        };

        JPanel infoPanel = MonitorUtils.buildInfoPanel();

        window.add(panel, BorderLayout.CENTER);
        window.add(infoPanel, BorderLayout.EAST);
        window.setSize(1560, 720);
        window.setVisible(true);  

        panel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!calibrated) {
                    double scaleX = (double) frame.cols() / panel.getWidth();
                    double scaleY = (double) frame.rows() / panel.getHeight();
                    int actualX = (int)(e.getX() * scaleX);
                    int actualY = (int)(e.getY() * scaleY);
                    cribCorners[clickCount] = new Point(actualX, actualY);
                    clickCount++;
                    if (clickCount == 4) {
                        calibrated = true;
                        System.out.println("Calibration complete!");
                    }
                }
            }
        });

        while(camera.isOpened()) {
            camera.read(frame);
            if (frame.empty()) continue;
            Imgproc.cvtColor(frame, grayFrame, Imgproc.COLOR_BGR2GRAY);

            if(!calibrated) {
                String[] instructions = {
                    "Click TOP-LEFT corner of crib",
                    "Click TOP-RIGHT corner of crib",
                    "Click BOTTOM-RIGHT corner of crib",
                    "Click BOTTOM-LEFT corner of crib"
                };

                Mat display = frame.clone();

                Imgproc.putText(display, instructions[clickCount],
                    new Point(10, 30),
                    Imgproc.FONT_HERSHEY_SIMPLEX,
                    1.2, new Scalar(255, 255, 255), 2);

                for (int i = 0; i < clickCount; i++) {
                    Imgproc.circle(display, cribCorners[i], 8,
                        new Scalar(0, 255, 255), -1);
                }
                currentImage = ImageUtils.matToBufferedImage(display);
                SwingUtilities.invokeLater(() -> panel.repaint());
            }
            else {
                if(previousFrame.empty()) {
                    grayFrame.copyTo(previousFrame);
                    continue;
                }
                Core.absdiff(previousFrame, grayFrame, diff);
                Imgproc.threshold(diff, diff, 30, 255, Imgproc.THRESH_BINARY);
                grayFrame.copyTo(previousFrame);

                MonitorUtils.currentMotionLevel = Core.countNonZero(diff);
                int motionThreshold = 500;
                boolean danger = false;
                if (MonitorUtils.currentMotionLevel > motionThreshold) {
                    danger = checkDanger(frame, diff);
                }
                MonitorUtils.updateInfoPanel(danger);

                Imgproc.line(frame, cribCorners[0], cribCorners[1], new Scalar(0, 255, 255), 2);
                Imgproc.line(frame, cribCorners[1], cribCorners[2], new Scalar(0, 255, 255), 2);
                Imgproc.line(frame, cribCorners[2], cribCorners[3], new Scalar(0, 255, 255), 2);
                Imgproc.line(frame, cribCorners[3], cribCorners[0], new Scalar(0, 255, 255), 2);

                faceDetector.detectMultiScale(grayFrame, faces, 1.1, 5, 0,
                    new Size(30, 30), new Size());
                for(Rect face : faces.toArray()){
                    Imgproc.rectangle(frame, face, new Scalar(0, 255, 0), 2);
                    MonitorUtils.babyState = "Face Detected!";
                }

                if (faces.toArray().length == 0) {
                    if (MonitorUtils.currentMotionLevel > 5000) {
                        MonitorUtils.babyState = "Active";
                    } 
                    else if (MonitorUtils.currentMotionLevel > 2500) {
                        MonitorUtils.babyState = "Stirring";
                    } 
                    else {
                        MonitorUtils.babyState = "Still";
                    }
                }

                currentImage = ImageUtils.matToBufferedImage(frame);
                SwingUtilities.invokeLater(() -> panel.repaint());
            }
        }
    
    }

    static boolean checkDanger(Mat frame, Mat diff){
        if (!calibrated) return false;

        Mat points = new Mat();
        Core.findNonZero(diff, points);

        if(points.empty()) return false;

        Rect motionArea = Imgproc.boundingRect(points);

        int left   = (int) Math.min(cribCorners[0].x, cribCorners[3].x);
        int top    = (int) Math.min(cribCorners[0].y, cribCorners[1].y);
        int right  = (int) Math.max(cribCorners[1].x, cribCorners[2].x);
        int bottom = (int) Math.max(cribCorners[2].y, cribCorners[3].y);

        int dangerZoneX = (right - left) / 10;
        int dangerZoneY = (bottom - top) / 10;


        boolean nearTop    = motionArea.y < top + dangerZoneY;
        boolean nearBottom = (motionArea.y + motionArea.height) > bottom - dangerZoneY;
        boolean nearLeft   = motionArea.x < left + dangerZoneX;
        boolean nearRight  = (motionArea.x + motionArea.width) > right - dangerZoneX;

        boolean danger = nearTop || nearBottom || nearLeft || nearRight;

        if(danger){
            Imgproc.putText(frame, "DANGER - Baby near edge!",
            new Point(10, 60),
            Imgproc.FONT_HERSHEY_SIMPLEX,
            0.8, new Scalar(0, 0, 255), 2);

            Imgproc.rectangle(frame,
                new Point(left, top),
                new Point(right, bottom),
                new Scalar(0, 0, 255), 3);

            MonitorUtils.logAlert("DANGER: Baby near crib edge!");
        }
        else {
            Imgproc.rectangle(frame,
                new Point(left, top),
                new Point(right, bottom),
                new Scalar(0, 255, 0), 2);
        }
        return danger;
    }
}
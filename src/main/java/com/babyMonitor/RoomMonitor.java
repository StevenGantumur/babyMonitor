package com.babyMonitor;

import java.awt.BorderLayout;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.opencv.videoio.VideoCapture;

import com.babyMonitor.utils.DangerZone;
import com.babyMonitor.utils.ImageUtils;
import com.babyMonitor.utils.MonitorUtils;

import nu.pattern.OpenCV;

public class RoomMonitor {
    
    static BufferedImage currentImage = null;
    static List<DangerZone> zones = new ArrayList<>();
    static DangerZone currentZone = null;
    static boolean calibrated = false;
    static Point currentMouse = new Point(0, 0);
    
    static String extractResource(String resourceName) throws Exception {
        InputStream is = CribMonitor.class.getResourceAsStream("/" + resourceName);
        if (is == null) throw new Exception("Resource not found: " + resourceName);
        File temp = File.createTempFile(resourceName, "");
        temp.deleteOnExit();
        Files.copy(is, temp.toPath(), StandardCopyOption.REPLACE_EXISTING);
        return temp.getAbsolutePath();
    }

    public static void start() {
        OpenCV.loadLocally();
        CascadeClassifier bodyDetector;

        try {
            String bodyPath = extractResource("haarcascade_frontalface_default.xml");
            bodyDetector = new CascadeClassifier(bodyPath);
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
            return;
        }

        VideoCapture camera = new VideoCapture(0);
        Mat frame = new Mat();
        Mat grayFrame = new Mat();
        Mat previousFrame = new Mat();
        Mat diff = new Mat();
        MatOfRect bodies = new MatOfRect();

        JFrame window = new JFrame("Room Monitor");
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


        currentZone = new DangerZone("Zone 1", 100);

        panel.setFocusable(true);
        panel.requestFocusInWindow();

        panel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!calibrated) {
                    double scaleX = (double) frame.cols() / panel.getWidth();
                    double scaleY = (double) frame.rows() / panel.getHeight();
                    int actualX = (int)(e.getX() * scaleX);
                    int actualY = (int)(e.getY() * scaleY);
                    currentZone.addPoint(new Point(actualX, actualY));
                }
            }
        });

        panel.addKeyListener(new KeyAdapter() {
              @Override
              public void keyPressed(KeyEvent e) {
                  if (e.getKeyCode() == KeyEvent.VK_ENTER && !calibrated) {
                      if (currentZone.getPoints().isEmpty()) {
                          calibrated = true;
                          System.out.println("Calibration complete! " + zones.size() + " zone(s) defined.");
                      } else {
                          zones.add(currentZone);
                          currentZone = new DangerZone("Zone " + (zones.size() + 1), 100);
                      }
                  }
              }
        });

        panel.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                double scaleX = (double) frame.cols() / panel.getWidth();
                double scaleY = (double) frame.rows() / panel.getHeight();
                int actualX = (int)(e.getX() * scaleX);
                int actualY = (int)(e.getY() * scaleY);
                currentMouse = new Point(actualX, actualY);
            }
        });

        while(camera.isOpened()) {
            camera.read(frame);
            if (frame.empty()) continue;
            Imgproc.cvtColor(frame, grayFrame, Imgproc.COLOR_BGR2GRAY);

            if(!calibrated){
                Mat display = frame.clone();

                Imgproc.putText(display,
                      "Click corners. Press ENTER to close zone. Double ENTER when done.",
                      new Point(10, 30),
                      Imgproc.FONT_HERSHEY_SIMPLEX,
                      0.7, new Scalar(255, 255, 255), 2);

                for (DangerZone zone: zones) {
                    List<Point> points = zone.getPoints();
                    for(int i = 0; i < points.size(); i ++){
                        Imgproc.circle(display, points.get(i), 6, new Scalar(0, 255, 0), -1);
                        Imgproc.line(display, points.get(i), points.get((i + 1) % points.size()),
                            new Scalar(0, 255, 0), 2);
                    }
                }

                List<Point> currentPts = currentZone.getPoints();
                for (int i = 0; i < currentPts.size(); i++) {
                    Imgproc.circle(display, currentPts.get(i), 6, new Scalar(0, 255, 255), -1);
                }
                for (int i = 0; i < currentPts.size() - 1; i++) {
                    Imgproc.line(display, currentPts.get(i), currentPts.get(i + 1),
                        new Scalar(0, 255, 255), 2);
                }
                if (!currentPts.isEmpty()) {
                    Imgproc.line(display, currentPts.get(currentPts.size() - 1), currentMouse,
                        new Scalar(128, 128, 128), 1);
                }

                currentImage = ImageUtils.matToBufferedImage(display);
                SwingUtilities.invokeLater(() -> panel.repaint());
            }

            else{
                if (previousFrame.empty()) {
                    grayFrame.copyTo(previousFrame);
                    continue;
                }

                Core.absdiff(previousFrame, grayFrame, diff);
                Imgproc.threshold(diff, diff, 30, 255, Imgproc.THRESH_BINARY);
                grayFrame.copyTo(previousFrame);

                boolean anyDanger = false;

                for(DangerZone zone: zones){
                    boolean zoneDanger = zone.checkDanger(diff);
                    Scalar color = zoneDanger ? new Scalar(0, 0, 255) : new Scalar(0, 255, 0);

                    List<Point> points = zone.getPoints();
                    for(int i = 0; i < points.size(); i++){
                        Imgproc.line(frame, points.get(i), points.get((i + 1) % points.size()), color, 2);
                    }

                    if(zoneDanger) { 
                        anyDanger = true;
                        MonitorUtils.logAlert("Danger in " + zone.getName());
                        Imgproc.putText(frame, "DANGER: " + zone.getName(),
                        points.get(0),
                        Imgproc.FONT_HERSHEY_SIMPLEX,
                        0.8, new Scalar(0, 0, 255), 2);
                    }
                }

                MonitorUtils.updateInfoPanel(anyDanger);
                currentImage = ImageUtils.matToBufferedImage(frame);
                SwingUtilities.invokeLater(() -> panel.repaint());
            }
            
        }
    }

    
}

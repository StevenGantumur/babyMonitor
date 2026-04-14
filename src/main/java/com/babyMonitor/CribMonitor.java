package com.babyMonitor;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
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

import nu.pattern.OpenCV;

public class CribMonitor {
    // Crib setup and count
    static Point[] cribCorners = new Point[4];
    static int clickCount = 0;
    static boolean calibrated = false;
    // diff
    static BufferedImage currentImage = null;

    //UI and overlay
    static int currentMotionLevel = 0;
    static String babyState = "Unknown";
    static java.util.ArrayList<String> alertLog = new java.util.ArrayList<>();
    static javax.swing.JLabel statusLabel = null;
    static javax.swing.JLabel babyStateLabel = null;
    static javax.swing.JLabel motionLabel = null;
    static javax.swing.JLabel lastMovementLabel = null;
    static javax.swing.JTextArea alertLogArea = null;
    static long lastMovementTime = 0;


    static String extractResource(String resourceName) throws Exception {
        InputStream is = CribMonitor.class.getResourceAsStream("/" + resourceName);
        if (is == null) throw new Exception("Resource not found: " + resourceName);
        File temp = File.createTempFile(resourceName, "");
        temp.deleteOnExit();
        Files.copy(is, temp.toPath(), StandardCopyOption.REPLACE_EXISTING);
        return temp.getAbsolutePath();
    }

    static JPanel buildInfoPanel() {
        JPanel infoPanel = new JPanel();
        infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
        infoPanel.setBackground(Color.decode("#1a1a2e"));
        infoPanel.setPreferredSize(new Dimension(280, 720));
        infoPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JLabel statusTitle = new JLabel("Status");
        statusTitle.setForeground(Color.GRAY);
        statusTitle.setFont(new Font("Monospaced", Font.BOLD, 11));
        statusLabel = new JLabel("SAFE");
        statusLabel.setForeground(Color.GREEN);
        statusLabel.setFont(new Font("Monospaced", Font.BOLD, 28));

        JLabel stateTitle = new JLabel("Baby State");
        stateTitle.setForeground(Color.GRAY);
        stateTitle.setFont(new Font("Monospaced", Font.BOLD, 11));
        babyStateLabel = new JLabel("Unknown");
        babyStateLabel.setForeground(Color.WHITE);
        babyStateLabel.setFont(new Font("Monospaced", Font.BOLD, 18));

        JLabel motionTitle = new JLabel("MOTION LEVEL");
        motionTitle.setForeground(Color.GRAY);
        motionTitle.setFont(new Font("Monospaced", Font.BOLD, 11));
        motionLabel = new JLabel("0 px");
        motionLabel.setForeground(Color.WHITE);
        motionLabel.setFont(new Font("Monospaced", Font.BOLD, 18));

        JLabel lastMovTitle = new JLabel("LAST MOVEMENT");
        lastMovTitle.setForeground(Color.GRAY);
        lastMovTitle.setFont(new Font("Monospaced", Font.BOLD, 11));
        lastMovementLabel = new JLabel("No movement yet");
        lastMovementLabel.setForeground(Color.WHITE);
        lastMovementLabel.setFont(new Font("Monospaced", Font.PLAIN, 14));

        JLabel alertTitle = new JLabel("ALERT LOG");
        alertTitle.setForeground(Color.GRAY);
        alertTitle.setFont(new Font("Monospaced", Font.BOLD, 11));
        alertLogArea = new JTextArea(10, 20);
        alertLogArea.setBackground(Color.decode("#0f0f1a"));
        alertLogArea.setForeground(Color.ORANGE);
        alertLogArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        alertLogArea.setEditable(false);
        alertLogArea.setLineWrap(true);
        JScrollPane scrollPane = new JScrollPane(alertLogArea);
        scrollPane.setBackground(Color.decode("#0f0f1a"));

        infoPanel.add(statusTitle);
        infoPanel.add(statusLabel);
        infoPanel.add(javax.swing.Box.createVerticalStrut(15));
        infoPanel.add(stateTitle);
        infoPanel.add(babyStateLabel);
        infoPanel.add(javax.swing.Box.createVerticalStrut(15));
        infoPanel.add(motionTitle);
        infoPanel.add(motionLabel);
        infoPanel.add(javax.swing.Box.createVerticalStrut(15));
        infoPanel.add(lastMovTitle);
        infoPanel.add(lastMovementLabel);
        infoPanel.add(javax.swing.Box.createVerticalStrut(15));
        infoPanel.add(alertTitle);
        infoPanel.add(scrollPane);

        return infoPanel;
    }

    static void updateInfoPanel(boolean danger) {
        SwingUtilities.invokeLater(()-> {
            if(danger) {
                statusLabel.setText("DANGER");
                statusLabel.setForeground(Color.RED);
            }
            else if(currentMotionLevel > 500) {
                statusLabel.setText("WARNING");
                statusLabel.setForeground(Color.ORANGE);
            }
            else {
                statusLabel.setText("SAFE");
                statusLabel.setForeground(Color.GREEN);
            }

            motionLabel.setText(currentMotionLevel + " px");

            if(currentMotionLevel > 500) {
                lastMovementTime = System.currentTimeMillis();
            }
            if(lastMovementTime > 0) {
                long secondsAgo = (System.currentTimeMillis() - lastMovementTime) / 1000;
                if (secondsAgo < 60) {
                    lastMovementLabel.setText(secondsAgo + " seconds ago");
                } 
                else {
                    lastMovementLabel.setText((secondsAgo / 60) + " mins ago");
                }
            }

            babyStateLabel.setText(babyState);
        });
    }
    
    static void logAlert(String message) {
        String timestamp = new SimpleDateFormat("hh:mm:ss a").format(new Date());
        String entry = timestamp + " - " + message + "\n";
        alertLog.add(entry);
        SwingUtilities.invokeLater(() -> {
            alertLogArea.append(entry);
            alertLogArea.setCaretPosition(alertLogArea.getDocument().getLength());
        });
    }

    public static void start() {
        OpenCV.loadLocally();
        CascadeClassifier faceDetector;
        try {
            String facePath = extractResource("haarcascade_frontalface_default.xml");
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

        JPanel infoPanel = buildInfoPanel();

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

                currentMotionLevel = Core.countNonZero(diff);
                int motionThreshold = 500;
                boolean danger = false;
                if (currentMotionLevel > motionThreshold) {
                    danger = checkDanger(frame, diff);
                }
                updateInfoPanel(danger);

                Imgproc.line(frame, cribCorners[0], cribCorners[1], new Scalar(0, 255, 255), 2);
                Imgproc.line(frame, cribCorners[1], cribCorners[2], new Scalar(0, 255, 255), 2);
                Imgproc.line(frame, cribCorners[2], cribCorners[3], new Scalar(0, 255, 255), 2);
                Imgproc.line(frame, cribCorners[3], cribCorners[0], new Scalar(0, 255, 255), 2);

                faceDetector.detectMultiScale(grayFrame, faces, 1.1, 5, 0,
                    new Size(30, 30), new Size());
                for(Rect face : faces.toArray()){
                    Imgproc.rectangle(frame, face, new Scalar(0, 255, 0), 2);
                    babyState = "Face Detected!";
                }

                if (faces.toArray().length == 0) {
                    if (currentMotionLevel > 5000) {
                        babyState = "Active";
                    } 
                    else if (currentMotionLevel > 2500) {
                        babyState = "Stirring";
                    } 
                    else {
                        babyState = "Still";
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

            logAlert("DANGER: Baby near crib edge!");
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
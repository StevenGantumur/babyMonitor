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
import com.babyMonitor.utils.MonitorUtils;

import nu.pattern.OpenCV;

public class RoomMonitor {
    
    static BufferedImage currentImage = null;
    
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

        
        
    }
}

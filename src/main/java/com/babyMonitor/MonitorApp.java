package com.babyMonitor;

import java.awt.BorderLayout;
import java.awt.Graphics;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.opencv.core.Mat;
import org.opencv.videoio.VideoCapture;
import com.babyMonitor.utils.MonitorUtils;

public class MonitorApp {

    private final List<Monitor> modes;
    private volatile Monitor active;

    private JPanel canvas;
    private volatile BufferedImage currentImage;

    private volatile int frameCols = 0;
    private volatile int frameRows = 0;

    private volatile boolean running = true;

    public MonitorApp(List<Monitor> modes) {
        if (modes == null || modes.isEmpty()) {
            throw new IllegalArgumentException("MonitorApp needs at least one Monitor");
        }
        this.modes = modes;
    }

    public void start() {
        buildWindow();

        Thread loop = new Thread(this::runCameraLoop, "camera-loop");
        loop.setDaemon(true);
        loop.start();
    }

    private void buildWindow() {
        JFrame window = new JFrame("Baby Monitor");
        window.setLayout(new BorderLayout());
        window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        window.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                running = false;
            }
        });

        canvas = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                BufferedImage img = currentImage;
                if (img != null) {
                    g.drawImage(img, 0, 0, getWidth(), getHeight(), null);
                }
            }
        };
        canvas.setFocusable(true);

        JPanel toolbar = new JPanel();
        for (Monitor mode : modes) {
            JButton button = new JButton(mode.getName());
            // Without this the button keeps keyboard focus and the canvas never sees keys.
            button.setFocusable(false);
            button.addActionListener(e -> switchTo(mode));
            toolbar.add(button);
        }

        window.add(toolbar, BorderLayout.NORTH);
        window.add(canvas, BorderLayout.CENTER);
        window.add(MonitorUtils.buildInfoPanel(), BorderLayout.EAST);
        window.setSize(1560, 780);
        window.setVisible(true);

        wireInput();
        switchTo(modes.get(0));
    }

    private void wireInput() {
        canvas.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                canvas.requestFocusInWindow();
                Monitor mode = active;
                if (mode != null && frameCols > 0) {
                    mode.onClick(toFrameX(e.getX()), toFrameY(e.getY()));
                }
            }
        });

        canvas.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                Monitor mode = active;
                if (mode != null && frameCols > 0) {
                    mode.onMouseMove(toFrameX(e.getX()), toFrameY(e.getY()));
                }
            }
        });

        canvas.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                Monitor mode = active;
                if (mode != null) {
                    mode.onKey(e.getKeyChar());
                }
            }
        });
    }

    private int toFrameX(int panelX) {
        return (int) (panelX * ((double) frameCols / canvas.getWidth()));
    }

    private int toFrameY(int panelY) {
        return (int) (panelY * ((double) frameRows / canvas.getHeight()));
    }

    private void switchTo(Monitor next) {
        if (active == next) return;

        if (active != null) active.setActive(false);
        active = next;
        next.setActive(true);

        MonitorUtils.babyState = "Unknown";
        MonitorUtils.currentMotionLevel = 0;
        canvas.requestFocusInWindow();
    }

    private void runCameraLoop() {
        VideoCapture camera = new VideoCapture(0);
        if (!camera.isOpened()) {
            System.out.println("Could not open camera 0. Is a webcam connected?");
            return;
        }

        Mat frame = new Mat();
        while (running && camera.isOpened()) {
            camera.read(frame);
            if (frame.empty()) continue;

            frameCols = frame.cols();
            frameRows = frame.rows();

            Monitor mode = active;
            if (mode == null) continue;

            currentImage = mode.processFrame(frame);
            SwingUtilities.invokeLater(canvas::repaint);
        }
        camera.release();
    }
}

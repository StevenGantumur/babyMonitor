package com.babyMonitor;

import java.util.List;
import javax.swing.SwingUtilities;
import nu.pattern.OpenCV;

public class Main {
    public static void main(String[] args) {
        OpenCV.loadLocally();

        List<Monitor> modes = List.of(new CribMonitor(), new RoomMonitor());

        SwingUtilities.invokeLater(() -> new MonitorApp(modes).start());
    }
}

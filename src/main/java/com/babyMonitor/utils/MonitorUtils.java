package com.babyMonitor.utils;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MonitorUtils {

    public static int currentMotionLevel = 0;
    public static String babyState = "Unknown";
    public static java.util.ArrayList<String> alertLog = new java.util.ArrayList<>();
    public static javax.swing.JLabel statusLabel = null;
    public static javax.swing.JLabel babyStateLabel = null;
    public static javax.swing.JLabel motionLabel = null;
    public static javax.swing.JLabel lastMovementLabel = null;
    public static javax.swing.JTextArea alertLogArea = null;
    public static long lastMovementTime = 0;

    public static JPanel buildInfoPanel() {
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

    public static void updateInfoPanel(boolean danger) {
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

    public static void logAlert(String message) {
        String timestamp = new SimpleDateFormat("hh:mm:ss a").format(new Date());
        String entry = timestamp + " - " + message + "\n";
        alertLog.add(entry);
        SwingUtilities.invokeLater(() -> {
            alertLogArea.append(entry);
            alertLogArea.setCaretPosition(alertLogArea.getDocument().getLength());
        });
    }
}

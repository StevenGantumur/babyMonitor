package com.babyMonitor;

import java.awt.image.BufferedImage;
import org.opencv.core.Mat;

public interface Monitor {

    String getName();

    BufferedImage processFrame(Mat frame);

    void onClick(int x, int y);

    void onKey(char key);

    void setActive(boolean active);

    default void onMouseMove(int x, int y) { }
}

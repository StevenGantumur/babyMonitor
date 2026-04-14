package com.babyMonitor.utils;

import java.awt.image.BufferedImage;
import org.opencv.core.Mat;


public class ImageUtils {
    public static BufferedImage matToBufferedImage(Mat mat) {
        int width = mat.cols();
        int height = mat.rows();
        int channels = mat.channels();
        byte[] data = new byte[width * height * channels];
        mat.get(0, 0, data);

        BufferedImage image;
        if (channels == 1) {
            image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
            image.getRaster().setDataElements(0, 0, width, height, data);
        } else {
            image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            int[] pixels = new int[width * height];
            for (int i = 0; i < pixels.length; i++) {
                int b = data[i * 3] & 0xFF;
                int g = data[i * 3 + 1] & 0xFF;
                int r = data[i * 3 + 2] & 0xFF;
                pixels[i] = (r << 16) | (g << 8) | b;
            }
            image.getRaster().setDataElements(0, 0, width, height, pixels);
        }
        return image;
    }   
}

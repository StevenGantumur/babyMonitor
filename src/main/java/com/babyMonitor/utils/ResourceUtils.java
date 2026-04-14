package com.babyMonitor.utils;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import com.babyMonitor.CribMonitor;

public class ResourceUtils {
    static String extractResource(String resourceName) throws Exception {
        InputStream is = CribMonitor.class.getResourceAsStream("/" + resourceName);
        if (is == null) throw new Exception("Resource not found: " + resourceName);
        File temp = File.createTempFile(resourceName, "");
        temp.deleteOnExit();
        Files.copy(is, temp.toPath(), StandardCopyOption.REPLACE_EXISTING);
        return temp.getAbsolutePath();
    }
}

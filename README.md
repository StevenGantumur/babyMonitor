# Baby Monitor

This is a real time baby monitor application built with Java using using CV for computer vision.

# Features

* Live camera feed via webcam or iPhone (WIP for separate camera)
* Manual crib boundary calibration (WIP automatic calibration)
* Motion based danger detection
* Face Detection
* Baby emotional state tracking (WIP)
* Real time status panel (UI is not very great but also a WIP LOL)
* Time stamp logging
* Room Monitor mode (Biggest WIP)

# Tech Stack

Java 25
OpenCV 4.7.0 (via OpenPnP)
Apache Maven
Java Swing

Requires Java 25+, Maven 9+, Webcam / external camera

# How to Run

Clone repo, then run:

mvn package
java -jar target/babyMonitor-1.0.jar

# How To Use

For the Nanny Cam:

1. Select option `1` from the launch menu
2. Click the four corners of the crib in order:
   * Top-left
   * Top-right
   * Bottom-right
   * Bottom-left
3. Monitor begins automatically after calibration
4. Green boundary = baby is safe
5. Red boundary + alert = baby near crib edge

(WARNING: change threshold based on how you want sensitivity, slight movements may cause danger warning alert when unwanted)

For the Room Monitor:

WORK IN PROGRESS, but simply add polygons with same clicking structure in dangerous areas where movement detected will alarm application.

# Project Structure

src/main/java/com/babyMonitor/

├── Main.java              
├── CribMonitor.java       
├── RoomMonitor.java       
└── utils/

└── ImageUtils.java

# Road Map

1. Finish Room Monitor part
2. Emotion detection with facial/audio analysis
3. Mobile integration
4. Multiple camera support


By: Steven Gantumur


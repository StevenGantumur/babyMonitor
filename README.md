<h1 align="center">Baby Monitor</h1>

<p align="center"><b>Watches a crib, or a whole room, through a webcam and flags movement that reaches somewhere it shouldn't.</b><br>
Baby Monitor is a Java desktop app built on OpenCV. You mark out the crib or the dangerous parts of a room by clicking on the live feed, and it compares every frame against the last one to work out where things are moving. Then it decides whether that movement is a baby shifting in their sleep or a baby at the rail.</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-25-ED8B00?style=flat&logo=openjdk&logoColor=white" alt="Java 25">
  <img src="https://img.shields.io/badge/OpenCV-4.7.0-5C3EE8?style=flat&logo=opencv&logoColor=white" alt="OpenCV 4.7.0">
  <img src="https://img.shields.io/badge/Maven-3.9+-C71A36?style=flat&logo=apachemaven&logoColor=white" alt="Maven 3.9+">
  <img src="https://img.shields.io/badge/Swing-desktop%20UI-555555?style=flat" alt="Java Swing">
  <img src="https://img.shields.io/badge/Haar-cascades-9ACD32?style=flat" alt="Haar cascades">
</p>

<!-- Screenshot goes here once the app has run on a live camera:
<p align="center"><img src="assets/media/hero.png" width="820" alt="Baby Monitor: Crib Monitor calibrated with a green box around the crib, status panel reading SAFE"></p>
-->

<p align="center">Built by <a href="https://github.com/StevenGantumur">Steven Gantumur</a>.<br>
<sub>A personal project, not a safety device. It doesn't replace watching your kid.</sub></p>

## How it works

1. **Capture.** One camera thread reads frames from the webcam and hands each one to whichever mode is active.
2. **Diff.** The frame goes to grayscale and is compared pixel by pixel with the previous one. Any pixel whose brightness changed by more than 30 counts as motion, which turns the frame into a black-and-white map of what moved.
3. **Decide.** The active mode checks *where* the motion is. The Crib Monitor asks whether it reached the crib's edge. The Room Monitor asks whether it landed inside a zone you drew, and whether a person is there too.
4. **Show.** The mode draws its verdict on the frame (green for safe, red for danger), updates the status panel, and writes a timestamped line to the alert log the moment something goes wrong.

Babies move all night while they sleep, so an alert on any motion would go off constantly. Both modes ask *where* the motion happened instead.

## Two modes, one window

| Mode | You draw | It trips when | Extra |
|---|---|---|---|
| **Crib Monitor** | The crib's 4 corners | Motion reaches the outer 10% of the crib | Face detection and a rough "baby state" |
| **Room Monitor** | Any number of polygons, any shape | 100+ moving pixels land inside a zone | Body detection, to tell "something moved" from "someone is there" |

A toolbar at the top switches between them on the same live feed. The panel on the right shows the status (SAFE, WARNING or DANGER), the baby state, the motion level in pixels, how long ago anything last moved, and the alert log.

## Crib Monitor: near the edge, not just moving

You click the crib's four corners. The monitor builds a box that spans them and treats the outer 10% of that box as the danger band. On every frame:

- Under 500 moving pixels, there's nothing worth checking.
- Over that, it takes the bounding box of everything that moved. If that box reaches into the danger band, the crib outline turns red and the frame reads **DANGER - Baby near edge!** Otherwise it stays green.

A baby rolling around the middle of the mattress is fine. Movement that reaches the rails trips it.

On every frame it also runs OpenCV's frontal-face Haar cascade. A detected face gets a green box and the baby state reads *Face Detected!* With no face in view, the state comes from the amount of motion instead: **Active** above 5,000 pixels, **Stirring** above 2,500, **Still** below that.

## Room Monitor: zones you draw yourself

Some dangers aren't in the crib: the stairs, an outlet, a bookshelf that can tip. In the Room Monitor you outline each one as a polygon with as many corners as it needs. A gray guide line follows the cursor while you draw.

Each zone is checked on its own. The zone's polygon is filled into a mask, the mask is combined with the motion map, and whatever moving pixels are left are counted. Past 100, the zone trips and its outline turns red.

Motion alone could be a curtain, a shadow or the dog, so a tripped zone gets a second check. OpenCV's full-body cascade runs on every frame, and if a detected body overlaps the zone, the alert escalates:

| Inside the zone | On screen | In the alert log |
|---|---|---|
| Motion only | `WATCHING: Zone 2` | `Watching: motion in Zone 2` |
| Motion and a detected body | `DANGER: Zone 2` | `DANGER: person in Zone 2` |

A body counts as inside if **any one corner** of its bounding box falls in the polygon. That's deliberately loose. A body clipping the edge of the stairs counts, because missing a real intrusion is worse than an occasional false alarm.

## Switching modes without restarting

The first version was two separate programs behind a text menu. You picked 1 or 2 at launch, and each monitor opened its own window, its own camera and its own loop. Switching modes meant quitting and starting again.

Now one shell, [`MonitorApp`](src/main/java/com/babyMonitor/MonitorApp.java), owns the window, the camera, the frame loop, the toolbar and the input wiring. The two monitors are interchangeable strategies behind one interface, [`Monitor`](src/main/java/com/babyMonitor/Monitor.java). `MonitorApp` never mentions `CribMonitor` or `RoomMonitor` by name. `Main` builds the list of modes and hands it over, so a third mode would be one new class and one line in `Main`.

| `Monitor` method | Called when |
|---|---|
| `getName()` | Once, to label the mode's toolbar button |
| `setActive(boolean)` | The user switches to the mode, or away from it |
| `processFrame(Mat)` | Every frame, on the camera thread. Returns the annotated image to draw |
| `onClick(x, y)` | A click on the feed, already converted to frame coordinates |
| `onMouseMove(x, y)` | The cursor moves over the feed. Optional, does nothing by default |
| `onKey(char)` | A key press while the feed has focus |

A few decisions that come with it:

- **Calibration survives a switch.** Leave the Room Monitor with three zones drawn, check the crib, come back, and the zones are still there.
- **The last frame doesn't survive.** Switching to a mode throws away its previous frame. Otherwise the first comparison after coming back would be against a frame from whenever you left, the whole picture would read as motion, and you'd get a false DANGER the instant you switched.
- **Clicks are converted to frame coordinates.** The feed stretches to fill the window, so a click is scaled by frame size over panel size before the mode sees it. Calibration points land where you clicked, whatever the window size.
- **The toolbar buttons can't take focus.** A clicked button normally keeps keyboard focus, which means Enter and `r` would go to the button and the active mode would never see them.

## What broke along the way

**The alert log fired about 30 times a second.** The log was written on every frame a zone stayed in danger, so a baby touching the rail for two seconds produced around 60 identical lines. Alerts now fire on the rising edge only. [`DangerZone`](src/main/java/com/babyMonitor/utils/DangerZone.java) remembers whether it was in danger on the last frame, and `isNewDanger` returns true only on the switch from safe to danger. That memory lives on the zone rather than on the monitor because the Room Monitor has any number of zones, and each one needs its own. The Crib Monitor got the fix for free, since its crib is a `DangerZone` too.

**The body detector was looking for faces.** `RoomMonitor` loaded `haarcascade_frontalface_default.xml` into a field named `bodyDetector`. Nothing failed: the cascade was valid, it loaded and it ran. Its detections weren't used for anything yet, so nothing looked wrong. It now loads `haarcascade_fullbody.xml`, which had been sitting in `resources/` the whole time, and those detections now drive the WATCHING / DANGER split.

**OpenCV can't read a cascade from inside the JAR.** `CascadeClassifier` only takes a file path, and the cascades are packed inside the JAR, where they aren't files. [`ResourceUtils`](src/main/java/com/babyMonitor/utils/ResourceUtils.java) copies each one to a temp file the first time it's needed and deletes it on exit. `RoomMonitor` used to carry its own copy of that method, and both monitors now share the one in `ResourceUtils`.

## Known issues

**Calibration races the camera loop.** Clicks and key presses arrive on Swing's event thread and add points to a zone, while the camera thread loops over those same points to draw them. Click at the wrong moment during calibration and Java throws a `ConcurrentModificationException`. This bug was already there before the refactor.

**The crib check looks at all motion in the frame, not just the crib.** The bounding box is taken over every moving pixel in the picture, so a parent walking past behind the crib stretches it past the rails and trips DANGER. The box spanning the four corners is also axis-aligned, so a crib filmed at an angle gets a box bigger than the crib. Masking the motion map to the crib polygon first, the way the Room Monitor already does for its zones, would fix both.

**The status panel can't tell WATCHING from DANGER.** Any tripped zone turns the panel to DANGER, even when the overlay says WATCHING. The escalation shows on the feed and in the log, but not in the panel.

**The body cascade was trained on pedestrians.** `haarcascade_fullbody.xml` was trained on upright people walking, from the MIT CBCL pedestrian set, and the minimum detection size here is a tall 50×100 box. A crawling baby is exactly what it's worst at. For now the escalation is better at catching an adult or an older sibling walking into a zone than the baby.

## System design

```
┌──────────────┐  Mat   ┌──────────────────────┐  processFrame   ┌──────────────────┐
│  Webcam      │ ─────> │  MonitorApp          │ ──────────────> │  active Monitor  │
│  index 0     │        │  camera-loop thread  │ <────────────── │  CribMonitor or  │
└──────────────┘        └──────────┬───────────┘  BufferedImage  │  RoomMonitor     │
                                   │ repaint                     └───┬──────────▲───┘
                                   v                                 │          │
                        ┌──────────────────────┐   status, alerts    │          │
                        │  Swing window        │ <───────────────────┘          │
                        │  canvas, toolbar,    │                                │
                        │  status panel        │ ───────────────────────────────┘
                        └──────────────────────┘   clicks, keys (event thread)
```

Two threads run the app. The camera thread reads a frame, passes it to the active mode, and stores the image it gets back. Swing's event thread paints that image and delivers clicks and key presses to the same active mode. The active mode is one shared reference, so switching modes in the toolbar redirects both threads at once.

## Stack

| What | How |
|---|---|
| Language | Java 25 |
| Computer vision | OpenCV 4.7.0, through the OpenPnP Java package, which bundles the native libraries |
| Motion | Frame differencing: `absdiff`, `threshold`, `countNonZero` |
| Zones | Polygon masks with `fillPoly`, point-in-polygon with `pointPolygonTest` |
| Detection | Haar cascades: frontal face for the crib, full body for the room |
| UI | Java Swing |
| Build | Maven, with the Shade plugin producing one runnable JAR |

## Run it

Needs Java 25+, Maven 3.9+ and a webcam.

```bash
git clone https://github.com/StevenGantumur/babyMonitor.git
cd babyMonitor
mvn package
java -jar target/babyMonitor-1.0.jar
```

The build produces one JAR with OpenCV's native libraries inside, so there's nothing else to install. At startup, `OpenCV.loadLocally()` unpacks the right native library for your OS.

The app always opens **camera index 0**, which is whichever camera your OS lists first. On a Mac that can be an iPhone through Continuity Camera. On macOS, allow camera access for your terminal the first time it asks. If no camera opens, the window still appears and the terminal prints `Could not open camera 0. Is a webcam connected?`

## Use it

**Crib Monitor**

1. Click the crib's four corners in order: top-left, top-right, bottom-right, bottom-left. The prompt at the top of the feed tells you which one is next.
2. Monitoring starts on the fourth click.
3. A green box means safe. A red box and **DANGER - Baby near edge!** mean motion reached the rails.

**Room Monitor**

1. Click the corners of a dangerous area, as many as its shape needs.
2. Press **Enter** to close that zone and start the next one.
3. Press **Enter** again, on an empty zone, to finish.
4. Quiet zones stay green. A zone with motion turns red and is labeled WATCHING, or DANGER if a person is detected in it.

| Input | Crib Monitor | Room Monitor |
|---|---|---|
| Click | Place the next crib corner | Add a corner to the current zone |
| Enter | | Close the zone. Press again on an empty zone to finish |
| `r` | Clear the corners and recalibrate | Clear every zone and recalibrate |

Clicking the feed also gives it keyboard focus, so click the picture once if Enter or `r` doesn't seem to do anything.

## Tuning

Sensitivity is set by constants in the code. If small movements set off alerts you don't want, these are the numbers to change.

| Setting | Where | Value | What it does |
|---|---|---|---|
| `DIFF_THRESHOLD` | both monitors | `30` | How much a pixel's brightness has to change between frames to count as motion |
| `MOTION_THRESHOLD` | `CribMonitor` | `500` px | Total motion needed before the edge check runs at all |
| Edge margin | `CribMonitor.checkDanger` | 10% | How far in from the crib's edge the danger band reaches |
| `ZONE_SENSITIVITY` | `RoomMonitor` | `100` px | Motion inside a zone before it trips. One value for every zone |
| Active / Stirring | `CribMonitor` | `5000` / `2500` px | Where the baby state labels change when no face is found |
| WARNING | `MonitorUtils` | `500` px | The panel shows WARNING above this when nothing is in danger |

## Project structure

| File | What's in it |
|---|---|
| [`Main.java`](src/main/java/com/babyMonitor/Main.java) | Loads OpenCV, builds the list of modes, starts the app |
| [`MonitorApp.java`](src/main/java/com/babyMonitor/MonitorApp.java) | The shell: window, toolbar, camera thread, input routing and mode switching |
| [`Monitor.java`](src/main/java/com/babyMonitor/Monitor.java) | The interface every mode implements |
| [`CribMonitor.java`](src/main/java/com/babyMonitor/CribMonitor.java) | Four-corner calibration, the near-edge check, face detection and baby state |
| [`RoomMonitor.java`](src/main/java/com/babyMonitor/RoomMonitor.java) | Polygon zone calibration, per-zone motion and body escalation |
| [`utils/DangerZone.java`](src/main/java/com/babyMonitor/utils/DangerZone.java) | One polygon: its points, its motion test, body overlap and rising-edge alert state |
| [`utils/MonitorUtils.java`](src/main/java/com/babyMonitor/utils/MonitorUtils.java) | The status panel and the timestamped alert log |
| [`utils/ImageUtils.java`](src/main/java/com/babyMonitor/utils/ImageUtils.java) | Converts OpenCV's BGR `Mat` into an RGB Swing `BufferedImage` |
| [`utils/ResourceUtils.java`](src/main/java/com/babyMonitor/utils/ResourceUtils.java) | Copies the bundled cascades out of the JAR to temp files |
| [`src/main/resources/`](src/main/resources/) | The two Haar cascades: frontal face and full body |

## What it is and isn't

**It is** a working computer-vision pipeline in plain Java: frame differencing, polygon masks and Haar cascade detection, running in a Swing app where two monitoring modes share one camera through a common interface.

**It isn't** a baby monitor to rely on, yet:
- It's a personal project, not a safety device.
- It runs on the computer the camera is plugged into. There's no remote viewing, so someone has to be at that screen.
- Alerts are text in the log. Nothing makes a sound.
- It only opens camera index 0.
- Calibration lives in memory and is lost when the app closes.
- "Baby state" is a motion level with a label on it. Emotion tracking isn't built.
- The single-window version builds cleanly but hasn't been run on a live camera yet, because the PC it was written on has no webcam. The next test is on a MacBook.

## Where this is going

There are two realistic paths from here to something a parent could actually use:

1. **An installer.** `jpackage` can bundle the JRE, the JAR and OpenCV's native libraries into an `.msi`, `.dmg` or `.deb`, so someone who isn't a developer can install it. It's the smallest step, but it's still one computer in one room.
2. **A real baby monitor.** A Raspberry Pi in the nursery runs capture and detection headless and streams to a phone on the home network. That means splitting the app into a capture-and-detect half and a viewer half, so it's more than a deployment. The cheap first step is running the current app on a Pi 5 with a USB webcam. The webcam has to be USB because OpenCV's `VideoCapture(0)` finds a USB webcam with no code changes, while the Pi camera module goes through libcamera.

**Road map**

1. Run the single-window app on a live camera, MacBook first
2. Fix the known issues above, starting with the calibration race
3. Per-zone sensitivity in the UI, instead of one hardcoded 100
4. Emotion detection from facial and audio analysis
5. A mobile viewer
6. Multiple cameras

## Credits

Computer vision by [OpenCV](https://opencv.org), packaged for Java by [OpenPnP](https://github.com/openpnp/opencv). The frontal face cascade is by Rainer Lienhart (Intel License Agreement). The full body cascade is by Hannes Kruppa and Bernt Schiele, ETH Zurich. Both ship with OpenCV, and their license headers are kept intact in [`src/main/resources/`](src/main/resources/).

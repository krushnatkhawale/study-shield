# Interrupter - TV App (Background Listener)

This is the receiver application for the Interrupter system. It runs as a persistent **Foreground Service** that listens for lock commands from the mobile app.

## Features
* **Auto-Start on Boot**: Starts automatically when the TV reboots.
* **Aggressive Interruption**: Uses high-priority notifications and `fullScreenIntent` to pull the lock screen to the front even if you are watching other apps.
* **Custom Duration**: Displays the lock message for the exact time sent from the mobile app.
* **Auto-Minimize**: Returns to the background automatically once the timer ends.

## Installation & Setup
1. **Deploy**: Select the `tv` configuration in Android Studio and click **Run**.
2. **Permissions**: 
   - When launched, the app will ask for **"Display over other apps"**. Please enable this; it is required to allow the app to interrupt other running applications.
3. **IP Discovery**: The TV's IP address is displayed in large text when the app is first opened.

## Requirements
* Android TV (API 23+)
* Network access on port 8888.

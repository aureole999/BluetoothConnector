# Bluetooth Connector

Bluetooth Connector allows you to easily manage Bluetooth connections on your Android device. Its primary use case is to serve as a trigger for iPhone automation.

## Core Feature: Trigger iPhone Automation

The main purpose of this app is to enable cross-device automation. By connecting your Android device to an iPhone via Bluetooth, you can trigger the **"When Connected to Bluetooth Device"** automation in the iOS Shortcuts app.

This allows you to perform actions on your iPhone (e.g., enable Hotspot) directly from your Android device.

### Features

- **Quick Connection**: Connect to paired Bluetooth devices with a single tap.
- **Quick Settings Tile**: Dedicated Quick Settings (QS) tile for one-tap connection from the notification shade.
- **Auto Disconnect**: Option to automatically disconnect 3 seconds after a successful connection (ideal for triggering automation without maintaining a permanent link).
- **Multi-language Support**: Fully localized for English, Japanese (日本語), and Simplified Chinese (简体中文).
- **Material You Design**: Modern UI that adapts to your device's theme.

## How to Use

1. Pair your Android device with your iPhone (or other target device) via system settings.
2. Open Bluetooth Connector and select the device you want to connect to.
3. (Optional) Enable "Auto Disconnect" in settings if you only need a momentary connection to trigger an event.
4. **For Quick Access**: Add the "BT Connect" tile to your Quick Settings panel. Long-press the tile to configure the target device.

## iOS Setup (for Automation)

1. Open the **Shortcuts** app on your iPhone.
2. Go to the **Automation** tab.
3. Create a new Personal Automation.
4. Select **Bluetooth** -> **When my iPhone connects to [Your Android Device]**.
5. Add your desired actions (e.g., "Set Personal Hotspot" or "Run Shortcut").

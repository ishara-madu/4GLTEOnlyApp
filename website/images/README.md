# Screenshots Folder

Place your app screenshots here to display them on the landing page.

## Required Screenshots

Add these 4 screenshots to showcase the app:

| Filename | Description | Recommended Size |
|----------|-------------|------------------|
| `screenshot-home.png` | Main dashboard showing signal metrics | 1080 x 1920 |
| `screenshot-analytics.png` | Analytics tab with charts | 1080 x 1920 |
| `screenshot-speedtest.png` | Speed test in progress or results | 1080 x 1920 |
| `screenshot-settings.png` | Settings page | 1080 x 1920 |

## How to Capture Screenshots

### From Android Device
1. Open the app and navigate to each screen
2. Press and hold Power + Volume Down simultaneously
3. Screenshots are saved to your gallery

### From Android Studio Emulator
1. Run the app on an emulator
2. Use the camera icon in the emulator toolbar
3. Or use the command: `adb exec-out screencap -p > screenshot.png`

### From Command Line
```bash
# Connect your device and run:
adb exec-out screencap -p > screenshot-name.png
```

## Tips for Great Screenshots

1. **Use a clean device** - No notification bar items, minimal status bar
2. **Show realistic data** - Include some signal readings and values
3. **Use dark mode** - Dark theme looks more modern in screenshots
4. **Frame it well** - Show the relevant UI elements clearly
5. **Use a phone frame** - Optional: Use a mockup tool to add a phone frame

## Optional: Add a Logo

Add your app icon/logo as `logo.png` (recommended size: 512x512 pixels).

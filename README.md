# Darling

A modern anime tracking and streaming app for Android with AniList integration.

![Platform](https://img.shields.io/badge/Platform-Android-green)
![MinSDK](https://img.shields.io/badge/MinSDK-36-blue)
![Kotlin](https://img.shields.io/badge/Kotlin-100%-purple)

## Features

### Anime Tracking
- **AniList OAuth Login** - Secure authentication with your AniList account
- **Personal Lists** - View your Watching, Planning, Completed, Dropped, and Paused anime
- **Progress Tracking** - Automatically update your watch progress
- **Explore** - Discover trending, seasonal, and top-rated anime

### Streaming
- **Built-in Video Player** - Stream anime directly in the app
- **Episode Navigation** - Seamlessly switch between episodes
- **Player Gestures**
  - Double-tap left/right to skip ±5 seconds
  - Tap to show/hide controls
  - Seek bar with time display
  - Aspect ratio toggle (Fit, Stretch, 16:9)
- **Subtitle Support** - VTT subtitle tracks

### UI/UX
- **Material Design 3** - Modern, clean interface
- **OLED Dark Mode** - Pure black theme for AMOLED screens
- **Swipe Navigation** - Smooth page transitions
- **Responsive Layouts** - Works on phones and tablets

## Screenshots

| Home Screen | Explore Screen | Player |
|-------------|----------------|--------|
| *Coming soon* | *Coming soon* | *Coming soon* |

## Requirements

- Android 16.0 or higher
- AniList account (optional, for tracking features)

## Installation

### From Release
1. Download the latest APK from [Releases](https://github.com/Suntrax/darling/releases)
2. Enable "Install from unknown sources" in your Android settings
3. Open the APK and install

### From Source
```bash
# Clone the repository
git clone https://github.com/Suntrax/darling.git

# Open in Android Studio
# Build and run on your device/emulator
```

## Configuration

### AniList OAuth Setup
The app comes pre-configured with AniList OAuth credentials. If you want to use your own:

1. Go to [AniList Developer Settings](https://anilist.co/settings/developer)
2. Create a new client
3. Set the redirect URL to: `animescraper://success`
4. Update `clientId` in `MainViewModel.kt`

### Stream Provider
The app uses a custom scraper service. Update the base URL in `AniwatchService.kt`:

```kotlin
private const val SCRAPER_BASE_URL = "http://your-server:4000"
```

## Tech Stack

| Category | Technology |
|----------|------------|
| Language | Kotlin |
| UI Framework | Jetpack Compose |
| Architecture | MVVM |
| Dependency Injection | Manual |
| Networking | OkHttp, Kotlinx Serialization |
| Video Player | Media3 ExoPlayer |
| Async | Kotlin Coroutines, Flow |
| Data Storage | SharedPreferences |

## Project Structure

```
app/src/main/java/com/blissless/anime/
├── MainActivity.kt           # Main entry point, navigation
├── MainViewModel.kt          # State management, API calls
├── AnimeMedia.kt             # Data models
├── api/
│   ├── AniListApi.kt         # AniList GraphQL API
│   └── AniwatchService.kt    # Stream provider API
├── ui/
│   ├── theme/
│   │   └── AppTheme.kt       # Material 3 theming
│   └── screens/
│       ├── HomeScreen.kt     # User's anime lists
│       ├── ExploreScreen.kt  # Discovery page
│       ├── PlayerScreen.kt   # Video player
│       └── SettingsScreen.kt # App settings
└── data/
    └── models/               # Data classes
```

## API Reference

### AniList API
The app uses AniList's GraphQL API for:
- User authentication (OAuth 2.0 implicit flow)
- Fetching user anime lists
- Updating watch progress
- Searching and discovering anime

[AniList API Documentation](https://docs.anilist.co/)

## Roadmap

- [ ] Search functionality
- [ ] Anime detail page
- [ ] Download for offline viewing
- [ ] Watch history
- [ ] Custom tracking percentage
- [ ] Notifications for new episodes
- [ ] Widget support

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## License

This project is for educational purposes. Please respect the terms of service of AniList and any streaming providers used.

## Acknowledgments

- [AniList](https://anilist.co/) - Anime tracking platform
- [Jetpack Compose](https://developer.android.com/jetpack/compose) - Modern Android UI toolkit
- [Media3/ExoPlayer](https://developer.android.com/media/media3) - Video playback

---

Made with ❤️

<div align="center">

# 🌸 Darling

**A modern anime tracking and streaming app for Android with AniList integration.**

![Platform](https://img.shields.io/badge/platform-android-green)
![MinSDK](https://img.shields.io/badge/minSDK-26-blue)
![Kotlin](https://img.shields.io/badge/kotlin-100%25-purple)

</div>

---

## ✨ Features

### 📊 Anime Tracking
* **AniList OAuth Login** - Secure authentication with your AniList account.
* **Personal Lists** - View your Watching, Planning, Completed, Dropped, and Paused anime.
* **Progress Tracking** - Automatically update your watch progress.
* **Explore** - Discover trending, seasonal, and top-rated anime.
* **Smart Bookmarks** - Add/remove anime from lists with visual feedback and animations.
* **Quick Status Management** - Change anime status directly from cards.

### 🧭 Explore & Discovery
* **Featured Carousel** - Auto-scrolling showcase of currently airing popular anime.
* **This Season** - See what's trending right now.
* **Top Rated Series** - Browse highest-scoring TV anime.
* **Top Rated Movies** - Discover top anime films.
* **Episode Badges** - See episode counts for all anime, including long-running series.
* **Search** - Find any anime with debounced, real-time search and detail previews.

### 📺 Streaming
* **Built-in Video Player** - Stream anime directly in the app.
* **On-Demand Scraping** - Episodes fetched dynamically when navigating (no caching).
* **Episode Navigation** - Previous/Next buttons to switch episodes seamlessly.

> **Player Gestures:**
> * Double-tap left/right to skip ±5 seconds.
> * Tap to show/hide controls.
> * Seek bar with time display.
> * Aspect ratio toggle (Fit, Stretch, 16:9).
> * Subtitle Support - VTT subtitle tracks.

### 🎨 UI/UX
* **Material Design 3** - Modern, clean interface.
* **OLED Dark Mode** - Pure black theme for AMOLED screens.
* **Swipe Navigation** - Smooth page transitions.
* **Responsive Layouts** - Works on phones and tablets.
* **Consistent Styling** - Unified button and badge designs across all screens.
* **Smooth Animations** - Visual feedback for all user interactions.

---

## 📱 Screenshots

| Home Screen | Explore Screen | Player |
|:---:|:---:|:---:|
| ![Home](screenshots/home.png) | ![Explore](screenshots/explore.png) | ![Player](screenshots/player.png) |

---

## ⚙️ Requirements
* Android 8.0 or higher (Optimized for Android 16)
* AniList account *(Optional, for tracking features)*

---

## 🚀 Installation

### From Release
1. Download the latest APK from [Releases](https://github.com/Suntrax/darling/releases).
2. Enable **"Install from unknown sources"** in your Android settings.
3. Open the APK and install.

### From Source
```bash
# Clone the repository
git clone [https://github.com/Suntrax/darling.git](https://github.com/Suntrax/darling.git)

# Open in Android Studio
# Build and run on your device or emulator
```

---

## 🛠 Configuration

### AniList OAuth Setup
The app comes pre-configured with AniList OAuth credentials. If you want to use your own:
1. Go to your **AniList Developer Settings**.
2. Create a new client.
3. Set the redirect URL to: `animescraper://success`
4. Update `clientId` in `MainViewModel.kt`.

### Stream Provider
The app uses a custom scraper service. More info can be found at: `aniwatch-api`.

---

## 💻 Tech Stack

| Category | Technology |
|---|---|
| **Language** | Kotlin |
| **UI Framework** | Jetpack Compose |
| **Architecture** | MVVM |
| **Dependency Injection** | Manual |
| **Networking** | OkHttp, Kotlinx Serialization |
| **Video Player** | Media3 ExoPlayer |
| **Async** | Kotlin Coroutines, Flow |
| **Data Storage** | DataStore Preferences |

---

## 📁 Project Structure

```text
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

---

## 📝 Changelog

**v1.1**
* Enhanced explore page with featured carousel and new sections.
* Working remove anime functionality.
* Episode badges now show for all anime.
* Improved episode counter for long-running series (1000+ episodes).
* Search with debounced input.
* Animated bookmark toggle with toast notifications.
* On-demand episode scraping for navigation.
* Consistent button and badge styling.
* Various bug fixes and UI improvements.

---

## 🔗 API Reference

**AniList API** The app uses AniList's GraphQL API for:
* User authentication (OAuth 2.0 implicit flow)
* Fetching user anime lists
* Updating watch progress and status
* Adding/removing anime from lists
* Searching and discovering anime

📚 [AniList API Documentation](https://anilist.gitbook.io/anilist-apiv2-docs/)

---

## 🤝 Contributing
Contributions are welcome! Please feel free to submit a Pull Request.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

---

## 📄 License
This project is for educational purposes. Please respect the terms of service of AniList and any streaming providers used.

---

## 🙏 Acknowledgments
* [AniList](https://anilist.co) - Anime tracking platform
* [Jetpack Compose](https://developer.android.com/jetpack/compose) - Modern Android UI toolkit
* [Media3/ExoPlayer](https://developer.android.com/guide/topics/media/media3) - Video playback

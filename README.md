<div align="center">

# 🌸 Darling

**A modern anime tracking and streaming app for Android with AniList integration.**

![Platform](https://img.shields.io/badge/Platform-Android-green?style=for-the-badge&logo=android)
![Min SDK](https://img.shields.io/badge/MinSDK-26-blue?style=for-the-badge)
![Kotlin](https://img.shields.io/badge/Kotlin-100%25-purple?style=for-the-badge&logo=kotlin)

</div>

---

## ✨ Features

### 📊 Anime Tracking
- 🔐 **AniList OAuth Login** – Secure authentication with your AniList account.
- 📚 **Personal Lists** – View your Watching, Planning, Completed, Dropped, and Paused anime.
- 📈 **Progress Tracking** – Automatically update your watch progress and sync with AniList.
- 🔍 **Explore** – Discover trending, seasonal, and top-rated anime.
- 🔖 **Smart Bookmarks** – Add/remove anime from lists with visual feedback and animations.
- ⚡ **Quick Status Management** – Change anime status directly from cards.

### 🧭 Explore & Discovery
- 🎞 **Featured Carousel** – Auto-scrolling showcase of currently airing popular anime.
- 🌸 **This Season** – See what's trending right now in the anime community.
- ⭐ **Top Rated Series** – Browse highest-scoring TV anime of all time.
- 🎬 **Top Rated Movies** – Discover top-rated anime films and features.
- 🔢 **Episode Badges** – High-visibility episode counts for all series, including 1000+ episode titles.
- 🔎 **Search** – Find any anime with debounced, real-time search and detail previews.

### 📺 Streaming & Video Player
- ▶️ **Built-in Player** – Stream anime directly in the app using Media3 ExoPlayer.
- 🌐 **Multi-Server Support** – Choose between different streaming sources for the best speed.
- 🗣 **SUB/DUB Selection** – Switch between Subtitled and Dubbed versions on the fly.
- ⚡ **Pre-fetched Streams** – Episodes fetched dynamically for seamless playback.
- 🔁 **Auto Server Fallback** – Automatically switch to a working server if the current one fails.
- ⏭ **Episode Navigation** – Seamless "Previous" and "Next" controls within the player.

> #### 🎮 Player Gestures & Controls
> - **Skip Time:** Double-tap left/right to skip ±5 seconds.
> - **Visibility:** Single tap to show or hide playback controls.
> - **Precision Seek:** Interactive seek bar with real-time time display.
> - **Aspect Ratio:** Toggle between Fit, Stretch, and 16:9 modes.
> - **Subtitles:** Native support for VTT subtitle tracks.

### 🎨 UI/UX
- 🎨 **Material Design 3** – Modern, clean interface following the latest Android standards.
- 🌑 **OLED Dark Mode** – Pure black theme designed to save battery on AMOLED screens.
- 🔄 **Swipe Navigation** – Smooth page transitions and intuitive gestures.
- 📱 **Responsive Layouts** – Fully optimized for both phone and tablet form factors.
- 🎯 **Consistent Styling** – Unified button and badge designs across the entire app.
- ✨ **Smooth Animations** – Visual feedback for all user interactions and state changes.

---

## 📱 Screenshots

| Home List | Explore Discovery | Video Player |
|:---:|:---:|:---:|
| ![Home](screenshots/home.png) | ![Explore](screenshots/explore.png) | ![Player](screenshots/player.png) |

---

## ⚙️ Requirements & Setup

- **Android Version:** 8.0 (API 26) or higher (Optimized for Android 16).
- **AniList Account:** Optional (required for tracking and list features).

### 🚀 Installation
1. Download the latest APK from the [Releases](https://github.com/Suntrax/darling/releases) page.
2. Enable **"Install from unknown sources"** in your Android settings.
3. Open the APK and install.

### 🛠 Developer Configuration
To use your own **AniList OAuth** credentials:
1. Visit [AniList Developer Settings](https://anilist.co/settings/developer) and create a new client.
2. Set the Redirect URL to: `animescraper://success`
3. Update the `clientId` in `MainViewModel.kt`.

---

## 💻 Tech Stack

| Category | Technology |
|:---|:---|
| **Language** | Kotlin |
| **UI Framework** | Jetpack Compose (Material 3) |
| **Architecture** | MVVM (Model-View-ViewModel) |
| **Networking** | OkHttp, Kotlinx Serialization |
| **Video Player** | Media3 ExoPlayer |
| **Async** | Kotlin Coroutines & Flow |
| **Data Storage** | SharedPreferences / DataStore Preferences |

---

## 📁 Project Structure

<pre>
app/src/main/java/com/blissless/anime/
├── MainActivity.kt           # Main entry point & navigation
├── MainViewModel.kt          # State management & API calls
├── AnimeMedia.kt             # Core data models
├── api/
│   ├── AniListApi.kt         # AniList GraphQL API implementation
│   └── AniwatchService.kt    # Stream provider & scraper logic
├── ui/
│   ├── theme/                # Material 3 color schemes & typography
│   └── screens/              # UI Composables (Home, Explore, Player, Settings)
└── data/
    └── models/               # Kotlin data classes
</pre>

---

## 📝 Changelog

### 🚀 v1.2 (Current)
- **Player Upgrades:** Added Server Selection and SUB/DUB support.
- **Reliability:** Implemented auto-server fallback and pre-fetched stream logic.
- **UX:** Instant login state restoration and improved tracking slider.
- **Stability:** Enhanced error handling and API connectivity patches.

### ✨ v1.1
- **Discovery:** Redesigned Explore page with Featured Carousel and new sections.
- **Functionality:** Added working "Remove from list" feature.
- **UI:** Added episode badges, animated bookmarks, and debounced search.
- **Fixes:** Improved episode counters for long-running series (1000+ episodes).

---

## 🔗 API Reference
The app uses the **AniList GraphQL API** for:
- User authentication (OAuth 2.0 implicit flow)
- Fetching and updating user anime lists
- Real-time search and discovery

📚 [AniList API Documentation](https://anilist.gitbook.io/anilist-apiv2-docs/)

---

## 🤝 Contributing
1. **Fork** the repository.
2. **Create** your feature branch (`git checkout -b feature/AmazingFeature`).
3. **Commit** your changes (`git commit -m 'Add some AmazingFeature'`).
4. **Push** to the branch (`git push origin feature/AmazingFeature`).
5. **Open** a Pull Request.

---

<div align="center">

**Made with ❤️ for the Anime Community**

</div>
<div align="center">

# 🌸 Darling

A modern anime tracking and streaming app for Android with AniList integration.

![Platform](https://img.shields.io/badge/Platform-Android-green)
![Min SDK](https://img.shields.io/badge/MinSDK-26-blue)
![Kotlin](https://img.shields.io/badge/Kotlin-100%25-purple)

---

## ✨ Features

### 📊 Anime Tracking
- 🔐 **AniList OAuth Login** – Secure authentication with your AniList account  
- 📚 **Personal Lists** – Watching, Planning, Completed, Dropped, Paused  
- 📈 **Progress Tracking** – Automatically update watch progress  
- 🔍 **Explore** – Discover trending, seasonal, and top-rated anime  
- 🔖 **Smart Bookmarks** – Animated add/remove with visual feedback  
- ⚡ **Quick Status Management** – Update status directly from cards  

---

### 🧭 Explore & Discovery
- 🎞 **Featured Carousel** – Auto-scrolling popular anime  
- 🌸 **This Season** – Currently trending anime  
- ⭐ **Top Rated Series** – Highest-scoring TV anime  
- 🎬 **Top Rated Movies** – Best anime films  
- 🔢 **Episode Badges** – See episode counts for all anime  
- 🔎 **Search** – Debounced real-time search with previews  

---

### 📺 Streaming
- ▶️ **Built-in Player** – Watch directly in-app  
- 🌐 **Multi-Server Support** – Choose streaming sources  
- 🗣 **SUB/DUB Selection** – Switch when available  
- ⚡ **Pre-fetched Streams** – Seamless playback  
- 🔁 **Auto Server Fallback** – Switch if a server fails  
- ⏭ **Episode Navigation** – Previous/Next controls  

#### 🎮 Player Gestures
- ⏪ Double-tap left/right → ±5 seconds  
- 👆 Tap → show/hide controls  
- 🎚 Seek bar with time display  
- 📐 Aspect ratio toggle (Fit / Stretch / 16:9)  
- 💬 Subtitle support (VTT)

---

### 🎨 UI/UX
- 🎨 **Material Design 3**  
- 🌑 **OLED Dark Mode**  
- ⚡ **Instant Login State**  
- 🔄 **Swipe Navigation**  
- 📱 **Responsive Layouts**  
- 🎯 **Consistent Styling**  
- ✨ **Smooth Animations**  

---

## 📱 Screenshots

| Home | Explore | Player |
|------|--------|--------|
| Home | Explore | Player |

---

## ⚙️ Requirements

- Android 8.0+ (Optimized for Android 16)  
- AniList account *(optional)*  

---

## 🚀 Installation

### 📦 From Release
1. Download latest APK from **Releases**  
2. Enable *Install from unknown sources*  
3. Install the APK  

---

## 🛠 Configuration

### 🔐 AniList OAuth
1. Go to AniList Developer Settings  
2. Create a client  
3. Redirect URL: `animescraper://success`  
4. Update `clientId` in `MainViewModel.kt`  

---

### 🌐 Stream Provider
Uses a custom scraper service (Aniwatch API).

---

## 💻 Tech Stack

| Category | Technology |
|----------|-----------|
| Language | Kotlin |
| UI | Jetpack Compose |
| Architecture | MVVM |
| Networking | OkHttp, Kotlinx Serialization |
| Player | Media3 ExoPlayer |
| Async | Coroutines, Flow |
| Storage | SharedPreferences |

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

### 🚀 v1.2
- 🎛 Server Selection in player  
- 🗣 SUB/DUB support  
- 🔁 Auto server fallback  
- ⚡ Pre-fetched streams  
- ⚡ Instant login state  
- 🎚 Improved tracking slider  
- ❗ Better error handling  
- 🔧 API fixes  

---

### ✨ v1.1
- Improved explore page  
- Working remove anime  
- Episode badges for all anime  
- Better episode counters  
- Debounced search  
- Animated bookmarks  
- On-demand episode scraping  
- UI consistency improvements  

---

## 🔗 API Reference

- AniList GraphQL API:
  - Authentication  
  - Anime lists  
  - Progress tracking  
  - Search & discovery  

📚 https://anilist.gitbook.io/anilist-apiv2-docs/

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

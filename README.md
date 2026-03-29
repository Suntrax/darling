# Darling

A modern anime tracking and streaming app for Android.

![Platform](https://img.shields.io/badge/Platform-Android-green.svg)
![Language](https://img.shields.io/badge/Language-Kotlin-blue.svg)
![MinSDK](https://img.shields.io/badge/MinSDK-26-orange.svg)

## Important

I do not host any content within this app. All anime content is streamed from third-party sources.

## Features

- **AniList and My Anime List Integration** - Login to sync your anime list
- **Streaming** - Watch anime with built-in player (ExoPlayer)
- **Progress Tracking** - Automatically sync watch progress
- **Explore** - Browse trending, seasonal, and top-rated anime
- **Video Player** - Opening and ending skip buttons, quality selection, resize button

## Requirements

- Android 8.0+ (API 26+)

## Installation

Download the APK from [Releases](https://github.com/Suntrax/darling/releases) and install.

## Tech Stack

- Kotlin + Jetpack Compose
- Media3 ExoPlayer
- AniList GraphQL API
- MVVM Architecture

## Project Structure

```
app/src/main/java/com/blissless/anime/
├── api/          # API clients
├── data/         # Repositories and data sources
├── dialogs/      # Dialog components
├── network/      # Network utilities
├── player/       # Video player helpers
├── ui/
│   ├── components/  # Reusable UI components
│   ├── screens/      # App screens
│   └── theme/        # Material theming
├── DarlingApplication.kt
├── MainActivity.kt
├── MainViewModel.kt
└── OverlayState.kt
```

## Forking the repository

local.properties file with the following keys needed:

ANIMEKAI_API_BASE_URL (can be adjusted to other providers)
CLIENT_ID_ANILIST 
TMDB_API_KEY
MAL_CLIENT_ID

## Disclaimer

This app is for educational purposes only. I do not host, upload, or distribute any anime content. All streaming links are provided by third-party sources.

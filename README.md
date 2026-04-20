# Toldya

A time-travelling audio recorder for Android. Toldya continuously listens in the background and lets you save recordings of events that already happened.

> This is a fork of [mafik/echo](https://github.com/mafik/echo) with a redesigned UI and new features.

## Features

### New in this fork

- **Material Design 3** - Complete UI overhaul with dynamic color, tonal surfaces, and modern MD3 components
- **Real-time waveform visualization** - Live microphone amplitude display on the main screen
- **Redesigned main screen** - Flat sections with save CTA button and duration chip selector
- **Redesigned settings page** - Clean flat sections with filter chips, tappable time cards for schedule, and storage location picker
- **Multiple output formats** - Save recordings as WAV, FLAC, OGG, or OPUS
- **Custom storage location** - Choose where recordings are saved via a directory picker
- **Active hours schedule** - Automatically pause/resume recording during specified hours to save battery
- **Last saved clip** - Quick access to the most recent recording with relative timestamp and play button
- **Auto-generated filenames** - Recordings are named with date and time by default, with the option to rename before saving
- **Silent notifications** - Recording notification stays visible without sound or vibration
- **Loading dialog** - Visual feedback while saving and compressing recordings

### From the original

- Continuous background audio recording with configurable memory buffer
- Adjustable audio quality (8kHz / 16kHz / 48kHz)
- Configurable RAM usage for the audio buffer
- Free/libre and open source software

## Download

- [F-Droid](https://f-droid.org/repository/browse/?fdid=eu.mrogalski.saidit)

## Building

Clone the repository and open in Android Studio, or build from the command line:

```bash
./gradlew assembleDebug
```

## License

This project is free/libre and open source software.

# DashTube

A YouTube-only app for the Android Auto car screen. It opens YouTube's mobile site,
keeps your Google sign-in, and blocks every other website. Back, Home and Reload
buttons sit down the left side; full-screen video is supported.

**Requires Android 15 or newer.**

## How it gets onto the car screen

DashTube uses the same method as [AABrowser](https://github.com/kododake/AABrowser):
an ordinary phone screen that declares itself an Android Auto launcher app. Android Auto
shows it on the car display when its developer settings allow unknown apps.

## Install

1. Download `DashTube.apk` from the latest release in this repository.
2. Install it from a PC (USB debugging on):
   `adb install -i com.android.vending DashTube.apk`
3. In Android Auto → Developer settings: tick **Unknown sources** and set
   **Application mode** to **Developer**.
4. Open DashTube once on the phone and sign in to YouTube, then connect to the car.

Only install DashTube builds from this repository's own releases.

## Licence

GPL-3.0. Based on the approach and web-view setup of AABrowser,
Copyright (C) 2025 AABrowser Contributors. The earlier Fermata-based version is kept
on the `fermata-version` branch.

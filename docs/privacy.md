---
title: Privacy Policy
permalink: /privacy/
description: How FocusRefine handles your notifications. Nothing leaves your phone.
---

# FocusRefine Privacy Policy

Last updated: 22 October 2026

FocusRefine is an Android app that decides which of your notifications are urgent in the mode you are in, and mutes the rest. This policy explains what it reads, what it stores, and what it never does.

## In short

- Everything happens on your phone. The app has no internet permission, so Android blocks it from using the network.
- Nothing is uploaded, shared, sold or used for ads. There is no account, no analytics and no crash reporting.
- Cloud backup is off, so your inbox is not copied to your Google account or to a new phone.

## What the app reads

To decide what is urgent, FocusRefine uses Android's notification access. For each notification that arrives it reads the app name, the title and the text.

This happens on your phone only. The reading is done by a model that is bundled inside the app.

## What is stored on your phone

Everything below lives in the app's private storage and nowhere else:

- Your inbox: the title, text, app, time and decision (Shown or Muted) of notifications the app handled, and a short reason for each decision.
- Rules you taught it by correcting a decision.
- A small personal model built from those corrections. It holds numbers only, never message text.
- Your settings: mode, strength, themes and prompts you edited.

## No network, no backup

- The app does not declare the internet permission. Cloud backup is switched off.
- Notification text and titles are never written to system logs.

## Permissions and why

- **Notification access:** lets the app see incoming notifications and move muted ones out of the shade.
- **Show notifications:** the ongoing status notification and the timed-session notification.
- **Foreground service:** keeps the filter running while your phone is in your pocket.
- **Live updates:** lets a timed session appear as a status bar chip on Android 16.

## Your control

- Switch filtering off any time on Home. The app then does not look at new notifications.
- Remove one rule in Learned Rules, or reset all learning in Privacy Insights.
- The inbox is not deleted automatically. Clear it from Settings, or uninstall the app, which removes everything the app stored.
- Take back notification access in Android settings at any time.

## Children

FocusRefine is a general-purpose utility. It collects no personal data from anyone, children included.

## Changes and questions

If this policy changes, the date above changes. Questions or requests: open an issue at [github.com/gnanaswaroop/FocusRefine](https://github.com/gnanaswaroop/FocusRefine/issues).

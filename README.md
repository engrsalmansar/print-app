# Irsa Print Agent

This repository contains a minimal Android app `Irsa Print Agent` prepared to build a debug APK via GitHub Actions.

Features implemented:
- Settings UI to enter Base URL, Token, and Polling seconds.
- Foreground `PrintAgentService` that polls the configured PHP endpoint for print jobs.
- Sends receipt text to ESC POS Print Service using intent `org.escpos.intent.action.PRINT`.
- Marks jobs as printed by POSTing back to the same endpoint.

GitHub Actions workflow is at `.github/workflows/build-apk.yml` and produces `app-debug.apk` artifact on successful build.

Default Base URL is `https://irsakitchen.com/print-agent-api.php`.

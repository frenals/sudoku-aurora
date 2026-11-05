# Changelog

All notable changes to Sudoku Aurora are documented here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versioning follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

**Important:** the section for a version is published verbatim as that version's GitHub Release notes by `scripts/release.sh`. Include install/upgrade information users need.

## [2.0.0] - 2026-11-05

First signed GitHub Release of the Aurora rewrite.

### Added
- Persistent signing key (`android-app/keystore/sudoku-aurora.keystore`) applied to both debug and release builds, so every APK shares one certificate
- `scripts/release.sh`: automatic `versionCode` increment, signed release build, signature/fingerprint/badging verification, and GitHub Release publishing
- `scripts/signing-fingerprint.txt`: locked certificate SHA-256 guard
- `README.md` and this changelog

### Changed
- `versionName` is now semantic (`2.0.0`); `versionCode` is 3 and increases on every release
- Release APKs are published as `SudokuAurora-vX.Y.Z.apk` on Releases, with `SudokuAurora.apk` in the repo root always pointing at the latest build

### Installing / upgrading
- **If you already installed an earlier Sudoku Aurora APK: just install this one on top.** Same package name (`com.workbuddy.sudoku`) and the same signing certificate, with a higher `versionCode` — your saved puzzle, timer and stats are preserved. No uninstall needed.
- Fresh install: download `SudokuAurora-v2.0.0.apk` below, or run `adb install -r SudokuAurora.apk`.
- Requires Android 5.0 (API 21) or newer. This is a self-signed side-load build, not a Play Store release.

### Full app features
- Unique-solution puzzle generator; Easy (40) / Medium (33) / Hard (27) clues
- Animated board (digit pop-in, error shake, staggered reveal, victory sweep), dark aurora theme, confetti win screen
- Interactive keypad with remaining-digit counts, notes/pencil marks, undo, erase, hint, timer, mistakes, auto-check
- Physical keyboard support and saved progress via SharedPreferences

Signature certificate SHA-256: `42d3eb12505fe91f077fa2e355778724c802c23a96f5d602fbad22bbc4e5bdd9`

## [2.0-aurora] - 2026-11-05

Unreleased preview build (debug APK only, `versionCode` 2, `versionName` `2.0-aurora`).

- Full visual overhaul: dark aurora gradient background with drifting orbs and star field
- Custom animations for digits, errors, puzzle reveal, selection, victory, keypad spring and confetti
- New interactive keypad, segmented difficulty control, progress bar, generating overlay and custom win card
- Physical keyboard support and haptic feedback

## [1.0.0] - 2026-11-05

Initial Sudoku app (debug APK, `versionCode` 1). Not published as a GitHub Release.

- Unique-solution generator, three difficulties, notes, undo, hints, timer, mistakes, auto-check, saved progress

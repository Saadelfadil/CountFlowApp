# CountFlow — Release Checklist

Practical, checkbox-oriented. Full reasoning and evidence for every item lives in
`docs/MVP_RELEASE_AUDIT.md` (Session 15) — this file is the short version to work through before
an actual Play Store submission, not a replacement for reading it.

---

## Blockers — must be done before any public submission

- [ ] **Get a real production signing key.** Either generate a keystore and configure
      `signingConfig` for the `release` build type, or enroll in Play App Signing and follow
      Google's upload-key flow. Never commit the keystore or its passwords to the repository.
- [ ] **Get a final Privacy Policy URL** and wire it into `AboutUiState.privacyPolicyUrl`
      (`feature/settings/.../about/AboutViewModel.kt`) — currently `null` by design (D-073).

---

## Strongly recommended before submission

- [ ] Measure cold start on a **signed release build**, on real hardware if available (this
      session's ~2.5–2.8 s number is debug-build, emulator, session-loaded — see
      `docs/MVP_RELEASE_AUDIT.md` Phase 9).
- [ ] Get real 4×2 (`WIDE`) widget confirmation on an actual launcher, ideally a physical device
      (TD-017, unresolved across every session that has attempted it, including this one).
- [ ] Re-confirm basic widget placement/configuration on a real device or a more cooperative
      emulator/launcher combination — this session's automation attempts did not succeed (see
      `docs/MVP_RELEASE_AUDIT.md` Phase 5).

---

## Verify before tagging a release build

- [ ] `./gradlew clean assembleDebug test :core:domain:koverVerify :app:lintDebug` — all green.
- [ ] `./gradlew :app:assembleRelease` and `./gradlew :app:bundleRelease` — both succeed.
- [ ] `versionCode`/`versionName` in `AndroidApplicationConventionPlugin.kt` bumped to match the
      release's `CHANGELOG.md` entry (D-072 established this as a per-release manual step).
- [ ] `CHANGELOG.md` has a dated entry for the release.
- [ ] No new lint warnings beyond the accepted 17 (`KNOWN_ISSUES.md` "Accepted warnings").

---

## Play Console submission mechanics (once the two blockers are resolved)

- [ ] Data Safety declaration — use `docs/PRIVACY_DATA_INVENTORY.md` as the factual source. Summary:
      no data collected, no data shared, no analytics, no ads, no network requests at all.
- [ ] Privacy Policy URL — same URL as the blocker item above.
- [ ] Target API level — already satisfied (`targetSdk 36`, ahead of the 31 Aug 2026 requirement).
- [ ] App content rating questionnaire.
- [ ] Store listing assets (screenshots, icon, description) — not produced by any session yet;
      `docs/SCREENSHOT_GUIDE.md` has real on-device screenshots that could seed store screenshots,
      but none are formatted for a store listing specifically.

---

## Explicitly not required for this release (post-MVP, do not add scope here)

Billing, AdMob, subscriptions, Pro gating, Android 16 Live Updates, cloud sync, accounts,
backup/restore beyond the OS default, recurring reminders/custom offsets, Chronometer second-level
ticking, a full localisation pass, R8 keep rules/Baseline Profiles/macrobenchmarks, a real
open-source-license enumeration mechanism, multi-launcher `WidgetSizeClass` threshold confirmation.

---

## After submission

- [ ] Watch the Play Console's pre-launch report for crashes on real device farm hardware — the
      first real-hardware signal this project will have had at all.
- [ ] Revisit R8/minification (`isMinifyEnabled = false` today) once a keep-rules pass is budgeted
      (`TODO.md` P3) — do not flip it on without one, or R8 will strip something real.

# AS ANAMUL VOICE — Delivery TODO

## Mobile control deck

- [ ] Show the AS ANAMUL VOICE brand, Free Fire target, Android 12–15+ target, and first-device profile (OnePlus Nord 2T / OxygenOS 14) in a dark RGB-neon home screen.
- [ ] Show truthful Shizuku/Root/audio-route states as checking, ready, not ready, unsupported, or failed; never show Injector ON unless the underlying native capability reports it.
- [ ] Provide an Injector control surface with explicit ON/OFF state, diagnostics text, and a link to the Shizuku setup guidance.
- [ ] Provide links to Telegram `https://t.me/crackffpanel` and YouTube `https://www.youtube.com/@AnamulModzOfficial`.

## Voice library

- [ ] Provide voice-library navigation with local voice cards and support for importing supported audio/video formats where the installed native capability exists.
- [ ] Show supported formats MP3, WAV, M4A, and OGG, plus backup/share guidance for `.isfile` packages.
- [ ] Keep local storage guidance aligned with `Music/AS ANAMUL VOICE/` and clearly distinguish local files from server catalog items.

## Backend and admin

- [ ] Persist voice metadata, categories, media addresses, app settings, social links, and update notices in the managed database.
- [ ] Expose public catalog/settings queries for the app and admin-only mutations protected by the existing authenticated admin procedure.
- [ ] Provide an admin surface for creating, editing, hiding, and deleting catalog metadata without exposing server credentials to the client.
- [ ] Keep uploaded media in managed storage and store only stable asset addresses and metadata in database rows.

## Quality and handoff

- [ ] Run database migration, TypeScript check, lint, and relevant tests after implementation.
- [ ] Start and verify Metro on port 8081 and API on port 3000.
- [ ] Save a managed checkpoint before delivery.
- [ ] Provide step-by-step APK build, Shizuku setup, physical-device verification, server configuration, and admin setup instructions.
- [ ] Document that native audio routing needs physical-device verification and is not guaranteed across every Android brand/build.

# AS ANAMUL VOICE — Implementation Plan

## Product scope

AS ANAMUL VOICE is an Android voice utility for Android 12–15+ with a first verification target of OnePlus Nord 2T / OxygenOS 14. It is based on the supplied APK's observable product behavior: Shizuku/Sui or Root readiness, voice library management, audio/video import, voice cards, backup/share, overlay controls, and an injector readiness flow for use with Free Fire. The app has a dark RGB-neon visual direction and must never show a fake success state.

This first implementation delivers a real, inspectable mobile product shell and a secure backend/admin foundation. Native microphone routing is capability-gated: the app reports whether the current build and device can support the route instead of claiming success. Android/ROM-specific Shizuku and AudioPolicy work will live behind a native module boundary so it can be verified on the user's physical device during the APK build phase.

## Architecture

- **Mobile client:** Expo Router / React Native / TypeScript starter, portrait-first and one-handed.
- **Backend:** the starter's Express + tRPC server on the API endpoint, with managed MySQL and storage.
- **Persistence:** users, voice metadata, app settings, and social links in MySQL; audio/image objects in managed storage; local imported files remain device-owned until uploaded or exported.
- **Authentication:** existing Manus OAuth/session flow for admin access; server-side role checks use the starter's admin procedure.
- **Admin surface:** an authenticated Admin tab/screen in the mobile/web-compatible app for managing voices, links, and update notices. No credentials or API keys are bundled into the client.
- **Native boundary:** a `nativeCapability` service interface exposes Shizuku/root/audio-route readiness. Preview and unsupported builds return explicit `unsupported`/`not-ready` states rather than pretending to inject audio.

## Design direction

### Candidate styles

- Cyberpunk utility console — probability 0.06; high-contrast RGB glow, compact instrumentation, and control-first layout.
- Midnight studio — probability 0.07; deep charcoal surfaces, saturated accent rails, and calm media cards.
- Neon hardware lab — probability 0.04; device telemetry, segmented controls, and luminous status indicators.

**Chosen movement:** Neon hardware lab.

**Core principles:** Clear system state before decoration; tactile controls with visible feedback; media cards that prioritize the selected voice; restrained glow so text remains readable.

**Color philosophy:** Near-black blue-gray surfaces provide a stable base. Cyan, magenta, violet, and lime rotate as accent signals for separate functions, while green is reserved for confirmed readiness and amber/red for warnings or unavailable capabilities.

**Layout paradigm:** A vertical instrument panel: a compact status header, a prominent injector control bay, then media/library rails and setup actions. The layout is not a generic centered dashboard; key actions sit on a single thumb-friendly spine.

**Signature elements:** Thin RGB edge rails, a segmented status pill with a pulsing indicator, and glass-like cards with a soft colored underglow.

**Interaction philosophy:** Every action produces a truthful state transition: checking, ready, blocked, or failed. Destructive actions require confirmation. Native-only operations remain visibly disabled with an explanation when running in Preview.

**Animation:** Short 180–260ms opacity/scale transitions, a slow RGB gradient drift on the hero rail, and a restrained pulse only for an active check. No continuous high-cost animation during playback.

**Typography:** System sans for legibility, bold condensed-looking hierarchy through weight and tracking rather than a custom font dependency; monospace is reserved for diagnostic output and API status.

**Brand essence:** A dependable voice control deck for users who need clear readiness and media management — direct, technical, energetic.

**Brand voice:** Short and concrete. Example lines: “Check the route before you inject.” and “No false success: this device is not ready.”

**Wordmark:** AS ANAMUL VOICE in a wide uppercase lockup with a small four-color signal bar; the app icon uses a waveform inside a rounded hardware badge.

## Project structure

- `app/(tabs)/index.tsx`: mobile home/control deck.
- `app/(tabs)/voices.tsx`: voice library and import/backup actions.
- `app/(tabs)/admin.tsx`: protected management surface.
- `components/`: reusable neon cards, status pills, buttons, and voice rows.
- `lib/native-capability.ts`: explicit native capability contract; no fake injection.
- `shared/types.ts`: shared voice, capability, and settings types.
- `drizzle/schema.ts`: users plus voice metadata, settings, links, and notices.
- `server/routers.ts`: public catalog and admin mutation procedures.
- `server/db.ts`: database queries and safe fallback behavior for preview.
- `assets/images/`: app branding assets.

## Build and delivery workflow

1. Implement the mobile screens and truthful local/native capability states.
2. Add additive database schema and tRPC catalog/admin procedures.
3. Run database migration, TypeScript diagnostics, lint, tests, and both Metro/API services.
4. Verify Preview behavior without claiming native audio injection.
5. Save a checkpoint to the managed canonical repository.
6. For an Android APK/AAB, use the Dashboard/native build flow. Install on the OnePlus Nord 2T, start Shizuku, grant permission, and verify the route against Free Fire. Only after device evidence should an Android-specific native module be marked ready.
7. Add production domain, secrets, admin role, storage policy, and backups before publishing live.

## Explicit limitations

- A compiled APK cannot restore the lost original server, database, signing key, or API key.
- Android 12–15+ and all manufacturers cannot be guaranteed to expose identical microphone routing APIs.
- Free Fire security/anti-cheat or microphone restrictions will not be bypassed.
- Preview is not proof of native Shizuku/audio-route behavior; physical-device verification is required.

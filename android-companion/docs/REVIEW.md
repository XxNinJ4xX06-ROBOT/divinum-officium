# Review notes — v0.5.0

This prototype was developed with AI assistance for a Portuguese-speaking user and is being shared for human review. It is an independent companion, without official endorsement or a promise of incorporation into Divinum Officium.

## Recorded checks

- Android build and lint: 0 errors, 33 warnings.
- 87 JUnit/Robolectric checks, including Android APIs 26 and 35: rubric choices, navigation, appearance, book catalog/readers, durable daily packages, date/rubric isolation, interrupted downloads, data validation/import/export, study progress, local HTML/PDF search and optional reminder scheduling.
- 17 Chromium checks of prayer capture on representative markup; 49 checks of documents produced by the Java offline renderer with network offline and JavaScript disabled, recording zero network requests; 62 online-reader fixture checks at 320/393/600 pixels.
- The APK signature was verified and matches the earlier private test builds. The source archive excludes local configuration, signing keys, build caches, credentials and downloaded publications.

These numbers describe the existing development run, not a GitHub Actions run. There is no CI badge implying an independent upstream review.

## Limits and device validation

Robolectric simulates Android and WebView callbacks; browser checks use representative fixture HTML rather than a current response from the public server. No physical phone or real Android emulator was available. The public website returned HTTP 403 from the development environment, so an actual day download in an Android WebView was not verified. TLS/CAPTCHA checks are not bypassed.

Before considering adoption, please check:

1. Install the evaluation APK and check the visible version, currently 0.5.0. This is a debug-signed prerelease.
2. Select a date and Office/Mass rubrics, download one day with internet until 9/9 readings are saved; repeat with 3/7 days if acceptable to the server operators.
3. Interrupt a download, reopen and resume only the missing readings. Change date/rubric to confirm another package is not reused accidentally.
4. In airplane mode, restart the app and open all saved hours and Mass. Compare dates, source warnings and the end of each text with the source.
5. Exercise Latin/Portuguese/both modes, light/dark themes, bookmarks, positions and pasted-excerpt notes. Earlier MHTML copies remain separate from the new daily-package search.
6. Search a downloaded HTML book and a text-based PDF. PDF indexing was unit-tested with a synthetic PDF; scanned image-only PDFs have no OCR, and arbitrary document encodings have not all been tested.
7. Export personal records using Android's document picker and import them again. Check deduplication and settings. Publications, snapshots, cookies and reminder configuration are excluded from the backup.
8. Opt into one reminder, check notification permission and delivery, tap it and disable it again. Alarms are inexact and vendor battery policies can delay them.

## Questions for upstream reviewers

- Would a companion of this kind be useful alongside the existing mobile website/apps?
- Would a separate Android repository or a later scoped pull request be preferable?
- Is foreground user-triggered caching of 9/27/63 pages appropriate, or should an endpoint/rate limit/alternative integration be used?
- Which calendar/rubric warnings or attribution details need additional treatment?

There is no proposed modification to the liturgical calculation engine in this submission. The first step is feedback on architecture, request load, scope and maintainability.

## Evaluation artifacts

APK SHA-256: `0a8d904fc2c8bc6fcee75063f4fd272755bb5752445f21b126a3ec5a1d3901ff`.

Original source ZIP SHA-256: `70943cd26e45601924604bbdd5269dca68bdf0fbc7340949c172f8139c9f4cf5`.

The Git repository adds this English presentation and generated screenshots around the original app source snapshot. App implementation and build configuration are unchanged from that snapshot. The release includes the original archive for byte-level comparison.

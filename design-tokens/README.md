# Pane design tokens

One source of truth, `tokens.json` (W3C Design Tokens format), shared by the Android app, the web landing page and store art.
Every token has `$extensions["app.pane"].status`:
- **locked**: taken from Visual Designer notes or specs in `/workspace/pane-directions-dark5/` or `/workspace/pane/*.md`
- **from-code**: only found in app code (`voltyclaw/peek`, checkout at `/workspace/scratch/peek`, HEAD c71dae9); not confirmed by the Visual Designer
- **proposed**: my guess, not locked. Needs sign-off.

Layers: `primitive.*` holds raw values. `semantic.*` holds roles (bg, text, accent.exit, type, size, radius, motion) and references primitives.
**Dark-only.** The sources define no light palette (see Gaps).

## Build
```
python3 build.py   # needs only stdlib Python 3
```
Writes:
- `android/compose/PaneTokenColors.kt`, `PaneTokenType.kt`, `PaneTokenDimens.kt` (PaneDimens, PaneShapes, PaneMotion). Package `app.pane.android.ui.theme.tokens`.
- `android/res/values/pane_tokens.xml` (colors, dimens, integers in ms)
- `web/tokens.css` (`--pane-*` custom properties on `:root`)

Usage:
- Android: copy the generated files into the app. Type styles take the app's existing families: `PaneTokenType.Body(PaneDisplay, Inter)`.
- Web: `<link rel="stylesheet" href="tokens.css">`, then `color: var(--pane-color-text-primary)`.
- Store art: use the hexes in `tokens.json`.

The app code has not been changed. The Kotlin was not compiled here (no Android toolchain was run).

## Rules carried over from the notes
- Coral `#D4886A` is the **rare exit only**: one hit per screen (the Open tile or chip). It is never chrome, never text styling, and never used for consent.
- Slate `#9DB4CC` is for **handle text only**: no icons, fills or chips. Handles also need a weight difference (560 inline, 500 in the header, 600 for reply names), because colour alone fails WCAG 1.4.1.
- Letter-spacing (tracking) goes on the display face only.

## Gaps (values missing from the sources)
| What | Status in tokens |
|---|---|
| Light theme: no light palette exists anywhere. Code says "This pass is dark-only" (`Color.kt`). | not built |
| Pressed coral: `tiktok/NOTES.md` cites "night on pressed coral 5.40:1" but gives no hex | `accent.exit-pressed` = `#C27A5F` **proposed** |
| Line heights for every type style | **proposed** (multipliers 1.1–1.45) |
| Full type scale: only display 44, titles 26/22, body 17/15, label 15, meta 13 are specified. Code also uses 8–27sp ad hoc. | rest not tokenised |
| Spacing scale: no Visual Designer scale. Only the 16dp inset and 8dp chip gaps are specified. | `space.*` 2–32 **proposed** (from the most used `.dp` values in code) |
| Easing curve: "ease-out" with no curve given | **proposed** cubic-bezier(0,0,0.2,1) |
| Durations other than the 150ms press fade | **from-code** 120/220/300 |
| Video controls auto-hide: 3000ms (option A) vs 5000ms (option B) in `slice_video_gestures.md` is undecided | **proposed** 3000 |
| Success colour | **from-code** `#5BA8A0` only |
| Icon size | **proposed** 24 |
| Error/warning colours | none in the sources. Consent notes say "quiet, not coral, not red" |

## Conflicts: Visual Designer notes/paints vs current code
1. **Ink on raised.** The brief says "ink `#DAD4CE` on raised surfaces". The VD notes lock **ink = `#F4EFEA`** (`piece-01g-warm-coral/NOTES.md`, `handles/NOTES.md`). `#DAD4CE` is described as **"soft ink"**, used for body and label text on raised and night (`tiktok/NOTES.md`, `tiktok/consent-shared/NOTES.md`, `tiktok/settings-sources/NOTES.md`). Buttons and titles on raised still use `#F4EFEA`. The tokens keep both: `text.primary` = #F4EFEA and `text.soft` = #DAD4CE. **Needs a decision.**
2. **`#DAD4CE` is missing from the theme.** It is hardcoded in `ui/tiktok/TikTokSurface.kt` (x2) and absent from `ui/theme/Color.kt`.
3. **Hardcoded hexes outside the theme.** `ui/player/PlayerView.kt` has #8A827A x3, #262018 x2, #F4EFEA and #191412. `ui/media/VideoPlayback.kt` has #F4EFEA, #8A827A and #262018.
4. **Typography is not wired up.** `ui/theme/Theme.kt` passes `Typography()` (Material defaults). The T2 `PaneType` scale from `piece-02-type/NOTES.md` is not used.
5. **Geist leftovers.** `ui/theme/Type.kt` defines `Geist = Inter` and `GeistMono = Inter`. They are used in `components/PostDetailSections.kt` (GeistMono 8sp SemiBold, letterSpacing 0.7sp), `viewer/ViewerView.kt` and `home/HomeView.kt`. Geist is out (piece-02 supersedes the DRAFT), and the tracked 8sp meta breaks the "tracking on display only" rule.
6. **Missing font weights.** The notes use Inter Tight **600** (22/600 page titles) and Inter **560** and **640**. `res/font/` ships only `inter_tight_medium` (500) and Inter 400/500/600, so Android renders these with substituted or synthesized weights.
7. **`secondary` and `muted` are the same value** (#8A827A) in `Color.kt`. The VD has a single "mute".
8. **ThemeMode.Light exists in code** (`ThemeMode.kt`, `ThemePreferences.kt`) but `LightPaneColors = M2w` (dark). The user-facing setting does nothing.
9. **Motion.** VD presses use 150ms ease-out. The code uses `tween(120)`, `tween(180)`, `tween(220)` and `tween(300)` with no shared token.
10. **Radii.** Code uses 2/8/12/14/16/18/20/24/999. The VD specifies 4, 5, 12, 14, 16, 18, 20 and a full pill (26 on 52). **24dp** (x7) and **8dp** (x5) have no VD source.
11. **`PaneSuccess` comment** says "Distinct from the brand greens above", but there are no greens. It is a stale sage-era comment, and the success colour itself is not VD-sourced.
12. **`themes.xml`** sets `android:colorAccent` to coral globally, so system widgets can pick up the accent, which goes against the rare-exit rule. Only applies if any View-based widgets remain.

Code paths are relative to `/workspace/scratch/peek/app/src/main/java/app/pane/android/` (`themes.xml` is in `res/values/`).

## Jev router (fork)
Ran once. `effective_action: proceed_local` (conf 0.58, below the threshold so the choice was not honoured). needs_more_info noul 0.85. The work continued as planned. Log: `/workspace/jev-lab/logs/20261010T104954181094Z.jsonl`.

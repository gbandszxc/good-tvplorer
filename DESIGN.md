# Design

## Metadata

- Product: Good TVplorer
- Register: product
- Platform: Android TV
- UI stack: Jetpack Compose + Material 3 primitives

## Design Intent

Good TVplorer 是客厅电视上的媒体文件工具。方向采用“冷静的 NAS 媒体库”：比传统文件管理器更适合图片、音频、视频的快速预览，但仍保持工具界面的密度、速度和遥控器优先。

## Color

Current palette comes from `TvTheme.kt` and should stay restrained, with a calm slate-blue media-library surface and amber focus.

| Role | Hex | Usage |
| --- | --- | --- |
| Background | `#0B121A` | App background and preview chrome |
| Rail / Panel | `#101A26` | Source rail, home SMB panel, quick preview panel |
| Surface | `#152232` | Buttons, rows, tiles |
| Primary | `#FFC857` | Focused surfaces and high-priority action feedback |
| On Primary | `#151007` | Text/icons on focused primary |
| On Background | `#F3F7FA` | Main text |
| Secondary | `#7CC7D8` | Section labels, quiet status accents |
| Error | `#FFA3A3` | Error messages |
| Muted Text | `#A8B8C7` / `#728397` | Secondary labels and remote hints |

Primary color is for focus and confirmation, not decoration. Avoid Material You-style dynamic palettes in MVP; TV readability and consistency win.

## Typography

Use one sans family through Compose defaults. Product UI uses fixed sizes, not fluid typography.

- App title: `40sp`
- Screen path/header: `26-32sp`
- List item title: `25sp`, semibold
- List metadata: `18sp`
- Preview text: `24sp`, `34sp` line height
- Button label: `24sp`, semibold
- Grid tile title: `18sp`, semibold

Text must fit inside focused surfaces. Long filenames should use one-line truncation in rows and full names in preview screens where space allows.

## Layout

The app uses full-screen bands, not nested cards.

```text
Home
+----------------------------------------------------------------+
| Good TVplorer        | SMB / NAS                               |
| media tool copy      | [ LivingRoomNAS host/share             ] |
| [ 本地文件 ]          | [ StudioNAS host/share                 ] |
| [ 添加 SMB ]          |                                         |
+----------------------------------------------------------------+

Browser
+----------------------------------------------------------------+
| Good TVplorer   smb:/Movies/2024        [网格] [刷新] [返回]     |
| 位置      |  file grid/list main surface          | 快速预览     |
| 本地      |  [cover] [cover] [cover]              | cover/thumb  |
| SMB/NAS   |  [cover] [cover] [cover]              | file details |
| 图片/音频/视频                                                     |
+----------------------------------------------------------------+
```

Spacing should stay large enough for TV: page padding around `36-56dp`, row padding around `18dp`, row gap around `10dp`, focused target height large enough to acquire from distance.

## Components

### TvButton

- Shape: `8dp` radius
- Default: surface background, light text
- Focused: primary background, dark text, `3dp` white border
- Behavior: focusable, clickable, D-pad confirmable

### File Row

- Shape: `8dp` radius
- Default: surface background
- Focused: amber primary background, dark text, `3dp` pale amber border
- Contents: thumbnail or type marker, filename, size/time metadata
- Directory rows sort before files

### File Tile

- Used in grid mode for image, audio, and video browsing.
- Thumbnail ratio: `16:10`.
- If no thumbnail exists, show a compact type marker (`DIR`, `IMG`, `AUD`, `VID`, `TXT`, `FILE`).
- Focused tile uses the same amber focus surface as rows.

### Quick Preview Panel

- Right-side fixed panel in browser screens.
- Shows focused item thumbnail/cover/frame, filename, type, size, and time.
- SMB previews are supported by caching the target media into app cache before thumbnail extraction.

### Dialogs

SMB add dialog uses standard Material dialog and text fields for now. All actions must remain focusable by remote. Future work should reduce text entry by supporting saved servers, QR/import, or recent hosts.

## States

- Loading: spinner plus clear copy is acceptable for MVP; future list loading should prefer skeleton rows.
- Empty: explicit copy, e.g. `目录为空`.
- Error: user-readable message, no password/log leakage.
- Preview loading: center spinner on black or dark background.

## Motion

Motion should be short and state-driven only.

- Focus transitions: 150-200 ms color/border; no bounce.
- Loading/preview transitions: simple fade or instant.
- No page-load choreography.
- No decorative bounce, elastic, or long stagger sequences.

## Accessibility

- Every actionable control must be focusable.
- Focus state must remain visible at TV viewing distance.
- Do not rely on color alone; use text and layout state.
- Maintain high contrast for text and focused controls.
- Touch should work as a fallback, but the main path is D-pad.

## Known Design Debt

- Current icons are text markers (`DIR`, `IMG`, `TXT`, `AUD`, `VID`, `FILE`); replace with a consistent icon set.
- Loading uses centered spinners; add list skeletons when browser polish starts.
- SMB text entry is heavy on TV; add better saved/recent connection flows.
- Preview image navigation does not yet support left/right sibling switching.
- Video preview is thumbnail-only; playback is a later feature.

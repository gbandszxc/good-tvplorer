# Design

## Metadata

- Product: Good TVplorer
- Register: product
- Platform: Android TV
- UI stack: Jetpack Compose + Material 3 primitives

## Design Intent

Good TVplorer 是客厅电视上的工具型界面。用户离屏幕较远、输入设备通常只有 D-pad 遥控器，界面必须优先服务扫描、定位和确认，而不是触屏探索。

## Color

Current palette comes from `TvTheme.kt` and should stay restrained.

| Role | Hex | Usage |
| --- | --- | --- |
| Background | `#101418` | App background and preview chrome |
| Surface | `#182028` | Buttons, rows, panels |
| Primary | `#D6F35F` | Focused primary surfaces and high-priority action feedback |
| On Primary | `#101418` | Text/icons on focused primary |
| On Background | `#F1F5F9` | Main text |
| Secondary | `#7DD3FC` | Secondary accent, future info states |
| Error | `#FCA5A5` | Error messages |
| Muted Text | `#CBD5E1` / `#94A3B8` | Secondary labels and empty states |

Primary color is for focus and confirmation, not decoration. Avoid Material You-style dynamic palettes in MVP; TV readability and consistency win.

## Typography

Use one sans family through Compose defaults. Product UI uses fixed sizes, not fluid typography.

- App title: `38sp`
- Screen path/header: `26-32sp`
- List item title: `25sp`, semibold
- List metadata: `18sp`
- Preview text: `24sp`, `34sp` line height
- Button label: `24sp`, semibold

Text must fit inside focused surfaces. Long filenames should use one-line truncation in rows and full names in preview screens where space allows.

## Layout

The app uses full-screen bands, not nested cards.

```text
Home
+--------------------------------------------------+
| Good TVplorer                                    |
| [ 本地文件 ] [ 添加 SMB ]                         |
|                                                  |
| SMB 服务器                                       |
| [ NAS 1  host/share ]                            |
+--------------------------------------------------+

Browser
+--------------------------------------------------+
| /path/to/folder                  [刷新] [返回]    |
|                                                  |
| [icon] filename.ext               size  time      |
| [icon] folder                                      |
+--------------------------------------------------+
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
- Focused: primary background, dark text, `3dp` white border
- Contents: thumbnail or type marker, filename, size/time metadata
- Directory rows sort before files

### Dialogs

SMB add dialog uses standard Material dialog and text fields for now. All actions must remain focusable by remote. Future work should reduce text entry by supporting saved servers, QR/import, or recent hosts.

## States

- Loading: spinner is acceptable for MVP; future list loading should prefer skeleton rows.
- Empty: explicit copy, e.g. `目录为空`.
- Error: user-readable message, no password/log leakage.
- Preview loading: center spinner on black or dark background.

## Motion

Motion should be short and state-driven only.

- Focus transitions: 150-200 ms color/border/scale once implemented.
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

- Current icons are text markers (`[D]`, `[I]`, `[T]`, `[A]`, `[F]`); replace with a consistent icon set.
- Focus transitions are abrupt; add short state-driven animation.
- Loading uses centered spinners; add list skeletons when browser polish starts.
- SMB text entry is heavy on TV; add better saved/recent connection flows.
- Preview image navigation does not yet support left/right sibling switching.

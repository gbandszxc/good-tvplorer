# Android 自适应启动图标决策记录

## 背景

项目使用一张 `1254 × 1254` 的 PNG 作为启动图标原图，路径为 `docs/raw/icon/raw_icon.png`。原图需要同时支持 Android 6–7 的旧式图标与 Android 8+ 的自适应图标。

自适应图标由前景和背景两层组成，启动器会按设备的圆形、圆角方形等掩膜裁切它。`108dp` 前景画布中，官方保证不会裁切的区域是中央 `66dp` 圆形；完整方图无法同时做到铺满图标且在所有掩膜下保留四个角。

## 方案演进

1. 初版将原图铺满 `108dp` 前景画布。图标主体很大，但设备掩膜裁掉了过多边缘画面。
2. 随后将原图缩至 `46dp`，使完整方图落入 `66dp` 安全圆内。画面虽完整，但留白过大；深色背景 `#101418` 在设备上形成明显黑框。
3. 最终采用“适度裁切、视觉填满”：原图缩至 `80dp` 并居中放入 `108dp` 前景画布，允许启动器裁切四角；背景改为与原图边缘接近的蓝灰到深蓝对角渐变，消除黑框。

## 当前实现

- `mipmap-*/ic_launcher.png`：Android 6–7 的旧式图标，原图铺满，不加留白。
- `drawable-*/ic_launcher_foreground.png`：Android 8+ 自适应前景，透明 `108dp` 画布内放置 `80dp` 原图。
- `drawable/ic_launcher_background.xml`：`#6E84A7 → #04142A` 对角渐变，用于前景周围的可见区域。
- `mipmap-anydpi-v26/ic_launcher.xml`：组合上述背景与前景。

## 后续更换原图

替换 `docs/raw/icon/raw_icon.png` 后，在仓库根目录执行：

```powershell
.\scripts\generate-launcher-icons.ps1
.\gradlew.bat assembleDebug
```

脚本要求原图为正方形，并会生成 `mdpi`、`hdpi`、`xhdpi`、`xxhdpi`、`xxxhdpi` 五组资源。

## 验证与调参

已通过 `assembleDebug` 构建。应在目标电视或启动器上实际安装确认图标观感。

若主体仍偏小或边缘裁切过多，只调整 `scripts/generate-launcher-icons.ps1` 中的 `$adaptiveForegroundDp` 后重新生成资源和构建。不要低于 `46dp`，否则会重现大面积背景框；不要期望大于 `66dp` 的方图在所有启动器掩膜下完整显示。

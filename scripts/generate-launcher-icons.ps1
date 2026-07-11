param(
    [string]$Source = (Join-Path $PSScriptRoot "..\docs\raw\icon\raw_icon.png")
)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Drawing

if (-not (Test-Path -LiteralPath $Source -PathType Leaf)) {
    throw "找不到图标原图：$Source"
}

function Save-Icon([System.Drawing.Image]$Image, [string]$Destination, [int]$CanvasSize, [int]$ImageSize = $CanvasSize) {
    $bitmap = [System.Drawing.Bitmap]::new($CanvasSize, $CanvasSize)
    try {
        $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
        try {
            $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
            $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
            $graphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
            $graphics.Clear([System.Drawing.Color]::Transparent)
            $offset = ($CanvasSize - $ImageSize) / 2
            $graphics.DrawImage($Image, $offset, $offset, $ImageSize, $ImageSize)
            $bitmap.Save($Destination, [System.Drawing.Imaging.ImageFormat]::Png)
        } finally {
            $graphics.Dispose()
        }
    } finally {
        $bitmap.Dispose()
    }
}

$image = [System.Drawing.Image]::FromFile((Resolve-Path -LiteralPath $Source))
try {
    if ($image.Width -ne $image.Height) {
        throw "启动图标原图必须为正方形，当前尺寸为 $($image.Width)x$($image.Height)。"
    }

    $root = Join-Path $PSScriptRoot "..\app\src\main\res"
    $adaptiveForegroundDp = 80
    $densities = @{
        mdpi = @(48, 108)
        hdpi = @(72, 162)
        xhdpi = @(96, 216)
        xxhdpi = @(144, 324)
        xxxhdpi = @(192, 432)
    }
    foreach ($density in $densities.GetEnumerator()) {
        $mipmap = Join-Path $root "mipmap-$($density.Key)"
        $drawable = Join-Path $root "drawable-$($density.Key)"
        New-Item -ItemType Directory -Force $mipmap, $drawable | Out-Null
        Save-Icon $image (Join-Path $mipmap "ic_launcher.png") $density.Value[0]
        $foregroundSize = [math]::Round($density.Value[1] * $adaptiveForegroundDp / 108)
        Save-Icon $image (Join-Path $drawable "ic_launcher_foreground.png") $density.Value[1] $foregroundSize
    }
} finally {
    $image.Dispose()
}

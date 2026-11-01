Add-Type -AssemblyName System.Drawing

function RoundedPath([single]$x, [single]$y, [single]$w, [single]$h, [single]$r) {
    $p = New-Object System.Drawing.Drawing2D.GraphicsPath
    $d = $r * 2
    $p.AddArc($x, $y, $d, $d, 180, 90)
    $p.AddArc($x + $w - $d, $y, $d, $d, 270, 90)
    $p.AddArc($x + $w - $d, $y + $h - $d, $d, $d, 0, 90)
    $p.AddArc($x, $y + $h - $d, $d, $d, 90, 90)
    $p.CloseFigure()
    return $p
}

function Blob($g, [single]$cx, [single]$cy, [single]$r, $color) {
    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    $path.AddEllipse($cx - $r, $cy - $r, 2 * $r, 2 * $r)
    $brush = New-Object System.Drawing.Drawing2D.PathGradientBrush($path)
    $brush.CenterColor = $color
    $brush.SurroundColors = @([System.Drawing.Color]::FromArgb(0, $color.R, $color.G, $color.B))
    $g.FillPath($brush, $path)
}

$s = 512
$bmp = New-Object System.Drawing.Bitmap($s, $s)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.SmoothingMode = 'AntiAlias'
$g.TextRenderingHint = 'ClearTypeGridFit'

# Mica 风格底：深色 + 三块柔光
$g.Clear([System.Drawing.Color]::FromArgb(11, 15, 23))
Blob $g 120 110 250 ([System.Drawing.Color]::FromArgb(175, 0, 103, 192))
Blob $g 430 90 200 ([System.Drawing.Color]::FromArgb(135, 76, 194, 255))
Blob $g 350 470 240 ([System.Drawing.Color]::FromArgb(150, 135, 100, 184))
Blob $g 60 400 180 ([System.Drawing.Color]::FromArgb(110, 0, 153, 204))

# 卡片投影
for ($i = 10; $i -ge 1; $i--) {
    $alpha = [int](4 + $i * 1.6)
    $shadow = RoundedPath (52 - $i) (152 - $i + 10) (408 + 2 * $i) (206 + 2 * $i) (18 + $i)
    $g.FillPath((New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb($alpha, 0, 0, 0))), $shadow)
}

# Acrylic 卡片
$card = RoundedPath 52 152 408 206 18
$acrylic = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(38, 255, 255, 255))
$g.FillPath($acrylic, $card)
$stroke = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(48, 255, 255, 255), 1.2)
$g.DrawPath($stroke, $card)

# 封面
$cover = RoundedPath 76 178 96 96 10
$coverRect = New-Object System.Drawing.Rectangle(76, 178, 96, 96)
$coverBrush = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
    $coverRect,
    [System.Drawing.Color]::FromArgb(0, 103, 192),
    [System.Drawing.Color]::FromArgb(76, 194, 255),
    45)
$g.FillPath($coverBrush, $cover)
$noteFont = New-Object System.Drawing.Font('Segoe UI Symbol', 44, [System.Drawing.FontStyle]::Bold)
$g.DrawString([char]0x266A, $noteFont, [System.Drawing.Brushes]::White, 100, 196)

# 文本
$white = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::White)
$dim = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(170, 255, 255, 255))
$titleFont = New-Object System.Drawing.Font('Microsoft YaHei UI', 22, [System.Drawing.FontStyle]::Bold)
$subFont = New-Object System.Drawing.Font('Microsoft YaHei UI', 11)
$lyricFont = New-Object System.Drawing.Font('Microsoft YaHei UI', 12)

$g.DrawString('Nightfall', $titleFont, $white, 196, 186)
$g.DrawString('Salt Player · Fluent Island', $subFont, $dim, 198, 224)
$g.DrawString('夜色渐浓，灯火未眠', $lyricFont, $dim, 198, 252)

# 进度条
$px = 198; $py = 296; $pw = 240
$g.FillPath((New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(46, 255, 255, 255))), (RoundedPath $px $py $pw 4 2))
$accentBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(76, 194, 255))
$g.FillPath($accentBrush, (RoundedPath $px $py ($pw * 0.46) 4 2))
$g.FillEllipse($accentBrush, ($px + $pw * 0.46 - 5), ($py - 3), 10, 10)

$timeFont = New-Object System.Drawing.Font('Segoe UI', 9)
$g.DrawString('1:24', $timeFont, $dim, $px, ($py + 10))
$g.DrawString('3:52', $timeFont, $dim, ($px + $pw - 22), ($py + 10))

# 底部标题
$sf = New-Object System.Drawing.StringFormat
$sf.Alignment = 'Center'
$bigFont = New-Object System.Drawing.Font('Microsoft YaHei UI', 34, [System.Drawing.FontStyle]::Bold)
$g.DrawString('音乐浮窗', $bigFont, $white, (New-Object System.Drawing.RectangleF(0, 392, $s, 60)), $sf)

$capFont = New-Object System.Drawing.Font('Segoe UI', 13)
$g.DrawString('Salt Player for Windows', $capFont, $dim, (New-Object System.Drawing.RectangleF(0, 452, $s, 26)), $sf)

$g.Dispose()
$bmp.Save('e:/spw/cover-fluent.png', [System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()
Get-Item e:/spw/cover-fluent.png | Select-Object Name, Length

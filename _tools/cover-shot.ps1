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

$s = 512
$bmp = New-Object System.Drawing.Bitmap($s, $s)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.SmoothingMode = 'AntiAlias'
$g.TextRenderingHint = 'ClearTypeGridFit'

# 纯色背景
$g.Clear([System.Drawing.Color]::FromArgb(32, 32, 32))

# 卡片（无渐变）
$card = RoundedPath 36 146 440 220 10
$g.FillPath((New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(44, 44, 44))), $card)
$g.DrawPath((New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(58, 58, 58), 1.2)), $card)

# 专辑图（虚构：纯色几何构成）
$art = RoundedPath 56 166 96 96 8
$g.FillPath((New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(20, 50, 79))), $art)
$circle = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(76, 194, 255))
$g.FillEllipse($circle, 70, 188, 56, 56)
$square = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 200, 61))
$g.FillRectangle($square, 100, 214, 26, 26)
$bar = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 255, 255))
$g.FillRectangle($bar, 70, 226, 40, 6)

# 文字（虚构歌名，避免版权）
$white = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::White)
$gray = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(160, 160, 160))
$titleFont = New-Object System.Drawing.Font('Segoe UI', 19, [System.Drawing.FontStyle]::Regular)
$subFont = New-Object System.Drawing.Font('Microsoft YaHei UI', 11)
$g.DrawString('Man, what can i say', $titleFont, $white, 168, 170)
$g.DrawString('男人，什么可以我说', $subFont, $gray, 170, 204)

# 进度条
$accent = [System.Drawing.Color]::FromArgb(76, 194, 255)
$px = 168; $py = 258; $pw = 280
$g.FillPath((New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(58, 58, 58))), (RoundedPath $px $py $pw 4 2))
$accentBrush = New-Object System.Drawing.SolidBrush($accent)
$g.FillPath($accentBrush, (RoundedPath $px $py ($pw * 0.32) 4 2))
$g.FillEllipse($accentBrush, ($px + $pw * 0.32 - 5), ($py - 3), 10, 10)

$timeFont = New-Object System.Drawing.Font('Segoe UI', 9)
$g.DrawString('1:12', $timeFont, $gray, $px, ($py + 10))
$g.DrawString('3:48', $timeFont, $gray, ($px + $pw - 22), ($py + 10))

# 控制条
$cx = 256; $cy = 322
# 上一首：竖条 + 左向三角
$g.FillRectangle($white, 194, 314, 3, 16)
$prev = @(
    (New-Object System.Drawing.PointF(207, 314)),
    (New-Object System.Drawing.PointF(207, 330)),
    (New-Object System.Drawing.PointF(197, 322))
)
$g.FillPolygon($white, $prev)

# 下一首：右向三角 + 竖条
$next = @(
    (New-Object System.Drawing.PointF(305, 314)),
    (New-Object System.Drawing.PointF(305, 330)),
    (New-Object System.Drawing.PointF(315, 322))
)
$g.FillPolygon($white, $next)
$g.FillRectangle($white, 315, 314, 3, 16)

$g.FillEllipse($accentBrush, ($cx - 17), ($cy - 17), 34, 34)
$pause = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(32, 32, 32))
$g.FillRectangle($pause, ($cx - 7), ($cy - 8), 5, 16)
$g.FillRectangle($pause, ($cx + 2), ($cy - 8), 5, 16)

# 列表图标
$listPen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(200, 200, 200), 2)
foreach ($i in 0..2) {
    $y = 314 + $i * 8
    $g.FillEllipse((New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(200, 200, 200))), 436, ($y - 1), 3, 3)
    $g.DrawLine($listPen, 443, $y, 458, $y)
}

# 底部标题
$sf = New-Object System.Drawing.StringFormat
$sf.Alignment = 'Center'
$bigFont = New-Object System.Drawing.Font('Microsoft YaHei UI', 34, [System.Drawing.FontStyle]::Bold)
$g.DrawString('音乐浮窗', $bigFont, $white, (New-Object System.Drawing.RectangleF(0, 392, $s, 60)), $sf)
$capFont = New-Object System.Drawing.Font('Segoe UI', 13)
$g.DrawString('Salt Player for Windows', $capFont, $gray, (New-Object System.Drawing.RectangleF(0, 452, $s, 26)), $sf)

$g.Dispose()
$bmp.Save('e:/spw/cover-shot.png', [System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()
Get-Item e:/spw/cover-shot.png | Select-Object Name, Length

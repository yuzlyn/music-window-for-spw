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

function Make-Cover([string]$path, [bool]$dark) {
    $s = 512
    $bmp = New-Object System.Drawing.Bitmap($s, $s)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = 'AntiAlias'
    $g.TextRenderingHint = 'ClearTypeGridFit'

    # 背景
    $bg1 = if ($dark) { [System.Drawing.Color]::FromArgb(15, 17, 22) } else { [System.Drawing.Color]::FromArgb(238, 242, 247) }
    $bg2 = if ($dark) { [System.Drawing.Color]::FromArgb(28, 38, 52) } else { [System.Drawing.Color]::FromArgb(255, 255, 255) }
    $bgRect = New-Object System.Drawing.Rectangle(0, 0, $s, $s)
    $bgBrush = New-Object System.Drawing.Drawing2D.LinearGradientBrush($bgRect, $bg1, $bg2, 60)
    $g.FillRectangle($bgBrush, $bgRect)

    # 卡片
    $cx = 36; $cy = 150; $cw = 440; $ch = 200
    $cardColor = if ($dark) { [System.Drawing.Color]::FromArgb(44, 44, 44) } else { [System.Drawing.Color]::FromArgb(255, 255, 255) }
    $card = RoundedPath $cx $cy $cw $ch 20
    $cardBrush = New-Object System.Drawing.SolidBrush($cardColor)
    $g.FillPath($cardBrush, $card)
    $borderColor = if ($dark) { [System.Drawing.Color]::FromArgb(58, 58, 58) } else { [System.Drawing.Color]::FromArgb(229, 229, 229) }
    $pen = New-Object System.Drawing.Pen($borderColor, 1.5)
    $g.DrawPath($pen, $card)

    # 封面
    $fx = $cx + 20; $fy = $cy + 20; $fs = 88
    $cover = RoundedPath $fx $fy $fs $fs 12
    $c1 = [System.Drawing.Color]::FromArgb(0, 95, 184)
    $c2 = [System.Drawing.Color]::FromArgb(96, 205, 255)
    $coverRect = New-Object System.Drawing.Rectangle($fx, $fy, $fs, $fs)
    $coverBrush = New-Object System.Drawing.Drawing2D.LinearGradientBrush($coverRect, $c1, $c2, 45)
    $g.FillPath($coverBrush, $cover)
    $noteFont = New-Object System.Drawing.Font('Segoe UI Symbol', 40, [System.Drawing.FontStyle]::Bold)
    $g.DrawString([char]0x266A, $noteFont, [System.Drawing.Brushes]::White, ($fx + 22), ($fy + 18))

    # 歌词
    $textColor = if ($dark) { [System.Drawing.Color]::FromArgb(255, 255, 255) } else { [System.Drawing.Color]::FromArgb(26, 26, 26) }
    $subColor = if ($dark) { [System.Drawing.Color]::FromArgb(160, 160, 160) } else { [System.Drawing.Color]::FromArgb(93, 93, 93) }
    $lyricFont = New-Object System.Drawing.Font('Microsoft YaHei UI', 21, [System.Drawing.FontStyle]::Bold)
    $subFont = New-Object System.Drawing.Font('Microsoft YaHei UI', 13, [System.Drawing.FontStyle]::Regular)
    $lyricBrush = New-Object System.Drawing.SolidBrush($textColor)
    $subBrush = New-Object System.Drawing.SolidBrush($subColor)

    $tx = $fx + $fs + 22
    $g.DrawString('夜色渐浓 灯火未眠', $lyricFont, $lyricBrush, $tx, ($cy + 34))
    $g.DrawString('Nightfall - Salt Player', $subFont, $subBrush, $tx, ($cy + 76))

    # 进度条
    $px = $tx; $py = $cy + 118; $pw = $cw - ($tx - $cx) - 20
    $trackColor = if ($dark) { [System.Drawing.Color]::FromArgb(60, 60, 60) } else { [System.Drawing.Color]::FromArgb(229, 229, 229) }
    $track = RoundedPath $px $py $pw 6 3
    $g.FillPath((New-Object System.Drawing.SolidBrush($trackColor)), $track)
    $accent = [System.Drawing.Color]::FromArgb(0, 95, 184)
    $fill = RoundedPath $px $py ($pw * 0.42) 6 3
    $g.FillPath((New-Object System.Drawing.SolidBrush($accent)), $fill)
    $g.FillEllipse((New-Object System.Drawing.SolidBrush($accent)), ($px + $pw * 0.42 - 6), ($py - 3), 12, 12)

    $timeFont = New-Object System.Drawing.Font('Segoe UI', 11)
    $g.DrawString('1:24', $timeFont, $subBrush, $px, ($py + 12))
    $g.DrawString('3:52', $timeFont, $subBrush, ($px + $pw - 34), ($py + 12))

    # 标题
    $titleFont = New-Object System.Drawing.Font('Microsoft YaHei UI', 40, [System.Drawing.FontStyle]::Bold)
    $sf = New-Object System.Drawing.StringFormat
    $sf.Alignment = 'Center'
    $titleRect = New-Object System.Drawing.RectangleF(0, 380, $s, 70)
    $g.DrawString('音乐浮窗', $titleFont, $lyricBrush, $titleRect, $sf)

    $sub2 = New-Object System.Drawing.Font('Microsoft YaHei UI', 15)
    $sub2Rect = New-Object System.Drawing.RectangleF(0, 452, $s, 30)
    $g.DrawString('Salt Player · Fluent 桌面歌词', $sub2, $subBrush, $sub2Rect, $sf)

    $g.Dispose()
    $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
}

Make-Cover 'e:/spw/cover-dark.png' $true
Make-Cover 'e:/spw/cover-light.png' $false
Get-ChildItem e:/spw/cover*.png | Select-Object Name, Length

# Driving runClient by POSTING to its window, instead of by taking the foreground.
#
# `mc-drive.ps1` types into whatever is in front and therefore has to prove, before every single
# keystroke, that what is in front is the game. That guard is the whole reason it exists, and it
# is also its two failure modes: it refuses to work when anything at all steals focus, and it is
# unusable while a human is at the keyboard. A screensaver ended a night run dead -- it takes the
# *input desktop*, `GetForegroundWindow` returns 0, and `SwitchDesktop` back is refused because
# the workstation is locked. Nothing can be typed anywhere, ever again, until somebody signs in.
#
# This file has neither problem, because a posted message is addressed to one window:
#
#   * It cannot land in the human's window. There is no "whatever is in front" involved, so the
#     assert that mc-drive.ps1 is built around has nothing left to protect.
#   * It works with the game behind a screensaver, behind a locked screen, minimised, or with the
#     human working in another app on the same desk.
#
# <b>The scancode is the key, and wParam is ignored.</b> GLFW's Win32 window proc reads
# `HIWORD(lParam) & (KF_EXTENDED | 0x1ff)` and looks the *scancode* up in its own table; the
# virtual key in wParam is never consulted. So a posted WM_KEYDOWN carrying the right VK and a
# zero lParam presses nothing at all -- which is the same shape as the older finding that
# SendKeys' WM_CHAR never arrives, one layer down. `Scan` asks Windows for the scancode with
# MapVirtualKey rather than carrying a table of them.
#
# What it still cannot do: nothing sends `Alt`+F4, and the game must be running with
# `pauseOnLostFocus:false` in run/options.txt, or every frame it spends unfocused is a paused one.
# `Ensure-Unpaused` sets that in the file; it is read at startup, so set it before launching.

Add-Type @"
using System;
using System.Runtime.InteropServices;
public class MP {
  [DllImport("user32.dll", SetLastError=true)] public static extern bool PostMessage(IntPtr h, uint m, IntPtr w, IntPtr l);
  [DllImport("user32.dll")] public static extern uint MapVirtualKey(uint code, uint type);
  [DllImport("user32.dll")] public static extern bool GetClientRect(IntPtr h, out RECT r);
  [DllImport("user32.dll")] public static extern bool ClientToScreen(IntPtr h, ref POINT p);
  [DllImport("user32.dll")] public static extern bool MoveWindow(IntPtr h, int x, int y, int w, int hh, bool repaint);
  [StructLayout(LayoutKind.Sequential)] public struct RECT { public int L, T, R, B; }
  [StructLayout(LayoutKind.Sequential)] public struct POINT { public int X, Y; }
}
"@

$script:MCPostShotW = 0
$script:MCPostShotH = 0

function MC-Window {
  # javaw too: a launcher-installed instance runs javaw, runClient runs java.
  $p = Get-Process java, javaw -ErrorAction SilentlyContinue |
       Where-Object { $_.MainWindowTitle -like 'Minecraft*' } | Select-Object -First 1
  if (-not $p) { throw "no Minecraft window" }
  return $p.MainWindowHandle
}

function MC-Shots {
  if ($env:MC_SHOTS) { return $env:MC_SHOTS }
  return (Join-Path (Split-Path $PSScriptRoot -Parent) "run\screenshots")
}

# Client size in FRAMEBUFFER pixels. Identical to GetClientRect only on an unscaled display, which
# is why every coordinate here is scaled through the newest screenshot the way mc-drive.ps1 does.
function MC-FB {
  $r = New-Object MP+RECT
  [MP]::GetClientRect((MC-Window), [ref]$r) | Out-Null
  return @{ W = $r.R; H = $r.B }
}

# Coordinates are measured off an F2 screenshot, and a screenshot is framebuffer pixels while
# GetClientRect is logical ones. Read the ratio off a real screenshot, in every invocation --
# nothing persists between `powershell -Command` calls.
function Sync-Post {
  $latest = Get-ChildItem (MC-Shots) -Filter *.png | Sort-Object LastWriteTime | Select-Object -Last 1
  if (-not $latest) { throw "no screenshot yet - call Post-Shot once first" }
  Add-Type -AssemblyName System.Drawing
  $img = [System.Drawing.Image]::FromFile($latest.FullName)
  $script:MCPostShotW = $img.Width
  $script:MCPostShotH = $img.Height
  $img.Dispose()
  $fb = MC-FB
  return "screenshot $($script:MCPostShotW)x$($script:MCPostShotH), window $($fb.W)x$($fb.H)"
}

function Scan([uint32]$vk) { return [MP]::MapVirtualKey($vk, 0) }   # MAPVK_VK_TO_VSC

# One key, down and up, with the scancode GLFW actually reads. `hold` matters for anything the
# game samples per tick rather than per event -- movement, sneak.
function Post-Key([uint32]$vk, [int]$hold = 60) {
  $h = MC-Window
  $sc = Scan $vk
  $down = [IntPtr](1 -bor ($sc -shl 16))
  $up = [IntPtr](1 -bor ($sc -shl 16) -bor 0xC0000000)
  [MP]::PostMessage($h, 0x0100, [IntPtr]$vk, $down) | Out-Null
  Start-Sleep -Milliseconds $hold
  [MP]::PostMessage($h, 0x0101, [IntPtr]$vk, $up) | Out-Null
  Start-Sleep -Milliseconds 90
}

# <b>A modifier cannot be posted.</b> GLFW builds its mods mask with `GetKeyState`, which reads
# the state the *system* keeps for real key events; PostMessage bypasses that machinery entirely,
# so Ctrl is never held as far as the game is concerned and Ctrl+V pastes nothing. It cost two
# commands that arrived in chat as plain text with a stray character on the front -- the same
# symptom the clipboard route was written to cure, one layer deeper. Text goes through WM_CHAR
# instead, which is the callback GLFW turns into `charTyped`, one character at a time and in
# order, because a posted message queue cannot reorder.
function Post-Text([string]$text) {
  $h = MC-Window
  foreach ($ch in $text.ToCharArray()) {
    [MP]::PostMessage($h, 0x0102, [IntPtr][int]$ch, [IntPtr]1) | Out-Null
    Start-Sleep -Milliseconds 12
  }
  Start-Sleep -Milliseconds 200
}

function Post-Chord([uint32]$modVk, [uint32]$vk) {
  $h = MC-Window
  $ms = Scan $modVk
  $ks = Scan $vk
  [MP]::PostMessage($h, 0x0100, [IntPtr]$modVk, [IntPtr](1 -bor ($ms -shl 16))) | Out-Null
  Start-Sleep -Milliseconds 40
  [MP]::PostMessage($h, 0x0100, [IntPtr]$vk, [IntPtr](1 -bor ($ks -shl 16))) | Out-Null
  Start-Sleep -Milliseconds 60
  [MP]::PostMessage($h, 0x0101, [IntPtr]$vk, [IntPtr](1 -bor ($ks -shl 16) -bor 0xC0000000)) | Out-Null
  Start-Sleep -Milliseconds 40
  [MP]::PostMessage($h, 0x0101, [IntPtr]$modVk, [IntPtr](1 -bor ($ms -shl 16) -bor 0xC0000000)) | Out-Null
  Start-Sleep -Milliseconds 90
}

function Post-Where([int]$gx, [int]$gy) {
  if ($script:MCPostShotW -eq 0) { Sync-Post | Out-Null }
  $fb = MC-FB
  $x = [int]($gx * $fb.W / $script:MCPostShotW)
  $y = [int]($gy * $fb.H / $script:MCPostShotH)
  return @{ X = $x; Y = $y; L = [IntPtr](($y -shl 16) -bor ($x -band 0xFFFF)) }
}

# Move the cursor without pressing anything -- which is what a tooltip needs, and the only way to
# photograph one.
function Post-Aim([int]$gx, [int]$gy) {
  $at = Post-Where $gx $gy
  [MP]::PostMessage((MC-Window), 0x0200, [IntPtr]0, $at.L) | Out-Null
  Start-Sleep -Milliseconds 250
}

# In a GUI this aims. In the world the game uses the crosshair and the pointer is ignored, so aim
# with /tp first -- exactly as with mc-drive.ps1.
function Post-Click([int]$gx, [int]$gy, [switch]$Right, [switch]$Shift) {
  $h = MC-Window
  $at = Post-Where $gx $gy
  [MP]::PostMessage($h, 0x0200, [IntPtr]0, $at.L) | Out-Null
  Start-Sleep -Milliseconds 150
  if ($Shift) {
    $ss = Scan 0xA0
    [MP]::PostMessage($h, 0x0100, [IntPtr]0xA0, [IntPtr](1 -bor ($ss -shl 16))) | Out-Null
    Start-Sleep -Milliseconds 60
  }
  # wParam carries the button flags; MK_LBUTTON = 1, MK_RBUTTON = 2, MK_SHIFT = 4.
  $flags = if ($Shift) { 4 } else { 0 }
  if ($Right) {
    [MP]::PostMessage($h, 0x0204, [IntPtr]($flags -bor 2), $at.L) | Out-Null
    Start-Sleep -Milliseconds 110
    [MP]::PostMessage($h, 0x0205, [IntPtr]$flags, $at.L) | Out-Null
  } else {
    [MP]::PostMessage($h, 0x0201, [IntPtr]($flags -bor 1), $at.L) | Out-Null
    Start-Sleep -Milliseconds 110
    [MP]::PostMessage($h, 0x0202, [IntPtr]$flags, $at.L) | Out-Null
  }
  if ($Shift) {
    $ss = Scan 0xA0
    Start-Sleep -Milliseconds 60
    [MP]::PostMessage($h, 0x0101, [IntPtr]0xA0, [IntPtr](1 -bor ($ss -shl 16) -bor 0xC0000000)) | Out-Null
  }
  Start-Sleep -Milliseconds 250
}

# WM_MOUSEWHEEL is the one that takes SCREEN coordinates, not client ones. A negative count steps
# forward through the hotbar, the same way it does through mc-drive.ps1's Scroll.
function Post-Scroll([int]$gx, [int]$gy, [int]$notches) {
  $h = MC-Window
  $at = Post-Where $gx $gy
  $p = New-Object MP+POINT; $p.X = $at.X; $p.Y = $at.Y
  [MP]::ClientToScreen($h, [ref]$p) | Out-Null
  $l = [IntPtr](($p.Y -shl 16) -bor ($p.X -band 0xFFFF))
  $delta = if ($notches -lt 0) { -120 } else { 120 }
  for ($i = 0; $i -lt [Math]::Abs($notches); $i++) {
    [MP]::PostMessage($h, 0x020A, [IntPtr]($delta -shl 16), $l) | Out-Null
    Start-Sleep -Milliseconds 90
  }
  Start-Sleep -Milliseconds 200
}

# The game's own F2, so the capture is the game and never the desktop -- and it still works with
# the desktop showing a screensaver, which a desktop capture would photograph instead.
function Post-Shot {
  $dir = MC-Shots
  $before = @(Get-ChildItem $dir -Filter *.png -ErrorAction SilentlyContinue)
  Post-Key 0x71                                   # VK_F2
  for ($i = 0; $i -lt 40; $i++) {
    Start-Sleep -Milliseconds 250
    $now = @(Get-ChildItem $dir -Filter *.png -ErrorAction SilentlyContinue)
    if ($now.Count -gt $before.Count) {
      return ($now | Sort-Object LastWriteTime | Select-Object -Last 1).FullName
    }
  }
  throw "no screenshot appeared - is the window still alive?"
}

# Chat, through the clipboard for the reason mc-drive.ps1 gives: characters typed one WM_CHAR at a
# time arrive, but a long command is one paste and cannot be reordered. Select-all first, so a
# stray character left in the box is replaced rather than prepended.
function Post-Say([string]$text) {
  $h = MC-Window
  Post-Key 0x54                                   # T opens chat
  Start-Sleep -Milliseconds 500
  # Clear whatever a previous call left in the box. Backspace on an empty box does nothing, and
  # Ctrl+A is not available here -- see Post-Text. Cheap: these are posts, not keystrokes.
  $sc = Scan 0x08
  for ($i = 0; $i -lt 90; $i++) {
    [MP]::PostMessage($h, 0x0100, [IntPtr]0x08, [IntPtr](1 -bor ($sc -shl 16))) | Out-Null
    [MP]::PostMessage($h, 0x0101, [IntPtr]0x08, [IntPtr](1 -bor ($sc -shl 16) -bor 0xC0000000)) | Out-Null
  }
  Start-Sleep -Milliseconds 400
  Post-Text $text
  Start-Sleep -Milliseconds 300
  Post-Key 0x0D                                   # Enter
  Start-Sleep -Milliseconds 500
}

# Text into a focused EditBox (a rename box, a search box), not chat.
function Post-Paste([string]$text) {
  $h = MC-Window
  $sc = Scan 0x08
  for ($i = 0; $i -lt 64; $i++) {
    [MP]::PostMessage($h, 0x0100, [IntPtr]0x08, [IntPtr](1 -bor ($sc -shl 16))) | Out-Null
    [MP]::PostMessage($h, 0x0101, [IntPtr]0x08, [IntPtr](1 -bor ($sc -shl 16) -bor 0xC0000000)) | Out-Null
  }
  Start-Sleep -Milliseconds 300
  Post-Text $text
}

# The window size drifts and every GUI coordinate depends on it, so pin it -- and pin it on the
# 24" panel beside the 32" OLED, which is `Screen-24`'s whole reason for existing in mc-drive.ps1.
# MoveWindow needs no foreground, so this one is safe to call at any time.
function Post-Size([int]$w = 1600, [int]$h = 900, [int]$x = 40, [int]$y = 40) {
  [MP]::MoveWindow((MC-Window), $x, $y, $w, $h, $true) | Out-Null
  Start-Sleep -Milliseconds 900
  return MC-FB
}

function Post-Screen24 {
  Add-Type -AssemblyName System.Windows.Forms
  $s = ([System.Windows.Forms.Screen]::AllScreens | Sort-Object { $_.Bounds.Width * $_.Bounds.Height })[0]
  return @{ X = $s.Bounds.X; Y = $s.Bounds.Y; W = $s.Bounds.Width; H = $s.Bounds.Height }
}

# An unfocused client pauses unless this is off, and it is read once at startup -- so this has to
# run before the client is launched, not after.
function Ensure-Unpaused {
  $opts = Join-Path (Split-Path $PSScriptRoot -Parent) "run\options.txt"
  if (-not (Test-Path $opts)) { return "no options.txt yet" }
  $text = Get-Content $opts -Raw
  if ($text -match '(?m)^pauseOnLostFocus:') {
    $text = $text -replace '(?m)^pauseOnLostFocus:.*$', 'pauseOnLostFocus:false'
  } else {
    $text = $text.TrimEnd() + "`npauseOnLostFocus:false`n"
  }
  Set-Content -Path $opts -Value $text -NoNewline
  return "pauseOnLostFocus:false"
}

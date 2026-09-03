# Driving runClient from a background process. OPEN_ISSUES #22.
# Every function that sends input asserts the game is in the foreground first.

Add-Type @"
using System;
using System.Runtime.InteropServices;
public class W {
  [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
  [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
  [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr h, int n);
  [DllImport("user32.dll")] public static extern bool BringWindowToTop(IntPtr h);
  [DllImport("user32.dll")] public static extern uint GetWindowThreadProcessId(IntPtr h, out uint pid);
  [DllImport("user32.dll")] public static extern bool AttachThreadInput(uint a, uint b, bool f);
  [DllImport("kernel32.dll")] public static extern uint GetCurrentThreadId();
  [DllImport("user32.dll")] public static extern bool SystemParametersInfo(uint a, uint b, IntPtr c, uint d);
  [DllImport("user32.dll")] public static extern void keybd_event(byte vk, byte scan, uint flags, IntPtr extra);
  [DllImport("user32.dll")] public static extern bool SetCursorPos(int x, int y);
  [DllImport("user32.dll")] public static extern void mouse_event(uint flags, uint dx, uint dy, uint data, IntPtr extra);
  [DllImport("user32.dll")] public static extern bool GetClientRect(IntPtr h, out RECT r);
  [DllImport("user32.dll")] public static extern bool ClientToScreen(IntPtr h, ref POINT p);
  [DllImport("user32.dll")] public static extern bool MoveWindow(IntPtr h, int x, int y, int w, int hh, bool repaint);
  [StructLayout(LayoutKind.Sequential)] public struct RECT { public int L, T, R, B; }
  [StructLayout(LayoutKind.Sequential)] public struct POINT { public int X, Y; }
}
"@

$SPI_SETFOREGROUNDLOCKTIMEOUT = 0x2001
$KEYEVENTF_KEYUP = 0x2

function Get-MC {
  $p = Get-Process java -ErrorAction SilentlyContinue |
       Where-Object { $_.MainWindowTitle -like 'Minecraft*' } | Select-Object -First 1
  if (-not $p) { throw "no Minecraft window" }
  return $p
}

# The fix OPEN_ISSUES #22 had not tried: drop the foreground lock timeout to zero for this
# user, then the usual AttachThreadInput dance is allowed to work. Restore-then-raise is the
# fallback, because restoring a minimised window is foregrounded by the shell, not by us.
function Focus-MC {
  $p = Get-MC
  $h = $p.MainWindowHandle
  [W]::SystemParametersInfo($SPI_SETFOREGROUNDLOCKTIMEOUT, 0, [IntPtr]::Zero, 3) | Out-Null
  $fg = [W]::GetForegroundWindow()
  $me = [W]::GetCurrentThreadId()
  $ot = [W]::GetWindowThreadProcessId($fg, [ref]([uint32]0))
  [W]::AttachThreadInput($me, $ot, $true) | Out-Null
  [W]::ShowWindow($h, 9) | Out-Null     # SW_RESTORE
  [W]::BringWindowToTop($h) | Out-Null
  [W]::SetForegroundWindow($h) | Out-Null
  [W]::AttachThreadInput($me, $ot, $false) | Out-Null
  Start-Sleep -Milliseconds 400
  if (-not (In-Front $p.Id)) {
    [W]::ShowWindow($h, 6) | Out-Null   # SW_MINIMIZE
    Start-Sleep -Milliseconds 300
    [W]::ShowWindow($h, 9) | Out-Null   # SW_RESTORE
    Start-Sleep -Milliseconds 600
  }
  return (In-Front $p.Id)
}

function In-Front([int]$processId) {
  $owner = [uint32]0
  [W]::GetWindowThreadProcessId([W]::GetForegroundWindow(), [ref]$owner) | Out-Null
  return ($owner -eq $processId)
}

# Never send input into whatever the human is using. OPEN_ISSUES #22.
# Compared by process id, not window handle: going fullscreen gives GLFW a different window and
# Process.MainWindowHandle is a snapshot, so a handle comparison starts failing on a window that
# is in fact in front.
function Assert-MC {
  $p = Get-MC
  if (-not (In-Front $p.Id)) {
    # Something took the foreground back -- the launcher finishing, the desktop app, a redraw.
    # One retry, then refuse: the guard is what stops keystrokes landing in the human's window.
    Focus-MC | Out-Null
    Start-Sleep -Milliseconds 300
  }
  if (-not (In-Front $p.Id)) { throw "Minecraft is NOT in the foreground - refusing to send input" }
  return [W]::GetForegroundWindow()
}

function Key([byte]$vk, [int]$hold = 40) {
  Assert-MC | Out-Null
  [W]::keybd_event($vk, 0, 0, [IntPtr]::Zero)
  Start-Sleep -Milliseconds $hold
  [W]::keybd_event($vk, 0, $KEYEVENTF_KEYUP, [IntPtr]::Zero)
  Start-Sleep -Milliseconds 60
}

# GLFW reads the key callback, so SendKeys' WM_CHAR never arrives; and SendKeys drops and
# reorders characters anyway. Command text goes through the clipboard instead.
function CtrlKey([byte]$vk) {
  [W]::keybd_event(0x11, 0, 0, [IntPtr]::Zero)
  [W]::keybd_event($vk, 0, 0, [IntPtr]::Zero)
  Start-Sleep -Milliseconds 60
  [W]::keybd_event($vk, 0, $KEYEVENTF_KEYUP, [IntPtr]::Zero)
  [W]::keybd_event(0x11, 0, $KEYEVENTF_KEYUP, [IntPtr]::Zero)
}

# Commands used to arrive with a stray leading character -- "x/give ..." -- which Minecraft then
# sends as chat instead of running. The cause is the T landing inside a chat box that a previous
# call left open, so it types a letter instead of opening anything. Ctrl+A before the paste fixes
# it whatever the residue was, because the paste then replaces a selection rather than appending to
# it. Do NOT "helpfully" press Escape first: with nothing open, Escape opens the pause menu, and
# every key after it goes nowhere.
function Say([string]$text) {
  Assert-MC | Out-Null
  Set-Clipboard -Value $text
  Start-Sleep -Milliseconds 300
  Key 0x54            # T
  Start-Sleep -Milliseconds 400
  CtrlKey 0x41         # select whatever is already in the box, stray or not
  Start-Sleep -Milliseconds 80
  CtrlKey 0x56         # replaces the selection with the clipboard
  Start-Sleep -Milliseconds 250
  Key 0x0D            # Return
  Start-Sleep -Milliseconds 350
}

# GUI coordinates are in framebuffer pixels; the window rect is not, on a scaled display.
function FB {
  $h = Assert-MC
  $r = New-Object W+RECT
  [W]::GetClientRect($h, [ref]$r) | Out-Null
  return @{ W = $r.R; H = $r.B }
}

# gx,gy are framebuffer pixels measured off an F2 screenshot.
function Click([int]$gx, [int]$gy, [switch]$Right) {
  $h = Assert-MC
  $fb = FB
  $p = New-Object W+POINT
  $p.X = [int]($gx * $fb.W / $script:ShotW)
  $p.Y = [int]($gy * $fb.H / $script:ShotH)
  [W]::ClientToScreen($h, [ref]$p) | Out-Null
  [W]::SetCursorPos($p.X, $p.Y) | Out-Null
  Start-Sleep -Milliseconds 120
  if ($Right) { [W]::mouse_event(0x08, 0, 0, 0, [IntPtr]::Zero); Start-Sleep -Milliseconds 60; [W]::mouse_event(0x10, 0, 0, 0, [IntPtr]::Zero) }
  else { [W]::mouse_event(0x02, 0, 0, 0, [IntPtr]::Zero); Start-Sleep -Milliseconds 60; [W]::mouse_event(0x04, 0, 0, 0, [IntPtr]::Zero) }
  Start-Sleep -Milliseconds 200
}

function Drag([int]$gx, [int]$gy, [int]$tx, [int]$ty) {
  $h = Assert-MC
  $fb = FB
  $a = New-Object W+POINT; $a.X = [int]($gx * $fb.W / $script:ShotW); $a.Y = [int]($gy * $fb.H / $script:ShotH)
  $b = New-Object W+POINT; $b.X = [int]($tx * $fb.W / $script:ShotW); $b.Y = [int]($ty * $fb.H / $script:ShotH)
  [W]::ClientToScreen($h, [ref]$a) | Out-Null
  [W]::ClientToScreen($h, [ref]$b) | Out-Null
  [W]::SetCursorPos($a.X, $a.Y) | Out-Null
  Start-Sleep -Milliseconds 150
  [W]::mouse_event(0x02, 0, 0, 0, [IntPtr]::Zero)
  $steps = 12
  for ($i = 1; $i -le $steps; $i++) {
    [W]::SetCursorPos([int]($a.X + ($b.X - $a.X) * $i / $steps), [int]($a.Y + ($b.Y - $a.Y) * $i / $steps)) | Out-Null
    Start-Sleep -Milliseconds 35
  }
  Start-Sleep -Milliseconds 100
  [W]::mouse_event(0x04, 0, 0, 0, [IntPtr]::Zero)
  Start-Sleep -Milliseconds 250
}

function Aim([int]$gx, [int]$gy) {
  $h = Assert-MC
  $fb = FB
  $p = New-Object W+POINT; $p.X = [int]($gx * $fb.W / $script:ShotW); $p.Y = [int]($gy * $fb.H / $script:ShotH)
  [W]::ClientToScreen($h, [ref]$p) | Out-Null
  [W]::SetCursorPos($p.X, $p.Y) | Out-Null
  Start-Sleep -Milliseconds 200
}

# The game's own F2, so the capture is the game and never the desktop.
$script:Shots = "C:\Users\Neryos\Desktop\Coding\CodingWithAI\Minecraft Mods\Clean Envitoment\run\screenshots"

function Shot {
  Assert-MC | Out-Null
  Key 0x71            # F2
  Start-Sleep -Milliseconds 1200
  $f = Get-ChildItem $script:Shots -Filter *.png | Sort-Object LastWriteTime -Descending | Select-Object -First 1
  Write-Output $f.FullName
}

# Screenshot pixels are framebuffer pixels; call this once the window is up so Click/Drag map 1:1.
function Sync-Shot {
  $fb = FB
  $script:ShotW = $fb.W
  $script:ShotH = $fb.H
  Write-Output "framebuffer $($fb.W)x$($fb.H)"
}

# Placing a block against a container needs sneak, or the container swallows the right-click.
function SneakClick([int]$gx, [int]$gy) {
  Assert-MC | Out-Null
  [W]::keybd_event(0xA0, 0, 0, [IntPtr]::Zero)     # LSHIFT down
  Start-Sleep -Milliseconds 250
  Click $gx $gy -Right
  Start-Sleep -Milliseconds 250
  [W]::keybd_event(0xA0, 0, $KEYEVENTF_KEYUP, [IntPtr]::Zero)
  Start-Sleep -Milliseconds 200
}

# The window size drifts across focus cycles, and GUI coordinates depend on it. Pin it.
function Size-MC([int]$w = 1600, [int]$h = 900) {
  $handle = Assert-MC
  [W]::MoveWindow($handle, 40, 40, $w, $h, $true) | Out-Null
  Start-Sleep -Milliseconds 800
  FB
}

# Text into a focused EditBox (not chat, which Say is for): clipboard, same reason as Say.
function PasteText([string]$text) {
  Assert-MC | Out-Null
  Set-Clipboard -Value $text
  Start-Sleep -Milliseconds 300
  CtrlKey 0x41
  Start-Sleep -Milliseconds 80
  CtrlKey 0x56
  Start-Sleep -Milliseconds 250
}

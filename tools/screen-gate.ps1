# Screen gate: every Minecraft window lives on the 24" panel, and the 32" OLED shows black.
#
#   Start-Process powershell -ArgumentList '-WindowStyle','Hidden','-File','tools\screen-gate.ps1'
#   New-Item night\gate-stop      # stops it (or close the black window with Alt+F4 while it is focused)
#
# Why: the 32" is an OLED and the 24" is the unscaled panel the drivers are calibrated on
# (OPEN_ISSUES: "drive the client on the unscaled 24 panel"). Every launcher opens on the primary,
# which is the OLED, so a gate is needed rather than a launch argument. Once a second: any window
# titled Minecraft whose centre is not on the keep screen is moved there, size kept, clamped to the
# screen; a black, borderless, topmost form covers the other screen the whole time. Moves are
# logged to night/gate.log. The 24" is found by size (1920x1200), so no display index is hard-coded.
param([int]$KeepWidth = 1920, [int]$KeepHeight = 1200)
Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing
$sig = @'
using System; using System.Runtime.InteropServices;
public class Gate {
  [StructLayout(LayoutKind.Sequential)] public struct RECT { public int L, T, R, B; }
  [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr h, out RECT r);
  [DllImport("user32.dll")] public static extern bool MoveWindow(IntPtr h, int x, int y, int w, int hgt, bool repaint);
  [DllImport("user32.dll")] public static extern bool IsIconic(IntPtr h);
}
'@
Add-Type -TypeDefinition $sig
$root = Split-Path $PSScriptRoot -Parent
$log = Join-Path $root 'night\gate.log'
$stop = Join-Path $root 'night\gate-stop'
$screens = [System.Windows.Forms.Screen]::AllScreens
$keep = $screens | Where-Object { $_.Bounds.Width -eq $KeepWidth -and $_.Bounds.Height -eq $KeepHeight } | Select-Object -First 1
if (-not $keep) { "no ${KeepWidth}x${KeepHeight} screen found" | Out-File $log -Append; exit 1 }
$others = $screens | Where-Object { $_.DeviceName -ne $keep.DeviceName }
"$(Get-Date -Format s) gate up: keep $($keep.DeviceName) $($keep.Bounds); black: $($others.DeviceName -join ',')" | Out-File $log -Append

$forms = foreach ($s in $others) {
  $f = New-Object System.Windows.Forms.Form
  $f.FormBorderStyle = 'None'; $f.BackColor = [System.Drawing.Color]::Black; $f.TopMost = $true
  $f.ShowInTaskbar = $false; $f.StartPosition = 'Manual'; $f.Bounds = $s.Bounds; $f.Text = 'OLED guard (Workbay night run)'
  $f
}
$timer = New-Object System.Windows.Forms.Timer
$timer.Interval = 1000
$timer.Add_Tick({
  if (Test-Path $stop) { Remove-Item $stop -Force; [System.Windows.Forms.Application]::Exit(); return }
  foreach ($f in $forms) { if (-not $f.TopMost) { $f.TopMost = $true }; $f.Bounds = ($others | Where-Object { $_.Bounds -eq $f.Bounds } | Select-Object -First 1).Bounds }
  foreach ($p in (Get-Process -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowTitle -match 'Minecraft' })) {
    $h = $p.MainWindowHandle
    if ($h -eq 0 -or [Gate]::IsIconic($h)) { continue }
    $r = New-Object Gate+RECT
    if (-not [Gate]::GetWindowRect($h, [ref]$r)) { continue }
    $cx = [int](($r.L + $r.R) / 2); $cy = [int](($r.T + $r.B) / 2)
    if ($keep.Bounds.Contains($cx, $cy)) { continue }
    $w = [Math]::Min($r.R - $r.L, $keep.Bounds.Width); $hgt = [Math]::Min($r.B - $r.T, $keep.Bounds.Height)
    $x = $keep.Bounds.X + [Math]::Max(0, [int](($keep.Bounds.Width - $w) / 2))
    $y = $keep.Bounds.Y + [Math]::Max(0, [int](($keep.Bounds.Height - $hgt) / 2))
    [Gate]::MoveWindow($h, $x, $y, $w, $hgt, $true) | Out-Null
    "$(Get-Date -Format s) moved pid $($p.Id) '$($p.MainWindowTitle)' from ($($r.L),$($r.T)) to ($x,$y) ${w}x${hgt}" | Out-File $log -Append
  }
})
$timer.Start()
foreach ($f in $forms) { $f.Show() }
[System.Windows.Forms.Application]::Run()
"$(Get-Date -Format s) gate down" | Out-File $log -Append

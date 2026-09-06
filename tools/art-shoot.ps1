# Renders one art direction in four fixed shots, into run/art-shots. Same camera, same time of
# day, same framing for every direction -- that is the whole point, so the coordinates live here
# and nothing about them changes between runs.
#
#   python tools/make-art.py rack alarm && ./gradlew processResources
#   <F3+T in the client>
#   powershell -File tools/art-shoot.ps1 -Tag rack-alarm [-Build]
#
# -Build digs the two sites first: an open daylight platform and a sealed unlit room with the
# identical arrangement inside it, both at y=150 around x=200. Without it they are assumed to be
# there already, which they are in run/saves/New World.
param([string]$Tag = "cabinet", [switch]$Build)

$ErrorActionPreference = "Stop"
$repo = "C:\Users\Neryos\Desktop\Coding\CodingWithAI\Minecraft Mods\Clean Envitoment"
. "$repo\tools\mc-drive.ps1"

$out = "$repo\run\art-shots"
New-Item -ItemType Directory -Force -Path $out | Out-Null

Add-Type -AssemblyName System.Drawing
Focus-MC | Out-Null
Shot | Out-Null
Sync-Shot | Out-Null

# F1 is a toggle with no way to ask which way it is set, and one dropped keypress puts every
# later shot on the wrong side of it. Read the answer off the picture instead: the hotbar is
# dark where the daylight floor behind it is not.
function HudVisible {
  $f = Get-ChildItem $script:Shots -Filter *.png | Sort-Object LastWriteTime -Descending |
       Select-Object -First 1
  $img = [System.Drawing.Bitmap]::FromFile($f.FullName)
  $y = $img.Height - 50
  $sum = 0; $n = 0
  for ($x = [int]($img.Width / 2) - 200; $x -lt [int]($img.Width / 2) + 200; $x += 10) {
    $c = $img.GetPixel($x, $y); $sum += ($c.R + $c.G + $c.B) / 3; $n++
  }
  $img.Dispose()
  return (($sum / $n) -lt 100)
}

# The three separated states, and the run of six the way a base really has them.
# Cleared to air first: /setblock on a block that is already in that state errors, and the
# error is printed to the sender whatever sendCommandFeedback says.
function Place([int]$z0, [int]$z1) {
  Say "/fill 197 150 $z0 203 150 $z0 minecraft:air"
  Say "/fill 197 150 $z1 202 150 $z1 minecraft:air"
  $i = 0
  foreach ($s in @("idle", "running", "stuck")) {
    Say "/setblock $(197 + $i * 3) 150 $z0 workbay:workbay[facing=south,state=$s,powered=false]"
    $i++
  }
  $mix = @("idle", "running", "running", "idle", "stuck", "running")
  for ($j = 0; $j -lt 6; $j++) {
    Say "/setblock $(197 + $j) 150 $z1 workbay:workbay[facing=north,state=$($mix[$j]),powered=false]"
  }
}

function Frame([string]$tp, [string]$name) {
  Say $tp
  Start-Sleep -Milliseconds 900
  $f = Shot
  Copy-Item $f (Join-Path $out "$Tag-$name.png") -Force
  Write-Output "$Tag-$name <- $(Split-Path $f -Leaf)"
}

if ($Build) {
  Say "/gamemode creative"
  Say "/gamerule doDaylightCycle false"
  Say "/gamerule doWeatherCycle false"
  Say "/gamerule sendCommandFeedback false"
  Say "/weather clear"
  Say "/time set noon"
  Say "/tp @s 200 160 206"
  # daylight site: an open platform, two backing walls, sky behind everything else
  Say "/fill 190 150 190 212 170 216 minecraft:air"
  Say "/fill 190 149 190 212 149 216 minecraft:smooth_stone"
  Say "/fill 190 150 199 212 152 199 minecraft:smooth_stone"
  Say "/fill 190 150 211 212 152 211 minecraft:smooth_stone"
  # unlit site: a sealed stone box with the identical arrangement inside it
  Say "/fill 190 148 290 212 158 316 minecraft:smooth_stone"
  Say "/fill 191 150 291 211 156 315 minecraft:air"
  Say "/fill 191 150 299 211 152 299 minecraft:smooth_stone"
  Say "/fill 191 150 311 211 152 311 minecraft:smooth_stone"
  # visit both so their chunks are loaded and meshed before the freeze
  Say "/tp @s 200.5 150 306.5 180 8"
  Start-Sleep -Seconds 3
  Say "/tp @s 200.5 150 206.5 180 8"
  Start-Sleep -Seconds 3
}

# A Workbay placed by /setblock has no record, and refreshLitState writes it back to idle on its
# next tick. Freezing is the only way to hold all three states in one frame -- and it freezes the
# stuck strip on its bright frame, so the pulse's trough is judged offline instead.
Say "/tick freeze"
Place 200 210
Place 300 310

# Into the daylight site, where HudVisible can read the floor, and hide the HUD once.
Say "/tp @s 200.5 150 206.5 180 8"
Start-Sleep -Milliseconds 900
Shot | Out-Null
if (HudVisible) { Key 0x70; Start-Sleep -Milliseconds 600 }
Shot | Out-Null
if (HudVisible) { throw "F1 did not take - HUD still visible" }

Frame "/tp @s 200.5 150 206.5 180 8"  "day-states"
Frame "/tp @s 200.0 150 204.5 0 8"    "day-row"
Frame "/tp @s 200.5 150 306.5 180 8"  "dark-states"
Frame "/tp @s 200.0 150 304.5 0 8"    "dark-row"

Say "/tp @s 200.5 150 206.5 180 8"
Start-Sleep -Milliseconds 800
Shot | Out-Null
if (-not (HudVisible)) { Key 0x70 }
Say "/tick unfreeze"
Write-Output "done $Tag"

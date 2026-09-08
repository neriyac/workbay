# Turns every display off. Neriya's primary panel is a 32" OLED; anything static left on it for
# hours is how one burns, so a long unattended run blanks the desk whenever it is not being
# driven. Any mouse or key event wakes them again, which is exactly what mc-drive.ps1 sends -- so
# call this at the end of a driving burst, not before one.
#
# SendNotifyMessage, never SendMessage: a broadcast SendMessage blocks on every top-level window
# on the desktop and one of them is always busy, so the call sits there for ever. Cost a hung
# process to find.
Add-Type @"
using System;
using System.Runtime.InteropServices;
public class Mon {
  [DllImport("user32.dll", SetLastError=true)]
  public static extern bool SendNotifyMessage(IntPtr h, uint msg, IntPtr wp, IntPtr lp);
}
"@
[Mon]::SendNotifyMessage([IntPtr]0xFFFF, 0x0112, [IntPtr]0xF170, [IntPtr]2) | Out-Null
Write-Output "displays off"

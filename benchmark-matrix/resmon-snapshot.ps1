# Opens Resource Monitor, lets its graphs fill, saves a screenshot of its window, and closes it: a visual record of the
# machine at the start and at the end of a run window, next to the per-process CPU log that the driver writes.
# Resource Monitor runs as administrator, so run this script from an administrator PowerShell, e.g.:
#
#   powershell -NoProfile -ExecutionPolicy Bypass -File benchmark-matrix\resmon-snapshot.ps1 results\rq1-rest\screenshots\start-20261105.png
#
# It captures the Resource Monitor window itself, so other windows on the screen never appear in the screenshot.
param(
    [Parameter(Mandatory = $true)][string]$OutFile,
    [int]$WaitSeconds = 30
)

$principal = New-Object Security.Principal.WindowsPrincipal([Security.Principal.WindowsIdentity]::GetCurrent())
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    throw "Run this script from an administrator PowerShell, since Resource Monitor runs as administrator."
}

Add-Type -AssemblyName System.Drawing
Add-Type @"
using System;
using System.Runtime.InteropServices;
public static class ResmonWindow {
    [StructLayout(LayoutKind.Sequential)] public struct RECT { public int Left, Top, Right, Bottom; }
    [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr hWnd, int nCmdShow);
    [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr hWnd, out RECT rect);
    [DllImport("user32.dll")] public static extern bool PrintWindow(IntPtr hWnd, IntPtr hdc, uint flags);
}
"@

$before = @(Get-Process perfmon -ErrorAction SilentlyContinue | ForEach-Object { $_.Id })
Start-Process resmon.exe
$monitor = $null
for ($i = 0; $i -lt 40 -and -not $monitor; $i++) {
    Start-Sleep -Milliseconds 500
    $monitor = Get-Process perfmon -ErrorAction SilentlyContinue |
        Where-Object { $before -notcontains $_.Id -and $_.MainWindowHandle -ne 0 } |
        Select-Object -First 1
}
if (-not $monitor) { throw "Resource Monitor did not start" }

try {
    [ResmonWindow]::ShowWindow($monitor.MainWindowHandle, 3) | Out-Null   # maximized
    Start-Sleep -Seconds $WaitSeconds

    $rect = New-Object ResmonWindow+RECT
    [ResmonWindow]::GetWindowRect($monitor.MainWindowHandle, [ref]$rect) | Out-Null
    $bitmap = New-Object System.Drawing.Bitmap ($rect.Right - $rect.Left), ($rect.Bottom - $rect.Top)
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    $hdc = $graphics.GetHdc()
    # PW_RENDERFULLCONTENT captures the window even when other windows cover it
    $captured = [ResmonWindow]::PrintWindow($monitor.MainWindowHandle, $hdc, 2)
    $graphics.ReleaseHdc($hdc)
    if (-not $captured) { throw "Cannot capture the Resource Monitor window" }
    New-Item -ItemType Directory -Force (Split-Path -Parent ([IO.Path]::GetFullPath($OutFile))) | Out-Null
    $bitmap.Save([IO.Path]::GetFullPath($OutFile), [System.Drawing.Imaging.ImageFormat]::Png)
    $graphics.Dispose()
    $bitmap.Dispose()
    "Wrote $OutFile"
} finally {
    Stop-Process -Id $monitor.Id -ErrorAction SilentlyContinue
}

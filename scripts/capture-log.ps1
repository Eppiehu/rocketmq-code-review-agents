# 打开一个控制台窗口显示日志内容, 然后把该窗口截图保存为 PNG
# 用法: .\capture-log.ps1 <日志名(不含 .log)> [列数]
$Name = $args[0]
$Cols = if ($args.Count -gt 1) { [int]$args[1] } else { 150 }
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$log = Join-Path $root "logs\$Name.log"
$png = Join-Path $root "screenshots\$Name.png"
New-Item -ItemType Directory -Force (Split-Path $png) | Out-Null
$lines = (Get-Content -Encoding UTF8 $log).Count
$rows = [Math]::Max($lines + 4, 10)
$title = "RocketMQ homework - $Name"

$inner = @"
`$OutputEncoding=[Console]::OutputEncoding=[Text.Encoding]::UTF8
`$host.UI.RawUI.WindowTitle='$title'
`$host.UI.RawUI.BufferSize=New-Object Management.Automation.Host.Size($Cols,300)
`$host.UI.RawUI.WindowSize=New-Object Management.Automation.Host.Size($Cols,$rows)
Set-Location '$root'
Clear-Host
Write-Host ('PS ' + (Get-Location) + '> Get-Content .\logs\$Name.log')
Get-Content -Encoding UTF8 .\logs\$Name.log
Start-Sleep 30
"@
$enc = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($inner))
$proc = Start-Process conhost.exe -ArgumentList "powershell.exe -NoProfile -EncodedCommand $enc" -PassThru

Add-Type -ReferencedAssemblies System.Drawing @"
using System; using System.Runtime.InteropServices; using System.Drawing;
public class Cap {
  [DllImport("user32.dll")] public static extern bool SetProcessDPIAware();
  [DllImport("user32.dll")] public static extern IntPtr FindWindow(string c, string t);
  [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
  [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr h, out RECT r);
  [DllImport("user32.dll")] public static extern bool PrintWindow(IntPtr h, IntPtr hdc, uint f);
  [DllImport("user32.dll")] public static extern bool SetWindowPos(IntPtr h, IntPtr a, int x, int y, int cx, int cy, uint f);
  public struct RECT { public int L, T, R, B; }
  public static void Shot(IntPtr h, string path) {
    RECT r; GetWindowRect(h, out r);
    using (var bmp = new Bitmap(r.R - r.L, r.B - r.T)) {
      using (var g = Graphics.FromImage(bmp)) {
        IntPtr hdc = g.GetHdc(); PrintWindow(h, hdc, 2); g.ReleaseHdc(hdc);
      }
      bmp.Save(path, System.Drawing.Imaging.ImageFormat.Png);
    }
  }
}
"@
[Cap]::SetProcessDPIAware() | Out-Null
$h = [IntPtr]::Zero
for ($i = 0; $i -lt 120 -and $h -eq [IntPtr]::Zero; $i++) { Start-Sleep -Milliseconds 250; $w = Get-Process powershell -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowTitle -eq $title } | Select-Object -First 1; if ($w) { $h = $w.MainWindowHandle } }
if ($h -eq [IntPtr]::Zero) { throw "window not found: $title" }
[Cap]::SetWindowPos($h, [IntPtr]::Zero, 0, 0, 0, 0, 0x0001) | Out-Null
[Cap]::SetForegroundWindow($h) | Out-Null
Start-Sleep -Milliseconds 1500
[Cap]::Shot($h, $png)
Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue
Get-Process powershell -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowTitle -eq $title } | Stop-Process -Force
Write-Output "saved $png"

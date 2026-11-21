# Captures only the probe's window through PrintWindow, never the user's desktop.
Add-Type -ReferencedAssemblies System.Drawing -TypeDefinition @'
using System;
using System.Drawing;
using System.Drawing.Imaging;
using System.Runtime.InteropServices;
public static class StartupWindowCapture {
    [StructLayout(LayoutKind.Sequential)]
    private struct Rect { public int Left, Top, Right, Bottom; }
    [DllImport("user32.dll")]
    private static extern bool GetWindowRect(IntPtr window, out Rect rect);
    [DllImport("user32.dll")]
    private static extern bool PrintWindow(IntPtr window, IntPtr dc, uint flags);
    public static bool Save(IntPtr window, string path) {
        Rect bounds;
        if (window == IntPtr.Zero || !GetWindowRect(window, out bounds)) return false;
        int width = bounds.Right - bounds.Left, height = bounds.Bottom - bounds.Top;
        if (width <= 0 || height <= 0) return false;
        using (var bitmap = new Bitmap(width, height)) {
            using (var graphics = Graphics.FromImage(bitmap)) {
                IntPtr dc = graphics.GetHdc();
                bool captured;
                try { captured = PrintWindow(window, dc, 2); }
                finally { graphics.ReleaseHdc(dc); }
                if (!captured) return false;
            }
            bitmap.Save(path, ImageFormat.Png);
        }
        return true;
    }
}
'@

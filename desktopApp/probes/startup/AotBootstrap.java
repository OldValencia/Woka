import com.woka.app.di.KoinKt;
import java.lang.management.ManagementFactory;
import java.util.Collections;

/** Headless compatibility smoke, not a measurement of the first displayed frame. */
public final class AotBootstrap {
    public static void main(String[] args) throws Exception {
        long start = System.nanoTime();
        KoinKt.initKoin(Collections.emptyList());
        ClassLoader loader = AotBootstrap.class.getClassLoader();
        for (String name : new String[] {
            "com.woka.MainKt",
            "androidx.compose.ui.awt.ComposeWindow",
            "org.jetbrains.skia.Bitmap",
            "org.bytedeco.javacv.FFmpegFrameGrabber"
        }) Class.forName(name, false, loader);
        var heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        System.out.printf("BOOTSTRAP_OK elapsedMs=%.3f heapUsedMiB=%d heapCommittedMiB=%d%n",
            (System.nanoTime() - start) / 1e6, heap.getUsed() / 1048576, heap.getCommitted() / 1048576);
    }
}

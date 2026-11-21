package nativeimage

import org.gradle.api.GradleException
import java.io.File

/**
 * GraalVM's home from the Gradle property `woka.graalvm.home` or `GRAALVM_HOME` — or a failure
 * that names both. Neither GraalVM nor its path belongs in the repository.
 */
fun graalvmHome(home: String?): File {
    val dir = File(
        home ?: throw GradleException(
            "No GraalVM: set woka.graalvm.home in ~/.gradle/gradle.properties, or GRAALVM_HOME"
        )
    )
    if (!File(dir, "bin/native-image.cmd").isFile) throw GradleException("$dir is not a GraalVM home (no bin/native-image.cmd)")
    return dir
}

/** The jars the distributable's launcher puts on the classpath, in its order (`Woka.cfg`). */
fun appClasspath(appDir: File): List<File> =
    File(appDir, "Woka.cfg").readLines()
        .filter { it.startsWith("app.classpath=") }
        .map { File(it.substringAfter('=').replace("\$APPDIR", appDir.path)) }

/**
 * Everything `native-image` is told, beyond the classpath. Each switch is ST-001's finding:
 * a GUI-subsystem executable (a console one waits ~300 ms for a terminal before `main`);
 * `compatibility`, never `native` (which miscompiled Compose's `Matrix.invert()`); all locales
 * (a date in a region the build machine is not in hung the Info panel); the heap the JVM's
 * `-Xmx` caps; Bytedeco's DLLs left out, since they load from `resources\javacpp`.
 */
fun nativeImageArguments(
    appDir: File,
    configDirs: List<File>,
    output: File,
    strict: Boolean,
    resources: File? = null
): List<String> =
    listOfNotNull(
        "-cp", appClasspath(appDir).joinToString(File.pathSeparator),
        "-o", File(output, "Woka").path,
        "--no-fallback",
        "-march=compatibility",
        "-H:+UnlockExperimentalVMOptions",
        "-H:ConfigurationFileDirectories=" + configDirs.joinToString(",") { it.path },
        "-H:ExcludeResources=.*\\.(dll|so|dylib)$",
        "-H:+IncludeAllLocales",
        "-H:NativeLinkerOption=/SUBSYSTEM:WINDOWS",
        "-H:NativeLinkerOption=/ENTRY:mainCRTStartup",
        resources?.let { resourceLinkerOption(it) },
        "--enable-native-access=ALL-UNNAMED",
        "-R:MaxHeapSize=1536m",
        "-H:+ReportExceptionStackTraces",
        "-J-Xmx16g",
        "--exact-reachability-metadata".takeIf { strict },
        "com.woka.MainKt"
    )

/** The newest Visual Studio with the C++ tools, through `vswhere` — GraalVM's own search knows only VS 2022. */
fun visualStudio(): File {
    val vswhere = File(System.getenv("ProgramFiles(x86)") ?: "C:/Program Files (x86)", "Microsoft Visual Studio/Installer/vswhere.exe")
    if (!vswhere.isFile) throw GradleException("No vswhere at $vswhere: install Visual Studio with the C++ tools")
    val process = ProcessBuilder(vswhere.path, "-latest", "-prerelease", "-products", "*", "-property", "installationPath")
        .redirectErrorStream(true).start()
    val path = process.inputStream.bufferedReader().readText().trim().lineSequence().firstOrNull().orEmpty()
    process.waitFor()
    val home = File(path)
    if (!File(home, "VC/Auxiliary/Build/vcvars64.bat").isFile) throw GradleException("No vcvars64.bat under '$path'")
    return home
}

/**
 * Runs `native-image` inside `vcvars64.bat`, with the arguments in an `@argfile` (the
 * classpath is longer than a command line) and System32 put back on `PATH` (a Gradle daemon
 * can start without it, and `vcvars64` calls tools from there). [resources], an `.rc` script,
 * is compiled by `rc.exe` first, in the same shell. Fails with the log's tail.
 */
fun runNativeImage(graalHome: File, arguments: List<String>, work: File, resources: File? = null) {
    work.mkdirs()
    val argFile = File(work, "args.txt")
    argFile.writeText(arguments.joinToString("\n") { "\"" + it.replace("\\", "\\\\") + "\"" })
    val vcvars = File(visualStudio(), "VC/Auxiliary/Build/vcvars64.bat")
    val script = File(work, "build.cmd")
    script.writeText(
        listOfNotNull(
            "@set \"PATH=%SystemRoot%\\System32;%SystemRoot%;%SystemRoot%\\System32\\Wbem;%PATH%\"",
            "@call \"${vcvars.path}\" >nul",
            resources?.let { "@rc /nologo /fo \"${compiledResource(it).path}\" \"${it.path}\" || exit /b 1" },
            "@\"${File(graalHome, "bin/native-image.cmd").path}\" \"@${argFile.path}\""
        ).joinToString("\r\n")
    )
    val log = File(work, "build.log")
    val shell = System.getenv("ComSpec") ?: "${System.getenv("SystemRoot")}\\System32\\cmd.exe"
    val code = ProcessBuilder(shell, "/c", script.path).redirectErrorStream(true).redirectOutput(log).start().waitFor()
    if (code != 0) {
        throw GradleException("native-image failed ($code); last lines of $log:\n" + log.readLines().takeLast(30).joinToString("\n"))
    }
}

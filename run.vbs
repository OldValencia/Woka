' Launch Woka with no console window at all.
'
' Double-click it in Explorer, or from a shell:  wscript run.vbs
' (`cscript` would give it back the console this exists to avoid.)
'
' `run.sh` keeps its terminal because bash needs one — this goes around bash and
' starts the Gradle wrapper directly, hidden. Everything that would have gone to
' the terminal lands in `run.log` beside this script, so a failed start is still
' readable afterwards.
'
' Nothing waits for the app: closing its window ends it, and the Gradle daemon
' stays warm for the next start as usual.

Dim shell, fso
Set shell = CreateObject("WScript.Shell")
Set fso = CreateObject("Scripting.FileSystemObject")

' Run from the project root whatever directory this was launched from. The
' command below is then relative, which keeps it free of quoting.
shell.CurrentDirectory = fso.GetParentFolderName(WScript.ScriptFullName)

' 0 = hidden window, False = do not wait for it to finish.
shell.Run "cmd /c gradlew.bat :desktopApp:run > run.log 2>&1", 0, False

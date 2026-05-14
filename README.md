# Scrollo

JavaFX desktop revision app built from `RevisionApp_Spec.md` and the editable CS-375 Logic module.

## Run

```powershell
.\gradlew.bat run
```

The app reads the CS-375 module directly from:

```text
src\main\resources\modules\cs375-logic.json
```

The in-app module editor writes back to that exact file only when `Save Active Module JSON` is clicked.
Gradle builds copy this file into build output but do not regenerate or overwrite it.

The app still stores settings, window state, themes, and progress in:

```text
%USERPROFILE%\.revisionapp
```

## Build

```powershell
.\gradlew.bat build
```

## Portable Windows Download

Build a self-contained Windows download with a real `Scrollo.exe` launcher:

```powershell
.\gradlew.bat packageWindowsPortable
```

The generated ZIP is:

```text
build\distributions\Scrollo-1.0.0-windows-portable.zip
```

The ZIP contains `Scrollo.exe` and a private Java runtime, so users should be able to extract it and run `Scrollo.exe` without installing Gradle or Java.

For GitHub, upload the ZIP to a Release. A copy can also be kept in:

```text
release\Scrollo-1.0.0-windows-portable.zip
```

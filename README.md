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

## Windows Downloads

Build a self-contained Windows installer:

```powershell
.\gradlew.bat packageWindowsInstaller
```

The generated installer is written to:

```text
build\installer
```

Users can download the installer `.exe` and install Scrollo without installing Gradle or Java.

You can also build a portable Windows ZIP with a real `Scrollo.exe` launcher:

```powershell
.\gradlew.bat packageWindowsPortable
```

The generated ZIP is:

```text
build\distributions\Scrollo-1.0.0-windows-portable.zip
```

The ZIP contains `Scrollo.exe` and a private Java runtime, so users should be able to extract it and run `Scrollo.exe` without installing Gradle or Java.

## GitHub Release

This repository includes a GitHub Actions workflow at:

```text
.github\workflows\windows-release.yml
```

To publish a downloadable Windows `.exe` on GitHub:

1. Push this repository to GitHub.
2. Open the repository on GitHub.
3. Go to `Actions` -> `Windows release` -> `Run workflow`.
4. Use a release tag such as `v1.0.1`.

The workflow builds on GitHub, creates or updates the Release, and uploads both:

```text
Scrollo-1.0.1.exe
Scrollo-1.0.1-windows-portable.zip
```

A local copy of the portable ZIP can also be kept in:

```text
release\Scrollo-1.0.0-windows-portable.zip
```

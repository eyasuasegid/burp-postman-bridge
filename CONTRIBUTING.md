# Contributing

Thanks for contributing to Burp → Postman Bridge.

## Development requirements

- Java JDK 21 or newer
- Gradle 9.x (or a compatible Gradle installation)
- Burp Suite with a Montoya API version compatible with the version declared in `build.gradle`

## Build

Linux/macOS:

```bash
./build.sh
```

Windows PowerShell:

```powershell
.\build.ps1
```

The scripts use a locally installed `gradle` command. If you prefer, run the Gradle task directly:

```bash
gradle clean build
```

The distributable extension is:

```text
build/libs/BurpPostmanBridge-1.0.0.jar
```

Do not commit generated files from `build/` or local API keys.

## Pull requests

- Keep changes focused.
- Do not commit Postman API keys or other credentials.
- Update the README when user-facing behavior changes.
- Test the extension in Burp before submitting a change.

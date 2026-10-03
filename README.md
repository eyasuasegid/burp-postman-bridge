# Burp Postman Bridge

> Send Burp Suite HTTP requests directly to an existing Postman collection.

**Burp Postman Bridge** is a Burp Suite extension for security testing workflows that removes the repetitive copy-and-paste step between Burp Suite and Postman. Select requests from Burp HTTP history, choose where they belong in an existing Postman collection, and import them through the Postman API.

## ✨ Highlights

- **Multi-request import** — send several Burp requests in one operation.
- **Existing collections only** — add requests to a collection or folder without creating a new collection.
- **Editable request names** — rename requests before importing.
- **Header controls** — optionally exclude `Content-Type` and/or `Content-Length`.
- **Body-aware conversion** — handles JSON, XML, HTML, JavaScript, and form-urlencoded bodies.
- **Authorization & custom headers** — preserves request headers by default.
- **Automatic backup** — creates a local backup before updating a collection.
- **Cross-platform build** — Gradle Wrapper included; no separate Gradle installation required.
- **Burp-native workflow** — accessible from HTTP history and message editors such as Repeater.

## How it works

```text
Burp Suite
    │
    │ Select requests
    ▼
Burp Postman Bridge
    │
    │ Postman API
    ▼
Existing Postman Collection
    └── Folder / Collection root
```

The extension reads the selected Burp requests, converts them into Postman request items, and updates the selected collection through the Postman API.

## Requirements

- **Burp Suite** with support for Java/Montoya extensions
- **Java JDK 21**
- **Postman account + API key**
- Internet access when using the Postman API

You **do not need to install Gradle**. The repository includes the Gradle Wrapper.

## Build

### Linux

```bash
git clone <YOUR_REPOSITORY_URL>
cd burp-postman-bridge

chmod +x gradlew build.sh
./build.sh
```

Or build directly:

```bash
./gradlew clean build
```

### Windows

PowerShell:

```powershell
git clone <YOUR_REPOSITORY_URL>
cd burp-postman-bridge

.\build.ps1
```

Or:

```powershell
.\gradlew.bat clean build
```

The first build downloads the project's configured Gradle version automatically.

The extension JAR is generated under:

```text
build/libs/
```

Use the regular `BurpPostmanBridge-*.jar` rather than the `*-thin.jar` artifact.

## Install in Burp Suite

1. Build the project.
2. Open **Burp Suite → Extensions**.
3. Add a new Java extension.
4. Select the generated JAR from `build/libs/`.
5. Load the extension.
6. Select one or more requests in **Proxy → HTTP history**.
7. Right-click and choose **Send selected requests to Postman**.

## Postman setup

The extension uses the **Postman API** to read and update collections.

Create a Postman API key and enter it in the extension when prompted.

The key is used for the API requests made by the extension. Do not commit API keys, tokens, or other credentials to Git.

## Import workflow

The extension can be opened from **Proxy → HTTP history** or from a Burp message editor such as **Repeater**.

After selecting or opening a request in Burp:


1. Choose the target Postman collection.
2. Choose an existing folder or the collection root.
3. Review and edit request names if needed.
4. Choose whether to include:
   - `Content-Type`
   - `Content-Length`
5. Import the requests.

The extension updates the selected existing collection; it does not create a new collection.

## Request conversion

### Headers

All request headers are imported by default.

Two headers can be controlled globally for the import:

| Option | Behavior |
|---|---|
| **Include Content-Type** | Controls whether `Content-Type` is added as a Postman header |
| **Include Content-Length** | Controls whether `Content-Length` is added as a Postman header |

`Content-Length` can normally be omitted because Postman can calculate it when sending the request.

`Content-Type` is also used to determine the body representation, so disabling the header does **not** prevent the extension from detecting the appropriate body format.

### Request bodies

Common content types are converted to appropriate Postman body modes:

| Burp request body | Postman representation |
|---|---|
| `application/x-www-form-urlencoded` | URL-encoded |
| JSON | Raw / JSON |
| XML | Raw / XML |
| HTML | Raw / HTML |
| JavaScript | Raw / JavaScript |
| Other supported content | Raw |

Authorization, cookies, and custom headers are preserved as request headers unless specifically excluded by the import options.

## Backups

Before updating a collection, the extension creates a local backup of the collection data.

This provides a recovery point if an import does not produce the result you expected.

Keep your backup files secure because a collection may contain sensitive information such as authentication tokens, cookies, or internal URLs.

## Project structure

```text
burp-postman-bridge/
├── src/
├── gradle/
│   └── wrapper/
├── build.gradle
├── settings.gradle
├── gradlew
├── gradlew.bat
├── build.sh
├── build.ps1
└── README.md
```

The Gradle Wrapper is intentionally committed to the repository so contributors can build the project with the same Gradle version without configuring Gradle manually.

## Development

Clone the repository and build with the wrapper:

```bash
./gradlew clean build
```

For a faster development cycle:

```bash
./gradlew build
```

Generated build files are placed in `build/` and are not intended to be committed.

## GitHub Actions

The project includes CI configuration for automated builds.

CI uses the repository's Gradle Wrapper rather than relying on whatever Gradle version happens to be installed on the runner.

## Security notes

This extension handles HTTP requests that may contain sensitive security-testing data.

In particular, requests may include:

- Session cookies
- Authorization headers
- API keys
- Personal access tokens
- Internal hostnames
- Sensitive request bodies

Use it only with systems and Postman collections you are authorized to access and test.

Do not commit:

```text
API keys
Access tokens
Cookies
Private collection exports
Personal credentials
```

## Troubleshooting

### `Permission denied` when running `./gradlew`

Run:

```bash
chmod +x gradlew
```

Then:

```bash
./gradlew clean build
```

### Gradle download fails

The wrapper downloads Gradle on its first run. Check your internet connection and retry.


### Linux: `Permission denied` from Gradle Wrapper

On some Linux systems, the downloaded Gradle executable may not retain its execute permission. If the wrapper reports an error such as:

```text
Exec failed, error: 13 (Permission denied)
```

run:

```bash
chmod +x gradlew build.sh
```

If the error points to the downloaded Gradle executable under `~/.gradle/wrapper/dists/`, run:

```bash
chmod +x ~/.gradle/wrapper/dists/gradle-9.2.0/gradle-9.2.0/bin/*
```

Then build again:

```bash
./gradlew clean build
```

This is a one-time Linux permission fix for that Gradle installation. You do **not** need to install Gradle manually.

### Java version error

Check:

```bash
java -version
```

The project requires **JDK 21**.

### Burp does not load the extension

Check the **Extensions → Errors** area in Burp and confirm that you selected the generated regular JAR from:

```text
build/libs/
```

### Postman import fails

Verify that:

- The Postman API key is valid.
- The target collection exists and is accessible by that key.
- The selected folder belongs to that collection.
- Your machine can reach the Postman API.

## Contributing

Contributions, bug reports, and improvements are welcome.

A useful contribution should ideally include:

- A clear description of the change.
- Reproduction steps for bug fixes.
- Relevant build/test information.
- No secrets or private security-testing data.

## License

See the repository license file for the terms governing use and contribution.

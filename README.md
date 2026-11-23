# CatSession Manager

**CatSession Manager** is a clean, local-only Minecraft Fabric 1.21.11 utility mod designed for managing account sessions authorized explicitly by the account owner.

---

## Key Features

- **In-Game GUI**: Accessible via the Title Screen, Multiplayer Screen, or by pressing the default shortcut key (`M`).
- **Explicit Authorization**: Integrates standard RFC 8628 Microsoft Device Code Flow where users authenticate directly on Microsoft's official verification portal (`microsoft.com/link`).
- **Manual Profile Import**: Supports local offline and developer test profiles without third-party services.
- **Hardware-Anchored Encrypted Storage**: Encrypts stored session credentials using AES-256-GCM with a PBKDF2 key derived from local hardware/OS parameters and restricted POSIX file permissions (`0600`).
- **No Credential Scraping**: Strictly prohibits scraping browser cookies, Windows Credential Manager, or other applications.
- **No 2FA Bypass**: Direct adherence to official OAuth2 protocols; all multi-factor authentication occurs securely on Microsoft's official servers.
- **Zero-Telemetry Policy**: Zero tracking, zero phone-home calls, and zero third-party relays. Network traffic is restricted strictly to official Microsoft and Mojang authentication servers during interactive authorization.
- **Zero-Leak Logging**: Built-in sanitization pipeline automatically redacts tokens, credentials, and sensitive parameters from logs and exception messages.
- **Full Data Sovereignty**: Features explicit **Delete Session** and **Clear All Data** actions with safety confirmation modals.

---

## Architecture Overview

```
com.catsession
├── CatSessionMod.java               # Client entrypoint and keybinding registration
├── auth/
│   ├── AuthProfile.java             # Authorized session and profile model
│   └── MicrosoftDeviceFlow.java     # RFC 8628 OAuth 2.0 Device Code Authorization Flow
├── storage/
│   └── CatSecureStorage.java        # AES-256-GCM encrypted persistence with strict permissions
├── manager/
│   └── SessionManager.java          # In-memory session registry and client session switcher
├── gui/
│   ├── CatSessionScreen.java        # Primary in-game management interface
│   ├── DeviceCodeModalScreen.java   # Microsoft device code interactive modal
│   ├── ManualImportScreen.java      # Offline/local profile importer
│   └── ConfirmActionScreen.java     # Destructive action confirmation dialogs
├── mixin/
│   ├── MinecraftClientAccessor.java # MinecraftClient session field accessor
│   ├── TitleScreenMixin.java        # Main menu GUI button injection
│   └── MultiplayerScreenMixin.java  # Multiplayer screen GUI button injection
└── util/
    ├── CatSanitizer.java            # Sensitive token & secret regex redaction
    └── CatSessionLogger.java        # Zero-leak logging wrapper
```

---

## Building from Source

### Prerequisites
- Java Development Kit (JDK) 21
- Git

### Build Command
Run the Gradle wrapper:

```bash
# Linux / macOS
./gradlew build

# Windows
gradlew.bat build
```

Built artifacts will be located in `build/libs/`:
- `catsession.1.0.0.jar` (Remapped production mod JAR)

---

## Continuous Integration (GitHub Actions)

A GitHub Actions workflow is provided in `.github/workflows/build.yml`. Every commit pushed to `main` builds the mod automatically in an Ubuntu environment using JDK 21 and publishes the resulting JAR as a workflow artifact.

---

## License

This project is licensed under the MIT License.

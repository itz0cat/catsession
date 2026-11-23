# Security Policy

## Core Security Commitments

CatSession Manager is engineered with strict adherence to the Microsoft and Mojang authentication security models:

1. **Explicit Consent & Device Code Flow**:
   - Authentication is performed strictly via standard OAuth 2.0 Device Code Flow (RFC 8628).
   - Users are given an explicit verification code and directed to `https://microsoft.com/link`.
   - All credentials, passwords, and 2FA challenges are entered exclusively on Microsoft's official web domain.

2. **Prohibition of Credential Scraping**:
   - The mod never inspects or extracts browser cookies, session stores, Windows Credential Manager / DPAPI, macOS Keychain, or files belonging to other launcher applications.

3. **Local Encryption at Rest**:
   - Stored session data is encrypted on disk using **AES-256-GCM** (`AES/GCM/NoPadding`) with a 128-bit authentication tag and a unique 96-bit initialization vector (IV) per save.
   - Encryption keys are derived via **PBKDF2WithHmacSHA256** (65,536 iterations) combining a machine-bound identity and an OS-restricted salt file.
   - On POSIX filesystems, permissions on `config/catsession/` and `sessions.enc` are restricted to `0600` (read/write only by the current OS user).

4. **Zero-Leak Logging & Error Handling**:
   - All internal logging passes through `CatSanitizer`, which scans for and redacts JWTs, bearer tokens, and sensitive query parameters before any text reaches log output or crash dumps.

5. **No Automatic Account Switching**:
   - Session changes require explicit manual user interaction in the GUI. The mod never switches sessions automatically in the background.

6. **Right to Erasure**:
   - Users can delete individual sessions or execute a full cryptographic wipe (**Clear All Data**), which overwrites sensitive files before deleting them from disk.

---

## Vulnerability Reporting

If you identify a security issue, please open a private security advisory on the GitHub repository or contact the maintainer directly.

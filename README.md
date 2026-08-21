# Cernunnos Diwaller

**Le gardien de votre identité / The guardian of your identity**

> « Diwaller » signifie « gardien » en breton. Cernunnos est le dieu celte protecteur de l'entre-deux.
> "Diwaller" means "guardian" in Breton. Cernunnos is the Celtic god who guards the in-between.

---

<img width="1830" height="400" alt="cernunnos_logo_horizontal_white_purple" src="https://github.com/user-attachments/assets/8703da14-53f1-4a66-bd09-b7a20cf6fccf" />


## FR — Français

Cernunnos Diwaller est une application Android **offline-first** dédiée à la sécurité de votre identité numérique. Elle combine un authenticateur 2FA, un coffre-fort de documents chiffrés et un système d'envoi sécurisé — sans dépendre d'aucune application tierce.

### Fonctionnalités

#### 2FA / TOTP / HOTP
- **TOTP** (RFC 6238), **HOTP** (RFC 4226), **Steam Guard**, **Yandex**, **mOTP**
- Algorithmes **SHA1, SHA256, SHA512**
- Codes à **6 ou 8 chiffres**
- Périodes personnalisables (30, 60 s…)
- **Scan QR code** (`otpauth://`) et **deep linking**
- Saisie manuelle d'entrées
- Compteurs HOTP préservés à l'import/export
- Vecteurs de test **RFC 6238** et **RFC 4226** validés

#### Sécurité & chiffrement
- **AES-256-GCM** pour le chiffrement authentifié
- **Argon2id** pour la dérivation de clé (plus moderne que scrypt)
- **Android Keystore** + déblocage **biométrique** (empreinte, visage) ou **code appareil**
- **FLAG_SECURE** — bloque captures d'écran et enregistrement (désactivable, non recommandé)
- **Tap-to-reveal** — codes masqués par ••••••, révélés au tap, auto-masqués après 10 s
- **Détection de root** — avertissement au démarrage
- **Détection de services d'accessibilité** — avertissement si un service peut lire l'écran
- Aucune copie automatique des codes TOTP dans le presse-papiers
- Tableaux de mots de passe **zeroed** en `finally`
- Aucun `GlobalScope`, aucun `!!` force-unwrap
- **ProGuard** supprime les logs en release
- `allowBackup="false"` + `dataExtractionRules` restrictif
- `FileProvider` non-exporté, `PendingIntent` avec `FLAG_IMMUTABLE`
- Aucune permission `SEND_SMS` / `READ_SMS` / `READ_CONTACTS`

#### Gestion des entrées
- **Catégories** personnalisables
- **Favoris**
- **Recherche** par nom/issuer
- **Tri** (nom, issuer, date, favoris, manuel)
- Modes de vue : **liste, tuiles, compact**
- **Icônes de service** intégrées + **icônes personnalisées** (image depuis la galerie)
- **Timers compte à rebours** en temps réel
- Statistiques d'usage (fréquent, régulier, occasionnel, jamais vu)

#### Import / Export
- Import depuis **Aegis**, **2FAS**, **Bitwarden**, **Google Authenticator**, **Authy**, **Microsoft Authenticator**, **FreeOTP**, **andOTP**, **Raivo OTP**, **LastPass**, **Steam Guard**, **WinAuth** et **texte brut** (otpauth://)
- Export **chiffré** avec checksum SHA-256 (format v1 + compatibilité v0 legacy)
- Export **QR code** individuel
- Format versionné avec détection de corruption

#### Sauvegarde & cloud
- **Sauvegarde automatique locale** (rotation 10 fichiers max)
- **WebDAV** (Nextcloud, ownCloud, Synology, Proton Drive…)
- **SFTP** avec pinning de clé d'hôte
- **Google Drive** (OAuth/PKCE)
- **Dropbox** (OAuth/PKCE)
- **Wi-Fi Direct** — transfert P2P chiffré entre appareils
- **Synchronisation cloud bidirectionnelle**
- Backup-before-write + **rollback** en cas d'échec
- Restauration depuis le cloud ou local

#### Coffre-fort de documents
- Stockage **chiffré** de documents d'identité (permis, assurances, carte d'assurance…)
- Support **recto/verso**
- **Recadrage** d'image avant chiffrement
- **Zoom plein écran** avec panoramique
- Motivation : en cas de vol ou perte, refaire les papiers est une galère

#### Enveloppe sécurisée
- Envoi de **documents confidentiels** chiffrés
- Conversion en **PDF chiffré** (compatible banques, cabinets d'avocats, comptables)
- Format **.cern** — chiffrement Cernunnos renforcé (AES-256-GCM + Argon2id)
- **Séquence sécurisée** : envoyer d'abord le mot de passe, puis le document chiffré
- Envoi du document par **email** ou **MMS**
- Envoi du mot de passe par **SMS** (via chooser), **email**, ou **copie**
- Multi-documents dans une seule enveloppe
- Formats supportés : jpg, webp, pdf, docx, xlsx, md, csv, xls, doc

#### Widgets
- **Quick Access** — accès rapide à l'app
- **Codes** — affichage des codes TOTP en direct sur l'écran d'accueil
- Configuration : mode favoris / catégorie / toutes les entrées
- Nombre maximum d'entrées configurable
- Option exiger déblocage

#### Interface
- **Jetpack Compose** + **Material 3**
- **Material You** (dynamic color, Android 12+)
- Thèmes : **sombre, clair, système**
- **Animation de splash** Lottie
- **Onboarding** guidé
- Localisation **français / anglais**
- Branding **Cernunnos** (logo violet, identité celte)

#### Fiabilité
- **Backup-before-write** sur tous les stores (vault, documents, index)
- **Rollback** automatique en cas d'échec
- Récupération depuis backup en cas de corruption
- **50+ tests unitaires** (crypto, TOTP, import/export, edge cases, RFC vectors)
- Lectures bornées (`IOUtils.readBounded`) contre les OOM
- Écritures atomiques (tmp + rename)
- Nettoyage des fichiers temporaires au démarrage

### Construction

```bash
# Prérequis : Android Studio, JDK 17, Android SDK 35

# Configuration
cp .env.example .env
# Éditer .env avec vos clés Google/Dropbox (optionnel)

# Build debug
./gradlew assembleDebug

# Tests unitaires
./gradlew testDebugUnitTest

# Tests instrumentés (nécessite un appareil/émulateur)
./gradlew connectedDebugAndroidTest
```

### Stack technique

- **Kotlin** + **Jetpack Compose**
- **Coroutines** + **Flow**
- **Argon2id** (Lambdapioneer)
- **PDFBox** (Tom Roush) — génération PDF chiffré
- **AppAuth** — flux OAuth
- **JSch** — SFTP
- **ZXing** — scan QR
- **CameraX** — caméra

---

## EN — English

Cernunnos Diwaller is an **offline-first** Android app dedicated to securing your digital identity. It combines a 2FA authenticator, an encrypted document vault, and a secure sending system — with no dependency on any third-party app.

### Features

#### 2FA / TOTP / HOTP
- **TOTP** (RFC 6238), **HOTP** (RFC 4226), **Steam Guard**, **Yandex**, **mOTP**
- **SHA1, SHA256, SHA512** algorithms
- **6 or 8 digit** codes
- Customizable periods (30, 60s…)
- **QR code scanning** (`otpauth://`) and **deep linking**
- Manual entry
- HOTP counters preserved across import/export
- **RFC 6238** and **RFC 4226** test vectors validated

#### Security & encryption
- **AES-256-GCM** authenticated encryption
- **Argon2id** key derivation (more modern than scrypt)
- **Android Keystore** + **biometric** (fingerprint, face) or **device credential** unlock
- **FLAG_SECURE** — blocks screenshots and screen recording (can be disabled, not recommended)
- **Tap-to-reveal** — codes hidden behind ••••••, revealed on tap, auto-hidden after 10s
- **Root detection** — warning at startup
- **Accessibility service detection** — warns if a service can read the screen
- No automatic copying of TOTP codes to clipboard
- Password char arrays **zeroed** in `finally` blocks
- No `GlobalScope`, no `!!` force-unwrap
- **ProGuard** strips logs in release builds
- `allowBackup="false"` + restrictive `dataExtractionRules`
- Non-exported `FileProvider`, `PendingIntent` with `FLAG_IMMUTABLE`
- No `SEND_SMS` / `READ_SMS` / `READ_CONTACTS` permissions

#### Entry management
- Custom **categories**
- **Favorites**
- **Search** by name/issuer
- **Sorting** (name, issuer, date, favorites, manual)
- View modes: **list, tiles, compact**
- Built-in **service icons** + **custom icons** (image from gallery)
- Real-time **countdown timers**
- Usage stats (frequent, regular, occasional, never viewed)

#### Import / Export
- Import from **Aegis**, **2FAS**, **Bitwarden**, **Google Authenticator**, **Authy**, **Microsoft Authenticator**, **FreeOTP**, **andOTP**, **Raivo OTP**, **LastPass**, **Steam Guard**, **WinAuth** and **plain text** (otpauth://)
- **Encrypted** export with SHA-256 checksum (v1 format + v0 legacy compatibility)
- Individual **QR code** export
- Versioned format with corruption detection

#### Backup & cloud
- **Automatic local backup** (10-file rotation max)
- **WebDAV** (Nextcloud, ownCloud, Synology, Proton Drive…)
- **SFTP** with host-key pinning
- **Google Drive** (OAuth/PKCE)
- **Dropbox** (OAuth/PKCE)
- **Wi-Fi Direct** — encrypted P2P transfer between devices
- **Two-way cloud sync**
- Backup-before-write + **rollback** on failure
- Restore from cloud or local

#### Document vault
- **Encrypted** storage of identity documents (driver's license, insurance, insurance card…)
- **Front and back** (recto/verso) support
- Image **cropping** before encryption
- **Full-screen zoom** with panning
- Motivation: if stolen or lost, replacing paperwork is a nightmare

#### Secure envelope
- Send **confidential documents** encrypted
- Convert to **encrypted PDF** (compatible with banks, law firms, accounting firms)
- **.cern** format — strengthened Cernunnos encryption (AES-256-GCM + Argon2id)
- **Secure sequence**: send the password first, then the encrypted document
- Send document by **email** or **MMS**
- Send password by **SMS** (via chooser), **email**, or **copy**
- Multiple documents in one envelope
- Supported formats: jpg, webp, pdf, docx, xlsx, md, csv, xls, doc

#### Widgets
- **Quick Access** — quick app access
- **Codes** — live TOTP codes on home screen
- Configuration: favorites / category / all entries mode
- Configurable max entries
- Require unlock option

#### Interface
- **Jetpack Compose** + **Material 3**
- **Material You** (dynamic color, Android 12+)
- Themes: **dark, light, system**
- **Lottie splash animation**
- Guided **onboarding**
- **French / English** localization
- **Cernunnos** branding (purple logo, Celtic identity)

#### Reliability
- **Backup-before-write** on all stores (vault, documents, index)
- Automatic **rollback** on failure
- Recovery from backup on corruption
- **50+ unit tests** (crypto, TOTP, import/export, edge cases, RFC vectors)
- Bounded reads (`IOUtils.readBounded`) against OOM
- Atomic writes (tmp + rename)
- Temp file cleanup at startup

### Build

```bash
# Prerequisites: Android Studio, JDK 17, Android SDK 35

# Configuration
cp .env.example .env
# Edit .env with your Google/Dropbox keys (optional)

# Debug build
./gradlew assembleDebug

# Unit tests
./gradlew testDebugUnitTest

# Instrumented tests (requires device/emulator)
./gradlew connectedDebugAndroidTest
```

### Tech Stack

- **Kotlin** + **Jetpack Compose**
- **Coroutines** + **Flow**
- **Argon2id** (Lambdapioneer)
- **PDFBox** (Tom Roush) — encrypted PDF generation
- **AppAuth** — OAuth flows
- **JSch** — SFTP
- **ZXing** — QR scanning
- **CameraX** — camera

---

## Screenshot

<img width="300"  alt="Screenshot_20260820-172543" src="https://github.com/user-attachments/assets/7d9365b8-0e8a-4391-8f07-2a9def30a04e" />  <img width="300" alt="Screenshot_20260820-172623" src="https://github.com/user-attachments/assets/7bb233f2-501f-4940-9b28-4a6c7e46a893" />  <img width="300" alt="Screenshot_20260820-172636" src="https://github.com/user-attachments/assets/32661ea6-5c5e-4e0b-8eaa-2f0e9adbba62" />

<img width="300"  alt="Screenshot_20260820-173017" src="https://github.com/user-attachments/assets/61f42020-bf41-4f28-9ba4-e07c90ed22a9" />  <img width="300" alt="Screenshot_20260820-173142" src="https://github.com/user-attachments/assets/ca41e7d9-e05a-4e1b-a4ae-b4cd982cdbfd" />





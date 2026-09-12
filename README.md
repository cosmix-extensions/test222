# NSFW Providers

> A collection of Cosmix / Cloudstream 3 extension providers, maintained by **cosmix-extensions**.

This repository is a multi-module Gradle project that builds several Cloudstream 3
provider extensions. Each provider is an independent Gradle subproject and is compiled
into its own plugin, then published through the repository manifest (`repo.json`).

---

## Table of Contents

- [Overview](#overview)
- [Included Providers](#included-providers)
- [Repository Manifest](#repository-manifest)
- [Requirements](#requirements)
- [Building from Source](#building-from-source)
- [Project Structure](#project-structure)
- [Dependencies](#dependencies)
- [Adding a New Provider](#adding-a-new-provider)
- [Contributing](#contributing)
- [Disclaimer](#disclaimer)
- [License](#license)

---

## Overview

| | |
|---|---|
| **Repository** | `cosmix-extensions/test222` |
| **Type** | Cloudstream 3 extension repository |
| **Language** | Kotlin |
| **Build System** | Gradle (Kotlin DSL) |
| **Manifest Version** | 1 |
| **Default Branch** | `main` |

The project uses the [recloudstream Gradle plugin](https://github.com/recloudstream/gradle)
to package each module into a Cloudstream-compatible extension. The repository metadata
that Cloudstream reads to install plugins lives in [`repo.json`](./repo.json).

---

## Included Providers

The following modules are declared in [`settings.gradle.kts`](./settings.gradle.kts):

| Module | Description |
|---|---|
| `ViralLinksProvider` | Provider extension for ViralLinks. |
| `WowProvider` | Provider extension for Wow. |
| `WowUncutProvider` | Provider extension for Wow Uncut. |
| `HamsterProvider` | Provider extension for Hamster. |

> Each provider targets the shared Android namespace `com.mlsbd` and is built with the
> same SDK / Kotlin toolchain configuration defined in the root build script.

---

## Repository Manifest

[`repo.json`](./repo.json) is the entry point used by Cloudstream to discover plugins
from this repository:

```json
{
  "name": "NSFW Providers",
  "description": "NSFW Cosmix Extension Repository",
  "manifestVersion": 1,
  "pluginLists": [
    "https://raw.githubusercontent.com/cosmix-extensions/Not-For-All/builds/plugins.json"
  ]
}
```

To add this repository inside Cloudstream, point it at the raw URL of `repo.json`:

```
https://raw.githubusercontent.com/cosmix-extensions/test222/main/repo.json
```

---

## Requirements

- **JDK 17** (the Android toolchain is pinned to Java 17)
- **Android SDK** with **compileSdk 36**
- **Gradle** — use the included wrapper (`./gradlew`), no local install required
- **Kotlin 2.4.0** (provided by the Gradle plugin)

---

## Building from Source

Clone the repository and build all modules:

```bash
git clone https://github.com/cosmix-extensions/test222.git
cd test222

# Build every provider module
./gradlew build

# Or assemble only the release artifacts
./gradlew assembleRelease
```

On Windows use `gradlew.bat` instead of `./gradlew`.

Built plugin artifacts (`.cs3` files) are written to each module's `build/` directory.
The CI workflow publishes them to the `builds` branch, which is what the manifest
ultimately serves to clients.

### Cleaning

```bash
./gradlew clean
```

---

## Project Structure

```
test222/
├── .github/                 # CI workflows
├── HamsterProvider/         # Hamster provider module
├── ViralLinksProvider/      # ViralLinks provider module
├── WowProvider/             # Wow provider module
├── WowUncutProvider/        # Wow Uncut provider module
├── gradle/                  # Gradle wrapper files
├── build.gradle.kts         # Root build script (shared config)
├── settings.gradle.kts      # Module declarations
├── gradle.properties        # Gradle properties
├── repo.json                # Cloudstream repository manifest
├── gradlew / gradlew.bat    # Gradle wrapper scripts
└── README.md
```

Every subproject automatically applies:

- `com.android.library`
- `com.lagradost.cloudstream3.gradle`

and inherits the shared `cloudstream { ... }` and `android { ... }` blocks from the
root build script, so individual modules only need to declare their own sources.

---

## Dependencies

Shared libraries injected into every provider module:

| Dependency | Version | Purpose |
|---|---|---|
| `com.lagradost:cloudstream3` | pre-release | Cloudstream provider API |
| `com.github.Blatzar:NiceHttp` | 0.4.18 | HTTP client helpers |
| `org.jsoup:jsoup` | 1.22.2 | HTML parsing |
| `org.jspecify:jspecify` | 1.0.0 | Nullness annotations |
| `androidx.annotation:annotation` | 1.10.0 | Android annotations |
| `com.fasterxml.jackson.module:jackson-module-kotlin` | 2.13.1 | JSON (Jackson) |
| `com.fasterxml.jackson.core:jackson-databind` | 2.13.1 | JSON databinding |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | 1.10.2 | Coroutines |
| `org.mozilla:rhino` | 1.8.1 | JavaScript engine |
| `me.xdrop:fuzzywuzzy` | 1.4.0 | Fuzzy string matching |
| `com.google.code.gson:gson` | 2.14.0 | JSON (Gson) |
| `org.jetbrains.kotlinx:kotlinx-serialization-json` | 1.11.0 | Serialization |
| `org.bouncycastle:bcpkix-jdk18on` | 1.84 | Cryptography |

---

## Adding a New Provider

1. Create a new directory at the repository root, e.g. `MyNewProvider/`.
2. Add your Kotlin sources under `MyNewProvider/src/main/`.
3. Register the module in `settings.gradle.kts`:

   ```kotlin
   include(":MyNewProvider")
   ```

4. Build and verify:

   ```bash
   ./gradlew :MyNewProvider:assembleRelease
   ```

No further Gradle configuration is needed — plugins, SDK versions and dependencies are
already applied to every subproject from the root build script.

---

## Contributing

1. Fork the repository.
2. Create a feature branch: `git checkout -b feature/my-provider`.
3. Commit your changes with a clear message.
4. Push the branch and open a Pull Request.

Please keep providers self-contained and avoid hard-coding credentials or API keys.
---

## Disclaimer

This repository is intended for educational and personal use only. The maintainers do
not host, store, or distribute any media content. All trademarks and content belong to
their respective owners. Users are solely responsible for how they use these extensions
and must comply with the laws applicable in their jurisdiction.

---

## License

Released under the **GNU General Public License v3.0**. See [`LICENSE`](./LICENSE) for
the full text.

---

<p align="center">
  Maintained by <a href="https://github.com/cosmix-extensions">cosmix-extensions</a>
</p>
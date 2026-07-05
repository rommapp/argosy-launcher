<!--
Request For Packaging (RFP) for gitlab.com/fdroid/rfp
Title to use when opening the issue:  New App: Argosy Launcher
Paste everything below (from the checklist down) into the issue body.
-->

* [x] The app complies with the [inclusion criteria](https://f-droid.org/docs/Inclusion_Policy/?title=Inclusion_Policy).
* [x] The app is not already [listed](https://gitlab.com/search?scope=issues&group_id=28397) in the repo or issue tracker.
* [x] The app has not already [been requested](https://gitlab.com/search?scope=issues&project_id=2167965)
* [x] The upstream app source code repo contains the app metadata _(summary/description/images/changelog/etc)_ in a [Fastlane](https://gitlab.com/snippets/1895688) or [Triple-T](https://gitlab.com/snippets/1901490) folder structure
* [x] The original app author has been notified, and does not oppose the inclusion.
* [ ] Optionally [donated](https://f-droid.org/donate/) to support the maintenance of this app in F-Droid.

---------------------

#### APPLICATION ID: com.nendo.argosy

```yaml
Categories:
 - Games

License: GPL-3.0-only

AuthorName: nendo
AuthorEmail:
AuthorWebSite:

WebSite:
SourceCode: https://github.com/rommapp/argosy-launcher

IssueTracker: https://github.com/rommapp/argosy-launcher/issues

Donate:
Bitcoin:
LiberaPay:

AutoName: Argosy Launcher

RepoType: git

Repo: https://github.com/rommapp/argosy-launcher.git
```

Why do you want this app added to F-Droid:

> It is a gamepad-first home-screen launcher built for retro gaming handhelds (Anbernic, Retroid Pocket, Odin, AYN, Android TV). It integrates with self-hosted RomM game servers, so users can browse and manage their own game libraries entirely on their own infrastructure. There is currently no comparable controller-native launcher on F-Droid, and its audience of handheld-Linux/Android tinkerers overlaps strongly with F-Droid's.

Summary:

> Gamepad-first retro-gaming launcher with native RomM library sync

Description:

> Argosy Launcher is a gamepad-first home screen for retro gaming handhelds. It syncs a game library from a self-hosted RomM server, downloads games and BIOS files on demand, tracks achievements, and keeps save files in sync across devices — all from a controller-native interface designed for Anbernic, Retroid Pocket, Odin, AYN, and Android TV devices.
>
> * Native RomM client with rich IGDB metadata (cover art, genres, franchises, player counts)
> * On-demand ROM and BIOS downloads, sorted by platform, with a download queue
> * Bidirectional save-file sync with conflict detection
> * Achievement tracking synced from RomM
> * Custom and smart collections (Top Unplayed, Recently Added, Most Played)
> * Automatic emulator detection and per-platform RetroArch core selection
> * Automatic multi-disc handling with a disc picker
> * Full D-pad, analog-stick, and button navigation; Quick Menu (L3) and Quick Settings (R3)
> * Offline mode with local cover-art caching
> * Customization: video wallpaper, ambient audio, themes, and box-art styles
>
> Requires Android 8.0 or higher. A RomM server is optional and only needed for sync features.

---------------------

### Packaging notes (for the reviewer/packager)

* A build recipe is already prepared and will be submitted as a `fdroiddata` merge request: two ABI-split builds (`armeabi-v7a`, `arm64-v8a`) with `submodules: true` and stable-tag autoupdate.
* The app is built in a **`foss` product flavor** that excludes an optional Steam integration. Steam relies on a prebuilt JavaSteam jar that is not on Maven Central, so it is confined to the non-F-Droid `full` flavor; the `foss` flavor F-Droid builds contains no prebuilt binaries (`scandelete: [libs/maven]` as a safety net).
* Optional social features (friends, activity feed, presence, netplay matchmaking) connect to the developer's `api.argosy.dev` backend, so the recipe declares **`AntiFeatures: NonFreeNet`**. The core launcher and self-hosted RomM sync work without it.
* Native components build from source: `libretrodroid` (C++/Oboe, CMake/NDK), the `rcheevos` submodule (C), and `sigil` (C, MPL-2.0). No Rust toolchain is required.

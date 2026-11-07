# Changelog

All notable changes to LlarTV are documented in this file. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions are tagged `vMAJOR.MINOR`.

## [Unreleased]

## [1.10] - 2026-11-07

### Added

- Almali LlarTV has its own app icon and Android TV banner (a TV with a dock of apps, from the Almali brand kit),
  instead of the generic Android icon. The README header shows the "LlarTV · by almali" lockup.
- The weather city can be chosen in the settings: *Ubicació del temps* searches a city by name with Open-Meteo's
  free geocoding service and lists the matches to pick from (#110). Its first choice, *Automàtic* (`location_auto=on`),
  follows the TV's Android location and keeps the current city when the TV has none, which many TVs do. The
  default is still Barcelona, and `location` and `location_name` work as before.

### Changed

- The README has a *Minimal mode* section with a screenshot, presenting it as LlarTV's fast mode with the gain
  measured on the reference TV, and the app icon separates its sections (#113).
- The APK is 786,186 bytes, 46,864 more than v1.9 (+6.3%), over the +5% budget: about 30.7 KB are the new app icon
  and banner, and the weather city adds about 14 KB. The overrun is accepted and explained in
  [docs/performance/v1.10.md](docs/performance/v1.10.md); the baseline is not moved.

### Fixed

- After a change of place the weather card and the screensaver no longer show the weather of the previous place:
  the weather cache now records where it was fetched for, and Home fetches it again at once.

## [1.9] - 2026-11-04

### Changed

- The APK is about 0.7 MB instead of 4.2 MB (#104). The release build is now shrunk with R8 and resource
  shrinking, which removes the code the app never runs, mostly the Kotlin standard library that androidx brought
  in. On the reference TV it starts 155 ms faster (−11.7%) and uses 3.5 MB less memory (−6.5%), with the same
  behaviour: installing, the settings over ADB and the screensaver were checked on the TV. It installs over v1.8
  and keeps every setting. Measurements: [docs/performance/minify-v1.8.md](docs/performance/minify-v1.8.md).
- The performance budgets are measured against the minified build from now on
  ([baseline](docs/performance/baseline-v1.0.md#rebaseline-for-the-minified-build-v19)), and every release
  publishes its R8 mapping file next to the APK, so a stack trace from a TV can still be read.

### Fixed

- The v1.8 record now says that the *Sense Internet* pill was seen on screen (on the emulator) (#107).

## [1.8] - 2026-11-03

### Added

- Home says when the network has no Internet (#95). A TV can be connected to a network with no way out (a router
  without an uplink, a captive portal): the Wi-Fi pill used to show the network name while the weather and news
  cards quietly disappeared, because their downloads failed. It now reads *Sense Internet* / *Sin Internet* /
  *No Internet* in that case. Nothing is claimed before it is observed: until Android reports the state, the pill
  keeps showing the network.
- The settings value list marks the value in use (#94). The focus moves while the list is open, so the saved
  option now keeps the panel's accent colour and a heavier weight, and you can see at a glance which one is set
  without closing the list. The reset confirmation and the hidden-apps list mark nothing: their choices are not
  saved values.

### Changed

- Home decodes the wallpaper off the main thread (#96), the way the screensaver already did. The photo now
  appears just after the first frame instead of before it, so starting Home no longer waits for a screen-sized
  JPEG to be decoded and averaged for the dock tint. The paired measurement is in
  [docs/performance/v1.8.md](docs/performance/v1.8.md).

### Fixed

- The wallpaper list can no longer point outside its own directory (#93). The list of downloaded photos lives in
  external storage, where another app can rewrite it on Android 9 and 10, and a name containing `../` escaped the
  photo directory, so Home and the screensaver could be made to show any image of the shared volume. Only the
  names LlarTV itself writes are used now.

## [1.7] - 2026-11-03

### Changed

- The quote of the day is re-curated in the three languages (675 lines each, the same number in every language; it was 1,000): the lines are now **short** (at most
  60 characters, so a quote always fits the space reserved for it on the TV) and **motivational** (each line urges,
  encourages or affirms: effort, hope, courage, patience, learning, kindness, self-belief, resilience or curiosity).
  Lines whose point is a warning, a prediction of misfortune, cautious advice, a plain description of an everyday
  fact or custom, mockery or fatalism are gone, and the ones that stay stand alone under the date. The provenance
  rules are unchanged (every author died before 1956, every line names the page it was checked against, the
  translations are public-domain ones or LlarTV's own from the public-domain original) and the app behaves exactly
  as in 1.6: the same line all day, the line of the day at `epochDay mod 675`, in the interface language (a file now repeats after 675 days).
  The rules, the curation method and the attribution: [docs/quotes.md](docs/quotes.md).
- The app is now **Almali LlarTV** and lives at [almali-dev/almali-llartv](https://github.com/almali-dev/almali-llartv):
  the visible app name, the settings, screensaver and reset texts, the documentation, the issue templates and the
  scripts use the new identity. The Android application ID is unchanged, so an installed TV updates in place and
  keeps its settings; the links of earlier releases keep working.

## [1.6] - 2026-11-01

### Added

- Quote of the day (#69): Home shows a discreet line under the date with a traditional saying or a quote by a
  classic author (died before 1956), at most two lines and then "— Author". The same quote all day and a new one
  at midnight (consecutive days show consecutive lines, so a file repeats only after as many days as it has quotes);
  it follows the interface language and uses the Catalan quotes when a language has none. The quotes are bundled
  (`assets/quotes/ca.tsv`, `es.tsv`, `en.tsv`), so no network is used: 1,000 quotes per language, so the same one
  comes back only after about 2.7 years. The night palette applies to it. New settings row *Frase del dia* / *Frase del día* /
  *Quote of the day* and `home.conf` key `quote=on|off` (default `on`; an empty value restores it; removed by the
  settings reset). Sources, selection rules and the CC BY-SA 4.0 attribution: [docs/quotes.md](docs/quotes.md).

- Weather location and source (#70): the weather card now ends with the place and the source, for example
  "Barcelona · Open-Meteo". The new optional `location_name` setting names the place; without it the default
  location is called Barcelona, another `location` shows only the source, and an empty value shows only the source.

- Minimal mode (#71): one reversible switch for a faster Home, settings row *Mode mínim* / *Modo mínimo* /
  *Minimal mode* and `home.conf` key `minimal=on|off` (default `off`; an empty value restores it; removed by the
  settings reset; accepted by `scripts/config.sh`). While on: no weather on Home or the screensaver, no news, no
  quote, no wallpaper (black background, nothing downloaded), no screensaver photos (the clock and the date on
  black), no football card or fixtures download, no screensaver music and the neutral dock colour. Home does not
  build, load or fetch any of them. The other settings are not rewritten, so switching it off restores them exactly.

### Fixed

- `scripts/config.sh` refused the `night_screen` and `night_mute` settings added in 1.5 ("Unknown setting"); it now
  accepts every documented key, including the new `quote` and `location_name`, and a test keeps its list equal to
  the settings LlarTV understands.

## [1.5] - 2026-10-31

### Added

- Hidden-app recovery guidance (#36): hiding an app from its long-press menu now shows a short message saying
  where to recover it (*Configuració → Apps amagades* / *Ajustes → Apps ocultas* / *Settings → Hidden apps*), in
  the interface language. The message appears only when `home.conf` was really written: a failed save still
  reports the failure and claims nothing. Hiding, unhide and the settings flow are otherwise unchanged.

- Continuous move mode (#39): the long-press menu now has one *Moure* / *Mover* / *Move* action instead of the
  single-step *Moure a l'esquerra / a la dreta / amunt / avall* rows. In move mode every arrow moves the app one
  more step (grid: four directions; dock: left/right) without reopening the menu, a localised banner (also
  announced to accessibility services) shows the mode, **OK** saves the final place with one `home.conf` write,
  and **Back**/MENU undoes every step. Leaving Home, pressing Home or an app install/removal drops unsaved steps.
  The saved `order` is the same one the equivalent single-step moves would have written.
  Move mode is unmistakable (#57): the moving app's highlight has a thicker warm amber outline (normal selection
  keeps its white hairline), the banner sits centred just above the dock band instead of covering the top-bar
  buttons (and stays on screen when a long grid scrolls), and the large app name is hidden while moving, so it
  never names a different app than the one being moved, grid included.

- Content recovers when the Internet comes back (#38): while Home is visible, LlarTV follows Android's validated
  Internet state (no polling). When it changes from unusable to usable, only the content whose last download
  failed — weather, headlines, wallpaper photos, football fixtures — is retried at once, through the same rules
  as before (a feature turned off everywhere still makes no request, one download per kind at a time). Joining a
  Wi-Fi network without Internet (or a captive portal) does not count, and repeated or flapping notifications
  recover at most once a minute. Previously the retry waited for the failure window (10 min to 3 h).

- Quiet night screensaver (#37): two new settings, both off by default, so existing installations keep their
  screensaver. `night_screen` (*Protector discret de nit* / *Salvapantallas discreto de noche* / *Quiet night
  screensaver*) shows only the clock on black during the night band — no photos, credits, weather or motion, and the
  clock even with `clock_screen=off`. `night_mute` (*Música en silenci de nit* / *Música en silencio de noche* /
  *Silence music at night*) stops the screensaver music when the night band begins and starts a fresh session when
  it ends, never two players at once; `music_screen=off` still never plays. A running screensaver follows the band
  boundaries through its existing per-minute clock tick (no new polling). It quiets the screensaver content only;
  it does not turn the backlight off.

- Move mode is also spoken (#39): with TalkBack, each step announces the new position, OK announces that the
  order was saved and Back announces that the move was undone, in the interface language.

### Changed

- Moving apps reuses the dock tiles instead of rebuilding them on every step, which removes visible jank while
  holding an arrow (emulator: janky frames 19 → 1 in the worst run).

- The configuration now lives in app-private storage (#58): `home.conf` and the optional `dock.txt` are moved
  once, the first time LlarTV reads its settings after the upgrade, from the external app folder to LlarTV's
  private files folder. The move is byte for byte (every value, comment and unknown line is kept), and the old
  copy is removed only after the new one has been checked; if the move cannot be completed, LlarTV keeps using
  the old file and tries again next time. The move runs once (also on a new installation with nothing to move);
  afterwards a `home.conf` or `dock.txt` put in the old folder is ignored. `scripts/config.sh`, the settings
  panel and the reset work as before; caches and pushed wallpaper images stay where they were.

### Fixed

- A news feed larger than 2 MiB is refused like any other network error instead of being read whole into memory.
- Football fixtures (#59): a response that is not HTTP 200 now counts as a failed fetch (the cached matches stay
  and the usual retry applies) instead of being taken as an empty schedule. Every other download is size-bounded
  too: fixtures 1 MiB, weather 256 KiB, the Bing image list 1 MiB and each wallpaper image 20 MiB. A larger body
  fails like a network error, an image download is streamed to disk and never leaves a partial file, and the
  previous image is kept.

## [1.4] - 2026-10-31

### Added

- Grid order from the remote (#13): holding OK on a grid app now also offers *Moure a l'esquerra* / *Moure a la
  dreta* and *Moure amunt* / *Moure avall* (a whole row); a move that would leave the grid is not shown. The order
  is stored in the new optional `order` key of `home.conf` (also accepted by `scripts/config.sh`), written with the
  same atomic, line-preserving write as the dock. Without `order` the grid stays sorted by name, as before. The
  first move writes every installed app in its current order, so a newly installed app is added at the end
  without moving the others; an uninstalled app leaves no gap. Moving a grid app never changes the dock, and an
  automatically filled dock keeps taking the first apps by name. Hidden apps keep their place in the order.
- Start the screensaver from the settings menu (#34): *Iniciar el protector* starts the LlarTV screensaver at once
  with its configured options, through SystemUI's exported `Somnambulator` activity (the platform's "start now"
  entry). It only reads `screensaver_components` and `screensaver_enabled` and writes no setting, so the
  inactivity timeout, `home.conf` and the launcher/screensaver assignments are unchanged. If another screensaver
  is selected, the screensaver is switched off, the settings cannot be read, or the TV does not provide that
  entry, a message says so and nothing is started; no success message is shown, the screensaver itself is the
  confirmation.
- Interface languages (#11): Catalan, Spanish and English, chosen under *Configuració → Idioma* (each language is
  listed in its own name) or with the new `language` key (`ca`, `es`, `en`; also accepted by
  `scripts/config.sh`). Catalan is the default for new installations and stays for existing ones on upgrade; an
  empty or unknown value is Catalan. Every LlarTV text follows it on Home, the settings and the screensaver: the
  greeting, the date, the pills, the cards, weather conditions, headline ages, the next-match card, menus,
  messages and the settings values, with plurals where a count is shown. The settings panel switches at once;
  Home on return; the screensaver when it next starts. The language never changes the TV's system language,
  time zone or clock format, nor other apps. `news_source=automatic` follows it (3Cat, BBC News Mundo, BBC News
  World); an explicit preset and a custom `news_rss` are kept as they are. Headlines, photo titles and credits
  are shown as their providers supply them.
- Settings reset (#9): *Configuració → Restablir la configuració* shows what is reset and what is kept, with
  *Cancel·lar* focused first; Cancel or Back changes nothing. Confirming removes the lines of the documented
  `home.conf` keys, so their defaults come back (language Catalan, dock from `dock.txt` or the built-in list, no
  hidden apps, grid by name) and stay after a restart; comments and unknown lines are kept verbatim. `dock.txt`,
  wallpaper images, music and other caches, installed apps and Android settings (launcher, screensaver) are not
  touched. A failed read or write is reported and leaves `home.conf` unchanged. Home follows a changed dock,
  hidden list, grid order or column count when it is shown again.
- Screensaver playlist (#35): local music rotates between Dancing, Chill Wave and Dream Culture every two
  minutes (or earlier at completion), wrapping without overlapping decoders and retaining audio focus. The
  original Dancing cache is reused; the two additional individually pinned CC BY 4.0 tracks live outside the
  APK. Offline ca/es/en credits provide the original titles, author, source and licence. Music stays off by
  default; custom HTTPS streams retain their own programming without forced rotation. Exit releases focus/player
  and timers; failures leave the visuals running silently. The full offline loop passed on an Android TV
  emulator; real-TV rotation/normal exit and owner listening also passed, and the published assets were verified
  over public HTTPS and on a cold-cache Android device. See the
  [acceptance record](docs/v1.4-acceptance.md) for the measured evidence.
- A failed `home.conf` write no longer leaves a `home.conf.tmp` file behind; `home.conf` itself is unchanged.

## [1.3] - 2026-10-30

The remote personalisation release: the settings open as a side panel like the Android TV ones, and the wallpaper,
night mode, screensaver clock and motion, dock favourites and hidden apps can all be set with the remote.

### Fixed

- `wallpaper=off` now shows the documented plain background: the home screen and the screensaver no longer keep
  showing the photos downloaded before it was switched off. A pushed `wallpaper.jpg`/`wallpaper.png` still wins.

### Added

- Favourite and hidden apps from the remote (#8): holding OK on a dock or grid app opens its action menu —
  *Afegir al dock* or *Treure del dock*, *Moure a l'esquerra* / *Moure a la dreta* (dock apps only; a move that
  would leave the dock is not shown), *Amagar* and *Informació de l'app*. A short OK press still launches the
  app, and the MENU key still opens its info page. Edits write `dock` and `hidden` to `home.conf` through the
  same atomic write as the settings panel, keeping every other line, and Home reloads with the focus on the
  moved app (a neighbour when it left the row; an empty dock moves the focus to the grid). A full dock shows a
  short message and writes nothing. The settings panel gains an *Apps amagades* row with the count ("Cap" /
  "2 apps"); OK opens the list by label, the package name when the app is not installed, and unhides one app.
  Hiding never uninstalls or disables anything.
- Screensaver clock and motion in the settings menu: *Rellotge al protector* hides the time only (the date, the
  weather and the photo credits stay) and *Moviment al protector* turns off the slow zoom and drift, so each photo
  stays still and still changes every 25 seconds with a fade; no animation runs while it is off. New keys
  `clock_screen` and `motion_screen`, both on by default.
- Wallpaper and night mode in the settings menu: *Fons de pantalla* (Bing photos or a plain background), *Canvi de
  foto* (every 15 min, 30 min, hour or 3 hours, or *Fixa*: always the newest photo, still updated daily) and *Mode
  nit* (off or one of several ranges across midnight). They write the existing `wallpaper`, `wallpaper_minutes` and
  `night` keys; a custom image URL is shown and left alone, and a custom interval or range is kept as a choice.

### Changed

- An existing `dock=` list in `home.conf` no longer gets filled from the grid: the dock now shows exactly the
  installed apps of that list, in order, even when there are fewer than the columns. Removing dock apps from
  the TV therefore leaves the slot empty. The automatic fill still applies to the built-in default list (and
  `dock.txt`); the first edit made with the remote writes the currently shown dock order explicitly, and a
  `dock=` line with an empty value stays an explicitly empty dock instead of falling back to the built-in list.
- The settings menu opens as a side panel on the right, like the Android TV settings: it takes the same width as
  the system panel, slides in over the dimmed home screen, and shows each setting with its value underneath. The
  values of a setting open inside the panel; Back closes them first and then the panel, and the focus returns to
  *Configuració*. No new runtime dependency.

## [1.2] - 2026-10-30

The screensaver music release: the screensaver can play calm music, off by default, downloaded once and looped
from the TV itself.

### Added

- Optional screensaver music (`music_screen`, `music_url`), off by default and switchable from the settings menu.
  With no custom URL, play Holizna's CC0 "Dancing": download once outside the APK, verify its size/SHA-256 and loop
  the private local file, including offline. Custom HTTPS streams retain uncached, non-looping playback.
  Exiting releases audio focus/player and cancels pending work; failed downloads or streams leave the visuals
  running silently. No new runtime dependency. Source, licence and setup: `docs/screensaver-music.md`.
  Verified on the reference TV (Xiaomi MiTV-MSSP3): audible cached playback, audio focus held across an hour of
  looping and released on exit, with no further network traffic after the one-time download.

## [1.1] - 2026-10-29

The lightweight settings release: LlarTV gains its own settings menu, and the weather, the news and the clock
become the owner's choice instead of the TV's.

### Added

- LlarTV settings on the TV: a native menu in the top bar (*Configuració*), navigable with the remote, that
  writes to the same `home.conf` as the ADB scripts. The Android/TV settings stay reachable from it.
- Clock format preference (`clock`): follow the TV, 24-hour or 12-hour with AM/PM, applied to the home screen,
  the screensaver and the next-match card.
- Weather and news controls (`weather_home`, `weather_screen`, `news_home`): never, only in the morning band or all
  day, for the home screen and for the screensaver. What is switched off on every surface is not downloaded any
  more, and a hidden card leaves no empty space.
- News source presets (`news_source`): 3Cat (Catalan), BBC News Mundo (Spanish) and BBC News World (English),
  plus `automatic` (the default), which follows the interface language and resolves to 3Cat until the language
  selector lands in v1.2. The source is credited next to the headlines and the article link is kept in the
  cache.
- The reuse terms of the 3Cat feed recorded in `docs/news-sources.md`, next to the BBC ones: personal and
  private use only, so a wider distribution of the app has to ask the CCMA first.

### Fixed

- Catalan fixes: the settings menu row for the clock is spelled "Rellotge", and the afternoon greeting is the
  Catalan "bon vespre" from 14:00 instead of the Spanishism "bon tarda".

## [1.0] - 2026-10-25

First release.

### Added

- Home screen with a greeting, a large clock and the date, a dock of favourite apps, a full app grid, and
  shortcuts to the TV inputs, the Wi-Fi settings and the system settings, all with predictable D-pad navigation.
- Daily wallpapers from Bing (the last eight images, rotating every 30 minutes by default) or from any image URL.
- Screensaver: the wallpapers with a slow zoom and cross-fade, the clock, the date, the weather and each image's
  title and credit.
- Time-of-day bands:
  - a morning panel (default 08:00–11:00) with the current weather from Open-Meteo and the latest RSS headlines
    (default: 3Cat front page);
  - a night mode (default 23:00–08:00) with a dim, warm palette on the home screen and the screensaver, applied
    gradually when the band changes.
- Dock band tinted with a dark colour taken from the current wallpaper.
- Optional next-match card for a football club.
- Catalan interface text and dates, regardless of the system language.
- Scripts to install, configure and uninstall LlarTV over ADB: `scripts/install.sh`, `scripts/config.sh` and
  `scripts/uninstall.sh`.

[Unreleased]: https://github.com/almali-dev/almali-llartv/compare/v1.10...HEAD
[1.10]: https://github.com/almali-dev/almali-llartv/compare/v1.9...v1.10
[1.9]: https://github.com/almali-dev/almali-llartv/compare/v1.8...v1.9
[1.8]: https://github.com/almali-dev/almali-llartv/compare/v1.7...v1.8
[1.7]: https://github.com/almali-dev/almali-llartv/compare/v1.6...v1.7
[1.6]: https://github.com/mmarkimark/llartv/compare/v1.5...v1.6
[1.5]: https://github.com/mmarkimark/llartv/compare/v1.4...v1.5
[1.4]: https://github.com/mmarkimark/llartv/compare/v1.3...v1.4
[1.3]: https://github.com/mmarkimark/llartv/compare/v1.2...v1.3
[1.2]: https://github.com/mmarkimark/llartv/compare/v1.1...v1.2
[1.1]: https://github.com/mmarkimark/llartv/compare/v1.0...v1.1
[1.0]: https://github.com/mmarkimark/llartv/releases/tag/v1.0

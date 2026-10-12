# LingoScape

An unfinished RuneLite plugin for translating OSRS into Danish, with other languages planned.
Currently it handles selected menus, names, and dialogue; it does not translate the full game.

## Prerequisites

- **JDK 11**, with `java` and `javac` on `PATH`. Validated on Windows with Temurin 11.0.25.
  If `JAVA_HOME` is set, point it to the JDK installation directory, not its `bin` directory.
- Internet access for Gradle, Maven Central, RuneLite dependencies, and the game client.
- A graphical desktop for RuneLite.

The included wrapper downloads Gradle 8.10; no separate Gradle installation is needed.
RuneLite is pinned to **1.13.1**, the release validated for this change.

## Build and test

In Windows PowerShell, from the repository root:

```powershell
java -version
javac -version
.\gradlew.bat clean build shadowJar --console=plain
```

`build` compiles the plugin and runs JUnit tests. `shadowJar` packages the development
launcher and runtime dependencies. Open the [test report](build/reports/tests/test/index.html)
after a build to inspect results.

### Continuous integration

[GitHub Actions](.github/workflows/ci.yml) runs on every push and pull request, and can
also be started manually. It uses Temurin JDK 11 and runs the Python collector and artifact
validator tests on Python 3.9 (the collector's minimum) and 3.14. These tests use only
checked-in inputs and temporary fixtures; they do not download the external English-source
cache or require game login. Gradle still needs access to its normal dependency repositories.

The build jobs run Java tests, translation validation, and development/release packaging
against both the repository's pinned RuneLite version and the version read from the
[Plugin Hub target](https://github.com/runelite/plugin-hub/blob/master/runelite.version).
The latter is recorded in the Actions summary and fails if it cannot be resolved. At the
time this CI was added, both versions were 1.13.1. This is API/build compatibility coverage;
in-game release verification remains tracked in [issue #6](https://github.com/Haramisi98/LingoScape/issues/6).

Run the same checks locally from the repository root (Python 3.9+ is required):

```powershell
.\gradlew.bat clean build shadowJar releaseJar --no-daemon --console=plain
python -m unittest discover -s tools -p "test_*.py"
python tools/validate_release.py
python tools/validate_plugin_hub.py
```

On macOS/Linux, replace `.\gradlew.bat` with `bash ./gradlew`. To reproduce the Hub-version
job, read the version from the link above and add `-PruneLiteVersion=<version>` to the
Gradle command. Rerun the artifact validator after that build.

[The artifact validator](tools/validate_release.py) inspects the actual `*-release.jar`
in `build/libs`, requiring all compiled production classes, all five dictionaries per
language with exactly the reviewed source entries, and the generated known-text index.
It checks that the index is nonempty, sorted, unique, and composed of 16-byte records.
Unexpected files (including the launcher, test dependencies, source collections, and legacy
archives) fail validation. CI retains Java test reports even on failure and uploads release
artifacts only after validation succeeds.

### Plugin Hub packaging

[Plugin metadata](runelite-plugin.properties) uses `build=gradle`: Hub must retain the
custom build to generate dictionaries and the known-text index. Production resources,
the normal JAR, `releaseJar`, and Hub's artifact contain **reviewed translations only**.
Draft dictionaries belong to the test/development classpath; `run` and `shadowJar` still
include them for local preview. Never submit the standalone `*-all.jar` as a plugin.

[Hub validation](tools/validate_plugin_hub.py) downloads the official
[v5 tooling bundle](https://github.com/runelite/plugin-hub-tooling/releases/tag/v5), caches
its unmodified init script and API recorder under `.gradle/hub-tooling`, and invokes Hub's
`runelitePluginHubPackage` and `runelitePluginHubManifest` tasks with the current Hub RuneLite
version. It requires `tar` with Zstandard support (available on current Windows and Ubuntu).
The wrapper uses Gradle 8.10, matching the v5 bundle. CI runs this in the Hub-version job.

It inspects `build/plugin-hub/plugin.jar` with the same strict content validator and runs
`smokeTestArtifact` with production classes and resources loaded from that JAR. The smoke
test loads every dictionary, looks up known text, and persists/reloads a missing-text
candidate. It excludes loose production resources, draft/test resources, and the development
launcher from the smoke-test classpath. This verifies Hub's build path, not Hub submission,
policy approval, signing, or an in-game session.

**Release dependency:** packaging checks do not approve translations.
Empty reviewed dictionaries are valid for these structural checks; all current translations
are drafts. A useful public release still requires the human review and nonempty dictionaries
tracked in [issue #3](https://github.com/Haramisi98/LingoScape/issues/3).

## Launch locally

```powershell
.\gradlew.bat run --console=plain
```

This starts RuneLite with `--developer-mode --debug` and Java assertions enabled.
In the plugin configuration panel, search for **LingoScape** and enable it if needed.
Open its settings (gear icon) and set **Language** to **Dansk (Danish)** or **English (original)**.
Danish is the default. RuneLite saves the selection; changes refresh dialogue and newly
opened menus after the language finishes loading, without restarting.
Use **English preview / resume learning** (Tab by default). Press it once to show
original English and again to return to the selected language. This temporary override
does not change the saved Language setting and resets when the plugin is disabled.
Dialogue changes apply on the next client tick. Already-open menus retain their current
text until closed; reopen them to see the selected language. RuneLite 1.13.1 measures
menu width when opening, so changing text while open can overflow the menu border.
A small top-left overlay shows the learning language, or **English preview**, and the
current hotkey. It is hidden on the login screen and when English is the saved language.
The **Preview English while learning** settings section explains the shortcut; hover the
hotkey setting for details. Existing custom or unassigned keybindings are preserved.
Enable **Hold to preview** to show English only while holding the hotkey; releasing it or
losing game focus resumes learning. Toggle behavior remains the default. Changing the
hotkey or preview behavior resets preview. Turn off **Show learning widget** to hide the
status overlay without disabling the shortcut. Open menus still update when reopened.
The plugin is disabled by default; an existing RuneLite profile may already have it enabled.
The development client uses your usual RuneLite configuration. Close another RuneLite instance
first to avoid confusion about which client contains your changes.

After editing code, close the client and rerun the command; Gradle rebuilds changed classes.
Close the window to end the run; use Ctrl+C in the terminal if it remains active.

Alternatively, after building:

```powershell
java -ea -jar .\build\libs\LingoScape-1.0-SNAPSHOT-all.jar --developer-mode --debug
```

The smaller JAR without `-all` is not standalone. The executable JAR is a development artifact
and includes test/runtime dependencies, following RuneLite's example.

For macOS/Linux, use `bash ./gradlew clean build shadowJar` and `bash ./gradlew run`.
These platforms have not been validated here.

### Account login

These commands validate client startup. Before configuring login, confirm whether you use the
**Jagex Launcher** or launch RuneLite directly. Account-specific setup has not yet been established
for this checkout, so no launcher replacement or authentication instructions are included here.
Launching the development client does not itself verify account login.

### Updating RuneLite

OSRS updates may require a newer client. To test another published version, pass
`-PruneLiteVersion=<version>` to both build and run commands, then update the default in
[build.gradle](build.gradle) after validation. For example, rechecking the pinned version:

```powershell
.\gradlew.bat clean build shadowJar -PruneLiteVersion=1.13.1
.\gradlew.bat run -PruneLiteVersion=1.13.1
```

The project omits `mavenLocal()`: an incomplete local Maven cache caused missing LWJGL native
artifacts during the initial test attempt.

## Translation behavior and known limits

An expanded, untranslated [English source collection](data/english/README.md) is available for
future translation. It combines existing dictionary keys with public cache and transcript exports,
includes provenance, and can be regenerated with `python tools/collect_english.py`. It is separate
from runtime dictionaries and does not change text displayed in-game.

- Startup uses the saved Language setting, defaulting to Danish (`dk`). English uses the
  original game text without loading a dictionary. Edit structured UTF-8 JSON in
  [translations/](translations/README.md); Gradle validates it and generates runtime JSON maps.
- Missing keys, null/blank translations, and `translationMissing` placeholders preserve English.
  Formatted name fallbacks preserve tags, numbers, casing, and membership suffixes. Dialogue
  fallbacks also preserve original `<br>` line breaks.
- 21 menu actions have migrated Danish test translations, including Use → Brug, Drop → Smid,
  Examine → Undersøg, Take → Tag, Talk-to → Tal med, Walk here → Gå herhen, and Open → Åbn.
  Untranslated entries are omitted from runtime maps and stay English.
  Inventory item names can therefore remain English while their action labels are Danish.
- Dialogue updates run on the client tick. Original menu and dialogue text is retained while
  those UI elements are live so the English override can restore markup and line breaks.
  Shutdown restores only live elements that still contain LingoScape's last text; later
  edits by the game or another plugin are left untouched. Deferred cleanup is completed
  before a re-enabled plugin translates again. Unchanged dialogue is not rewritten each tick.
  Language loading and collection I/O use a background worker that shuts down with the plugin.

## Scaffolding and validation

The setup follows the official [RuneLite example plugin](https://github.com/runelite/example-plugin):
its [build configuration](https://github.com/runelite/example-plugin/blob/master/build.gradle),
[launcher](https://github.com/runelite/example-plugin/blob/master/src/test/java/com/example/ExamplePluginTest.java),
and [metadata](https://github.com/runelite/example-plugin/blob/master/runelite-plugin.properties).
Unlike the example's `latest.release`, this project pins the validated RuneLite version.

[LingoScapeLauncher](src/test/java/com/haramisi98/LingoScape/LingoScapeLauncher.java) registers the
plugin with `ExternalPluginManager` before calling `RuneLite.main`. It lives in the test source
set so the development client remains outside the production plugin code.

Validated on Windows with JDK 11 and RuneLite 1.13.1:

- The initial `clean build shadowJar` succeeded with 40 tests. After adding menu translations
  and language settings, `build shadowJar` succeeded with 55 tests, zero failures or skips.
- The executable JAR contains the launcher, plugin, dictionaries, and correct `Main-Class`.
- The initial scaffolding's `run` launched a responding RuneLite window. Logs confirmed `TranslationPlugin` loaded and
  running, and client initialization completed.
- Account login and in-game translation were not tested. The executable JAR was inspected;
  the desktop launch used the Gradle run task.
- The new settings UI and translated menus have automated coverage but still need manual
  in-game verification. Restart the development client to load the updated code, select Danish,
  and right-click an inventory item to check Brug, Smid, and Undersøg where those actions exist.

Compilation reports deprecated RuneLite API use and an unchecked call in the example-style
launcher. Gradle also reports deprecations relevant to a future Gradle 9 upgrade; the checked-in
Gradle 8.10 build succeeds.

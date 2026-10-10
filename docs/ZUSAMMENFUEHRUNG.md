# Zusammenführung: aus zwei Repos wurde eines

Dieses Repo entstand am 2026-10-10 durch Zusammenlegen von `ysamjo-youtubetv-patches` (Stand
v1.4.0) und `ysamjo-rtlplus-patches` (Stand v1.0.0). Diese Datei hält fest, was dabei angepasst
werden musste — und was bewusst nicht.

## Ausgangslage

Beide Repos stammen aus demselben Template (`MorpheApp/morphe-patches-template`), verwenden
dieselbe Plugin-Version (`app.morphe.patches` 1.3.4), dieselbe Kotlin-Gruppe (`app.ysamjo`) und
dieselbe Paketwurzel (`app.ysamjo.patches`). Der Vergleich der getrackten Dateien ergab:

| | |
| --- | --- |
| Dateien, die in beiden Repos vorkommen | 38 |
| davon byte-identisch | 26 |
| davon abweichend | 12 — **9 davon reine Prosa** (AGENTS, README, CHANGELOG, drei `docs/`-Dateien) |
| echte Code-Kollision | **genau eine**: `patches/src/main/kotlin/app/ysamjo/patches/shared/Constants.kt` |

Weil die Patch-Quellen bereits produktweise in Unterordner geschnitten waren (`youtubetv/`,
`tizentube/` bzw. `rtlplus/`), gab es außer `Constants.kt` keinen Namenskonflikt — weder bei
Dateien noch bei Kotlin-Klassen noch bei Fingerprints.

## Was angepasst werden musste

### 1. `shared/Constants.kt` — die einzige echte Kollision

Beide Repos hatten ein `object Constants` mit ihrer jeweiligen `Compatibility`. Sie wurden zu
einem Objekt mit drei Einträgen vereinigt:

| Eintrag | Quelle |
| --- | --- |
| `COMPATIBILITY_YOUTUBE_TV` | youtubetv |
| `COMPATIBILITY_TIZENTUBE_COBALT` | youtubetv |
| `COMPATIBILITY_RTLPLUS` | rtlplus |

Der KDoc-Block, der im RTL+-Repo über der ganzen Datei stand, wurde in den KDoc von
`COMPATIBILITY_RTLPLUS` verschoben — die Datei hat jetzt eine einheitliche Form: ein
Kurz-KDoc am Objekt, je ein KDoc pro Eintrag.

Jede `Compatibility` erzeugt im Morphe Manager eine eigene App-Gruppe; es war also nichts
weiter zu tun, damit die drei Apps nebeneinander erscheinen.

### 2. `settings.gradle.kts` — Projektname

```diff
-rootProject.name = "ysamjo-youtubetv-patches"
+rootProject.name = "morphe-patches"
```

Rein kosmetisch (Name des Gradle-Root-Projekts), aber die alte Bezeichnung wäre irreführend.

### 3. `patches/build.gradle.kts` — Bundle-Metadaten

Der `about`-Block trug Name, Beschreibung, `source` und `website` des jeweiligen Alt-Repos.
Neu:

```kotlin
name = "ysamjo Morphe Patches"
description = "Patches für YouTube for Android TV (com.google.android.youtube.tv), " +
    "TizenTube Cobalt (io.gh.reisxd.tizentube.cobalt) und RTL+ (de.rtli.tvnow)."
source = "https://github.com/ysamjo/morphe-patches"
website = "https://github.com/ysamjo/morphe-patches"
```

`group` bleibt `app.ysamjo` — daran hängt nichts.

### 4. `gradle.properties` — Versionszähler zurückgesetzt

```diff
-version = 1.4.0        # youtubetv
+version = 1.0.0        # frisches Repo, keine Tags
```

Ein neues Repo hat keine Git-Tags, und das Gradle-Semantic-Release-Plugin liest die aktuelle
Version aus dieser Datei. `1.0.0` ist derselbe Startwert, mit dem auch das RTL+-Repo damals
sauber bei `v1.0.0` herausgekommen ist.

### 5. `.releaserc` — Backmerge-Plugin entfernt

Das Template bringt `@cleyrop-org/semantic-release-backmerge` mit, das `main` automatisch nach
`dev` zurückführt. Das RTL+-Repo hatte es bereits entfernt (Commit „Backmerge-Plugin entfernt
(Konflikt bei versionsgestempelten generierten Dateien)") — **diese Fassung wurde übernommen.**

Grund: beide Läufe erzeugen auf `dev` und `main` jeweils einen Release-Commit, der dieselben
generierten Dateien stempelt (`CHANGELOG.md`, `gradle.properties`, `patches-bundle.json`,
`patches-list.json`, Patch-Block im README). Ein Backmerge rebased genau darauf und kollidiert
zwangsläufig. Das Problem wird mit zwei Produkten nicht kleiner.

### 6. `patches-bundle.json` — auf das neue Repo umgeschrieben

Die Datei trug `download_url` und die Changelog-Links der Alt-Repos. Neu zeigt sie auf
`github.com/ysamjo/morphe-patches`. Sie wird beim ersten Release von `release.yml`
überschrieben — bis dahin ist der Download-Link tot, weil es noch kein Release gibt.

### 7. `patches-list.json` — neu erzeugt statt kopiert

Die Datei listet die Patches samt Kompatibilität und wird aus dem gebauten Bundle generiert.
Sie wurde deshalb nicht gemergt, sondern über

```bash
./gradlew generatePatchesList
```

frisch erzeugt. Siehe `docs/BUILD.md` für die Credentials, die dafür nötig sind.

### 8. Doku zusammengeführt

`README.md`, `AGENTS.md`, `docs/ARCHITEKTUR.md`, `docs/BUILD.md` und `docs/VERIFIKATION.md`
wurden inhaltlich verschmolzen statt eines der beiden zu übernehmen:

- `README.md` — eine Tabelle mit allen drei Apps, danach die produktspezifischen Abschnitte.
- `AGENTS.md` — Konventionen in drei Blöcke geteilt: Cobalt-Seam, RTL+-Seam, repo-weit.
- `docs/ARCHITEKTUR.md` und `docs/VERIFIKATION.md` — je ein Teil pro Ziel, mit
  Inhaltsverzeichnis am Kopf.
- `docs/BUILD.md` — gemeinsamer Rahmen plus ein Unterabschnitt pro Produkt für die
  Patch-Kommandos.

### 9. Extension — nichts zu tun

Nur das youtubetv-Repo enthielt echten Extension-Code
(`app/ysamjo/extension/youtubetv/CobaltScriptInjector.java`). Das RTL+-Repo hatte die
Extension-Struktur nur als leeres Skeleton (`AndroidManifest.xml`, sonst nichts) — die
Manifest-Datei ist in beiden Repos byte-identisch, das Skeleton fiel also ersatzlos weg.

## Was bewusst **nicht** angepasst wurde

- **Die Kotlin-Pakete.** `app.ysamjo.patches.youtubetv.*`, `…tizentube.*`, `…rtlplus.*` bleiben
  wie sie waren. Ein Umbenennen hätte keinen Nutzen und nur Risiko.
- **`util/PatchListGenerator.kt`** — in beiden Repos byte-identisch, unverändert übernommen.
- **`.github/`** — Workflows, Issue-Templates, Dependabot und
  `generate_patches_readme.py` sind in beiden Repos byte-identisch und enthalten keine
  Repo-Namen im Quelltext (der Generator bekommt `owner/repo` als Argument). Unverändert
  übernommen.
- **`package.json`** — enthält weiterhin `@cleyrop-org/semantic-release-backmerge` als
  devDependency, obwohl `.releaserc` es nicht mehr lädt. Das war im RTL+-Repo schon so und
  lief dort; das Paket zu entfernen hieße, `package-lock.json` (192 KB) neu zu erzeugen — für
  null funktionalen Gewinn. Bekanntes, harmloses Überbleibsel.
- **`LICENSE`, `NOTICE`, Gradle-Wrapper, `gradle/libs.versions.toml`** — identisch bzw.
  superset-kompatibel.
- **`local.properties`** — gitignored und maschinenspezifisch, pro Maschine neu anzulegen.

## Die Fallstricke, die überhaupt erst gelöst werden mussten

Unabhängig von der Zusammenführung hat der Bau dieser Patches an fünf Stellen gehakt. Sie sind
in `AGENTS.md` (Konventionen) und `docs/BUILD.md` (Fehlerbehebung) festgehalten, damit sie
nicht erneut gesucht werden müssen:

| Fallstrick | Kern |
| --- | --- |
| **`rawResourcePatch` kann keine `res/…`-Datei anfassen** | `get("res/…")` löst über die Paket-Map des Coders auf, die nur im Full-Resource-Modus gefüllt wird. Ein `rawResourcePatch` allein läuft in `RAW_ONLY` und wirft `PatchException: Package <pkg> not found` — auch wenn der Pfad existiert. Für Bildtausch `resourcePatch` verwenden. |
| **Fehlende GitHub-Packages-Credentials melden sich als Plugin-Fehler** | Nicht `401`, sondern `Failed to apply plugin 'app.morphe.patches' … IllegalArgumentException` ohne Nachricht. Ursache ist `gpr.user`/`gpr.key` bzw. `GITHUB_ACTOR`/`GITHUB_TOKEN`. |
| **Extension-Änderungen brauchen `clean`** | `:patches:buildAndroid` sieht die gebaute `.mpe` nicht als Input, hält den Task für aktuell und packt eine veraltete Extension ins Bundle. |
| **`invoke-static` erreicht `p0` ab v16 nicht** | Dex-Format 35c hat 4-Bit-Register. `CobaltActivity.onCreate` hat 26 Register, `p0` liegt auf v24 → `invoke-static/range` verwenden. |
| **Ein getauschtes Icon beweist sich nicht im Launcher** | Launcher halten Artwork in eigenen Bild-Caches. Belegfläche ist die System-App-Liste; für den Banner hilft nur Cache leeren in den Einstellungen. |

## Eine Behauptung aus dem RTL+-Repo, die sich nicht bestätigt hat

Das RTL+-Repo führte als „erwiesenen Gotcha":

> `import ...Constants.COMPATIBILITY_RTLPLUS` (Member-Import) kompiliert hier NICHT.
> Stattdessen `import app.ysamjo.patches.shared.Constants` und `Constants.COMPATIBILITY_RTLPLUS`
> verwenden.

Im zusammengeführten Repo stehen beide Stile nebeneinander: die drei YouTube-Patches nutzen
Member-Imports (`import app.ysamjo.patches.shared.Constants.COMPATIBILITY_TIZENTUBE_COBALT`),
der RTL+-Patch den Objekt-Import. `:patches:compileKotlin` läuft mit beiden durch (siehe unten).
Die Aussage war also falsch oder bezog sich auf einen Zwischenstand — in `docs/BUILD.md` ist sie
deshalb nicht als Fehlerbehebungszeile übernommen. Beide Stile funktionieren; der Objekt-Import
ist trotzdem der robustere, weil er beim Hinzufügen weiterer Compatibilities nicht angefasst
werden muss.

## Verifikation der Zusammenführung (2026-10-10)

Der Build wurde lokal durchgeführt und gegen echte APKs geprüft — ein kompilierendes Projekt
beweist für sich genommen nichts.

| Prüfung | Ergebnis |
| --- | --- |
| Gradle-Struktur | Root `morphe-patches` mit `:patches` und `:extensions:extension` |
| `:patches:compileKotlin` | läuft durch — beide Import-Stile nebeneinander |
| `:patches:buildAndroid` | `BUILD SUCCESSFUL`, `patches-1.0.0.mpp` (824 KB) |
| Bundle-Inhalt | alle vier Patch-Klassen, alle Fingerprints, `extensions/extension.mpe` |
| `patches-list.json` | 4 Patches, 3 App-Gruppen, Zielversionen korrekt zugeordnet |
| `list-patches` gegen das Bundle | alle vier Patches mit richtiger Kompatibilität und Optionen |
| Apply gegen TizenTube 2.0.2 (Original-APK von GitHub) | `Branding: 10 von 10 Ressourcen ersetzt`, beide Patches `Applied` |
| Bilder im gepatchten APK | 10/10 byte-identisch mit den Ressourcen im Repo |
| Injektion im gepatchten Dex | `CobaltScriptInjector` und `ysamjo`-Marker in `classes.dex` |
| Apply gegen RTL+ 7.15.2 | Fingerprint löst auf, `Applied: RTL+ Werbung deaktivieren (Yospace DAI)` |
| Injizierte Instruktion im RTL+-Dex | `insns size` 17 → 19, an Index 0 `const/4 v0, #0` / `return v0` |

**Eine Einschränkung, die dazugehört:** Das RTL+-APK für den Apply-Test kam vom Google TV
Streamer — und es ist dort bereits die **gepatchte** Fassung installiert (`firstInstallTime`
2026-10-05, im Dex steht schon ein `const/4 v0, #0` / `return v0` ohne Debug-Position an
Index 0). Der Lauf beweist damit, dass der Fingerprint auf einem echten 7.15.2-APK auflöst und
die Instruktion korrekt landet — nicht, dass der Patch auf einem ungepatchten RTL+ sauber
aufsetzt. Gegen ein Stock-APK wurde RTL+ nicht geprüft.

Die Laufzeit-Frage aus `docs/VERIFIKATION.md` (verschwindet die Werbung wirklich, bricht die
Wiedergabe?) ist damit weiterhin offen.

## Nach dem Merge

- **Lokal** sind die Ordner `ysamjo-youtubetv-patches` und `ysamjo-rtlplus-patches` per Finder in
  den Papierkorb verschoben worden (der `osascript`-Aufruf meldete Erfolg). Nachprüfen lässt sich
  das nachträglich nicht mehr: `~/.Trash` ist für den aufrufenden Prozess gesperrt (macOS-TCC,
  `Operation not permitted`), und auch der Finder verweigert die Abfrage (`-10004`). Ob die
  Ordner dort noch liegen oder inzwischen von macOS geräumt wurden, ist also **nicht belegt**.

  Darauf kommt es aber nicht an, denn die vollständigen ZIP-Archive **inklusive Git-Historie**
  liegen unter `~/Developer/Archiv/` und sind verifiziert (`unzip -t` → OK):

  ```
  ~/Developer/Archiv/ysamjo-youtubetv-patches-v1.4.0.zip   (1 269 116 B, 117 .git-Einträge)
  ~/Developer/Archiv/ysamjo-rtlplus-patches-v1.0.0.zip     (384 450 B, 215 .git-Einträge)
  ```

  Sie liegen bewusst **außerhalb** des Repos — `git clean -xdf` würde sie innerhalb nicht
  verschonen.

- **Auf GitHub** sind die Alt-Repos am 2026-10-10 gelöscht:

  ```
  ✓ Deleted repository ysamjo/ysamjo-youtubetv-patches
  ✓ Deleted repository ysamjo/ysamjo-rtlplus-patches
  ```

  Beide URLs antworten danach mit `HTTP 404`, `gh repo list ysamjo` führt nur noch
  `morphe-patches`. Damit sind auch die alten Releases und ihre `.mpp`-Assets weg — es gibt
  jetzt genau eine Quelle. Rückgängig ist das nicht; die ZIP-Archive oben sind die einzige
  verbliebene Kopie der Historie.

  **Stolperfalle beim Löschen:** `gh repo delete` scheiterte zuerst mit

  ```
  HTTP 403: Must have admin rights to Repository.
  This API operation needs the "delete_repo" scope.
  ```

  Das liest sich wie ein Rechteproblem am Repo, ist aber ein fehlender OAuth-Scope —
  `delete_repo` steckt nicht in den Standard-Scopes und muss einzeln nachgezogen werden:

  ```bash
  gh auth refresh -h github.com -s delete_repo
  ```

  Danach steht der Scope in `gh auth status` und das Löschen geht ohne weiteres Zutun durch.

- **Wer die alte Quelle im Morphe Manager eingetragen hatte, muss sie ersetzen** — die alten
  URLs sind tot. Die gültige Quelle ist:

  `https://morphe.software/add-source?github=ysamjo/morphe-patches`

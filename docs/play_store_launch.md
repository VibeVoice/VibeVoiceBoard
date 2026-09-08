# Play Store: alles außer dem Video

## Kontext

Die App funktioniert, die Screenshots sind aufgenommen, die Store-Texte stehen. Was fehlt, ist
die Strecke dazwischen: Textbanner auf den Bildern, zwei fehlende Aufnahmen, und der ganze
Play-Console-Apparat, den Google verlangt, bevor ein Eintrag live gehen darf.

Dieses Dokument deckt **alles bis auf den Videoschnitt** ab. Das Video braucht eine Neuaufnahme
mit sichtbaren Fingertipps und kommt separat.

---

## Teil 0 — Was schon steht

| | Stand |
|---|---|
| Store-Texte en-US + de-DE | geschrieben, aus den Zahlen der Landingpage |
| 25 veraltete HeliBoard-Locales | entfernt |
| App-Name | `VibeVoice Keyboard`, überall gleich |
| APK-Dateiname | `VibeVoiceKeyboard_<version>-<type>.apk` |
| Signing-Konfiguration | vorhanden, liest `keystore.properties` (gitignored) |
| Schwebende Marke, freigestellt | `marketing/assets/floating_mark.png` |
| Rohaufnahmen | WhatsApp, Gmail_2, Google+Marke, Homescreen+Marke |

---

## Teil 1 — Die Bilder

### 1a. Bestand und Lücken

| # | Datei | Aussage | Stand |
|---|---|---|---|
| 1 | `WhatsApp.jpg` | Diktat in einer echten App, Interpunktion von selbst | ✅ |
| 2 | `Gmail_2.jpg` | Fachbegriffe richtig geschrieben | ✅ |
| 3 | `Google_mark_down.jpg` | Drei Sprachen in einem Satz, dazu die Marke | ✅ |
| 4 | `Homescreen.jpg` | Läuft weiter, nachdem man die App verlassen hat | ✅ |
| 5 | Wizard-Hero | Markenbild | ❌ fehlt |
| — | Schritt 1 des Wizards | nur zur Kontrolle, nicht für den Store | ❌ fehlt |

**Aufnahmeanweisung für 5:** Tastatur in den Systemeinstellungen deaktivieren
(Samsung: Einstellungen → Allgemeine Verwaltung → Tastaturliste und Standard), App öffnen,
Hero abfotografieren, *Get started* tippen, Schritt 1 abfotografieren, Tastatur wieder aktivieren.

### 1b. Die Textbanner — HTML, nicht Bildbearbeitung

Neu: **`marketing/render/`**

Jeder Screenshot bekommt oben eine Textzeile im Markenlila. Gebaut als **eine HTML-Datei mit
einem Template**, gerendert über Chrome headless auf exakte Pixelmaße, kompositiert per ffmpeg.

Warum HTML und nicht ein Grafikprogramm: Die Typografie ist dieselbe wie in der App und auf der
Website — Ubuntu Sans, dieselben Gewichte, dieselben Farben aus `tailwind.config.cjs`. Eine
Textänderung ist eine Textänderung, kein neuer Export. Und die Feature-Grafik entsteht aus
derselben Datei.

```
marketing/render/
  frame.html            ein Template für alle Bilder mit Telefon; die Anordnung
                        kippt am Seitenverhältnis
  feature.html          die Feature-Grafik, eigene Komposition ohne Telefon
  captions.json         die Texte, nach Zielgruppe gruppiert
  targets.json          welches Maß wohin
  build.sh / plan.py    Chrome headless → PNG → fastlane/ bzw. marketing/out/
```

**Aufbau je Bild:** Der Screenshot ist 1440 × 3120. Das Banner sitzt als eigene Fläche **über**
dem Screenshot, nicht darauf — das Bild wird also nicht beschnitten, sondern das Ergebnis wird
1440 × 3120 mit dem Banner in den obersten ~420 px, hinter dem der Screenshot leicht nach unten
skaliert weiterläuft. Hintergrund des Banners: der Blob-Verlauf der Landingpage.

### 1c. Die Bannertexte

| # | Englisch | Deutsch |
|---|---|---|
| 1 | Speak. The punctuation writes itself. | Sprich. Die Satzzeichen kommen von selbst. |
| 2 | Terminology, spelled right. | Fachbegriffe, richtig geschrieben. |
| 3 | Three languages. One sentence. | Drei Sprachen. Ein Satz. |
| 4 | It keeps listening after you leave. | Es hört weiter zu, wenn du die App verlässt. |
| 5 | *(kein Banner — das Bild spricht für sich)* | |

Nummer 3 trägt zusätzlich eine kleine Fußzeile: `9,31 % Wortfehlerrate · über fünfzig Sprachen`.
Das ist die einzige Zahl im ganzen Satz Bilder, und sie steht da, wo der mehrsprachige Beweis
daneben liegt.

### 1d. Feature-Grafik

1024 × 500, aus demselben Template: Wortmarke, Slogan, Blobs, Wellen. Wird von Play nur
angezeigt, wenn ein Promo-Video existiert — also erst mit dem Video relevant, aber sie kostet
im selben Durchlauf nichts.

### 1e. Das Seitenverhältnis, vorab klären

1440 × 3120 ist 9:19,5. Play dokumentiert 16:9 bis 9:16. **Ein Testupload in die Play Console
entscheidet, ob beschnitten werden muss** — und das ändert den Zuschnitt aller Bilder. Deshalb
zuerst.

---

## Teil 2 — Die Texte

Weitgehend fertig. Was noch fehlt:

* **Kurzbeschreibung de-DE** prüfen: „Stop typing. Start speaking." bleibt englisch (Slogan),
  der Rest deutsch. 71 Zeichen, passt.
* **Changelogs.** `fastlane/metadata/android/*/changelogs/` trägt noch HeliBoards Versionscodes
  (1001–4005). Unsere sind sechsstellig, es kann also nie einer greifen. Löschen und einen
  einzigen für den ersten Release schreiben.
* **Website-Feld** im Store-Eintrag: `https://vibevoice.net`.
* **Support-E-Mail:** Pflichtfeld. Muss benannt werden.

---

## Teil 3 — Play Console: was Google verlangt

Das ist der Teil, der einen Eintrag blockiert, und er hat nichts mit Code zu tun.

### 3a. Signing

Es existiert **kein Release-Key**. `app/build.gradle.kts` liest `keystore.properties`, die Datei
gibt es nicht.

```
keytool -genkeypair -v -keystore vibevoice-release.jks \
  -alias vibevoice -keyalg RSA -keysize 4096 -validity 10000
```

`keystore.properties` daneben, beides gitignored. **Der Key darf nie verloren gehen** — ohne ihn
kann die App nie wieder aktualisiert werden. Play App Signing nimmt Google den Upload-Key ab,
aber der Upload-Key selbst bleibt deiner. Sichere Ablage besprechen, nicht in dieses Repo.

### 3b. App Bundle statt APK

Play nimmt seit 2021 nur `.aab`. Braucht `bundleRelease` statt `assembleRelease` — die
Konfiguration steht, es ist ein anderer Gradle-Task.

### 3c. Deklarationen

| Was | Warum |
|---|---|
| **Data Safety** | Pflichtformular. Muss sagen: Audio wird erfasst und übertragen, gelöscht sobald das Transkript vorliegt oder der Versuch fehlschlägt; Konto-E-Mail; keine Weitergabe an Dritte. |
| **Foreground Service `microphone`** | Ab Android 14 deklarationspflichtig, mit Begründung und meist einem Demo-Video. Unsere Begründung: das Diktat läuft weiter, wenn die Tastatur zugeht. |
| **Prominent Disclosure** | Vor dem ersten Zugriff auf das Mikrofon muss im Vordergrund stehen, was gesendet wird. Der Wizard-Schritt 4 macht das bereits — der Text muss nur explizit sagen, dass Audio an vibevoice.net geht. |
| **Kontolöschung** | Play verlangt seit 2023 einen Weg, das Konto **außerhalb** der App zu löschen, als URL. Muss auf vibevoice.net existieren und im Formular hinterlegt werden. |
| **Datenschutzerklärung** | URL auf vibevoice.net, existiert. |

### 3d. Berechtigungen, die erklärt oder entfernt werden müssen

| Berechtigung | Lage |
|---|---|
| `SYSTEM_ALERT_WINDOW` | Play prüft das streng. Begründung: die schwebende Marke während eines Diktats ohne Tastatur. Optional, der Nutzer erteilt es selbst. |
| `READ_CONTACTS` | **Von HeliBoard geerbt**, für Namensvorschläge. Es ist die einzige Berechtigung im Manifest, die wir nicht brauchen und die einen Prüfer stutzen lässt. Entfernen ist die klare Empfehlung — es kostet eine Vorschlagsfunktion, die niemand vermissen wird, und spart eine Rechtfertigung. |
| `RECORD_AUDIO`, `INTERNET`, `FOREGROUND_SERVICE*`, `POST_NOTIFICATIONS` | erklärt durch das Produkt |

### 3e. Inhaltseinstufung, Zielgruppe, Werbung

Fragebogen ausfüllen (keine Werbung, keine Käufe in der App — die Bezahlung läuft über die
Website, was Play erlaubt, solange in der App nicht darauf verlinkt wird). **Prüfen, ob der
Wizard oder die Einstellungen irgendwo auf die Preisseite verlinken** — das wäre ein Verstoß
gegen die Zahlungsrichtlinie.

---

## Teil 4 — Reihenfolge

1. **Testupload eines Screenshots** in die Play Console → klärt das Seitenverhältnis.
2. **Wizard-Hero aufnehmen** (Bild 5).
3. `READ_CONTACTS` entfernen, Changelogs aufräumen, Support-Mail und Website eintragen.
4. **Overlay-Pipeline bauen**, alle fünf Bilder + Feature-Grafik rendern.
5. **Release-Key erzeugen**, `bundleRelease` einmal durchlaufen lassen.
6. Data Safety, FGS-Deklaration, Kontolöschung, Inhaltseinstufung ausfüllen.
7. Interner Test-Track, auf dem Gerät installieren, durchspielen.
8. Video (separat).

---

## Betroffene Dateien

| Datei | Änderung |
|---|---|
| `marketing/render/*` | neu — Template, Texte, Ziele, Build-Skript |
| `marketing/assets/*` | die gerenderten Banner und die Feature-Grafik |
| `fastlane/metadata/android/{en-US,de-DE}/images/*` | die fertigen Store-Bilder |
| `fastlane/metadata/android/*/changelogs/` | HeliBoards Codes weg, einer neu |
| `app/src/main/AndroidManifest.xml` | `READ_CONTACTS` raus |
| `app/src/main/res/values/strings.xml` | Prominent Disclosure in Schritt 4 schärfen |
| `docs/play_store_launch.md` | dieses Dokument, als Checkliste geführt |

---

## Verifikation

1. `./gradlew lint testRunTestsUnitTest` — **einmal am Ende**, nicht pro Änderung.
2. `./gradlew bundleRelease` mit gesetztem `keystore.properties` → `.aab` entsteht und ist signiert.
3. `bundletool build-apks --mode=universal` und installieren, um zu prüfen, dass das Bundle das
   Gleiche tut wie das APK.
4. Alle gerenderten Bilder im Zielmaß gegen die Play-Vorgaben halten (PNG/JPEG, kein Alpha,
   kurze Seite ≥ 320 px).
5. Wizard nach dem Entfernen von `READ_CONTACTS` einmal durchlaufen — die Vorschlagsfunktion
   darf nicht abstürzen, sondern muss stillschweigend nichts vorschlagen.

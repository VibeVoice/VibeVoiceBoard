# Onboarding mit dem Branding der Landingpage, und die Assets für den Play Store

## Kontext

Die Tastatur funktioniert, aber sie verkauft sich nicht. Zwei Lücken:

**Der erste Eindruck.** Der Wizard öffnet mit `setup_welcome_image` — HeliBoards Grafik. Wer die App
installiert, sieht als Erstes nichts von VibeVoice. Die Landingpage hat ein Branding, das trägt
(Wortmarke, Slogan, driftende Farbblobs, Schallwellen), und die Tastatur hat davon bisher nur die
Wellen, und die nur während einer Session.

**Der Store.** `fastlane/metadata/android/` trägt 27 Locales. `en-US` ist auf VibeVoiceBoard
umgeschrieben; die 26 anderen sind unverändert HeliBoard — `de-DE/full_description.txt` wirbt
wörtlich mit „Verwendet keine Internetberechtigung und ist daher zu 100 % offline", was für diesen
Fork falsch ist und in einem Store-Listing eine Falschangabe gegenüber Play. Die 14 Screenshots und
die Feature-Grafik sind ebenfalls HeliBoards.

Dazu kommt eine Erkenntnis aus der Recherche, die eine dritte Lücke aufmacht: der Nutzer muss ein
Konto verknüpfen, **bevor** er ein einziges Wort diktiert hat. Das ist die härteste Friktion im
ganzen Ablauf, und der Server hat für genau dieses Problem bereits eine Lösung — für WhatsApp.

Ergebnis dieses Plans: ein Onboarding mit unserem Branding und einem Probierfeld, eine
Anforderungsliste an den Server für Gratis-Minuten ohne Konto, saubere Store-Texte, und ein
Ordner mit Einzelbriefs für die Assets, die Florian aufnimmt.

---

## Was verifiziert wurde (Quelle: `~/repos/VibeVoice`, Stand `main` nach Pull)

Der `jsx-text-extractor` (`website_react/tools/extract-jsx-text.js`) konnte nicht laufen —
`website_react/node_modules` fehlt und war für diese Runde nicht zu installieren. Die Zahlen wurden
stattdessen direkt aus den Konfigurationsdateien gelesen, was die bessere Quelle ist: dort stehen
sie mit ihrer Begründung, und Unit-Tests im Server halten sie gegen die Werte, die er durchsetzt.

| Fakt | Wert | Quelle |
|---|---|---|
| Word Error Rate (Makro) | **9,31 %**, 12 Fixtures, 6 Sprachen | `src/config/accuracy.js` → `WER.macro` |
| Ohne die angezweifelte Fixture | 7,45 % | `WER.macroWithoutDoubtedFixture` |
| Tempo-Headline | **3×** (Band 2–3) | `src/config/rates.js` → `SPEEDUP_HEADLINE` |
| Tippen / Sprechen | 40 wpm / 150 wpm; Bänder 40–70 / 120–160 | `TYPING_RANGE`, `SPEAKING_RANGE` |
| Beleg fürs Tempo | Ruan et al. 2017, ACM IMWUT, DOI 10.1145/3161187 — 153 gesprochen gegen 52 getippt | `rates.js` |
| Sprachen | **„Over fifty"** — bewusst untertrieben | `accuracy.js` → `LanguageClaim()` |
| Was mit dem Audio passiert | „deleted as soon as the transcript exists or the attempt fails" | `accuracy.js` → `AUDIO_DELETED` |
| Free-Tarif | 0 € / 30 Min. pro Monat / 10 Min. pro Datei | `src/config/plans.js` → `PLAN_FIGURES.free` |
| Pro / Ultra | 3 € / 180 Min. — 10 € / 6000 Min. | ebd. |
| Slogan | „VibeVoice / Stop typing. / Start speaking." | `src/components/landing/Hero.jsx` |
| Subline | „Speech to text for everywhere you type." + „3x faster than your keyboard — emails, messages, documents, code." | ebd. |

### Zwei harte Sperren, die für die Store-Texte gelten

1. **Kein Vergleichswert gegen einen Wettbewerber.** `accuracy.js` → `TERMINOLOGY.hedge`: *„We have
   benchmarked no competitor, so that is the shape of the difference rather than a measured result."*
   Es gibt also keine belastbare Zahl für Googles Spracheingabe, und wir dürfen keine erfinden.
   → Wir zeigen **unsere** 9,31 % und demonstrieren die Interpunktion **im Bild**, statt einen
   Vergleich zu behaupten. Das war schon die Empfehlung aus der Diskussion; jetzt ist sie belegt.
2. **Die Engine wird nie genannt.** `accuracy.js` begründet ausdrücklich, warum die Sprachzahl
   untertrieben wird: die echte Zahl ist ein Fingerabdruck der Engine. Store-Texte nennen keine
   Modell- oder Bibliotheksnamen und schreiben „over fifty languages".

### Der Fund, der die Gratis-Minuten trägt

Der Server hat den Mechanismus bereits — für WhatsApp:

```
server_side/modules/user_management/core_mixin.py:408   CREATE TABLE whatsapp_guests (
                                                          phone_number TEXT PRIMARY KEY,
                                                          minutes_used REAL DEFAULT 0,
                                                          created_at TEXT, last_used_at TEXT)
server_side/modules/user_management/whatsapp_mixin.py    check_guest_status / track_guest_usage /
                                                          mark_guest_contact
server_side/blueprints/whatsapp/bot.py:27                GUEST_TRIAL_MINUTES = 5.0
website_react/src/config/plans.js                        WHATSAPP_GUEST_TRIAL_MINUTES = 5
```

Ein kumulativer Zähler je Telefonnummer, ohne Monatsspalte und ohne Reset — *fünf Minuten, einmal,
für immer*. Beim Verknüpfen wird der Zähler auf 9999 gesetzt, damit Entknüpfen den Trial nicht
zurückgibt. Genau die Form, die die Tastatur braucht, nur mit einer Installations-ID statt einer
Telefonnummer. Das Proposal fordert deshalb keine Erfindung, sondern eine Verallgemeinerung.

Das Device-Authorization-Grant, an das der Trial andockt, liegt in
`server_side/blueprints/client/device_linking.py` (`/oauth/device/code`, `/approve`, `/token`).

---

## Branches

Auf **beiden** Repositories gleich benannt:

```
feat/onboarding-and-store-assets
```

* `~/repos/VibeVoiceBoard` — abgezweigt von `feat/background-dictation` (dem aktuellen Stand).
* `~/repos/VibeVoice` — abgezweigt von `main`. Dort landet **nur** das Proposal, nichts sonst;
  es wird gepusht.

---

## Teil 1 — Der Briefing-Ordner (VibeVoiceBoard)

Neu: **`marketing/briefs/`**, eine Textdatei je Ressource, die Florian aufnimmt. Kein Markdown-Roman,
sondern Gedankenstütze: was aufs Bild, wie aufgenommen, worauf zu achten ist, was ich danach damit
mache.

| Datei | Ressource |
|---|---|
| `README.md` | Wie der Ordner zu lesen ist, Zielmaße für Play, wohin die fertigen Dateien wandern |
| `01-keyboard-whatsapp.txt` | Tastatur mit Wellen während einer Session, in einem WhatsApp-Chat |
| `02-keyboard-gmail.txt` | Neue Mail, Platzhalter-Empfänger, halb diktierter Text, Session läuft |
| `03-widget-on-black.txt` | Das schwebende Widget vor schwarzem Screen — Rohmaterial zum Freistellen |
| `04-widget-stage.txt` | Der Hintergrund-Screenshot **ohne** Textfeld, auf den das Widget montiert wird |
| `05-punctuation-proof.txt` | Diktierter Absatz mit Kommas, Punkten, Fragezeichen — der Beweis im Bild |
| `06-gesture-recording.txt` | Die Bildschirmaufnahme der beiden Auslöser (Logo-Tipp, Leertaste halten) |
| `07-wizard-hero.txt` | Screenshot des neuen ersten Wizard-Screens, sobald Teil 3 gebaut ist |

Jeder Brief enthält: Zweck, Gerät/Orientierung, was im Bild sein muss, was **nicht** im Bild sein
darf, Aufnahmehinweise und die Nachbearbeitung, die ich übernehme.

Feste Vorgaben, die in jedem Brief wiederholt werden:
* Hochformat, mindestens 1080 px Breite, Seitenverhältnis zwischen 16:9 und 9:16 (Play-Vorgabe).
* Statusleiste: Uhrzeit ok, aber keine privaten Benachrichtigungen im Ausschnitt.
* Dunkles Tastatur-Theme, damit die Wellen und der Glow zur Marke passen.
* Bei `01`: ein echter privater WhatsApp-Chat ist in Ordnung — keine Nachbearbeitung nötig, solange
  im Ausschnitt nichts steht, das nicht öffentlich sein soll.

Fertige Bilder gehen nach `fastlane/metadata/android/en-US/images/phoneScreenshots/`
(und `de-DE/`), die Rohaufnahmen bleiben im Brief-Ordner. `.gitignore` bekommt keinen Eintrag —
die Assets gehören ins Repository, sonst sind sie beim nächsten Klon weg.

---

## Teil 2 — Das Server-Proposal (VibeVoice-Repository)

Neu: **`proposals/P-058-the-keyboard-asks-for-an-account-before-it-has-shown-anything.md`**

Format nach `proposals/README.md`: Frontmatter mit `id`, `title`, `severity`, `status: proposed`,
`found`, `area`; danach Prosa. Kein Code, keine Implementierung — eine Anforderungsliste mit einem
vorgeschlagenen Interface. Die Zeile in der Tabelle in `proposals/README.md` wird ergänzt.

Inhalt:

**Das Problem.** Die Android-Tastatur ist ohne verknüpftes Konto stumm. Der Nutzer installiert,
öffnet, und der Wizard verlangt eine Registrierung für ein Produkt, dessen Nutzen er noch nicht
erlebt hat. WhatsApp hat dieses Problem bereits gelöst und die Tastatur nicht.

**Der Vorschlag.** `whatsapp_guests` auf eine identitätsunabhängige Form heben: ein Gast ist ein
undurchsichtiger Schlüssel, kein Telefonnummernfeld. Die Tabelle wird zu `guest_trials
(guest_id TEXT PRIMARY KEY, channel TEXT, minutes_used REAL, created_at, last_used_at)`, WhatsApp
schreibt mit `channel='whatsapp'` weiter hinein, die Tastatur mit `channel='android'`. Die vorhandene
Semantik bleibt unangetastet: kumulativ, kein Reset, beim Verknüpfen auf 9999 verbrannt.

**Anforderungen** (nummeriert, damit sie einzeln abgehakt werden können), unter anderem:
* Ein Endpunkt, der gegen eine client-erzeugte Installations-ID einen zeitlich und mengenmäßig
  begrenzten Schlüssel ausgibt, ein Mal je ID.
* Der Schlüssel authentifiziert `wss://vibevoice.net/stream` wie ein normaler API-Key, trägt aber
  ein Trial-Flag.
* Ein **unterscheidbarer** Fehler bei Erschöpfung — nicht derselbe Code wie ein ungültiger
  Schlüssel, sonst kann die Tastatur „registriere dich" nicht von „etwas ist kaputt" trennen.
* Verbrauch wird in derselben Einheit gezählt wie beim WhatsApp-Gast (Minuten, `REAL`).
* Beim Verknüpfen desselben Geräts wird der Trial verbrannt.
* Der Trial-Verbrauch erscheint in Export und Löschung (`export_mixin.py`, `deletion_mixin.py`),
  wie `whatsapp_guests` es heute tut.
* Missbrauchsgrenze: eine Neuinstallation gibt fünf Minuten. Das ist die Obergrenze des Schadens
  und dieselbe, die WhatsApp seit jeher akzeptiert.
* `WHATSAPP_GUEST_TRIAL_MINUTES` wird zu einem kanalunabhängigen Wert oder bekommt ein Geschwister;
  `test_plan_specs_agree.py` muss beide Seiten weiter zusammenhalten.

**Vorgeschlagenes Interface** (Skizze, nicht bindend):

```
POST /api/trial/key            { install_id }      → { api_key, minutes_granted, expires_at }
                                                   → 409 { error: "trial_already_used" }
GET  /api/trial/status         Authorization: …    → { minutes_used, minutes_granted, exhausted }
WS   /stream                   Fehlercode "trial_exhausted" statt "invalid_api_key"
```

**Was ausdrücklich nicht gefordert wird:** kein Play-Integrity, keine Geräte-Attestierung, keine
Telefonnummer. Der Aufwand stünde in keinem Verhältnis zu fünf Minuten.

---

## Teil 3 — Der erste Eindruck im Wizard (VibeVoiceBoard)

### 3a. Der Blob-Hintergrund

Neu: `app/src/main/java/helium314/keyboard/settings/BrandBackground.kt`

Portiert `website_react/src/components/ui/AnimatedPageBackground.css` als Compose-Canvas. Drei
radiale Gradienten, driftend, aus der CSS übernommen:

| Blob | hell | dunkel | Periode |
|---|---|---|---|
| purple | `#9333EA` | `#A855F7` | 25 s |
| green | `#16A34A` | `#22C55E` | 30 s |
| blue | `#2563EB` | `#3B82F6` | 35 s |

* Verlauf: `radial-gradient(ellipse, colour 0%, transparent 70%)` → `Brush.radialGradient` mit
  Stopps bei 0 % und 70 %.
* Größe 140 vmin, also `1.4 * min(breite, höhe)`.
* Deckkraft 0,165 mit `BlendMode.Screen` auf dunklem Grund, 0,12 mit `BlendMode.Multiply` auf hellem
  — beide Werte stehen so in der CSS und sind dort begründet.
* Bewegung `ease-in-out infinite alternate` → `rememberInfiniteTransition` mit
  `RepeatMode.Reverse` und `FastOutSlowInEasing`, Anordnung nach `bg-set-c-v` (der Satz, den der
  Hero benutzt).
* `Settings.Global.ANIMATOR_DURATION_SCALE == 0` respektieren — dann statisch zeichnen, wie es
  `VoiceWaveView.readAnimationsEnabled()` bereits vormacht.

Liegt hinter **allen** Wizard-Seiten, nicht nur der ersten.

### 3b. Der Hero als Schritt 0

`WelcomeWizard.kt` → `Step0` wird ersetzt. Statt `R.drawable.setup_welcome_image`:

* Das Logo groß: `R.drawable.ic_launcher_foreground`, auf Tintenmaß skaliert. `VoiceGlow.inkBounds()`
  und `VoiceGlow.renderMark()` existieren bereits und lösen genau dieses Problem — die Vektorgrenzen
  sind nicht die Tintengrenzen — und werden wiederverwendet statt nachgebaut.
* Darunter die Wortmarke „VibeVoice" (Versalien, halbfett) und der Slogan in zwei dünnen Zeilen:
  „Stop typing." / „Start speaking." — genau die Aufteilung aus `Hero.jsx`.
* Darunter die Subline: „Speech to text for everywhere you type."
* Ein Knopf. Kein zweiter — der Hero der Website hat aus demselben Grund nur einen.
* Hinter allem: `BrandBackground` **plus** `VoiceWaveView` mit synthetischem Pegel.

Für den synthetischen Pegel bekommt `VoiceWaveView` eine zweite Startvariante ohne
`VibeVoiceClient` — eine langsame Sinusschwingung statt `currentLevel`. Der bestehende
`start(source: VibeVoiceClient)` bleibt unverändert; `client` ist bereits eine `WeakReference`, also
kostet ein Null-Fall dort nichts.

Nur Schritt 0 bekommt die Wellen. Die übrigen Seiten bekommen nur die Blobs — die Wellen sind die
Signatur der Tastatur und sollen sie bleiben.

### 3c. Das Probierfeld in Schritt 5

In der jetzigen Reihenfolge ist das Konto in Schritt 4 verknüpft und das Mikrofon wird in Schritt 5
erteilt. Ein Textfeld direkt darunter funktioniert also heute schon, ohne jede Serveränderung.

* Ein `TextField` mit Platzhalter, das die Tastatur öffnet, sobald die Berechtigung erteilt ist.
* Erscheint erst, wenn `RECORD_AUDIO` gewährt wurde — vorher wäre es ein Feld, das nichts tut.
* Der Wizard bleibt sichtbar; der Nutzer diktiert in die App, in der er gerade steht.
* Das ist gleichzeitig die Bühne für die Briefs `05` und `07`: die obere Bildhälfte ist dann unsere.

Wenn der Trial-Key aus Teil 2 kommt, wandert dasselbe Feld vor Schritt 4. Nichts an ihm muss dafür
neu geschrieben werden.

### 3d. Strings

Neue Schlüssel nach `app/src/main/res/values/strings.xml`, Übersetzungen nach
`app/src/main/res/values-de/strings_vibevoice.xml`. Nie in eine Weblate-Datei — die Regel steht in
`AGENTS.md` unter „Strings and translations".

---

## Teil 4 — Die Store-Texte (VibeVoiceBoard)

* `fastlane/metadata/android/en-US/` — Titel, Kurzbeschreibung und Volltext überarbeiten. Die
  Kurzbeschreibung (80 Zeichen) ist das meistgelesene und indexierte Feld; sie führt mit dem Slogan.
* `fastlane/metadata/android/de-DE/` — vollständig neu. Der aktuelle Text ist HeliBoards und
  behauptet das Gegenteil dessen, was dieser Fork tut.
* **Die 25 übrigen Locale-Ordner werden gelöscht.** Play fällt dann auf `en-US` zurück, statt in
  25 Sprachen eine Offline-Behauptung auszuliefern, die für diese App falsch ist. Sie können später
  einzeln und übersetzt zurückkommen.
* Fakten, die in den Volltext gehören, alle aus der Tabelle oben: die 9,31 %, das 3-fache Tempo mit
  der Ruan-Quelle, „over fifty languages", automatische Interpunktion, der Free-Tarif mit 30 Minuten,
  der Verbleib des Audios in der Formulierung aus `AUDIO_DELETED`, und ein Link auf
  `https://vibevoice.net`.
* Keine Zahl gegen einen benannten Wettbewerber. Siehe die Sperre oben.

---

## Betroffene Dateien

| Datei | Änderung |
|---|---|
| `marketing/briefs/*` | neu, 8 Dateien |
| `settings/BrandBackground.kt` | neu — die Blobs |
| `settings/WelcomeWizard.kt` | Step0 ersetzt, Hintergrund auf allen Seiten, Probierfeld in Schritt 5 |
| `latin/vibevoice/VoiceWaveView.kt` | zweite Startvariante mit synthetischem Pegel |
| `res/values/strings.xml`, `values-de/strings_vibevoice.xml` | neue Schlüssel |
| `fastlane/metadata/android/{en-US,de-DE}/` | neu geschrieben; 25 Locales gelöscht |
| `docs/onboarding_and_store_assets.md` | dieser Plan, als Dokument im Repository |
| *(VibeVoice)* `proposals/P-058-…md`, `proposals/README.md` | neu bzw. Zeile ergänzt |

---

## Stand

| Teil | Stand |
|---|---|
| 1 — Briefing-Ordner | fertig, `marketing/briefs/` |
| 2 — Server-Proposal | fertig und gepusht, `VibeVoice@feat/onboarding-and-store-assets` |
| 3a — Blob-Hintergrund | fertig, `BrandBackground.kt` |
| 3b — Hero als Schritt 0 | fertig, `WizardHero` in `WelcomeWizard.kt` |
| 3c — Probierfeld | fertig, Schritt 5 |
| 3d — Strings | fertig, en + de |
| 4 — Store-Texte | fertig, en-US und de-DE neu; 26 Locales entfernt |
| — Bilder | offen, wartet auf die Aufnahmen aus `marketing/briefs/` |

Die Gratis-Minuten selbst (der Trial-Key) sind bewusst **nicht** Teil dieses Branches. Sie hängen
an P-058 im Server-Repository; das Probierfeld sitzt so lange hinter dem Konto-Schritt statt davor.

---

## Verifikation

1. `./gradlew assembleDebugNoMinify` mit `JAVA_HOME=/opt/homebrew/opt/openjdk@21`.
2. `./gradlew lint testRunTestsUnitTest`.
3. Wizard von vorn durchlaufen — dazu die App-Daten löschen oder
   `Settings.PREF_SHOW_SETUP_WIZARD` zurücksetzen: Logo und Slogan auf Seite 0, Blobs auf allen
   Seiten, keine hängende Animation nach dem Verlassen.
4. In Schritt 5 tatsächlich diktieren und prüfen, dass Text im Probierfeld landet.
5. Bei ausgeschalteten Systemanimationen (`ANIMATOR_DURATION_SCALE = 0`) nachsehen: Blobs und Wellen
   müssen statisch stehen, nicht flackern.
6. Hell und dunkel prüfen — die Blob-Deckkraft und der Blendmodus unterscheiden sich zwischen beiden.
7. Über Nextcloud aufs Gerät (`tools/build-and-deploy.sh`), nicht über ADB.
8. Im VibeVoice-Repo: das Proposal liest sich als Anforderungsliste, nennt keine Implementierung,
   und `proposals/README.md` führt es.

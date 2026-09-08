# Play Console: was du klickst, und was ich mache

Für ein **persönliches Konto nach dem 13.11.2023**. Das heißt: geschlossener Test mit
**12 Testern, 14 zusammenhängende Tage**, bevor die Produktion freigeschaltet wird.

> **Die 14 Tage sind der längste Weg im ganzen Projekt.** Sie laufen erst, wenn 12 Tester
> tatsächlich beigetreten sind. Alles andere — Bilder, Banner, Texte — kann parallel entstehen.
> Priorität ist deshalb nicht „schöner Eintrag", sondern **heute ein Build in den geschlossenen
> Test bekommen.**

---

## Block 1 — Heute, ~20 Minuten: die Uhr starten

### 1.1 App anlegen

<https://play.google.com/console> → **Alle Apps** → **App erstellen**

| Feld | Eintrag |
|---|---|
| App-Name | `VibeVoice Keyboard` |
| Standardsprache | Englisch (USA) |
| App oder Spiel | App |
| Kostenlos oder kostenpflichtig | Kostenlos |

Die beiden Erklärungen darunter (Programmrichtlinien, US-Exportgesetze) abhaken.

### 1.2 Dienstkonto anlegen, damit ich den Rest übernehmen kann

Drei Schritte, alle einmalig:

1. **Projekt anlegen:** <https://console.cloud.google.com/projectcreate> → Name z. B. `vibevoice-play`
2. **API einschalten:** <https://console.cloud.google.com/apis/library/androidpublisher.googleapis.com>
   → Projekt oben auswählen → **Aktivieren**
3. **Dienstkonto:** <https://console.cloud.google.com/iam-admin/serviceaccounts>
   → **Dienstkonto erstellen** → Name `play-publisher` → **Fertig**
   → in der Liste auf das Konto klicken → Reiter **Schlüssel** → **Schlüssel hinzufügen** →
   **Neuen Schlüssel erstellen** → **JSON** → die Datei landet im Download-Ordner

Dann in der Play Console verknüpfen:

<https://play.google.com/console> → Zahnrad **Einstellungen** → **API-Zugriff**
→ falls gefragt, das Cloud-Projekt verknüpfen → unter **Dienstkonten** dein
`play-publisher@…iam.gserviceaccount.com` → **Zugriff gewähren**

Berechtigungen setzen: **Version** → *App-Versionen erstellen und in Testtracks veröffentlichen*
sowie *Store-Präsenz verwalten*. Finanzdaten braucht es **nicht**.

**Die JSON-Datei nach `~/repos/VibeVoiceBoard/play-service-account.json` legen.**
Sie ist bereits durch `.gitignore` gedeckt (`*.json` ist es nicht — ich trage sie ausdrücklich ein).

### 1.3 Die zwölf Tester

Das ist der eigentliche Engpass, und es sind echte Google-Konten, keine Aliase.

Am robustesten: **Google-Gruppe** statt einer Liste, dann kannst du Leute nachtragen, ohne die
Track-Konfiguration anzufassen.

<https://groups.google.com> → **Gruppe erstellen** → z. B. `vibevoice-testers` →
Beitritt „Nur eingeladene" → die zwölf Adressen einladen.

In der Play Console: **Test → Geschlossener Test → Track verwalten → Tester** →
**E-Mail-Liste erstellen** oder die Gruppenadresse eintragen.

> Die 14 Tage zählen ab dem Moment, in dem die Tester **beigetreten** sind und beigetreten
> **bleiben**. Wer zwischendrin austritt, setzt seinen Beitrag zurück. Also: einmal einladen,
> Opt-in-Link schicken, und dann in Ruhe lassen.

---

## Block 2 — Die Formulare, die es per API nicht gibt

Play lässt keinen Track frei, bevor **App-Inhalte** vollständig ist. Unten steht für jedes Feld
die Antwort. Pfad jeweils: **Monetarisierung/Richtlinien → App-Inhalte**.

### Datenschutzerklärung
`https://vibevoice.net/privacy`

### App-Zugriff
**Nicht** „Alle Funktionen sind ohne besonderen Zugriff verfügbar."

Das Diktat läuft zwar zehn Minuten ohne Konto (der Trial aus P-058), danach braucht es eins.
Also: **Alle oder einige Funktionen sind eingeschränkt** → Anweisungen hinterlegen:

> Dictation works for 10 free minutes without an account. Beyond that a VibeVoice account is
> required. Test account: `<E-Mail>` / `<Passwort>`. Link it in the app's setup wizard, step 5,
> or under Settings → VibeVoice.

**→ Dafür brauchst du ein echtes Testkonto auf vibevoice.net.** Anlegen und die Zugangsdaten
hier eintragen.

### Werbung
Nein, die App enthält keine Werbung.

### Inhaltseinstufung (IARC-Fragebogen)
Kategorie **Dienstprogramm / Produktivität / Kommunikation**. Alle Fragen nach Gewalt, Sexualität,
Drogen, Glücksspiel: **Nein**. Nutzergenerierte Inhalte: **Nein** — der transkribierte Text geht
in das Textfeld des Nutzers, nicht in einen geteilten Bereich. Ergebnis wird PEGI 3 / USK 0.

### Zielgruppe
Nur **18 und älter** ankreuzen. Das hält uns aus dem Families-Programm mit seinen zusätzlichen
Auflagen heraus, und die App richtet sich ohnehin nicht an Kinder.

### Datensicherheit (Data Safety)

Das längste Formular, und das einzige, bei dem eine falsche Angabe später teuer wird.

| Frage | Antwort |
|---|---|
| Erfasst oder teilt deine App Nutzerdaten? | **Ja** |
| Sind alle Daten bei der Übertragung verschlüsselt? | **Ja** (WSS/HTTPS) |
| Können Nutzer die Löschung ihrer Daten beantragen? | **Ja**, mit URL |

Datentypen:

| Typ | Erfasst | Geteilt | Zweck | Pflicht |
|---|---|---|---|---|
| **Audio → Sprach- oder Tonaufnahmen** | Ja | Nein | App-Funktionalität | Ja |
| **Persönliche Infos → E-Mail-Adresse** | Ja | Nein | Kontoverwaltung | Ja |
| **App-Infos → Absturz-/Diagnoseprotokolle** | Ja | Nein | Diagnose | Nein, optional |

Zwei Punkte, die stimmen müssen und die ich **nicht** aus dem Repo beantworten kann:

* **Audio als „vorübergehend verarbeitet" markieren?** Play meint damit „nur im Arbeitsspeicher,
  nicht gespeichert". Der Server legt die Aufnahme als temporäre Datei ab und löscht sie in einem
  `finally` — also berührt sie kurz die Platte. Sicherer und ehrlicher: **nicht** als vorübergehend
  markieren, sondern als erfasst mit der Aufbewahrung „gelöscht, sobald das Transkript vorliegt
  oder der Versuch fehlschlägt".
* **Fehlerberichte enthalten Diagnoseprotokolle, und darin kann diktierter Text stehen.** Das steht
  bereits in unserer Store-Beschreibung und muss hier konsistent deklariert werden.

### Kontolöschung
Play verlangt seit 2023 einen Weg, das Konto **außerhalb der App** zu löschen, als öffentliche URL.
Muss auf vibevoice.net existieren, z. B. `https://vibevoice.net/delete-account`.
**Ohne diese Seite geht der Eintrag nicht durch.**

### Foreground Service
**Monetarisierung/Richtlinien → App-Inhalte → Berechtigungen für Vordergrunddienste**

Typ `microphone`, Begründung:

> Dictation continues after the keyboard is dismissed, so the user can start speaking in one app
> and finish in another. Android silently feeds a process with no visible window zeroed audio
> buffers, so a foreground service with the microphone type is the only way this can work. The
> service runs only while a dictation session is active, shows a notification with the live
> transcript and a stop button, and stops with the session.

Google fragt hier gelegentlich nach einem kurzen Demonstrationsvideo. Falls ja: das ist dasselbe
Video, das wir für den Store schneiden — dann eben zuerst dafür.

### Restliche Ja/Nein-Fragen
Nachrichten-App: **Nein**. COVID-19: **Nein**. Behörden-App: **Nein**.
Finanzfunktionen: **Nein**. Gesundheits-App: **Nein**.

---

## Block 3 — Was ich mache

Sobald `play-service-account.json` liegt:

| | |
|---|---|
| Release-Schlüssel | erzeugen, `keystore.properties` schreiben, beides gitignored |
| AAB | `bundleRelease` bauen und in den geschlossenen Test hochladen |
| Store-Texte | Titel, Kurz- und Volltext in en-US und de-DE, per API |
| Bilder | die fünf Screenshots mit Bannern rendern und hochladen |
| Feature-Grafik | 1024 × 500 aus demselben Template |
| Changelogs | HeliBoards tote Versionscodes löschen, einen neuen schreiben |
| `READ_CONTACTS` | aus dem Manifest entfernen |

**Über den Schlüssel:** Ohne ihn kann die App nie wieder aktualisiert werden. Er gehört an einen
Ort, der einen Festplattenausfall überlebt — Passwortmanager oder verschlüsseltes Backup. Nicht in
dieses Repo, und nicht nur auf diesem Rechner.

---

## Block 4 — Reihenfolge

| Wann | Was | Wer |
|---|---|---|
| **Heute** | App anlegen, Dienstkonto, 12 Tester einladen | du |
| **Heute** | Schlüssel, AAB, Upload in den geschlossenen Test | ich |
| **Heute** | Testkonto auf vibevoice.net, Löschseite | du |
| **Heute+** | App-Inhalte ausfüllen (Block 2) | du |
| **Parallel** | Banner, Bilder, Texte, Feature-Grafik | ich |
| **Parallel** | Wizard-Hero-Screenshot | du |
| **Tag 14** | Produktionszugriff beantragen | du |

Der einzige Punkt, der nicht warten kann, ist der Test-Track. Alles andere darf schlampig anfangen
und bis Tag 14 gut werden.

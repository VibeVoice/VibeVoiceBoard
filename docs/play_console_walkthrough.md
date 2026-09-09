# Play Console: Stand, und was noch zu klicken ist

Für ein **persönliches Konto nach dem 13.11.2023**: geschlossener Test mit **12 Testern über
14 zusammenhängende Tage**, bevor die Produktion freigeschaltet wird.

> **Die 14 Tage sind der längste Weg im ganzen Projekt.** Sie laufen erst, wenn 12 Tester
> tatsächlich beigetreten sind. Alles andere — Bilder, Texte, Seiten — kann parallel entstehen.

Der ausführliche Auftrag an den VibeVoice-Agenten liegt in Nextcloud unter
*Geteilte Dokumente / VibeVoiceBoard / HANDOFF_VibeVoice_Agent.md*. Dieses Dokument ist die
Play-Console-Seite davon.

---

## Erledigt

Per API gesetzt und zurückgelesen:

| | |
|---|---|
| App | `org.vibevoice.board`, Titel `VibeVoice Keyboard` |
| Bundle | `4.3.1`, versionCode `403001`, signiert |
| Track | `alpha` = **Closed testing**, Release als **Draft** |
| Store-Listing | en-US und de-DE: Titel, Kurz- und Volltext |
| Screenshots | je vier, aus `marketing/shots/*/tall.store.*.png` |
| Feature-Grafik, Icon | je Sprache |
| Release-Notes | `fastlane/metadata/android/*/changelogs/403001.txt` |
| Kontakt | `support@vibevoice.net`, `https://vibevoice.net` |
| Testerliste | `vibevoice-testers@googlegroups.com` |
| `READ_CONTACTS` | aus dem Manifest entfernt |

Zwei Regeln, die dabei am lebenden Objekt gelernt wurden und die man sonst falsch plant:

* **Der Release muss `draft` sein.** *„Only releases with status draft may be created on draft app."*
  Solange die App nie etwas veröffentlicht hat, geht kein anderer Status. Ausrollen ist ein Klick in
  der Console, und den lässt Play erst zu, wenn App content vollständig ist.
* **Länder gehen nicht per API.** *„Country targeting is only supported for staged releases."* Für
  einen normalen Closed-Track-Release ist das ein UI-Schritt.

---

## Das Dienstkonto

`claude@vibevoice-play.iam.gserviceaccount.com`, Schlüssel in `play-service-account.json`
(gitignored). Cloud-Projekt `vibevoice-play`, Android Publisher API aktiviert.

**Die alte „API access"-Seite gibt es nicht mehr.** Google hat das umgestellt: Ein Cloud-Projekt muss
nicht mehr verknüpft werden, und das Dienstkonto wird wie ein Nutzer eingeladen —
**Users and permissions → Invite new users**, mit der Konto-Adresse als E-Mail. Rechte auf
Kontoebene: *Release apps to testing tracks*, *Manage testing tracks and edit tester lists*,
*Manage store presence*. Finanzdaten nicht.

Was die API **nicht** kann: E-Mail-Listen als Tester (*„email lists are not supported by this
resource"* — nur Google-Gruppen), Länder für einen normalen Release, und keine einzige der
App-content-Deklarationen.

---

## Was noch zu klicken ist

### Sofort, ohne auf jemanden zu warten

| Wo | Antwort |
|---|---|
| App content → **Ads** | No |
| App content → **Target audience** | **13-15, 16-17, 18 and over.** Die Grenze liegt bei 13, nicht bei 18: unter 13 gilt die App als kinderorientiert und die Families-Richtlinien greifen, die eine App mit Kontopflicht und Audio-Übertragung nicht besteht. Nur-18+ wäre der andere Fehler — Play warnt selbst vor *„additional restrictions to your availability"*. |
| App content → **Government apps** | No |
| App content → **Financial features** | „My app doesn't provide any financial features". Die Bezahlung läuft über die Website; Abos sind keine financial features im Sinne von Play, gemeint sind Banking, Kredite, Krypto, Versicherungen. |
| App content → **Health** | No |
| App content → **Advertising ID** | No. Geprüft: keine `AD_ID`-Berechtigung, keine Ads-, Firebase- oder Analytics-Abhängigkeit. |
| App content → **Content rating** | Kategorie *Utility, Productivity, Communication or Other*. Gewalt, Sexualität, Drogen, Glücksspiel: durchweg No. Nutzergenerierte Inhalte: **No** — der Text geht in das Feld des Nutzers, nicht in einen geteilten Bereich. Ergebnis PEGI 3 / USK 0. |
| App content → **Foreground service permissions** | Typ `microphone`, Begründung unten |
| Store settings → **App category** | Tools |
| Closed testing → **Countries/regions** | Alle. Kostet im geschlossenen Test nichts. |

**Begründung für den Foreground Service**, wörtlich zu übernehmen:

> Dictation continues after the keyboard is dismissed, so the user can start speaking in one app and
> finish in another. Android silently feeds a process with no visible window zeroed audio buffers, so
> a foreground service with the microphone type is the only way this can work. The service runs only
> while a dictation session is active, shows a notification with the live transcript and a stop
> button, and stops with the session.

### Erst wenn der VibeVoice-Agent geliefert hat

Diese vier gehen zwischen zwei Leuten hin und her und werden deshalb gern vergessen:

1. **Sign in details** — Username und Passwort des Testkontos eintragen und absenden. Name und
   Anleitungstext stehen fertig im Nextcloud-Handoff, Abschnitt 3b; das Feld für die Anleitung fasst
   500 Zeichen und der Text nutzt 496 davon.
2. **Set privacy policy** — die URL eintragen, die der Agent bestätigt.
3. **Data safety** — `play/data_safety.csv` ist ausgefüllt und wird über *Import from CSV*
   eingelesen. Vier Zeilen fehlen noch, sie stehen mit ihrer Question-ID in `play/DATA_SAFETY.md`.
4. **Release ausrollen.** Erst danach greift die Opt-in-URL — und das kann Stunden dauern. Vorher
   darf die Rundmail nicht raus.

---

## Die Tester

Opt-in-URL: `https://play.google.com/apps/testing/org.vibevoice.board`
Gruppe: `https://groups.google.com/g/vibevoice-testers`

Beide URLs stehen fest, bevor sie funktionieren — die eine folgt aus dem Package-Namen, die andere
aus dem Gruppennamen. Es gibt also kein Henne-Ei-Problem: Seite, Mail und Gruppentexte dürfen sie
eintragen, solange niemand den Link bekommt, bevor er greift.

**25 bis 30 einladen, nicht 12.** Es gibt keine Obergrenze, und Puffer erspart es, jemanden um
Wohlverhalten zu bitten. Nachrücken hilft nicht kurzfristig: Wer an Tag 8 beitritt, hat an Tag 14
erst sechs.

Rückmeldungen laufen auf `keyboard-beta@vibevoice.net`, getrennt von `support@`. Beim
Produktionsantrag fragt Google, was die Tester gemeldet haben und was daraufhin geändert wurde —
dieses Postfach ist die Quelle für diese Antwort.

---

## Reihenfolge

| Wann | Was | Wer |
|---|---|---|
| erledigt | Dienstkonto, Bundle, Listing, Screenshots, Testerliste | ich |
| jetzt | die zehn Felder oben | Florian |
| jetzt | Löschseite, Testkonto, Datenschutz-URL, die vier CSV-Zeilen | VibeVoice-Agent |
| dann | Sign in details, Privacy policy, Data safety importieren | Florian |
| dann | Release ausrollen, Opt-in-URL prüfen | Florian |
| dann | Rundmail, 25–30 Einladungen | VibeVoice-Agent |
| Tag 14 | Produktionszugang beantragen | Florian |

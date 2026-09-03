# Diktieren, das die Tastatur überlebt

Stand: 2026-09-03. Vorschlag, nichts davon ist gebaut.

## Was heute passiert, wenn sich die Tastatur schließt

Kurz: die Session läuft weiter, aber sie nimmt nichts mehr auf.

Ab Android 11 darf ein Prozess ohne sichtbares Fenster das Mikrofon nicht mehr benutzen. Er
bekommt keinen Fehler — er bekommt **Nullen**. Eine IME zählt als sichtbar, solange ihre Input-View
steht; ist sie weg, ist der Prozess Hintergrund und `AudioRecord` liefert Stille.

Der Ablauf im Code, nachvollziehbar an `VibeVoiceClient`:

1. `onWindowHidden` — die Session bleibt bestehen, das Objekt lebt, der Socket ist offen.
2. Die Aufnahmeschleife liest weiter, aber nur noch Nullbytes.
3. Nach 2 s (`zeroLimitBytes`) greift die Zero-Buffer-Erkennung, `isSilencedByPolicy()` bestätigt
   es über `AudioRecordingConfiguration.isClientSilenced`.
4. Recovery initialisiert das Mikrofon neu — drei Mal, mit demselben Ergebnis, weil sich an der
   Sichtbarkeit nichts geändert hat.
5. `WARN_MIC_UNAVAILABLE`, dann Ende.

Das heißt: **„nicht stoppen" allein reicht nicht.** Ohne weitere Arbeit ist das Ergebnis nicht
Hintergrunddiktat, sondern eine Session, die zwei Sekunden lang Stille aufnimmt und dann anders
scheitert als vorher. Der Unterschied ist nur, wie es sich anfühlt.

Es gibt genau einen legitimen Weg, das Mikrofon offen zu halten: einen **Foreground Service mit
`foregroundServiceType="microphone"`**. Der ist nicht Zierde, sondern die Bedingung. Und er bringt
zwangsläufig eine Notification mit — das System verlangt sie. Deine Intuition mit der
Benachrichtigung ist also nicht nur Komfort, sie ist Teil des Preises.

Nebenbei: die grüne Mikrofon-Anzeige des Systems läuft ohnehin die ganze Zeit mit. Ein Teil von
„hey, VibeVoice hört noch zu" ist bereits da, nur eben ohne unseren Namen daneben.

## Aufbau

Vier Stufen. Die erste ist die eigentliche Arbeit, die letzte ist optional.

### Stufe 1 — die Session von der Tastatur lösen

Heute besitzt `LatinIME` die Session: `mVibeVoiceClient`, `mIsRecordingVoice`, `mVoiceComposingText`
sind Felder der IME, und ihre Lebensdauer ist die der Input-View. Genau das ist der Grund, warum
jedes Schließen des Fensters ein Problem ist.

Vorschlag: `VoiceSessionService`, ein Foreground Service, der `VibeVoiceClient` besitzt. Die IME
wird von der Eigentümerin zur **Konsumentin** — sie abonniert Teiltranskripte und schreibt sie in
die Input Connection, solange sie eine hat.

```
VoiceSessionService  (Foreground, microphone)
  └── VibeVoiceClient        Mikrofon + Socket, unverändert
        │
        ├── TranscriptSink: LatinIME        solange eine Input Connection existiert
        └── TranscriptSink: Buffer          sonst
```

Das ist die logische Trennung, die du meinst. Alles Weitere hängt daran, und ohne sie ist keine der
anderen Stufen baubar.

Zu klären beim Bauen:

- Der Service darf ab Android 14 nur gestartet werden, während die App sichtbar ist. Beim Start
  einer Diktatsession steht die Tastatur — passt, aber die Reihenfolge ist bindend: Service zuerst,
  Mikrofon danach.
- Neue Permissions: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MICROPHONE`, `POST_NOTIFICATIONS`.
- Der Prozess ist derselbe wie die IME. Stirbt die IME, stirbt der Service — ein IME-Prozess wird
  aber selten weggeräumt, solange er die aktive Tastatur ist.

### Stufe 2 — die Notification als Anzeige *und* Bedienung

Kommt mit Stufe 1 ohnehin. Ihr Inhalt ist eine Entscheidung, keine Pflicht:

- Live das letzte Teiltranskript, damit sichtbar ist, dass wirklich etwas ankommt.
- **Stopp**, damit die Session ohne Tastatur beendet werden kann. Das ist der wichtigste Knopf:
  ohne ihn gibt es eine Aufnahme, die man nicht mehr erreicht.
- **Kopieren**, als Ausweg, wenn der Text nirgends eingefügt werden konnte.

Kostet keine Sonderrechte außer `POST_NOTIFICATIONS`, funktioniert auf jedem Gerät, und ist nicht
wegwischbar, solange der Service läuft.

### Stufe 3 — wohin der Text geht, wenn es kein Feld gibt

Der offene Punkt aus deiner Nachricht: „vielleicht braucht man es gar nicht, weil da, wo das nächste
Textfeld ist, man ja dann auch wieder einfügen kann."

Fast. Es gibt drei Fälle, und nur einer davon ist trivial:

| Lage | Verhalten |
|---|---|
| Tastatur offen, Feld da | Wie heute: Composing Text, dann Commit. |
| Tastatur zu, später dasselbe Feld | Puffern, beim Wiederauftauchen anbieten. |
| Tastatur zu, später ein **anderes** Feld | Anbieten, nicht einfügen. |

Der dritte Fall ist der, den ich gerade zu scharf gelöst hatte. Automatisch einzufügen ist falsch —
das Feld kann ein Passwortfeld sein. Aber wegwerfen ist eben auch falsch, das war mein Fehler.

Richtig ist ein drittes: **anbieten**. Der Text landet als Chip in der Suggestion Strip
(„Diktat einfügen"), einen Tap entfernt, und parallel in der Notification. Der Nutzer entscheidet,
wo er hingehört. Passwort- und Incognito-Felder bekommen den Chip gar nicht erst.

Das ist heute schon halb da: die aktuelle Regel lässt eine Session über einen Feldwechsel
weiterlaufen und bricht nur bei Passwort- oder Incognito-Feldern ab.

### Stufe 4 — das schwebende Widget

Die Idee mit dem Herausziehen des Mikrofonsymbols. Ehrlich bewertet:

**Was es braucht.** `SYSTEM_ALERT_WINDOW` — eine Sonderberechtigung, die der Nutzer in den
Systemeinstellungen erteilen muss, nicht per Dialog. Play prüft sie genauer als normale Rechte, und
sie ist der Grund, warum viele Apps das Feature gar nicht erst anbieten.

**Was die Geste kostet.** Das IME-Fenster endet an seiner Oberkante; ein Drag darüber hinaus ist
kein normales Drag, sondern muss beim Überschreiten der Kante in ein Overlay-Fenster übergeben
werden. Machbar, aber es ist die Sorte Interaktion, die auf drei Geräten funktioniert und auf dem
vierten nicht.

**Was es bringt, das die Notification nicht bringt.** Sichtbarkeit ohne Wischen nach unten, eine
Pegelanzeige, und das Gefühl, dass das Ding wirklich läuft. Das ist echt — es ist nur nicht die
Funktion, sondern deren Darstellung.

**Empfehlung:** Stufen 1–3 zuerst, und dann am Gerät schauen, ob die Notification reicht. Wenn sie
nervt oder übersehen wird, ist Stufe 4 danach immer noch baubar — und dann mit dem Wissen, wofür
genau. Andersherum baut man die Sonderberechtigung ein, bevor man weiß, ob man sie braucht.

## Play Store

Zwei Punkte, die früh geklärt sein wollen, weil sie die Architektur bestimmen:

- Ein **Foreground Service vom Typ `microphone`** muss im Play-Formular begründet werden. Eine
  Tastatur, die im Hintergrund das Mikrofon offen hält, ist eine Kombination, die Prüfung anzieht.
  Die Begründung ist gut — Diktat, das ein App-Wechsel nicht abschneidet — aber sie muss stehen.
- **`SYSTEM_ALERT_WINDOW`** (Stufe 4) zieht dieselbe Prüfung noch einmal, zusätzlich.

Das ist ein weiteres Argument, Stufe 4 zu vertagen: eine Einreichung mit einer erklärungsbedürftigen
Berechtigung ist leichter als eine mit zwei.

## Wo es beim Bauen weh tun wird

Nicht als Abschreckung, sondern damit es niemanden überrascht:

- Die Kopplung Aufnahmezustand ↔ Anzeige war in diesem Fork wiederholt Fehlerquelle
  (`CLAUDE.md` sagt es, diese Session hat es zweimal bestätigt). Nach Stufe 1 gibt es **drei**
  Konsumenten dieses Zustands statt einem: Toolbar-Taste, Wellen, Notification. Die Quelle muss
  dann der Service sein, nicht die IME, sonst wird aus einem Kopplungsproblem ein dreifaches.
- `mVoiceSessionId` schützt heute gegen verspätete Callbacks. Über einen Service hinweg wird diese
  Prüfung wichtiger, nicht unwichtiger.
- Prozesstod im Hintergrund ist selten, aber möglich. Eine Session, die stirbt, muss das in der
  Notification sagen und nicht stumm verschwinden.

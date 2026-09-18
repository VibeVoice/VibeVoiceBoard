# Das Onboarding, neu gedacht

## Der Befund

Sieben Bildschirme, und der Nutzer *tut* auf keinem davon etwas mit der Tastatur außer
Häkchen setzen:

```
0  Hero
1  Aktivieren              ← Android verlangt es
2  Auswählen               ← Android verlangt es
3  „Congratulations, you're all set!"
4  Mikrofon + Probierfeld
5  Konto verknüpfen
6  Extras (Hintergrund, Overlay)
```

Zwei konkrete Fehler stecken darin.

**Die Schrittnummern lügen.** `firstUnfinishedStep()` springt beim Einstieg auf 1, 2 oder 3, je
nachdem, was am System schon eingestellt ist. Wer die Tastatur vorher aus Androids eigener
Aufforderung aktiviert hat, landet auf „Schritt 2" und fragt sich, was Schritt 1 war. Die Nummer ist
absolut, der Einstieg ist es nicht.

**Schritt 3 ist ein Glückwunsch.** Ein ganzer Bildschirm, der bestätigt, was der Nutzer gerade sieht.

## Die neue Form

Sechs Bildschirme statt sieben — aber das ist nicht der Punkt. Der Punkt ist, dass zwei davon
*Erfahrungen* sind statt Anweisungen.

```
0  Hero                  unverändert
1  Turn it on            Aktivieren + Auswählen, ein Schritt, zwei Haken
2  Try it                Tippen → Mikrofon → Sprechen, drei Phasen, ein Bildschirm
3  Your minutes          die Zahlen, NACHDEM er es gefühlt hat
4  The floating mark     Vorschau, gesicherter Ausstieg
5  Done                  wie der Hero, zum Schluss
```

---

## Schritt 1 — „Turn it on"

Aktivieren und Auswählen sind für Android zwei Dialoge und für den Nutzer **eine** Sache: die
Tastatur anschalten. Also ein Schritt mit zwei Haken darin.

```
  ○ Enable VibeVoice Keyboard        [ Open settings ]
  ○ Select it as your keyboard
```

Kommt er vom ersten Systemdialog zurück, füllt sich Haken eins und der Knopf wird zu
*Switch to VibeVoice*. Wer schon aktiviert hat, sieht Haken eins **bereits gefüllt** — statt eines
Schritts, den es nie gab.

Das ist der eigentliche Fix für die lügenden Nummern: Der Schritt verschwindet nicht, sein Zustand
wandert. Es gibt nichts mehr zu überspringen, also auch keine Lücke zu erklären.

## Schritt 2 — „Try it", der Kern

Ein Bildschirm, drei Phasen. Der Text über dem Feld wechselt, der Bildschirm nicht.

**Phase A — tippen.** Feld hat Fokus, Tastatur ist offen, darüber steht:
> *Your keyboard is ready. Type something.*

Er tippt. Das ist das erste Mal, dass er die Tastatur sieht, und er benutzt sie sofort.

**Phase B — Mikrofon.** Sobald ein paar Zeichen stehen, eine Sekunde Pause, dann wechselt die Zeile:
> *Nice. Now the other half — let it hear you.*

Darunter die Offenlegung (was an vibevoice.net geht) und der Knopf. Die Offenlegung bleibt vollständig,
sie ist Play-Pflicht — aber sie steht jetzt bei jemandem, der die Tastatur schon mag.

**Phase C — sprechen.** Nach der Erteilung:
> *Tap the mark and say something.*

Und hier das Entscheidende: **die VibeVoice-Marke in der Werkzeugleiste pulsiert.** Aktiv/inaktiv im
Wechsel, bis er sie antippt. Er muss nicht suchen, wohin — das Ziel zeigt auf sich selbst.

Technisch: Der Wizard setzt eine Prefs-Flag, `SuggestionStripView` liest sie und animiert die Taste.
Gelöscht wird sie beim ersten Diktat, beim Schließen des Wizards und nach einem Timeout — eine
Tastatur, die für immer blinkt, weil eine Activity gestorben ist, wäre schlimmer als gar kein Hinweis.

Am Ende dieses Schritts hat er **diktiert**. Alles davor war Verwaltung, alles danach ist Angebot.

## Schritt 3 — „Your minutes"

Erst jetzt die Zahlen, und erst jetzt, weil sie jetzt etwas bedeuten:

> *These first 10 minutes are on us — no account needed.*
> *After that, a free account gives you 30 minutes a month. Pro is €3 for 180.*

Vorher wäre das ein Preisschild an etwas, das er nicht kennt. Genau der Defekt, den P-058 benennt:
Das Konto ist der Preis, die Transkription ist der Nutzen, und den Preis zuerst zu nennen heißt,
jemanden etwas kaufen zu lassen, das man ihm nicht gezeigt hat.

Verknüpfen ist hier ein echtes Angebot — mit *Later* als gleichwertigem Weg, denn die zehn Minuten
laufen ja.

## Schritt 4 — „The floating mark"

Vorschaubild (`floating_mark_preview` existiert), daneben, was es kann: Diktat überlebt das Schließen
der Tastatur, die Marke pulsiert mit der Stimme, zum Beenden auf das X ziehen.

**Der Ausstieg ist gesichert.** Primärknopf *Enable*, sekundär ein Textlink *Not now* — nicht zwei
gleich aussehende Knöpfe nebeneinander, an denen man sich vertippt. Auf *Not now* ein Dialog:

> *Without this, dictation stops when the keyboard closes.*
> [ Keep it off ]  [ Turn it on ]

**Genau einmal.** Eine zweite Nachfrage wäre Nötigung, und Play sieht bei
`SYSTEM_ALERT_WINDOW` ohnehin genau hin. Der Dialog darf den Abschluss nie blockieren — er stellt die
Frage einmal, dann gilt die Antwort.

## Schritt 5 — „Done"

Kein Kartenbildschirm, sondern die Hero-Seite noch einmal: Blobs, Wellen, die Wortmarke groß.

> **You're set.**
> *Typing will never feel the same.*

Der Wizard hat mit der Marke begonnen und endet mit ihr. Dazwischen lag die Einrichtung; der letzte
Bildschirm gehört wieder dem Produkt.

---

## Was das kostet

| | |
|---|---|
| Schritt 1 zusammengelegt | `firstUnfinishedStep()` fällt weg, ersetzt durch zwei abgeleitete Haken |
| Schritt 2, drei Phasen | neuer Zustand im Schritt; die Phase hängt an Textlänge, Berechtigung, Diktatzustand |
| Pulsierende Marke | Prefs-Flag + Animation in `SuggestionStripView`, mit drei Löschpfaden |
| Schritt 3 | im Wesentlichen der heutige Link-Schritt, andere Worte |
| Schritt 4 | heutiger Extras-Schritt plus Bestätigungsdialog |
| Schritt 5 | `WizardHero` mit anderem Text wiederverwendet |

## Die Risiken, ehrlich benannt

**Phase B kann hängen.** Wer nichts tippt, sieht nie die zweite Phase. Also: nach einigen Sekunden
ohne Eingabe geht es trotzdem weiter — der Bildschirm darf nicht davon abhängen, dass jemand mitspielt.

**Die pulsierende Taste ist eine Tastatur, die sich bewegt.** Sie muss aufhören, sobald er getippt hat,
und sie darf nie in einer echten Sitzung auftauchen. Drei Löschpfade sind kein Übermaß.

**Der Bestätigungsdialog ist grenzwertig.** Einmal fragen ist Fürsorge, zweimal ist Nötigung. Die
Grenze ist eine Zeile Code und ein großer Unterschied im Ton.

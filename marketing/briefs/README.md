# Briefs für die Store-Assets

Eine Datei je Aufnahme, die Florian macht. Kein Prozessdokument — eine Gedankenstütze
mit dem Bildschirm vor der Nase.

## Wie eine Datei zu lesen ist

Jeder Brief hat dieselben fünf Abschnitte:

* **Wofür** — welchen Slot im Store dieses Bild füllt und was es beweisen soll.
* **Aufbau** — was im Bild sein muss.
* **Nicht im Bild** — was im Ausschnitt nichts zu suchen hat.
* **Aufnahme** — wie es gemacht wird.
* **Danach** — was ich mit der Rohdatei mache.

## Wohin die Dateien

Rohaufnahmen: `marketing/raw/` (anlegen, wenn die erste kommt). Benennung frei, aber die
Brief-Nummer im Namen hilft: `01-whatsapp-roh.png`.

Fertige Bilder lege ich ab unter:

```
fastlane/metadata/android/en-US/images/phoneScreenshots/1.png … 5.png
fastlane/metadata/android/de-DE/images/phoneScreenshots/1.png … 5.png
fastlane/metadata/android/en-US/images/featureGraphic.png
```

Die Reihenfolge im Store ist die Dateinummer. Die ersten drei sind das, was jemand ohne
Wischen sieht — sie tragen die Entscheidung.

## Play-Vorgaben (harte Grenzen)

| Asset | Format |
|---|---|
| Screenshot Telefon | PNG oder JPEG, 24 bit, kein Alpha. Kurze Seite ≥ 320 px, lange Seite ≤ 3840 px. Seitenverhältnis zwischen 16:9 und 9:16. Mindestens 2, höchstens 8 |
| Feature-Grafik | 1024 × 500 px, PNG oder JPEG, kein Alpha |
| App-Icon | 512 × 512 px, PNG mit Alpha |

Ein Screenshot direkt vom Telefon erfüllt das Format von allein. Wichtig ist nur, dass
alle aus **derselben** Auflösung kommen — gemischte Größen sehen im Karussell schief aus.

## Für alle Aufnahmen

* **Hochformat.** Kein Querformat, keine Tablet-Aufnahme.
* **Dunkles Tastatur-Theme.** Die Wellen und der Schein hinter dem Logo leben von dunklem
  Grund; auf hell verschwinden sie fast.
* **Statusleiste aufräumen.** Uhrzeit und Akku sind in Ordnung und wirken echt. Private
  Benachrichtigungen im oberen Rand sind es nicht — Flugmodus kurz an, oder die Leiste
  vorher leeren.
* **Volle Auflösung.** Nicht aus einer Galerie-Vorschau heraus teilen, sonst kommt eine
  skalierte Fassung an.
* **Nichts nachträglich zuschneiden.** Ich schneide, wenn es nötig ist, und dann alle
  Bilder gleich.

## Reihenfolge

`01` und `03` zuerst — das sind die zwei Bilder, die die Entscheidung tragen, und `03`
ist Rohmaterial, das ich noch weiterverarbeite. Der Rest kann warten.

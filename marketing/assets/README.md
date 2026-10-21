# Fertige Bildbausteine

Nicht die Store-Bilder selbst, sondern die Teile, aus denen sie zusammengesetzt werden.

| Datei | was es ist |
|---|---|
| `floating_mark.png` | die schwebende Marke, freigestellt, 405 × 405, mit Alpha |

`floating_mark.png` entsteht aus `../raw/floating_mark.jpg` durch
`../tools/extract_mark.py`. Der Kopf des Skripts erklärt, warum die Rohaufnahme vor
**schwarzem** Grund entstehen muss und warum das keine Hintergrundentfernung ist.

Wenn die Marke sich ändert (andere Größe, anderer Schein), muss `DISC` im Skript neu
gemessen werden. Wie, steht ebenfalls im Kopf.

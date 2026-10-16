# Changelog

English below each version, then Spanish. · En cada versión, primero inglés y después español.

## 0.1.0 · 2026-10-16

First public version.

- Extracts PS4 fake PKGs (fPKG) into the game folder with an engine ported from shadPS4 `v.0.7.0` and LibOrbisPkg. Tested with a real game fPKG, compressed (PFSC) parts included, on a POCO F9 Ultra.
- Meet Mr. Bin: the icon, the themed (monochrome) icon, the notification icon and the Extract tab all show the gentleman in the tweed jacket. Everything is drawn by `art/icon.py`.
- Dark tweed-brown palette taken from the icon: lime green for actions, mustard for labels, green and tie red only for results.
- English and Spanish, following the phone's language (per-app language on Android 13+). The log is always in English.
- Two tabs, Extract and Results. The log sits behind a small button at the top right, with Copy and Share; a failed result has an "Open log" button.
- Folder paths are shown short (`Download/Games` instead of `/storage/emulated/0/Download/Games`).
- Extract screen: while extracting, the progress card goes first, with written / total under the bar, the file count, time left, speed, elapsed time and the current file on one line. The Extract button lives in a bar above the tabs. Each PKG shows its type, version and size as chips and long file names are cut in the middle.
- Default destination: `Download/MrBin`. Partial folders are named `<ID>.partial`.
- GPL-3.0-or-later.

**Español**

Primera versión pública.

- Extrae PKG de PS4 falsos (fPKG) a la carpeta del juego con un motor traducido de shadPS4 `v.0.7.0` y LibOrbisPkg. Probado con el fPKG de un juego real, partes comprimidas (PFSC) incluidas, en un POCO F9 Ultra.
- Llega Mr. Bin: el ícono, el ícono temático (monocromo), el de la notificación y la pestaña Extraer muestran al señor del saco de tweed. Todo lo dibuja `art/icon.py`.
- Paleta oscura café tweed sacada del ícono: verde lima para las acciones, mostaza para las etiquetas, y verde y rojo corbata solo para los resultados.
- En inglés y en español, según el idioma del celular (idioma por app desde Android 13). El registro siempre va en inglés.
- Dos pestañas, Extraer y Resultados. El registro está detrás de un botón pequeño arriba a la derecha, con Copiar y Compartir; un resultado con error trae el botón «Abrir registro».
- Las rutas se ven cortas (`Download/Juegos` en vez de `/storage/emulated/0/Download/Juegos`).
- Pantalla Extraer: mientras extrae, la tarjeta del avance va primero, con lo escrito / el total bajo la barra, la cuenta de archivos, lo que falta, la velocidad, el tiempo que lleva y el archivo actual en una línea. El botón Extraer vive en una barra encima de las pestañas. Cada PKG muestra tipo, versión y tamaño como etiquetas, y los nombres largos se cortan por la mitad.
- Destino por defecto: `Download/MrBin`. Las carpetas a medias se llaman `<ID>.partial`.
- GPL-3.0-or-later.

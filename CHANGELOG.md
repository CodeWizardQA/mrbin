# Changelog

English below each version, then Spanish. · En cada versión, primero inglés y después español.

## 0.2.0 · 2026-10-16

- The permissions screen no longer closes as soon as file access is given. It shows the three permissions with their state and goes away when you tap **Continue** (enabled once file access is there) or when all three are given. Battery and display over other apps can be skipped and given later from the new key button at the top right, next to the log.
- Each permission says where to tap when the phone opens its own page: **No restrictions** on the battery page of Xiaomi / HyperOS, and Mr. Bin in the list of apps for display over other apps.
- New icon from the Mr. Bin artwork: Mr. Bin holding up the glowing .BIN file, teddy in his pocket, on navy with a violet glow. Same art in the themed (monochrome) icon, as line art, and on the Extract tab. `art/icon.py` cuts every icon from `art/source/mrbin-art.jpg`. The notification icon stays a line drawing made for 24 dp.
- New palette from the artwork: deep navy surfaces, violet glow around the main action, the neon green of the .BIN file for actions and the yellow of the logo for labels. Main buttons and the progress bar use the yellow → green of the logo. Every text pair stays at 4.5:1 or more.
- The README shows the artwork.

**Español**

- La pantalla de permisos ya no se cierra apenas das el acceso a archivos. Muestra los tres permisos con su estado y se quita cuando tocas **Continuar** (se activa con el acceso a archivos) o cuando los tres están dados. Batería y mostrar sobre otras apps se pueden saltar y dar después con el botón nuevo de la llave, arriba a la derecha, junto al registro.
- Cada permiso dice dónde tocar cuando el celular abre su propia pantalla: **Sin restricciones** en la de batería de Xiaomi / HyperOS, y Mr. Bin en la lista de apps para mostrar sobre otras apps.
- Ícono nuevo sacado del arte de Mr. Bin: Mr. Bin levanta el archivo .BIN que brilla, con el osito en el bolsillo, sobre azul oscuro con brillo violeta. El mismo arte en el ícono temático (monocromo), como dibujo de líneas, y en la pestaña Extraer. `art/icon.py` saca todos los íconos de `art/source/mrbin-art.jpg`. El de la notificación sigue siendo un dibujo de líneas hecho para 24 dp.
- Paleta nueva sacada del arte: fondos azul muy oscuro, brillo violeta alrededor de la acción principal, el verde neón del archivo .BIN para las acciones y el amarillo del logo para las etiquetas. Los botones principales y la barra de avance usan el amarillo → verde del logo. Todos los textos siguen en 4.5:1 o más.
- El README muestra el arte.

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

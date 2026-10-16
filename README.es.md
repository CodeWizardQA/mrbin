<p align="center"><img src="docs/icon.png" width="160" alt="Mr. Bin, el ícono de la app: un señor de caricatura despeinado y con una ceja levantada, saco de tweed y corbata roja delgada, que levanta un archivo .BIN verde brillante sobre un fondo azul oscuro y violeta"></p>

<h1 align="center">Mr. Bin</h1>

<p align="center"><i>Entra PKG, sale BIN.</i></p>

<p align="center"><a href="README.md">English</a> · <b>Español</b></p>

<p align="center"><img src="art/source/mrbin-art.jpg" width="720" alt="Mr. Bin echa una caja fPKG en una caneca llena de binario y levanta el .BIN que sale, con un osito de peluche en el bolsillo"></p>

App de Android que desempaca un **PKG de PS4 falso (fPKG)** a la carpeta del juego (`<ID del juego>/eboot.bin`, `sce_sys/`, `sce_module/` y el resto del juego) en el mismo celular, sin computadora. Funciona en cualquier celular o tablet con **Android 11 o más nuevo**, en español o en inglés.

Por qué el nombre: la app convierte un `.pkg` en la carpeta que arranca desde `eboot.bin`, y Mr. Bin es el señor quisquilloso del saco de tweed que lo hace por ti, con una ceja levantada.

**Estado:** 0.2.0 (qué cambió: [CHANGELOG](CHANGELOG.md)). El motor que extrae se probó con el fPKG de un juego real, partes comprimidas (PFSC) incluidas, en un POCO F9 Ultra con HyperOS. En otros celulares todavía no se ha probado: si la usas en otro, cuéntanos cómo te fue en un [issue](https://github.com/CodeWizardQA/mrbin/issues).

- Lee los `.pkg` de una carpeta y dice de cada uno qué es antes de tocar nada: fPKG, PKG de tienda, PKG de PS3, no es un PKG, o está incompleto o dañado.
- Extrae los fPKG que marques, uno tras otro, en segundo plano y con la pantalla apagada.
- Muestra el avance, la velocidad y el tiempo que falta en la app, en la notificación y, si lo permites, en una cápsula junto a la cámara frontal.
- Se puede pausar, reanudar o cancelar, también desde la notificación. Al cancelar nunca queda una carpeta a medias.
- Guarda un registro de lo que hizo, detrás del botón pequeño de arriba a la derecha, listo para copiar o compartir.
- Nunca modifica ni borra el `.pkg`: lo abre solo para leer.
- **Nunca inventa archivos:** todo lo que escribe sale del PKG. Si no puede abrir uno, dice por qué y no escribe nada.

## Instalar

1. Descarga `mrbin-X.Y.Z.apk` de la última versión en [Releases](https://github.com/CodeWizardQA/mrbin/releases). Ya viene compilado y firmado: no hay que compilar ni firmar nada.
2. Ábrelo en el celular. Android le pide permiso para «instalar apps desconocidas» al navegador o al administrador de archivos con que lo abras, como con cualquier APK que no viene de Google Play.
3. Las versiones nuevas se instalan encima de la anterior, sin desinstalar, porque todas van firmadas con la misma llave. Para enterarte cuando sale una, puedes agregar este repositorio en [Obtainium](https://github.com/ImranR98/Obtainium).

### Comprobar el APK

El certificado de firma oficial tiene este SHA-256 (se ve con `apksigner verify --print-certs` o con la app AppVerifier):

```
9325701afac386fd5a61ce7f506f2e44b5dd9c290e0a858fc3982e77c90334e8
```

## Uso

1. La primera pantalla muestra tres permisos. Solo el primero es obligatorio: da los otros si los quieres y toca **Continuar**. Cualquiera se puede cambiar después con el botón de la llave, arriba a la derecha.
   1. **Acceso a archivos** (obligatorio): «Acceso a todos los archivos», para leer el `.pkg` y escribir la carpeta del juego.
   2. **Sin límite de batería** (recomendado): evita que el sistema (HyperOS, One UI, ColorOS…) corte la extracción de un juego grande con la pantalla apagada. En Xiaomi / HyperOS el celular abre su propia pantalla de batería: elige **Sin restricciones**.
   3. **Mostrar sobre otras apps** (opcional): la cápsula con el porcentaje junto a la cámara frontal. Si el celular no tiene recorte de cámara, sale arriba al centro. Desde Android 11 el sistema abre la lista de todas las apps: busca Mr. Bin y actívalo.
2. En **Extraer**, elige la carpeta del `.pkg` (por defecto `Download`) y la carpeta de destino (por defecto `Download/MrBin`).
3. Marca los PKG que dicen **fPKG** y toca **Extraer** en la barra de abajo. Mientras extrae, la tarjeta del avance, arriba, tiene **Pausar** y **Cancelar**.
4. Al terminar, **Resultados** dice dónde quedó cada juego, cuántos archivos y bytes escribió, el tamaño y los primeros bytes de `eboot.bin`, y lo que no escribió.

Si algo falla, toma una captura del mensaje de **Resultados** y copia el registro (botón de arriba a la derecha → **Copiar**). El registro siempre va en inglés para que cualquiera lo entienda en un issue. También está en la carpeta privada de la app (`files/log.txt`).

### Qué queda en el destino

```
Download/MrBin/CUSA01623/
  eboot.bin           el ejecutable del juego (SELF de PS4), tal como viene en el PKG
  sce_module/         librerías del juego (.prx)
  sce_sys/            param.sfo, icon0.png, playgo-chunk.dat, … (las entradas del PKG con nombre)
  …                   el resto de carpetas y archivos del juego
```

- La carpeta se llama con el ID del juego (`CUSA01623`). Si el PKG no trae un ID válido, usa el Content ID.
- Primero escribe en `<ID>.partial` y la renombra solo cuando todo está completo. Si hay un error o se cancela, la borra.
- Antes de empezar revisa que haya espacio para todo el juego más 64 MB. Si ya existe una carpeta con ese nombre y no está vacía, se detiene y no la toca.

### Idioma

La app sigue el idioma del celular: español en celulares en español e inglés en todos los demás. Desde Android 13 también se puede elegir solo para esta app en **Ajustes → Aplicaciones → Mr. Bin → Idioma**. Los textos están en `app/src/main/res/values/strings.xml` (inglés) y `values-es/strings.xml` (español); un idioma nuevo es un `values-xx/strings.xml` más.

## Lo que no puede hacer

- **PKG de tienda (PlayStation Store).** Su llave de imagen depende de la licencia de la cuenta, que solo descifra la consola. La app lo marca como **TIENDA** y no escribe nada. Ninguna app de celular o de computadora puede abrirlo: hace falta un volcado hecho en una PS4 con homebrew que tenga esa licencia.
- **PKG de PS3.** Los reconoce (`\x7FPKG`) y los marca como no soportados.
- **Archivos de `sce_sys` cifrados con llaves de la consola** (por ejemplo `license.info`, llave 2). No se escriben, y Resultados los lista.

## Cómo funciona el motor

`app/src/main/java/io/github/codewizardqa/mrbin/engine/orbis/` es una traducción a Java de dos extractores conocidos:

- **shadPS4 `v.0.7.0`** (commit `3b2c01272383e1fcd0b82c7873e1ebf1a641aada`): `src/core/file_format/pkg.cpp`, `pkg.h`, `pfs.h`, `pkg_type.cpp`, `src/core/crypto/crypto.cpp` y `keys.h`.
- **LibOrbisPkg** (commit `643477263b2644e0803e0f58b8726ea4e3f3b7d4`): `PKG/Pkg.cs`, `PFS/PfsReader.cs`, `PFS/PFSCReader.cs`, `PFS/XtsDecryptReader.cs` y `PFS/PfsStructs.cs`.

Los pasos son estos:

1. Lee el encabezado y la tabla de entradas del PKG, y revisa que el archivo esté completo.
2. Descifra dk3 con el juego de llaves público `PkgDerivedKey3`.
3. Con dk3 saca la llave de imagen, y con ella la EKPFS usando el juego de llaves público de los fPKG.
4. Deriva las llaves XTS con la semilla del PFS.
5. Descifra el PFS externo y busca `pfs_image.dat`.
6. Lo descomprime (PFSC) y abre el PFS interno.
7. Escribe cada archivo de `uroot`.
8. Escribe en `sce_sys/` las entradas del PKG que tienen nombre, igual que shadPS4.

Un PKG de tienda falla en el paso 2 o en el 3: eso es lo que la app muestra como **TIENDA**.

| Archivo | Qué hace |
| --- | --- |
| `OrbisPkg.java` | Entrada pública: `inspect()` (no escribe nada) y `extract()` |
| `PkgFile.java` | Encabezado, tabla de entradas y llaves del PKG |
| `PfsImage.java` | Lector de PFS: encabezado, inodos y directorios |
| `Source.java` | Lecturas por rangos: archivo, porción, bloques, XTS y PFSC |
| `OrbisCrypto.java` | RSA, SHA-256, HMAC, AES-CBC y llaves XTS del PFS |
| `OrbisKeys.java` | Llaves públicas de fPKG (generado, ver abajo) |
| `PkgEntryNames.java` | Nombre de cada entrada del PKG, 611 en total (generado) |
| `Sfo.java` | Lee `param.sfo`: título, ID, categoría, versión |
| `Le.java` | Lectura little-endian |

El resto de la app:

| Archivo | Qué hace |
| --- | --- |
| `engine/PkgConverterEngine.java` | Busca los `.pkg`, los extrae en lote en un hilo aparte y lleva el registro |
| `engine/PkgModels.java` | Estado que muestran la app y la notificación |
| `engine/SettingsStore.java` | Carpetas de origen y destino, y si ya se pasó la pantalla del primer inicio |
| `service/ConversionService.java` | Servicio en primer plano: notificación con avance, wake lock y cápsula junto a la cámara |
| `service/ConversionActionReceiver.java` | Botones de la notificación (pausar/reanudar, cancelar) |
| `ui/MainActivity.java` | Pestañas Extraer y Resultados, la página del registro y la de los permisos |
| `ui/FolderPickerDialogs.java` | Selector de carpetas |
| `ui/Palette.java` | Todos los colores de la app |
| `ui/Texts.java` | Estados y motivos del motor en el idioma del celular |
| `ui/Icons.java` | Íconos de la interfaz, dibujados en código (la cara de Mr. Bin es `res/drawable-nodpi/ic_glyph.png`) |
| `App.java` | Crea el motor y el archivo de registro, y lee la versión del APK |

La app es Java sin AndroidX, Kotlin ni Gradle. Funciona desde Android 11 (API 30) y apunta a la API 34.

## Estructura

```
app/
  src/main/                  la app (AndroidManifest.xml, java/, res/)
  src/test/…/OrbisCli.java   ejecuta el motor en la computadora, sin Android
art/
  source/                    el arte de Mr. Bin y la máscara que lo recorta
  icon.py                    saca todos los íconos del arte (capas del ícono adaptable, monocromo, ícono de la app)
  lift.swift                 vuelve a hacer esa máscara con el recorte de sujetos de macOS
docs/icon.png                el ícono para este README
scripts/
  build-apk.sh               compila y firma el APK
  test-engine.sh             corre el motor en la computadora (keys / inspect / extract)
  compare-tree.sh            compara la extracción con la de otra herramienta, por SHA-256
tools/
  gen_names.py               genera PkgEntryNames.java a partir de pkg_type.cpp de shadPS4
  emit_keys.py               genera OrbisKeys.java a partir de keys.h de shadPS4 (usa keys2java.py)
CHANGELOG.md                 cambios por versión
LICENSE                      GPL-3.0
```

## Compilar

Requisitos en una Mac, de Homebrew: `android-commandlinetools` con `build-tools;34.0.0` y `platforms;android-34` (en `/opt/homebrew/share/android-commandlinetools`, o indica otra ruta en `ANDROID_SDK_DIR`), `openjdk` y `python3`.

```bash
scripts/build-apk.sh
```

Funciona desde cualquier carpeta y deja `mrbin-X.Y.Z.apk` en `app/build/outputs/apk/release/` y una copia en la raíz del repositorio (git ignora los `.apk`). Los pasos son `aapt2` → `javac` → `d8` → `zipalign` → `apksigner`.

**Versión nueva:** se cambia solo en `scripts/build-apk.sh` (`VERSION_CODE` y `VERSION_NAME`). La app la lee del APK para el encabezado y el registro. `VERSION_CODE` siempre tiene que subir: Android no instala una versión menor encima de una mayor.

**Ícono:** todos los íconos salen del arte `art/source/mrbin-art.jpg`. El encuadre se cambia en `art/icon.py` y se regenera con `python3 art/icon.py` (necesita Pillow): escribe las capas `res/mipmap-*dpi/ic_launcher_*.png`, `res/drawable-nodpi/ic_glyph.png` (la cara de Mr. Bin en la pestaña Extraer), `docs/icon.png` y una vista previa en `art/out/`. Esos archivos no se editan a mano. Si cambia el arte, la máscara se vuelve a hacer con `swift art/lift.swift art/source/mrbin-art.jpg art/source/mrbin-mask.png` (macOS 14 o más nuevo). El ícono de la notificación, `res/drawable/ic_stat_app.xml`, es un dibujo de líneas que se edita a mano, porque a 24 dp el arte se vuelve una mancha.

### Firma

Las versiones oficiales van firmadas con la llave de Mr. Bin (alias `mrbin`). La llave vive fuera del repositorio y su contraseña en el Llavero de macOS; el script se la pasa a `apksigner` por la entrada estándar, nunca en la línea de comandos, y al final revisa que el certificado sea el oficial. Si falta la llave, se detiene en vez de crear otra, porque Android no actualiza una app firmada con otra llave.

**Si compilas tu propia versión**, fírmala con tu propia llave:

```bash
keytool -genkeypair -keystore ~/mi-llave.p12 -storetype PKCS12 -alias mialias -keyalg RSA -keysize 4096 -validity 10000
security add-generic-password -s mi-mrbin -a mialias -w          # pide la contraseña de la llave
RELEASE_KEYSTORE=~/mi-llave.p12 RELEASE_KEY_ALIAS=mialias RELEASE_KEYCHAIN_SERVICE=mi-mrbin scripts/build-apk.sh
```

Tu APK no se instala encima del oficial (ni al revés), porque las firmas son distintas.

## Probar el motor en la computadora

```bash
scripts/test-engine.sh keys                         # revisa que los juegos de llaves RSA cuadren
scripts/test-engine.sh inspect juego.pkg            # lo que la app diría de ese PKG
scripts/test-engine.sh extract juego.pkg /tmp/out   # extrae igual que la app, en /tmp/out/<ID>/
scripts/compare-tree.sh <referencia>/uroot /tmp/out/<ID>
```

Para tener una referencia contra la cual comparar, se extrae el mismo PKG con `PkgTool pkg_extract` de LibOrbisPkg (.NET). Con el fPKG de Apollo Save Tool (`IV0000-APOL00004_00-APOLLO0000000PS4`) los 100 archivos salieron idénticos por SHA-256 a los de `PkgTool pkg_extract`, y las entradas de `sce_sys` idénticas a las de `pkg_extractentry`. Apollo no trae partes comprimidas (PFSC); esa parte se probó en el celular con el fPKG de un juego real.

## Regenerar las tablas

`PkgEntryNames.java` y `OrbisKeys.java` no se editan a mano. Se generan a partir de un clon de shadPS4 en el tag `v.0.7.0`:

```bash
python3 -I tools/gen_names.py <shadPS4>/src/core/file_format/pkg_type.cpp app/src/main/java/io/github/codewizardqa/mrbin/engine/orbis/PkgEntryNames.java
python3 -I tools/emit_keys.py <shadPS4>/src/core/crypto/keys.h > app/src/main/java/io/github/codewizardqa/mrbin/engine/orbis/OrbisKeys.java
```

Con `v.0.7.0`, los dos salen idénticos a los del repositorio.

## Lo que sigue

- Soporte para PKG de PS3.
- Pruebas en más celulares.
- Más idiomas (primero portugués).

## Licencia y créditos

Mr. Bin es software libre bajo la **GNU GPL versión 3 o posterior** (`GPL-3.0-or-later`); el texto completo está en [LICENSE](LICENSE). Puedes usarla, copiarla, cambiarla y repartirla, siempre que lo que repartas también salga bajo la GPL y con su código fuente.

El motor (`engine/orbis/`) es una traducción de código de:

- [**shadPS4**](https://github.com/shadps4-emu/shadPS4), Copyright 2024 shadPS4 Emulator Project, bajo **GPL-2.0-or-later**.
- [**LibOrbisPkg**](https://github.com/maxton/LibOrbisPkg), de Maxton, bajo **LGPL-3.0**.

Las dos licencias permiten distribuir el resultado bajo GPL-3.0. Cada archivo del motor lleva el aviso correspondiente.

Las llaves de `OrbisKeys.java` son las mismas llaves públicas de fPKG que traen shadPS4 y LibOrbisPkg. Solo abren fPKG.

**Aviso:** Mr. Bin es un proyecto independiente, sin relación con Sony Interactive Entertainment. «PlayStation» y «PS4» son marcas de Sony. Mr. Bin no rompe ninguna protección de los PKG de tienda: es para fPKG, homebrew y copias de tus propios juegos.

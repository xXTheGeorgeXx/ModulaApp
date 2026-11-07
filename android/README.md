# Modula — APK de prueba

> Nota v2: este README es el de la app de prueba v1, copiado a `android/`
> en la reorganización v2. Los números de la v1 siguen siendo válidos;
> la app todavía no se reconstruyó desde este árbol v2 (ver
> `../DESIGN_NOTES.md`).

App de prueba para el motor Modula (llama.cpp + JNI) en el Samsung Galaxy A52.
**No es el agente completo todavía**: es la pieza que faltaba para probar que
el binario carga y chatea offline en el teléfono.

## Qué hace la app

- Carga `libmodula_engine.so` al arrancar y muestra si cargó bien o con qué error.
- Deja elegir un modelo `.gguf` con el selector de archivos de Android
  (sin rutas fijas de Termux). Lo copia a la memoria interna porque
  llama.cpp necesita una ruta de archivo real.
- Inicializa el modelo con contexto 8192, 2 hilos y temperatura 0.7
  (los valores de la prueba en Termux; se pueden cambiar en pantalla).
- Chat con streaming (los tokens van apareciendo) y estadísticas al final:
  tokens, TTFT, tokens/seg y tokens de contexto.
- Botón "Liberar memoria" (`trimMemory`) y liberación al cerrar la app.

Formato de prompt: Gemma 4 oficial
`<|turn>user\n{pregunta}<|end_of_turn>\n<|turn>model\n`.
El wrapper ya añade BOS al tokenizar, por eso no se escribe `<bos>` a mano.
(El script de Termux mezclaba el formato Gemma 3 con el canal de
pensamiento de Gemma 4; acá se usa el formato Gemma 4 limpio.)

## El APK

- Archivo: `build/manual/app-debug.apk` — **8.5 MB**, versión 1.0 (debug)
- Paquete: `com.modulaappr1`, `minSdk 29`, `targetSdk 34`, solo `arm64-v8a`
- Contiene: `libmodula_engine.so` stripped de 6.7 MB + `libomp.so` de 1.2 MB
  (ambos sin comprimir dentro del APK, como exige Android), `classes.dex`
- Firmado con keystore debug (`debug.keystore`, clave `android`).
  Es un build de prueba, no de Play Store.
- Verificado: `apksigner verify` OK (esquema v3), `aapt dump badging`
  muestra el paquete, la actividad lanzable `com.modulaappr1.MainActivity`
  y `native-code: arm64-v8a`.

## Cómo instalarlo en el A52

1. Copiá `app-debug.apk` al teléfono (por WhatsApp a vos mismo, por cable
   USB, o subilo a Drive y bajalo en el teléfono).
2. En el teléfono, abrí el APK con el administrador de archivos.
   Si te pide permiso, activá **"Permitir instalar apps desconocidas"**
   para esa app (es normal: no viene de Play Store).
3. Instalá. No necesita internet para funcionar, solo para bajar el modelo
   la primera vez (eso es aparte, ver abajo).

## Dónde va el modelo

1. Descargá `Gemma-4-E2B-it-IQ4_NL.gguf` (~3.38 GB) en el teléfono,
   por ejemplo a la carpeta **Descargas**. Necesitás internet solo para esto.
2. Abrí la app Modula → **"Elegir modelo .gguf"** → buscá el archivo en
   Descargas. La app lo copia a su memoria interna (tarda un rato, es
   un archivo grande) y te avisa cuando está listo.

## Qué probar primero

1. Al abrir: tiene que decir **"✅ libmodula_engine.so cargada"**.
   Si dice ❌, anotá el error.
2. Elegí el `.gguf` y tocá **"Inicializar modelo"**.
   La primera vez tarda (está mapeando el archivo de 3.38 GB).
   Tiene que decir "✅ Modelo inicializado".
3. Escribí **"Hola, ¿quién eres?"** y tocá **"Enviar"**.
   La respuesta tiene que ir apareciendo por partes (streaming).
   Al final muestra tokens, TTFT y tokens/seg.
   En el A52 con 2 hilos, esperá algo como 4.5–6 tok/s, igual que en Termux.
4. Probá una segunda pregunta sin reiniciar: el motor guarda el contexto
   entre turnos. Si la respuesta se pone rara o lenta, tocá
   **"Liberar memoria"** y preguntá de nuevo.

## Límites conocidos

- **No probado en un teléfono acá**: en esta máquina Linux no hay
  emulador Android, así que la prueba en el A52 es el paso que falta.
- RAM: el A52 tiene 6 GB. Con contexto 8192 y el modelo de 3.38 GB va
  justo; si se cierra la app, bajá el contexto a 4096 y probá de nuevo.
- Usá **2 hilos**: el Snapdragon 720G tiene solo 2 núcleos Gold; con 3
  hilos ya entra 1 núcleo Silver y no rinde más.
- El modelo es grande: la copia a memoria interna necesita ~3.4 GB libres
  además del archivo original en Descargas.

## Cómo se compiló

Hay dos formas de compilar este mismo código:

- **GitHub Actions (recomendado para Jorge, sin PC):**
  subí el proyecto a GitHub y el workflow `../.github/workflows/build-apk.yml`
  compila con Gradle y deja el APK como artefacto descargable.
  En GitHub Gradle funciona normal.
- **Build manual (el que generó este APK):**
  `scripts/build-apk-manual.sh` usa kotlinc + d8 + aapt2 + zipalign +
  apksigner, sin Gradle. Existe porque en esta VM el sandbox bloquea los
  sockets TCP de Java y el daemon de Gradle se comunica por TCP en
  loopback, así que `./gradlew` no puede correr acá (el error era
  "Could not receive a message from the daemon"). Si en el futuro se
  habilita el permiso de red TCP directa en los ajustes de Muse, el
  build con Gradle debería funcionar también en local. El script inyecta
  el atributo `package` en una copia temporal del manifest (aapt2 lo
  exige); el manifest del repo queda sin `package`, como pide AGP 8.5.
- El proyecto no tiene dependencias externas (sin Compose ni AndroidX),
  a propósito: solo `android.jar` + `kotlin-stdlib`, para que el build
  manual sea posible.

## Archivos del proyecto

- `app/src/main/java/com/modulaappr1/ModulaEngine.kt` — puente JNI
  (firmas exactas del wrapper C++).
- `app/src/main/java/com/modulaappr1/MainActivity.kt` — UI y lógica.
- `app/src/main/jniLibs/arm64-v8a/` — `libmodula_engine.so` (stripped)
  y `libomp.so` (obligatorio en tiempo de ejecución).
- `app/src/main/AndroidManifest.xml` — paquete `com.modulaappr1`.
- `../.github/workflows/build-apk.yml` — build en GitHub Actions.
- `scripts/build-apk-manual.sh` — build local sin Gradle.
- `BUILD` del motor nativo: ver `../factory/README.md`.

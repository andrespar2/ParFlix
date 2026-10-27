# ParFlix

ParFlix es una aplicación Android para disfrutar de películas, series y anime, con un
amplio catálogo actualizado automáticamente, búsqueda inteligente, información
detallada, reproducción integrada, favoritos e historial de visualización.

> **Importante:** ParFlix no aloja contenido propio. La aplicación consume exclusivamente
> los servicios externos configurados por la persona usuaria (un proveedor de catálogo + metadatos
> de TMDB) y reproduce el contenido a través del reproductor web oficial de dichos servicios.

---

## Características

- **Inicio** con destacados por tipo, sección «Continuar viendo» y estado de sincronización.
- **Catálogos** de Películas, Series y Anime con paginación y ordenación (recientes, A–Z, año).
- **Buscador local inteligente**: tolerante a acentos, mayúsculas, errores tipográficos y
  títulos parciales; coincide también con títulos originales, sinónimos, IMDb y TMDB.
- **Ficha de detalle**: sinopsis, año, duración, valoración, géneros, créditos e imágenes,
  con enriquecimiento progresivo desde TMDB y selección de temporadas/episodios en series
  y anime.
- **Reproductor integrado** (WebView) con pantalla completa, orientación horizontal,
  control de retroceso, indicador de carga, reintento y apertura en ventana externa.
- **Mi lista** (favoritos), **Continuar viendo** e **historial** almacenados solo en el
  dispositivo.
- **Ajustes**: sincronización, caché (tamaño y borrado), fuentes de metadatos, idioma,
  comportamiento del reproductor y gestión de credenciales importadas.
- **Diseño AMOLED** negro moderno con acento rojo, oscuro por defecto.
- **Sin registro ni cuenta**: todo el estado personal es local.

## Arquitectura autónoma

ParFlix funciona **sin backend propio**. No usa servidores intermedios, bases de datos
centrales, servicios de autenticación propios ni sincronización entre dispositivos:

```
┌────────────┐   X-API-Key    ┌──────────────────┐
│  ParFlix   │ ─────────────▶ │ proveedor de     │  Catálogo y URLs de reproducción
│  (Android) │                │ catálogo         │
│            │   Bearer       ┌──────────────────┐
│  Room +    │ ─────────────▶ │ api.themoviedb   │  Metadatos, imágenes y créditos
│  DataStore │                └──────────────────┘
└────────────┘
       │
       ▼  SQLite/FTS4 en el dispositivo
   Catálogo, favoritos, historial y preferencias (nunca salen del teléfono)
```

- La base de datos local (Room + FTS4) es la fuente de verdad de la interfaz: el catálogo
  se muestra al instante y se enriquece con el tiempo.
- La sincronización incremental usa el parámetro `synced_at` del catálogo, con checkpoints
  por página, huellas SHA-1 por página y reconciliación completa periódica.
- La sincronización se lanza al abrir la app, al pasar a primer plano y mediante WorkManager
  (periodicidad aproximada, nunca interrumpe la reproducción ni borra datos válidos ante
  fallos transitorios).
- TMDB se consulta con estrategia de idioma progresiva (es-CO → es → en) y se cachea en
  local para no repetir llamadas.

## Tecnologías

| Área | Tecnología |
| --- | --- |
| Lenguaje | Kotlin 2.0 |
| UI | Jetpack Compose + Material 3 |
| Patrón | MVVM con arquitectura por capas (core / data / ui) |
| Asíncrono | Coroutines + StateFlow |
| Red | Retrofit 2 + OkHttp 4 |
| JSON | Moshi (codegen) |
| Persistencia | Room 2.6 + FTS4 + DataStore |
| Imágenes | Coil |
| Tareas en segundo plano | WorkManager |
| Navegación | Navigation Compose |
| Reproductor | WebView + WebChromeClient |
| Seguridad | Android Keystore + AES-GCM, ofuscación R8 |
| Build | Gradle Kotlin DSL, AGP 8.7, JDK 17 |

## Requisitos y limitaciones

- **Conexión a Internet obligatoria**: sin conexión se muestran los datos cacheados
  (catálogo, favoritos, historial), pero no catálogo nuevo ni reproducción.
- Las claves de API del proveedor de catálogo y de TMDB se proporcionan **a nivel de dispositivo**
  mediante un archivo de configuración privado (`secrets.properties`); nunca se incluyen en el repositorio.
- La reproducción depende de la disponibilidad y compatibilidad del reproductor web del
  proveedor: si un título no se reproduce, la app lo indica y ofrece reintento. ParFlix no
  evita DRM ni restricciones del proveedor.
- La reproducción no se ha verificado automáticamente en CI; se realiza en un dispositivo
  físico/emulador de forma manual.

## Protección de credenciales (límites conocidos)

- Las credenciales de compilación se guardan fuera del repositorio y se inyectan al APK
  mediante un generador de fuentes con **ofuscación de cadena** (rotación XOR + Base64) y
  ofuscación R8 en release.
- Esto es un **disuasivo, no confidencialidad**: cualquier persona con el APK y conocimientos
  puede extraer las claves embebidas. Quien distribuya el APK acepta la responsabilidad de
  que sus claves se compartan con quien lo instale.
- Las credenciales que la persona usuaria importa dentro de la app (si se usan) se guardan
  con **Android Keystore + AES-GCM**, nunca en claro, y se pueden revocar desde Ajustes.
- Las URLs de reproducción con `view_key` se almacenan en Room para no perder la sesión,
  pero **nunca se registran en logs** ni se exponen en capturas.
- El mecanismo de rotación: sustituir el valor en `secrets.properties` y reconstruir; el
  escáner de secretos (`scripts/check-secrets.ps1`) bloquea accidentalmente subir claves al repo.

## Configuración privada de credenciales

1. Copia la plantilla y rellena tus claves (archivo **no rastreado** por git):

   ```powershell
   Copy-Item secrets.defaults.properties secrets.properties
   notepad secrets.properties
   ```

   | Clave | Descripción |
   | --- | --- |
   | `PROVIDER_API_KEY` | Clave del catálogo (`X-API-Key`). |
   | `PROVIDER_BASE_URL` | URL base del proveedor de catálogo. |
   | `PROVIDER_VIEW_KEY` | Clave de reproducción (`view_key`). |
   | `TMDB_ACCESS_TOKEN` | Token de lectura de TMDB (`Authorization: Bearer`). |

2. (Opcional) En release se puede definir el keystore de firma:

   | Clave | Descripción |
   | --- | --- |
   | `RELEASE_STORE_FILE` | Ruta absoluta al `.jks`. |
   | `RELEASE_STORE_PASSWORD` | Contraseña del almacén. |
   | `RELEASE_KEY_ALIAS` | Alias de la clave. |
   | `RELEASE_KEY_PASSWORD` | Contraseña de la clave. |

## Compilación e instalación

Requisitos: JDK 17, Android SDK (platform 35, build-tools 35).

```powershell
# APK de depuración
.\gradlew.bat :app:assembleDebug

# APK de producción (firma desde secrets.properties; si no existe usa debug)
.\gradlew.bat :app:assembleRelease

# Pruebas unitarias
.\gradlew.bat :app:testDebugUnitTest

# Instalar en un dispositivo conectado
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

Cada APK se publica como Release en GitHub con su APK adjunto.

## Proveedores y atribución

- Catálogo y reproducción: un proveedor externo configurado por la persona usuaria
  (integración de terceros sujeta a sus propios términos).
- Metadatos, imágenes y créditos: [TMDB](https://www.themoviedb.org/). Esta aplicación
  utiliza la API de TMDB pero no está respaldada ni certificada por TMDB.

## Publicidad

- El sitio web oficial integra unidades de Adsterra en el dominio registrado
  `andrespar2.github.io`, con etiquetas de publicidad visibles y sin alterar el
  funcionamiento interno de las unidades.
- La aplicación Android carga dos páginas dedicadas del sitio aprobado:
  `ad-banner.html` para el banner 320×50 y `ad-native.html?app=1` para una
  tarjeta nativa compacta. No se inyecta la etiqueta en un documento local ni se
  falsifica el origen, el User-Agent o las métricas.
- El banner se oculta en WebView anteriores a la versión principal 100 y cuando
  no hay creatividad visible. El anuncio nativo ocupa una celda del catálogo y
  se retira si el proveedor no devuelve contenido. La app sigue funcionando si
  la publicidad falla.
- El propietario de ParFlix autorizó el uso de las unidades web en WebView bajo
  su responsabilidad. Adsterra no ha emitido una aprobación escrita específica
  para este uso; sus términos limitan las etiquetas al sitio aprobado y no
  documentan un SDK oficial de banners/nativos para Android.
- El sistema de publicidad se recupera de fallos temporales sin reiniciar y
  respeta la conectividad, el primer plano y los límites de frecuencia. No se
  usan anuncios simulados ni tráfico artificial.

## Créditos

- **ParFlix** — aplicación desarrollada por **andrespar2**.
- Repositorio: <https://github.com/andrespar2/ParFlix>
- Datos de películas/series: TMDB. Reproducción: servicios de terceros configurados.

## Licencia

Uso personal. Consulta los términos de cada proveedor antes de distribuir la aplicación.

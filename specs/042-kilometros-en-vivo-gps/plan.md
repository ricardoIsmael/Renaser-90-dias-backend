# Implementation Plan (alto nivel): Kilómetros en vivo con GPS

**Branch**: `042-kilometros-en-vivo-gps` (backend y app) | **Date**: 2026-11-05 | **Spec**: [spec.md](./spec.md)

> Plan de alto nivel. Antes de implementar se cierran los supuestos de la spec y se hace la investigación técnica de la app (sección
> «Investigación pendiente»), que puede cambiar las librerías nativas.
>
> **Corregido 2026-11-05 (backend implementado, D-323).** La migración es **V170** (V166–V168 son de la rama de la 041 y V169 de la 040).
> La tabla suma `motivo_no_cuenta`, `km_del_dia` y `origen_del_dia`, la distancia admite hasta 100 km (entre 60 y 100 se guarda y no
> cuenta), y la ruta ajena se lee por las puertas de las fichas (contrato §3), con constancia en `consultas_de_ficha`
> (`RUTA_DE_CARRERA`). El servicio que aplica al hábito se llama `CarreraEnElHabito`; subir el número de un registro cumplido es
> `RegistroHabito.subirMedicion` (no hizo falta un caso de uso aparte).

## Summary

La app graba la carrera solo en el teléfono (GPS filtrado + podómetro para el avatar) y, al terminar, manda **un** `POST /api/v1/carreras`
con una `carreraId` generada al empezar. El servidor la guarda con índice único `(participante, carrera_id)` y huella del cuerpo, y en la
misma transacción aplica los km al hábito KILÓMETROS DIARIOS con la regla «el mayor entre carreras y Health Connect», cerrando por
`RegistroService.completar` como la 037. Sin red, cola local con espera creciente.

## Technical Context

**Backend:** Java 25 / Spring Boot, módulo `habits` (hexagonal). Sin dependencias nuevas. PostgreSQL: una tabla nueva y un CHECK ampliado.
**App:** Expo SDK 57 / React Native. Dependencias nativas nuevas a revisar antes de instalar (autor, scripts, red, como en la 037):
`react-native-maps` (proveedor Google), `expo-location` + `expo-task-manager` (servicio en primer plano), `expo-sensors` (podómetro y
brújula), `expo-keep-awake` (pantalla encendida), almacenamiento local persistente (`expo-sqlite` o archivo con escritura atómica).
Ya están: `expo-speech` (voz), `rive-react-native` (avatar), `expo-haptics`.
**Testing:** backend JUnit + Mockito, `@WebMvcTest`, IT con Testcontainers (idempotencia concurrente con dos hilos, día cerrado, regla del
mayor), `ArchitectureTest`, prueba con el reloj en la madrugada UTC. App: Jest de la lógica pura (filtro GPS, fusión, cola, tramos,
calorías) y Maestro escrito (el GPS se simula con `mobile_set_location` o rutas del emulador).

## Constitution Check (reglas del repo)

| Regla | Cómo se cumple |
|---|---|
| Hexagonal (01) | `habits/application/ports/in/carrera/RegistrarCarreraUseCase`; servicio `CarreraService` que orquesta: guardar (puerto `out` `CarreraPort`, adaptador JPA/JDBC) y aplicar al hábito por `CompletarRegistroUseCase` o un caso nuevo `ActualizarMedicionDelDiaUseCase` (subir el valor de un registro cumplido, sin puntos). Validación pura en `PoliticaKilometros.validarCarrera`. Controller `CarreraController` llama un solo caso de uso. La lectura de la ruta para el acompañamiento pasa por `habits.api` y el acceso por los puertos que ya usa `leadership`/`mentoring`. |
| Tiempo y zonas (02) | El día es `iniciadaEn` en la zona del participante; D-259 lo impone `RegistroService`; no se reasigna un día cerrado. Pruebas con reloj 00:00–05:00 UTC. |
| Pruebas (03) | Unitarias de la política (topes, velocidad, por revisar), del servicio (los 7 resultados de `habito.resultado`), IT del endpoint (201/200 repetida/409 huella/403/400/503) y concurrencia real. |
| Migraciones (04) | Una migración (V170) con cabecera que justifica la tabla; CHECKs evaluables con la fila. |
| Documentación (05) | Decisión D-NNN en `docs/MODULOS_A_AVANZAR.md` §8 al implementar; `CLAUDE.MD` apunta a la spec. |
| Trazabilidad (06) | Cada FR/SC con prueba marcada `spec 042 FR-NNN`; la spec entra a `specs/trazabilidad.json` en el mismo cambio que pone las marcas, no antes. |
| Seguridad (0.3) | `@RequiresPermission(USE_APP)` y `@Funcionalidad("habitos.km.carreras")`; prueba 403 de suspendido y de rol ajeno en la ruta. |

## Modelo de datos (resumen)

**`carreras`** (nueva). Por qué tabla: varias carreras por día, cada una con su clave de idempotencia, su huella, su ruta y su resultado; no
caben en la fila única `(participante, hábito, fecha)` de `registros_habito`.

| Columna | Tipo | Notas |
|---|---|---|
| `id` | `uuid` PK | del servidor |
| `participante_id` | `uuid NOT NULL` FK | |
| `carrera_id` | `uuid NOT NULL` | la del teléfono; **`UNIQUE (participante_id, carrera_id)`** |
| `huella` | `char(64) NOT NULL` | SHA-256 del cuerpo canónico |
| `fecha` | `date NOT NULL` | día de la persona (FR-021); índice `(participante_id, fecha)` |
| `iniciada_en`, `terminada_en` | `timestamptz NOT NULL` | CHECK `terminada_en > iniciada_en` |
| `duracion_activa_seg` | `integer NOT NULL` | CHECK 60..43200 |
| `distancia_m` | `numeric(8,1) NOT NULL` | CHECK 100..60000 |
| `subida_m`, `pasos`, `calorias_aprox`, `metros_sin_gps` | `integer NULL` | topes por CHECK |
| `tramos` | `jsonb NOT NULL` | arreglo de segundos por km (≤ 61); se guarda porque sin ruta no se puede recalcular |
| `ruta_polyline` | `text NULL` | ≤ 100 KB; se borra a los 180 días (FR-042) |
| `ruta_puntos` | `integer NULL` | junto con la polyline |
| `cuenta_para_el_habito` | `boolean NOT NULL` | |
| `por_revisar` | `boolean NOT NULL DEFAULT false` | `motivo_revision text NULL` |
| `registro_habito_id` | `uuid NULL` FK | el registro al que se aplicó, si se aplicó |
| `resultado_habito` | `text NOT NULL` | CHECK con los 7 valores del contrato; la respuesta repetida lo devuelve igual |
| `puntos_otorgados` | `integer NOT NULL DEFAULT 0` | lo que pagó ESTA carrera (solo `CERRADO` > 0) |
| `app_version` | `varchar(20) NULL` | |
| `creada_en` | `timestamptz NOT NULL DEFAULT now()` | |

**`registros_habito`**: el CHECK de `origen_medicion` pasa a `('MANUAL','HEALTH_CONNECT','GPS')`. Con `GPS`, las columnas de lectura de
Health Connect (`fuente_medicion`, `dispositivo_medicion`, `medicion_leida_en`) van en `NULL` (se ajustan sus CHECKs de la V161 para que
sean obligatorias solo con `HEALTH_CONNECT`); el detalle vive en `carreras`.

**Limpieza:** un barrido diario pone `ruta_polyline = NULL` en carreras de más de 180 días (scheduler con la regla 02).

## Lógica del servidor (orden en una transacción)

1. Validar cuerpo y topes (400). Calcular huella.
2. `INSERT … ON CONFLICT (participante_id, carrera_id) DO NOTHING`. Si no insertó: leer la guardada; misma huella → 200 con lo guardado;
   otra → 409.
3. `PoliticaKilometros.validarCarrera` → `cuentaParaElHabito` y `porRevisar` (FR-024, FR-025).
4. Si cuenta: leer con cerrojo el registro de km de `fecha` (si existe). PENDIENTE y día abierto → `completar` con origen `GPS` y
   km = Σ carreras válidas del día (FR-023). COMPLETADO GPS/HC → `valor = max(Σ carreras, km de HC)` si sube. MANUAL / día cerrado / sin
   registro → no se toca.
5. Guardar `resultado_habito`, `registro_habito_id` y `puntos_otorgados` en la carrera. Responder.

**Cambio en la 037 (acotado):** `MedicionAutomaticaService.yaCumplido` hoy devuelve 409 si el registro se cerró con otra cosa que Health
Connect. Con un registro cerrado por `GPS`, la lectura de Health Connect aplica la regla del mayor (sube el valor, 200, sin puntos). Un
registro cerrado por Health Connect sigue como en la 037 (no cambia el número). **Anotado:** esa diferencia (HC sobre HC no sube) se deja
como está para no ampliar el alcance; si el dueño quiere que también suba, es un cambio chico.

## La app (resumen)

```text
src/features/carrera/
  utils/        filtroGps (precisión, saltos, suavizado, haversine), fusionDePasos (zancada, rumbo, reconciliación),
                tramos, calorias, cuerpoDeLaCarrera (canónico), polyline (codificar, Douglas-Peucker, recorte de privacidad)
  servicios/    servicioEnPrimerPlano (expo-location + task-manager), podometro, almacenCarrera (persistente), colaDeEnvio
  api/          carrerasApi (POST /carreras, GET ruta)
  hooks/        useCarreraEnCurso, useColaDeCarreras
  components/   CuentaRegresiva, PantallaCorriendo (Mapa/Estadísticas/Tramos/Voz), BarraDeslizarParaPausar, PantallaPausa,
                PantallaTerminaste, AvatarCaminante (Rive: hombre/mujer/neutral, estados caminar/quieto)
```
- **Mapa:** `MapView provider={PROVIDER_GOOGLE}` con `camera.pitch ≈ 60`, `showsBuildings`, `heading` del rumbo suavizado; `Polyline`
  que crece. **Riesgo:** en Android un `Marker` con vista personalizada se dibuja como imagen fija; animarlo exige `tracksViewChanges`
  (caro). Alternativa a medir: la cámara sigue a la persona y el avatar Rive va **encima del mapa, fijo al centro**, y el mapa se desplaza
  bajo él (entre puntos se anima la cámara, no el marcador).
- **Fusión:** el podómetro emite pasos → `fusionDePasos` adelanta la posición mostrada una zancada en el rumbo y dispara el ciclo de
  caminata; el siguiente punto GPS aceptado corrige con una animación ≤ 600 ms. La distancia oficial solo sale de `filtroGps` (y del relleno
  de FR-035).
- **Cola:** una entrada por `carreraId` con el cuerpo ya armado (la huella no cambia entre reintentos), intentos y próximo reintento; se
  procesa al terminar, al volver a primer plano y al recuperar la red. Se borra solo con 2xx o 409.

## Investigación pendiente (antes de implementar la app)

1. Si `expo-location` (`startLocationUpdatesAsync` con `foregroundService`) exige `ACCESS_BACKGROUND_LOCATION` en Android 14. Un
   servicio en primer plano iniciado con la app visible podría no necesitarlo; si no hace falta, no se pide (revisión de Play más simple).
2. Si la notificación del servicio de `expo-location` admite las acciones «Pausar» y «Abrir»; si no, una notificación propia
   (`expo-notifications` con categoría) o un módulo nativo mínimo.
3. Precisión y latencia del sensor de pasos (`TYPE_STEP_DETECTOR`) frente a `expo-sensors` Pedometer (que en Android agrupa pasos) para
   cumplir los 500 ms de FR-031; puede requerir un módulo nativo pequeño.
4. Costo de batería con GPS a 1 s durante 1 h en un teléfono de gama media.

## Build y publicación

- APK nuevo (nativo). Clave de Maps en `app.json` (`android.config.googleMaps.apiKey`), restringida por paquete y SHA-1; no se versiona
  en claro si el repo es público (variable de EAS).
- Permisos Android: `ACCESS_FINE_LOCATION`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION`, `ACTIVITY_RECOGNITION` (opcional),
  `POST_NOTIFICATIONS`, `WAKE_LOCK`; `ACCESS_BACKGROUND_LOCATION` solo si la investigación 1 lo exige.
- Play Console: declaración de servicio en primer plano (ubicación, con video), ubicación en segundo plano si aplica, Data safety.
- Orden: backend publicado → APK.

# Investigación y decisiones técnicas — spec 037

Fuente: investigación del 2026-11-02 (`investigacion-pasos-km.md`, fuera del repo). Niveles: **[V]** verificado en la fuente oficial o en el código, **[S]** fuente secundaria, **[I]** inferencia (hay que probarla en un teléfono).

## Decisión 1 — Solo Health Connect (cambio del dueño, 2026-11-02)

- En **Android 14 o más** el propio sistema lee `TYPE_STEP_COUNTER` y escribe `StepsRecord` y la distancia estimada en Health Connect en cuanto **alguna app** tiene `READ_STEPS`; escribe en lotes de como mucho un minuto. Sin servicio nuestro, sin batería extra, con la app cerrada. **[V]** `developer.android.com/health-and-fitness/health-connect/features/native-tracking` y `.../features/steps`.
- Los relojes escriben en el mismo lugar: Samsung Health (6.22.5+) **[V]**, Fitbit/Google Health **[V parcial]**, Garmin Connect (solo envía) **[S]**, Xiaomi Mi Fitness / Zepp **[S]**. **No se promete «cualquier dispositivo»: se promete «lo que escriba en Health Connect».**
- **Descartado por el dueño:** el módulo nativo propio con la Recording API (`play-services-fitness`) para Android 10 a 13, y la instalación de Health Connect desde Play. Quien no tiene Android 14 usa la captura.
- **Descartado por la investigación:** `expo-sensors` Pedometer (en Android solo cuenta con la app en primer plano, desde que te suscribes y sin historial), un servicio propio con el sensor (notificación fija, OEMs que lo matan, revisión de Play), Google Fit (APIs obsoletas en 2026) y la Fitbit Web API (se apaga hoy según fuentes secundarias). **[V]/[S]**

## Decisión 2 — Una fuente por día, sin doble conteo

`aggregateRecord` de Health Connect **elimina duplicados** de pasos y distancia y se queda con la app de mayor prioridad según la lista que fija la **persona** en Health Connect **[V]** (`.../aggregate-data`). No se suma por origen. Lo que el agregado **no** hace es filtrar por método de registro: ni `AggregateRequest` ni `ReadRecordsRequest` tienen ese filtro **[V]**, y un agregado simple incluiría lo escrito a mano **[I]**. Receta que se implementa:

1. Leer los registros de pasos y distancia **del día** (paginando) con su `metadata.dataOrigin` y `metadata.recordingMethod`.
2. Un origen es **limpio** si ninguno de sus registros es `MANUAL_ENTRY` (3) ni método desconocido (0).
3. Pedir el **agregado** de pasos y de distancia con `dataOriginFilter` = orígenes limpios. Ese es el número.
4. Si todos los orígenes están sucios o no hay: «Todavía no vemos pasos hoy» (o, si había manuales, «Los pasos escritos a mano no cuentan»).
5. La fuente que se muestra sale de los `dataOrigins` del agregado y del `device` de sus registros: teléfono (`android` o `com.android.healthconnect.phone.*`, **no se escribe fijo: sale del origen leído** — desde junio de 2026 el teléfono usa un nombre sintético por dispositivo **[V]**), reloj (`device.type` 1 o 6) u otro.

El día se acota con la fecha del registro; con `aggregateGroupByPeriod` el filtro tendría que ser `LocalDateTime`, por eso se usa `aggregateRecord` con un rango de instantes **[V]**.

## Decisión 3 — Qué se guarda y dónde (sin tablas nuevas)

V84 creó `registros_habito.valor_medido` y `origen_medicion` «para que la fase 2 (lectura automática de la captura, o pasos del teléfono) pueda convivir con lo manual». Se reutilizan. Hacen falta columnas para lo que no cabe ahí (pasos, fuente, tipo de dispositivo, hora de lectura, «por revisar») porque son atributos **del registro del día** (una fila por participante, hábito y fecha: justo la UNIQUE de la tabla); una tabla aparte repetiría esa clave. Mismo razonamiento de V84. La **meta** es atributo del hábito y vive en `habitos.meta_medicion`.

## Decisión 4 — Validación en el servidor

El teléfono no es de fiar: cualquier app con permiso de escritura puede escribir pasos en Health Connect y declararlos automáticos **[I]**. Por eso el servidor valida meta, tope (60 km, 150 000 pasos), la hora de la lectura y el día, **guarda el origen** y marca lo dudoso «por revisar» para el mentor. La captura con número a mano de hoy es **más fácil de falsear** que esto; el cambio mejora la confianza aunque no sea infalible.

## Precisión (para los textos: «aprox.»)

Caminata deliberada con el teléfono en el bolsillo: error de pasos ≈ 0–7 %. Día completo solo con teléfono: subcuenta 12–30 % (Hong 2024: 29,6 %; Thomson 2019: −12 %). Km estimados desde pasos: 7–39 % de error; con GPS, 2–10 % (Adamakis 2022). Umbral aceptable para población general: ≤ 10–15 % (INTERLIVE, 2021). **Los km de pasos son «aprox.»**.

## Play Console (antes de publicar)

Declarar **Activity and fitness** con Steps y Distance (justificación por separado), completar **Data safety**, y que la política de privacidad sea **la misma** que se muestra desde la pantalla de permisos de Health Connect **[V]** (`.../publish/declare-access`, `support.google.com/googleplay/android-developer/answer/12991134`). No hay plazo oficial de revisión. Mostrar a otros datos de salud sin consentimiento está prohibido: la hoja de conexión dice que **el total de km lo ven el mentor y el ranking** y la persona acepta.

## Revisión de seguridad de las dependencias (antes de instalar)

Se miró el registro de npm y el tarball **sin instalarlo**, en una carpeta aparte.

| Paquete | Resultado |
|---|---|
| `react-native-health-connect` **4.1.3** (fijada, sin `^`) | MIT. Autor `matinzd <me@matinzd.dev>`, repo `github.com/matinzd/react-native-health-connect`, publicada por **GitHub Actions con OIDC (trusted publisher)** y atestación de **proveniencia SLSA** en npm. **Sin `preinstall`, `install` ni `postinstall`** (`prepack`/`release` no corren al instalar). **Sin `dependencies`**; `peerDependencies`: `react`, `react-native`, y `expo` / `@expo/config-plugins` (opcionales). 142 archivos, 473 KB. Se verificó el `integrity` sha512 y el `shasum` del tarball descargado contra el registro. En `src`, `lib`, `android`, `android-expo` y `app.plugin.js`: **ni `fetch`, ni `XMLHttpRequest`, ni `child_process`, ni `eval`/`new Function`, ni `process.env`, ni `java.net`/`HttpURLConnection`/`OkHttp`, ni `Runtime.exec`/`DexClassLoader`**, y ninguna URL salvo comentarios y el espacio de nombres del manifest. El módulo Android depende de `androidx.health.connect:connect-client:1.1.0` (Google), `kotlinx-coroutines-android:1.7.3` y React Native; su manifest solo declara `<queries>` para `com.google.android.apps.healthdata`. La parte de Expo (`android-expo`) solo registra un `ReactActivityLifecycleListener` que llama a `HealthConnectPermissionDelegate.setPermissionDelegate(activity)`. Compatibilidad: sus `devDependencies` apuntan a `@expo/config-plugins ~57.0.6` y `react-native 0.86.2`; nosotros tenemos `expo ~57.0.18` y `react-native 0.86.3`. **Riesgo:** un solo mantenedor; plan B, un módulo local mínimo como `modules/renaser-alarmas`. Se revisa el diff del tarball en cada subida. |
| `expo-build-properties` **57.0.22** (línea `sdk-57`, `~57.0.22`) | MIT, 650 Industries (Expo), repo `expo/expo`, `packages/expo-build-properties`. Sin scripts de instalación. Dependencias: `semver`, `resolve-from`, `@expo/schema-utils` (todas de Expo o de uso común). Necesario para subir `minSdkVersion` a 26 (la librería lo exige; RN 0.86 trae 24). |
| `androidx.health.connect:connect-client:1.1.0` (Gradle, Google) | Lo fija la librería; no se cambia. |

**No se agrega** `expo-dev-client` (ver `plan.md`, «Build nativo»). **No se agrega** `play-services-fitness`.

## Lo que no se pudo verificar sin un teléfono

Que el agregado incluya entradas manuales (inferido; se prueba con Health Connect Toolbox); la zancada que usa el conteo nativo; cómo se comporta el permiso de Health Connect en un APK de depuración fuera de Play; Garmin/Xiaomi/Strava con Health Connect; el plazo de revisión de Play.

# Contrato: módulo nativo `RenaserAlarmas` (app)

Se carga con `requireOptionalNativeModule('RenaserAlarmas')`. Un APK sin estas funciones devuelve el valor neutro y la app no falla.

| Función | Firma JS | Android | Neutro (APK viejo, iOS, web) |
|---|---|---|---|
| `puedeProgramarAlarmasExactas` | `() => boolean \| null` | existe (D-217) | `null` |
| `abrirAjusteDeAlarmasExactas` | `() => boolean` | existe (D-217) | `false` |
| `detencionForzadaDesde` **(nueva)** | `(desdeMs: number) => { detenida: boolean; cuando: number \| null; motivo: string \| null }` | API 35+: algún arranque **en frío** (`START_TYPE_COLD`) posterior a `desdeMs` con `ApplicationStartInfo.wasForceStopped()`, ubicado por `START_TIMESTAMP_LAUNCH` y no por `pid` (los arranques en frío quedan con `pid=0`; E-638). API 30–34: último `REASON_USER_REQUESTED` más nuevo que `desdeMs`. Una salida por quitar de recientes (`subreason=22` / «remove task») **solo cuenta en las marcas que detienen apps** (FR-022, regla de abajo); en las demás se descarta. `motivo` = `toString()` recortado, para el log | `{ detenida: false, cuando: null, motivo: null }` |
| `abrirAjusteDeLaMarca` **(nueva)** | `() => 'pantalla_de_la_marca' \| 'lista_de_bateria' \| 'ajustes_de_la_app' \| 'ninguna'` | Prueba los componentes de la marca en orden (research R3); luego `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`; luego `ACTION_APPLICATION_DETAILS_SETTINGS` con `package:` | `'ninguna'` (la app cae a `Linking.openSettings`) |
| `proximaAlarmaPropia` **(nueva, refuerzo FR-020)** | `() => 'propia' \| 'de_otra_app' \| 'ninguna' \| 'desconocido'` | `AlarmManager.getNextAlarmClock()` (API 21+): `null` → `'ninguna'`; si no, `'propia'` cuando `showIntent?.creatorPackage == packageName` (API 17+) y `'de_otra_app'` en cualquier otro caso (incluido un `showIntent` ausente). Cualquier excepción o contexto no disponible → `'desconocido'`. Log: solo «de Renaser» / «de otra app», sin nombres de paquete | `'desconocido'` |

Reglas:
- Ninguna función lanza: errores → valor neutro.
- No se declara `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` ni se usa `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.
- No se agregan `<queries>`: no se pre-comprueba, se intenta y se captura.

### `proximaAlarmaPropia` y cómo la usa la app (FR-020)

`getNextAlarmClock()` es **global**: devuelve la primera alarma del despertador de todo el teléfono, no la de Renaser. Por eso la app solo concluye algo en dos casos:

| Alarmas programadas a futuro en la app | `proximaAlarmaPropia` | Resultado |
|---|---|---|
| ninguna | cualquiera | `vivas` (nada que perder) |
| sí | `'ninguna'` | **`borradas`** → se aplica FR-007 (aviso una vez por día) |
| sí | `'propia'` | `vivas` |
| sí | `'de_otra_app'` o `'desconocido'` | `no_se_sabe` → no se avisa por esta vía |

Condiciones del lado de la app (`alarmasVivas.ts`):
- Sin el permiso «Alarmas y recordatorios» (Android 12+) las alarmas **no** son del tipo despertador (`setAndAllowWhileIdle`) y no salen como próxima alarma aunque estén bien programadas: el resultado es `no_se_sabe`.
- Cuentan como «a futuro» las diarias, semanales y de fecha por venir; no la prueba de sonido (intervalo de 3 s).
- La lectura se toma **antes** del rearmado (`ponerAlDiaLasAlarmas`, paso 0) y se reutiliza, porque rearmar vuelve a poner las alarmas y el sistema siempre diría `propia`. El valor del sistema se lee de forma síncrona antes de cualquier espera.

### Regla de marca para «quitar de recientes» (FR-022)

En `detencionForzadaDesde`, API 30–34, una salida `REASON_USER_REQUESTED` por quitar de recientes cuenta como detención si `Build.MANUFACTURER + " " + Build.BRAND` (minúsculas) contiene alguna de: `honor`, `huawei`, `xiaomi`, `redmi`, `poco`, `oppo`, `realme`, `oneplus`, `vivo`, `iqoo`, `samsung`. En las demás marcas se descarta (en un Android sin capa del fabricante no borra las alarmas, probado en el emulador el 19/10). API 35+ no cambia: manda `wasForceStopped()`.

La app reconoce las mismas marcas en JS (`utils/marcaDelTelefono.ts`, `marcaQueDetieneApps`) para FR-021 (guía preventiva); `moduloNativoDeAlarmas.test.ts` compara las dos listas para que no se desfasen.

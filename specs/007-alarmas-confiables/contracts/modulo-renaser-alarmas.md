# Contrato: módulo nativo `RenaserAlarmas` (app)

Se carga con `requireOptionalNativeModule('RenaserAlarmas')`. Un APK sin estas funciones devuelve el valor neutro y la app no falla.

| Función | Firma JS | Android | Neutro (APK viejo, iOS, web) |
|---|---|---|---|
| `puedeProgramarAlarmasExactas` | `() => boolean \| null` | existe (D-217) | `null` |
| `abrirAjusteDeAlarmasExactas` | `() => boolean` | existe (D-217) | `false` |
| `detencionForzadaDesde` **(nueva)** | `(desdeMs: number) => { detenida: boolean; cuando: number \| null; motivo: string \| null }` | API 35+: `ApplicationStartInfo.wasForceStopped()` del arranque actual. API 30–34: último `REASON_USER_REQUESTED` más nuevo que `desdeMs` que no sea quitar de recientes (`subreason=22` / «remove task»). `motivo` = `toString()` recortado, para el log | `{ detenida: false, cuando: null, motivo: null }` |
| `abrirAjusteDeLaMarca` **(nueva)** | `() => 'pantalla_de_la_marca' \| 'lista_de_bateria' \| 'ajustes_de_la_app' \| 'ninguna'` | Prueba los componentes de la marca en orden (research R3); luego `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`; luego `ACTION_APPLICATION_DETAILS_SETTINGS` con `package:` | `'ninguna'` (la app cae a `Linking.openSettings`) |

Reglas:
- Ninguna función lanza: errores → valor neutro.
- No se declara `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` ni se usa `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.
- No se agregan `<queries>`: no se pre-comprueba, se intenta y se captura.

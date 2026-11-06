# Plan: spec 047 — transcripción de las sesiones por Meet

**Spec**: `spec.md` · **Rama**: `047-transcripcion-de-eventos` · **Supuestos**: mientras el dueño no responda, se usan las opciones recomendadas: P1-A, P2-B, P3-B (6 meses), P4-B, P5-A. Si cambia una, se corrige el punto que la nombra.

## Arquitectura (hexagonal, módulo `calendar`, junto a la 040)

| Capa | Qué | Archivo (nuevo salvo que se diga) |
|---|---|---|
| Dominio | `TranscripcionDeSesion` (estados y transiciones: `ESPERANDO` → `LISTA`/`SIN_*`/`DUDA` → `BORRADA`), `VentanaDeLaFecha` (inicio − 30 min, fin + 3 h), `TextoDeTranscripcion` (arma «Nombre: frase», tope de 500 000, sin teléfonos) | `calendar/domain/model/meet/transcripcion/…` |
| Puerto de salida | `ReunionesDeMeetPort`: `conferenciasDeLaSala(codigo, ventana)`, `transcripcion(conferencia)` → entradas y participantes, `grabaciones(conferencia)` → `exportUri` | `calendar/application/ports/out/meet/ReunionesDeMeetPort.java` |
| Puerto de salida | `LoadTranscripcionPort`, `SaveTranscripcionPort` | idem |
| Adaptador Google | `GoogleMeetReunionesAdapter`: Meet REST API v2 (`conferenceRecords?filter=space.meeting_code=…`, `participants`, `transcripts`, `transcripts/{t}/entries`, `recordings`), paginado, tolerante, con el mismo `LlamadasAGoogle` y token de la 040 | `calendar/infrastructure/adapter/out/google/` |
| Persistencia | `TranscripcionesJdbcAdapter` (cifra y descifra con `CifradoDeSecretosPort`; `AesGcmCifrado` ya existe) | `calendar/infrastructure/adapter/out/persistence/meet/` |
| Servicio | `BusquedaDeTranscripcionesService`: toma pendientes (FR-002), respeta `transcripcion_confirmada` (FR-009), decide estado y reintento (FR-008) | `calendar/application/services/` |
| Servicio | `RetencionDeTranscripcionesService` (diario, FR-014) y borrado al borrar evento o fecha (FR-015, por la FK en cascada + constancia antes) | idem |
| Servicio | `DescargaDeTranscripcionService` (P1-A: ADMIN/ALCHEMIST, motivo de 10 a 500 caracteres, auditoría `TRANSCRIPCION_LEIDA`) | idem |
| Entrada | `TranscripcionesScheduler` (cada 5 min, con el bloqueo que ya usa `SalasDeMeetScheduler`) y `RetencionDeTranscripcionesScheduler` (diario, 03:30 Lima) | `calendar/infrastructure/adapter/in/scheduler/` |
| Entrada REST | `GET /api/v1/calendar/events/{id}/meet/transcripciones` (estado por fecha, sin texto), `POST …/{fecha}/descarga` (cuerpo `{motivo}`, `text/plain` con `Cache-Control: no-store`) y `POST …/{fecha}/borrar` (FR-016). `recordingNotice` en el detalle del evento (P4-B) | `calendar/infrastructure/adapter/in/rest/evento/` |
| Catálogo Soporte TI | `eventos.meet.transcripcion`, hija de `eventos.meet`, `apagadaDeFabrica` | `plataforma/domain/model/catalogo/EntradasDelCatalogo.java` (editar) |
| Configuración | `meetings.space.readonly` en `EVENTOS_GOOGLE_PERMISOS` por defecto; `CuentaDeGoogle` suma `PERMISO_LEER_REUNIONES` y el estado dice si falta | `application.yaml`, `CuentaDeGoogle.java` (editar) |

## Datos (migración nueva, número al implementar)

```sql
CREATE TABLE eventos_meet_transcripciones (
  id uuid PRIMARY KEY,
  evento_meet_id uuid NOT NULL UNIQUE REFERENCES eventos_meet(id) ON DELETE CASCADE,
  estado text NOT NULL CHECK (estado IN ('ESPERANDO','LISTA','SIN_REUNION','SIN_TRANSCRIPCION','SIN_PERMISO','DUDA','BORRADA')),
  conferencias text[] NOT NULL DEFAULT '{}',
  entradas integer, largo integer, recortada boolean NOT NULL DEFAULT false,
  texto_cifrado text, grabacion_cifrada text, version_clave smallint,
  intentos integer NOT NULL DEFAULT 0, proximo_intento_en timestamptz, motivo varchar(300),
  obtenida_en timestamptz, borrar_despues_de timestamptz, borrada_en timestamptz,
  creada_en timestamptz NOT NULL DEFAULT now(), actualizada_en timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT lista_con_texto CHECK (estado <> 'LISTA' OR texto_cifrado IS NOT NULL),
  CONSTRAINT borrada_sin_texto CHECK (estado <> 'BORRADA' OR (texto_cifrado IS NULL AND grabacion_cifrada IS NULL))
);
CREATE INDEX ON eventos_meet_transcripciones (proximo_intento_en) WHERE estado = 'ESPERANDO';
CREATE INDEX ON eventos_meet_transcripciones (borrar_despues_de) WHERE estado = 'LISTA';
```

Auditoría: se reemplaza el CHECK de `integracion_google_auditoria.accion` (como V171) sumando `TRANSCRIPCION_GUARDADA`, `TRANSCRIPCION_SIN_DATOS`, `TRANSCRIPCION_LEIDA`, `TRANSCRIPCION_BORRADA`. El motivo de quién sacó o borró a pedido una transcripción va en `eventos_meet_transcripciones_accesos` (V184: actor, evento, fecha, `LECTURA`/`BORRADO`, motivo; 12 meses). Sin texto ni enlaces. Lo implementado está en V184.

## Reglas clave

- Ventana de búsqueda: 5 min a 8 h tras el fin agendado; reintento cada 10 min; 429/5xx con espera creciente (la de `PasosConGoogle`); 401 → `CuentaGoogleRevocadaException` (la 040 ya la maneja); 403 por permiso → `SIN_PERMISO` sin reintento; recuperación de pendientes de hasta 29 días.
- `transcripcion_confirmada = false` → `SIN_TRANSCRIPCION` sin llamar a Google.
- Nada del texto sale a logs: el adaptador y el servicio solo registran estado, cantidades y códigos HTTP. Prueba con texto testigo.
- `borrar_despues_de = obtenida_en + 6 meses` (P3-B), configurable por `EVENTOS_MEET_TRANSCRIPCION_RETENCION_DIAS` (por defecto 180).

## App (frontend, aparte y después del backend)

- P4-B: una línea en el detalle del evento «Esta sesión se graba y se transcribe.» cuando `meet.grabacion` o `meet.transcripcion` vengan en `true` y la función esté disponible. Necesita autorización de tabs (Comunidad → Eventos).
- Política de privacidad (`public/privacidad/index.html`): grabación y transcripción, finalidad, acceso, 6 meses, cifrado, cómo pedir el borrado (renaserlab@gmail.com). Se publica antes de encender el interruptor.
- Administración → detalle del evento: el estado por fecha (FR-012). Sin texto.

## Pasos manuales (dueño o equipo, no los hace el agente)

1. Google Cloud: activar **Google Meet REST API** (ya activa por la 040) y sumar `…/auth/meetings.space.readonly` en «Acceso a los datos» de la pantalla de consentimiento.
2. Volver a conectar la cuenta en Administración (flujo de la 040).
3. SC-001 y SC-002 con una sala de prueba y personas del equipo.
4. Publicar la política; recién entonces encender `eventos.meet.transcripcion` en Soporte TI.

## Riesgos

- **Google cambia lo que deja a cuentas personales**: hoy RenaserOs lo usa en producción; si Google lo corta, queda `SIN_TRANSCRIPCION` y se avisa (FR-009). No se rompe nada más.
- **Mismo Gmail que RenaserOs**: leer las reuniones de nuestras salas no toca las de ellos (filtramos por el código de NUESTRAS salas), pero el Drive de esa cuenta guarda ambas; acceso al Drive = acceso a todo. Anotado para el dueño.
- **30 días de Google**: si el servidor queda caído más de 29 días, se pierde la vía de la API.

## Verificación

`./mvnw verify` (unitarias, `@WebMvcTest`, IT con Testcontainers y Google simulado con `MockRestServiceServer`), sin tocar producción. Las marcas `spec 047 FR-NNN` van en las pruebas; `047` entra a `specs/trazabilidad.json` al implementar.

# Data model: Código Renaser en las fichas

**No hay tablas ni columnas nuevas** (la V133 reservada no se usa).

## El dato

Una fila del catálogo (código) más, solo cuando alguien lo cambia, una fila de `funcionalidades_estado` con sus excepciones por rol:

| Pieza | Dónde vive | Forma |
|---|---|---|
| Definición | `EntradasDelCatalogo` (código) | `radar.fichas` · madre `radar` · área «Programa» · «Código Renaser en las fichas» · `rolesQueLaUsan = {MENTOR, TRAINEE_GUIDE, MENTOR_LEAD}` · `porDefecto = PRENDIDA` · `apagadaPorDefectoPara = {MENTOR, TRAINEE_GUIDE, MENTOR_LEAD}` · `seEsconde = true` · no crítica · procesos `NO_APLICA` |
| Estado guardado | `funcionalidades_estado` (`clave`, `prendida_global`, `aviso`, `version`, `actualizado_por`, `actualizado_en`) + `funcionalidades_estado_rol` (`clave`, `rol`, `prendida=false`) | **sin fila** = apagado para los tres roles (el valor por defecto del catálogo) |
| Cambio | `cambios_auditados` (`tipo = MANUAL`, `actor_id`, `clave`, `antes`, `despues`, `motivo`, `creado_en`) | append-only (trigger): quién, cuándo, antes y después |

## Estados

```text
sin fila ──(Admin: visible=true)──▶ fila {prendidaGlobal: true, apagadaPara: {}}            "visible"
    ▲                                        │
    │                                        └──(Admin: visible=false)──▶ fila {prendidaGlobal: true, apagadaPara: {MENTOR, TRAINEE_GUIDE, MENTOR_LEAD}}   "oculto"
    └── (nunca se borra la fila desde la app: queda la última decisión)
```

Cualquier otra combinación (Soporte TI desde su consola) se lee así: `visible` solo si la funcionalidad está prendida **y** ninguno de los tres roles está apagado; si no, `visible=false` y `porRol`
dice cuál ve y cuál no (**mixto**).

## Reglas de validación

- El cuerpo del `PUT` es `{"visible": true|false}`; cualquier otra cosa es 400. No acepta `role` ni nada más (se ignora).
- Quien cambia o lee: ADMIN, ALCHEMIST o IT_SUPPORT con la cuenta `ACTIVE`; el resto, 403 (incluida una cuenta suspendida con la sesión viva).
- La evaluación de la clave para una persona (`EvaluacionDeFuncionalidades`): apagada para todos gana; si no, la excepción del rol; **si no hay fila, `apagadaPorDefectoPara` contiene el rol del actor**.
  Un proceso sin actor (tareas programadas) la ve prendida.
- Apagada con `seEsconde` ⇒ `noDisponible` (404), nunca el aviso de mantenimiento.

## Invariantes

- Ninguna lectura de Administración ni de la propia persona depende de esta clave (FR-010).
- Apagar y encender no toca ningún registro del Código Renaser (FR-011): el interruptor es una puerta de lectura.
- Un cambio sube la `version` global (secuencia `funcionalidades_version_seq`), que es lo que ven las instancias y la app (`ETag` de `/funcionalidades/mias`).

# Data Model: Análisis 80/20 con IA

## `analisis_80_20` (V153)

Una fila por persona, tipo de periodo y primer día del periodo.

| Columna | Tipo | Notas |
|---|---|---|
| `id` | uuid PK | |
| `participante_id` | uuid NOT NULL | FK `participantes_programa(usuario_id)` ON DELETE CASCADE (el análisis se va con la cuenta, 031-9 A) |
| `formacion` | text NOT NULL | FK `formaciones(codigo)`: la de la persona al generar; solo se muestra la de su formación actual |
| `periodo` | text NOT NULL | `SEMANA` \| `MES` |
| `numero` | int NOT NULL | semana o mes del programa (1…; sin tope: 031-8 B) |
| `inicio`, `fin` | date NOT NULL | primer y último día del periodo (zona de la persona) |
| `estado` | text NOT NULL | `PENDIENTE` \| `LISTO` \| `SIN_DATOS` \| `AGOTADO` |
| `intentos` | int NOT NULL DEFAULT 0 | solo cuentan los intentos fallidos de verdad (un diferimiento no cuenta) |
| `ultimo_intento_en` | timestamptz | espacia los reintentos (6 h) y cuenta el tope global de 500 en 24 h |
| `generado_en` | timestamptz | solo `LISTO` |
| `creado_en` | timestamptz NOT NULL | |
| `lo_que_mas_movio`, `lo_que_se_te_fue`, `como_estuviste` | text | solo `LISTO` (máx 700 caracteres cada una) |
| `proximos_pasos` | text[] | solo `LISTO`: de 1 a 3 pasos de hasta 280 caracteres |
| `cifras` | jsonb | la FOTO de las cifras que el texto cita: lo que se leyó hoy es lo que se lee mañana (única desnormalización; nadie la consulta por dentro) |

**Restricciones:** `UNIQUE (participante_id, periodo, inicio)` (el índice que usa la lectura y el upsert); `CHECK`: solo `LISTO` lleva texto, con 1–3 pasos, cifras y `generado_en`, y los demás estados no llevan nada de eso; `fin >= inicio`; índice parcial por `ultimo_intento_en` (el tope diario cuenta los intentos de las últimas 24 horas sin una tabla de contadores) e índice por `formacion`.

## Estados (`AnalisisDelPeriodo`)

```text
            (el periodo cierra)
                  │
                  ▼
   ┌──────── PENDIENTE ───────┐
   │  intento falla < 3        │ sin datos mínimos
   │  (espera 6 h) ↺           ▼
   │                        SIN_DATOS   (no se llama a la IA; si luego hay datos y sigue en la ventana, se reevalúa)
   │ texto válido
   ▼
 LISTO  (el texto no se reescribe)          AGOTADO  (3 intentos fallidos: «No pudimos generarlo»)
```

La app ve además `EN_CURSO` (el periodo no cerró: solo cifras): no es una fila, se calcula al leer.

## Periodos (`CalendarioDelAnalisis`, puro)

- **Semana n ≤ 13:** la de `SemanaPrograma` (la 1 empieza el Día 1; la 13 llega al día 90 de Plan).
- **Semana 14:** desde el día siguiente al fin de la 13 hasta su domingo (parcial). **Semana ≥ 15:** lunes a domingo, sin tope.
- **Mes m:** bloques de 4 semanas; la 13 queda en el mes 3 → semanas 1–4, 5–8, 9–13, 14–17, 18–21…
- Un periodo está **cerrado** cuando su `fin` ya pasó en la zona de la persona.

## Lo que no se guarda

Cifras de un periodo en curso (se calculan al leer), la muestra del Código Renaser y de «Cierra tu día» que se envió al proveedor (se arma en memoria y se descarta), ni las consultas de la IA.

## Otras estructuras

- `tipo_notificacion` suma `ANALISIS_80_20` (V154); `consultas_de_ficha` (ya existe) recibe la constancia del liderazgo.
- Catálogo de Soporte TI: `ia.analisis` (en código, `EntradasDelCatalogo`; sin fila hasta que se toque).

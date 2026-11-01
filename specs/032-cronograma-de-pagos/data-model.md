# Modelo de datos: cronograma de pagos (spec 032, V158)

## `cuotas_de_pago` (tabla nueva)
| Columna | Tipo | Notas |
|---|---|---|
| `id` | uuid PK | lo genera el servidor |
| `usuario_id` | uuid NOT NULL → `usuarios` | `ON DELETE CASCADE` |
| `vence_el` | date NOT NULL | fecha fija del calendario que escribe Administración; sin hora ni zona |
| `monto` | numeric(12,2) NOT NULL | `CHECK (monto > 0)` |
| `estado` | text NOT NULL | `PENDIENTE` (por defecto) o `PAGADA` |
| `estado_cambiado_por` | uuid → `usuarios` | quién marcó por última vez; `ON DELETE SET NULL` |
| `estado_cambiado_en` | timestamptz | cuándo; obligatorio si `estado = 'PAGADA'` |
| `creada_en` | timestamptz NOT NULL | orden estable de dos cuotas del mismo día |

Índice `(usuario_id, vence_el)`.

## `estado_onboarding` (columnas nuevas; 1:1 con `usuarios`)
| Columna | Notas |
|---|---|
| `pago_modalidad` | `SIN_CRONOGRAMA`, `CON_CRONOGRAMA` o NULL = sin anotar |
| `pago_moneda` | `PEN` o `USD`; solo con cronograma |
| `cronograma_confirmado_en` / `_nombre` / `_huella` | la confirmación de la persona; van juntas o ninguna; solo con cronograma |

`CHECK`: con cronograma hay moneda y sin él no; la confirmación solo existe con cronograma y va completa.

## Reglas del dominio (no de la base)
- 1 a 60 cuotas; monto de hasta 9 999 999,99 con dos decimales; fecha entre 2020 y 2100.
- Una cuota pagada no se edita ni se quita; debe quedar al menos una; no se reemplaza el cronograma si hay pagadas.
- **Huella** = SHA-256 de `moneda|fecha:monto|…` con las cuotas ordenadas por fecha y monto. La confirmación es vigente si `cronograma_confirmado_huella` = huella de ahora.

## Qué se reutiliza
`estado_onboarding` (la persona 1:1, el «Acuerdo» = `terminos_aceptados_en`, la ficha con diagnóstico), `ConsultarActorPort` (nombre y «administra»), `EtapaOnboardingPort` (diagnóstico terminado), `IdGenerator`, `Clock`.

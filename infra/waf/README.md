# AWS WAF en CloudFront (spec 050, tramo B)

Un Web ACL de AWS WAF delante de la distribución de CloudFront de la API: dos reglas por tasa por IP y tres grupos
administrados de AWS. **Todo nace en `Count`** (cuenta y deja pasar): se mide una semana y el dueño pasa a `Block` regla por
regla. Nada de esto cambia el tráfico hasta ese último paso.

**Todo lo que cambia producción lo corre el dueño** (D12). El id de la cuenta y el de la distribución entran por variables;
no hay secretos (el WAF no maneja ninguno). Cada script es un simulacro salvo que se le pase su bandera de aplicar, usa el
perfil `prod`, se niega si la cuenta no es la esperada, es idempotente y guarda un respaldo antes de cambiar algo
(`$RENASER_RESPALDOS`, por omisión `~/.renaser/respaldos/`, fuera del repositorio, permiso 600).

| Archivo | Qué es |
|---|---|
| `web-acl.plantilla.json` | El Web ACL (alcance `CLOUDFRONT`, `us-east-1`): reglas, respuesta 429, registro y excepciones. |
| `crear-web-acl.sh` | Crea el grupo de registros y el Web ACL (sin asociarlo); `--actualizar` y `--revertir` (borrar). |
| `asociar-web-acl.sh` | Pone el Web ACL en la distribución (`WebACLId`) con simulacro y vuelta atrás. |
| `pasar-regla-a-bloqueo.sh` | `Count` → `Block` de **una** regla, con su propia bandera `--aplicar-bloqueo`; `--volver-a-count`. |
| `ensayo-local.sh` | Los tres scripts de punta a punta contra un AWS de mentira (sin Docker, sin cuenta). |

## Qué crea

| Regla | Qué hace | Cuándo bloquea (en `Block`) |
|---|---|---|
| `limite-auth` | cuenta los pedidos por IP a rutas que empiezan por `/api/v1/auth/` | más de **300 en 5 min** → 429 |
| `limite-global` | cuenta los pedidos por IP a toda la API | más de **2.000 en 5 min** → 429 |
| `reputacion-de-ip` | `AWSManagedRulesAmazonIpReputationList` | IP con mala reputación según AWS |
| `entradas-conocidas-como-malas` | `AWSManagedRulesKnownBadInputsRuleSet` | pedidos con patrones de explotación conocidos |
| `reglas-comunes` | `AWSManagedRulesCommonRuleSet` | el conjunto general (incluye límites de tamaño del cuerpo) |

- **429 de las reglas por tasa** (FR-002): `Retry-After: 60` y el cuerpo
  `{"message":"Espera un momento y vuelve a intentarlo.","codigo":"DEMASIADOS_PEDIDOS"}`, el mismo que usarán nginx y el filtro por
  cuenta de Spring. Los grupos administrados, en `Block`, usan las acciones propias de cada una de sus reglas (403).
- **Registro** (Q8): CloudWatch Logs `aws-waf-logs-renaser`, 14 días, con `x-auth-token`, `authorization` y `cookie`
  **censurados**; el WAF no guarda el cuerpo de los pedidos. Pasados los 14 días se baja a muestreo (filtro de registro).
- **Costo aproximado**: el Web ACL (~US$ 5) + ~US$ 1 por regla (5) + ~US$ 0,60 por millón de pedidos + el registro: del orden de
  US$ 10 al mes con el tráfico de hoy. Las reglas usan unos 934 WCU de los 1.500 incluidos (lo dice `check-capacity`).

## Orden de salida

```bash
export RENASER_CUENTA_AWS=<id de 12 dígitos de la cuenta de producción>
export RENASER_DISTRIBUCION=<id de la distribución de CloudFront del backend>

# 1. Simulacro: AWS revisa la plantilla (check-capacity) y se ve qué se crearía. No crea nada.
infra/waf/crear-web-acl.sh
# 2. Crear el Web ACL y el registro (sin asociar: todavía no ve tráfico).
infra/waf/crear-web-acl.sh --aplicar
# 3. Asociarlo a la distribución, en Count: no bloquea nada, solo cuenta.
infra/waf/asociar-web-acl.sh
infra/waf/asociar-web-acl.sh --aplicar
# 4. Siete días de métricas (CloudWatch, us-east-1, AWS/WAFV2, región Global) y de muestras (consola de WAF).
# 5. Bloquear, de a una regla. Primero las dos por tasa y la reputación de IP:
infra/waf/pasar-regla-a-bloqueo.sh --regla limite-auth                      # simulacro: muestra lo que contó en 7 días
infra/waf/pasar-regla-a-bloqueo.sh --regla limite-auth --aplicar-bloqueo
infra/waf/pasar-regla-a-bloqueo.sh --regla limite-global --aplicar-bloqueo
infra/waf/pasar-regla-a-bloqueo.sh --regla reputacion-de-ip --aplicar-bloqueo
# 6. Los otros dos grupos, solo si no hubo falsos positivos con tráfico de la app (ver abajo).
```

Necesita `aws` (perfil `prod`) y `jq`. Permisos de AWS del perfil: `sts:GetCallerIdentity`; `wafv2:CheckCapacity`,
`ListWebACLs`, `GetWebACL`, `CreateWebACL`, `UpdateWebACL`, `DeleteWebACL`, `PutLoggingConfiguration`,
`DeleteLoggingConfiguration`; `logs:DescribeLogGroups`, `CreateLogGroup`, `PutRetentionPolicy`, `DeleteLogGroup`,
`CreateLogDelivery`, `PutResourcePolicy`, `DescribeResourcePolicies` (el registro del WAF lo pide);
`cloudfront:GetDistributionConfig`, `UpdateDistribution`, `ListDistributionsByWebACLId`; `cloudwatch:GetMetricStatistics`.

## Antes de bloquear los grupos administrados

El cuerpo de un pedido de la app puede ser un texto largo (publicaciones del Muro, diario, transcripciones) y algunas reglas de
`reglas-comunes` miran el tamaño del cuerpo y patrones que un texto libre puede parecer. Se revisan en `Count` (muestras en la
consola de WAF, el registro en CloudWatch). Si una regla acierta mal con tráfico legítimo (FR-003), **se excluye esa regla por
nombre, nunca el grupo entero**: se agrega a `Renaser.Excepciones` de la plantilla con su motivo y su fecha,

```json
{ "Grupo": "reglas-comunes", "Regla": "SizeRestrictions_BODY", "Motivo": "textos largos del Muro", "Fecha": "2026-11-20" }
```

y se corre `infra/waf/crear-web-acl.sh --actualizar --aplicar`: la regla queda en `Count` **dentro** de su grupo y se conserva
el `Count`/`Block` que cada regla tenga en ese momento (no devuelve a `Count` lo que ya se bloqueó). Las subidas de archivos van
directo a S3 con URL firmada y no pasan por el WAF.

**IP compartida.** Una red móvil o una oficina puede poner a muchas personas detrás de la misma IP: 2.000 pedidos en 5 minutos
es holgado para una persona (≈ 6,7 por segundo) pero no para un salón entero. Por eso se mira primero en `Count` cuántas IP pasan
del 50 % y del 100 % del umbral antes de bloquear (`pasar-regla-a-bloqueo.sh` sin bandera muestra lo contado por día).

## Vuelta atrás

```bash
# Una regla de Block a Count:
infra/waf/pasar-regla-a-bloqueo.sh --regla limite-auth --volver-a-count
# Sacar el Web ACL de la distribución (queda creado, deja de ver tráfico). Quita SOLO WebACLId: no pisa lo que se haya cambiado
# después en la distribución (la cabecera de origen del tramo A2, por ejemplo):
infra/waf/asociar-web-acl.sh --revertir [--aplicar]
# Borrarlo (se niega si sigue asociado; guarda una copia del Web ACL antes; el grupo de registros se deja salvo --borrar-registros):
infra/waf/crear-web-acl.sh --revertir [--aplicar] [--borrar-registros]
```

## Qué se probó y qué no

- `ConfiguracionDelWafTest` (la plantilla: valores, ventanas, grupos, todo en `Count`, 429, excepciones por nombre, registro
  censurado) y `ScriptsDelWafTest` (los scripts: simulacro por omisión, perfil, cuenta, respaldos, bandera aparte del bloqueo,
  borrar solo desasociado), ambas en `scripts/verificar.sh`.
- `ensayo-local.sh`: más de cuarenta comprobaciones de lo que los scripts arman y mandan, contra un AWS de mentira que rechaza lo
  que AWS rechazaría de forma evidente (una sola acción por regla, la respuesta 429 definida, el `LockToken`, campos que no son
  de AWS).
- **No se probó contra AWS** (no hay acceso): el **esquema** de la plantilla lo valida AWS en el simulacro (`check-capacity`),
  que el dueño corre antes de crear nada; el comportamiento real de las reglas administradas con el tráfico de la app lo dice la
  semana en `Count`; y la **distribución de ensayo** de la tarea T022 (la misma ACL contra un CloudFront de prueba con un origen de
  mentira, para ver el 429 y la censura del registro) queda opcional y sin hacer. En `Count` el WAF no cambia el tráfico, así que
  asociar la ACL real es reversible y no bloquea nada.

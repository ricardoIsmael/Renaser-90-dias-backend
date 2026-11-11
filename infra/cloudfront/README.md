# Cabecera de origen CloudFront → nginx (spec 050, tramo A2)

CloudFront agrega a cada pedido hacia el origen la cabecera `X-Origen-Renaser` con un secreto, y nginx (contenedor
`proxy`) rechaza con `403 {"message":"No autorizado","codigo":"ORIGEN_NO_PERMITIDO"}` lo que no la trae. Así nadie se
salta CloudFront (y el WAF del tramo B) hablándole directo a la IP del origen. Si el visitante manda esa cabecera por su
cuenta, CloudFront la pisa con la suya.

**Todo lo que cambia producción lo corre el dueño** (D12). Nada de esto vive en el repositorio salvo el código: el
secreto, el id de la distribución, el de la instancia y la cuenta de AWS entran por variables y por Parameter Store.

| Archivo | Qué es |
|---|---|
| `cabecera-de-origen.sh` | Agrega, exige, rota y quita la cabecera. Simulacro por omisión; `--aplicar` cambia algo. |
| `ensayo-local.sh` | El mismo código de punta a punta contra un nginx de verdad en Docker y un AWS de mentira. No toca AWS. |
| `../../scripts/despliegue/desplegar-backend.sh` | Genera la configuración de nginx en cada despliegue (y con `SOLO_PROXY=si`, solo nginx, sin tocar el backend). |

## Parámetros en `/renaser/prod/` (Parameter Store)

| Parámetro | Tipo | Qué es |
|---|---|---|
| `origen-secreto` | SecureString | El valor vigente. Lo crea `--paso agregar` (32 bytes aleatorios, nunca se imprime). |
| `origen-secreto-anterior` | SecureString | Solo durante una rotación: nginx acepta los dos. |
| `origen-ips-directas` | String | IPv4 o rangos, separados por coma o espacio, que entran **sin** la cabecera (la IP fija de operación del security group, si hace falta). Opcional. |
| `origen-modo` | String | `registrar` (anota y no rechaza; es el modo si el parámetro no existe) o `exigir`. |

## Lo que nginx deja pasar sin la cabecera

- **El propio host y la red `renaser` de Docker.** El CD comprueba la salud por `127.0.0.1:8080` y Docker entrega ese
  pedido al contenedor con la IP de la **pasarela de la red**, no con `127.0.0.1` (comprobado en el ensayo local): por
  eso nginx exime también la subred de la red `renaser`, que sale de `docker network inspect`. La comprobación del
  contenedor nuevo va directo a su IP y no pasa por nginx.
- **Las IP de `origen-ips-directas`.** El security group acepta el 8080 de la lista de CloudFront **y de una IP fija**:
  si esa IP es de operación (o de otro sistema que llama a la API sin CloudFront), ponla ahí. Si no, en `exigir` recibe
  403. El modo `registrar` lo muestra antes: cada pedido sin cabecera queda en `docker logs proxy` con su IP.
- Si faltan los secretos o la subred, `exigir` **cae solo a `registrar`** y lo avisa: nunca deja a la API sin entrada.
- Entre el despliegue de este cambio y `--paso agregar --aplicar` todavía no hay secreto, así que en `registrar` nginx anota **todo**
  pedido que llega por CloudFront como «sin cabecera». Es ruido esperable (el log de Docker rota a 2 × 10 MB): corre `--paso agregar`
  poco después de desplegar, y las 24 h de observación se cuentan desde que `estado` muestra que la distribución ya lleva la cabecera.

## Orden de salida (cada paso se verifica antes del siguiente)

```bash
export RENASER_CUENTA_AWS=<id de 12 dígitos de la cuenta de producción>
export RENASER_DISTRIBUCION=<id de la distribución de CloudFront del backend>
export RENASER_INSTANCIA=<id de la instancia EC2 del backend>
# si la distribución tiene más de un origen:  export RENASER_ORIGEN_ID=<Id del origen del backend>

# 1. Fusionar el PR y dejar que el CD despliegue: nginx queda en `registrar` (anota, no rechaza).
# 2. Simulacro y luego aplicar (crea el secreto, se lo da a nginx y lo agrega a la distribución):
infra/cloudfront/cabecera-de-origen.sh --paso agregar
infra/cloudfront/cabecera-de-origen.sh --paso agregar --aplicar
# 3. Esperar 24 h y mirar quién llega sin la cabecera (debe ser solo operación):
infra/cloudfront/cabecera-de-origen.sh --paso estado
# 4. Exigirla (se niega si hubo pedidos sin cabecera; comprueba por CloudFront y vuelve a registrar si falla):
infra/cloudfront/cabecera-de-origen.sh --paso exigir
infra/cloudfront/cabecera-de-origen.sh --paso exigir --aplicar
# 5. Después: comprobar un despliegue completo por SSM (el CD) con nginx en `exigir`.
```

Necesita `aws` (con el perfil `prod`), `jq`, `openssl` y `curl`. Permisos de AWS del perfil: `sts:GetCallerIdentity`,
`cloudfront:GetDistributionConfig`, `cloudfront:UpdateDistribution`, `cloudfront:GetDistribution`,
`ssm:GetParameter`, `ssm:PutParameter`, `ssm:DeleteParameter`, `ssm:SendCommand` y `ssm:GetCommandInvocation`.

## Rotar el secreto, sin corte (FR-007)

```bash
infra/cloudfront/cabecera-de-origen.sh --paso rotar            # simulacro: imprime los 5 pasos
infra/cloudfront/cabecera-de-origen.sh --paso rotar --aplicar
```

En este orden, que es lo que evita el corte: **nginx acepta los dos** → **CloudFront cambia** (con respaldo, ETag y espera
a «Deployed») → se comprueba por CloudFront y se espera la propagación (`ESPERA_ROTACION`, 120 s) → **nginx suelta el
viejo**. Si algo falla antes del último paso, nginx sigue aceptando los dos: no se pierde nada.

## Vuelta atrás

```bash
infra/cloudfront/cabecera-de-origen.sh --revertir            # simulacro
infra/cloudfront/cabecera-de-origen.sh --revertir --aplicar
```

Pone nginx en `registrar` y **después** quita la cabecera de la distribución (en ese orden, para que nunca haya un 403
de por medio). Quita **solo** la cabecera: no restaura una copia completa de la distribución, así no pisa lo que se
haya cambiado después (el WAF del tramo B, por ejemplo). Antes de cada cambio se guarda la configuración completa con su
`ETag` en `$RENASER_RESPALDOS` (por omisión `~/.renaser/respaldos/`, fuera del repositorio, permiso 600): si hiciera
falta restaurarla a mano, es `aws cloudfront update-distribution --if-match <ETag actual> --distribution-config`
con el `DistributionConfig` de ese archivo. Los secretos quedan en Parameter Store por si se vuelve a poner.

Solo nginx, a mano y sin pasar por el script (por ejemplo, para pasar a `registrar` desde la instancia):
`aws ssm put-parameter --profile prod --name /renaser/prod/origen-modo --type String --value registrar --overwrite` y un
despliegue cualquiera, o `SOLO_PROXY=si` con el script de despliegue.

## Qué se probó y qué no

- `ConfiguracionDeNginxTest` y `ScriptDeCabeceraDeOrigenTest` (estáticas, en `scripts/verificar.sh`).
- `ensayo-local.sh`: un nginx 1.28 de verdad y el mismo código de producción, con más de cincuenta comprobaciones: los dos modos,
  vigente y anterior, IP de operación, valores raros en Parameter Store, configuración inválida, el `/actuator/health`
  por el loopback, el simulacro y la idempotencia de cada paso, la rotación y la vuelta atrás.
- **No se probó** contra la distribución ni la instancia reales (no hay acceso): `update-distribution`, `wait
  distribution-deployed` y SSM los simula un `aws` de mentira; lo que haga CloudFront con la cabecera (pisar la del
  visitante) es comportamiento documentado de AWS, no medido aquí.

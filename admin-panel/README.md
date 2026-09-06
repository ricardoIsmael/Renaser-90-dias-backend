# Panel de solicitudes de cuenta

Panel de administración **mínimo** para aceptar y rechazar solicitudes de alta de Renaser OS,
desplegado como una AWS Lambda con Function URL.

**Alcance, y es a propósito chico:** listar las solicitudes pendientes, aprobar y rechazar.
Nada más. No hay usuarios, ni hábitos, ni métricas, ni lógica de negocio: cada acción es una
llamada a un endpoint que el backend Java ya expone.

| | |
|---|---|
| URL | `https://vaxh4jr4vrzwyq2nw4u6jzpgxm0saxzm.lambda-url.us-east-1.on.aws/` (`AWS_IAM`) |
| Se abre con | `python admin-panel/scripts/abrir-panel.py` → `http://127.0.0.1:8788` |
| Función | `renaser-admin-panel` (us-east-1, cuenta 302277511407) |
| Runtime | Node.js 22, arm64, 256 MB, 15 s, **sin dependencias externas** |
| Costo | ~**0 USD/mes** (entra entero en la capa gratuita) |

---

## Por qué una Lambda y no una página estática

El backend corre en EC2 (`i-0ea00f555c5fe8028`) sobre **HTTP plano**: no hay dominio ni
certificado, y su security group solo acepta el 8080 desde la IP del dueño. Una página estática
en S3 no serviría — el navegador tendría que hablarle al backend por HTTP sin TLS, desde una IP
que el security group rechaza.

Una **Function URL da HTTPS gratis** (certificado administrado por AWS, sin dominio propio) y,
puesta **dentro de la VPC**, llega al backend por IP privada. La Lambda habla HTTP con el backend
por dentro de la VPC, y **el 8080 no se abre a internet en ningún momento**.

```
navegador --> abrir-panel.py --HTTPS+SigV4--> Function URL --> Lambda (en la VPC,
 (127.0.0.1)   (máquina del dueño)                                    sg-08897be53ffe6faec)
                                                                          |
                                                        HTTP, IP privada 172.31.26.17:8080
                                                                          v
                                                        EC2 backend (sg-0ba472486a99c47a6)
                                                                          |
                                                                          v
                                                              RDS renaser-prod
```

---

## Las tres decisiones

### 1. Cómo llega la Lambda al backend — VPC + regla de security group entre SGs

La Lambda vive en `vpc-0025b53ddda33cdf3`, en las subredes `subnet-0522b2bad1681744f`
(us-east-1c, la misma AZ que la EC2) y `subnet-0af424a3e509c0374` (us-east-1a, para no depender
de una sola AZ). Tiene su propio security group, `renaser-admin-panel-lambda`
(`sg-08897be53ffe6faec`), **sin ninguna regla de entrada** — solo sale.

En el SG del backend se agregó **una** regla: TCP 8080 con origen el SG de la Lambda, no un
CIDR. La regla que ya existía para la IP del dueño no se tocó, y **el 8080 sigue cerrado a
internet**. La ventaja de referenciar el SG y no una IP es que no hay ninguna dirección que
mantener: si la Lambda cambia de ENI o de AZ, la regla sigue valiendo.

**El costo escondido de esta decisión, y cómo se evitó.** Una Lambda dentro de una VPC **no
tiene salida a internet** salvo que haya un NAT gateway (~32 USD/mes) o un endpoint de interfaz
(~7,30 USD/mes). Eso significa que la Lambda **no puede leer Parameter Store en caliente**. La
salida fue no necesitarlo: ver la decisión 2.

### 2. Cómo se autentica quien entra — Function URL `AWS_IAM` + dos barreras propias

**Se intentó primero `NONE` y no se pudo, y la razón importa:** esta cuenta tiene **Lambda Block
Public Access** en denegar por defecto para funciones nuevas, así que la URL respondía
`403 AccessDeniedException` aunque la política de recurso fuera la correcta. Está documentado en
`docs/BITACORA_ERRORES.md` E-130, con todo lo que se descartó antes de dar con la causa.

Había dos salidas: **desactivar esa protección** para esta función, o **usar `AWS_IAM`**. Se eligió
`AWS_IAM`, por dos motivos: es la que cumple más literalmente el pedido de que el panel no quede
abierto al mundo, y desactivar un bloqueo de acceso público es una decisión de seguridad de la
cuenta que corresponde al dueño del proyecto, no a quien despliega.

**Quedan tres capas:**

1. **Credenciales AWS de esta cuenta.** Sin una firma SigV4 válida, la URL no contesta nada — ni
   la página de ingreso. Ni existe una política de recurso pública: se retiró.
2. **La clave del panel.** Se pide antes que nada y, sin ella, el panel **ni siquiera llama al
   backend**. Vive en Parameter Store, `/renaser/prod/ADMIN_PANEL_CLAVE` (SecureString).

   > En la variable de entorno de la Lambda **no está la clave**, está su **verificador PBKDF2**
   > (SHA-256, 600 000 iteraciones, sal aleatoria). Un hash no es un secreto: se puede verificar
   > con él, no se puede volver a la clave. Así se cumple la regla de "ningún secreto en claro en
   > variables de entorno" *y* se evita el endpoint de interfaz de 7,30 USD/mes que haría falta
   > para leer Parameter Store desde adentro de la VPC.
3. **Tu propia cuenta de Renaser OS.** Correo y contraseña, contra `POST /api/v1/auth/login`. Si
   el rol no es `ADMIN` ni `ALCHEMIST`, no entra.

La tercera capa importa porque el backend **no limita los intentos de login**
(`AutenticacionService` defiende el *timing* pero no cuenta intentos). El panel frena por IP
(5 fallos → 15 minutos), aunque ese contador es por contenedor de Lambda y no es un límite duro:
las barreras reales son las credenciales AWS y la clave del panel.

**El compromiso asumido:** `AWS_IAM` deja al navegador sin poder entrar solo, porque no sabe
firmar SigV4. Lo resuelve `scripts/abrir-panel.py`, que corre en la máquina del dueño, escucha en
`127.0.0.1:8788`, firma cada request con el perfil `renaser` (las credenciales las maneja
botocore, el script no las lee ni las imprime) y reenvía. **Costo real del compromiso:** hay que
levantar ese ayudante antes de usar el panel, y el panel solo se abre desde una máquina con
credenciales de la cuenta — no desde un teléfono cualquiera.

### 3. Cómo se identifica el admin ante el backend — sesión real, nunca `X-Actor-Id`

Hoy `/api/v1/account-requests/**` está en `permitAll()` y el actor sale del header
`X-Actor-Id`, que lo escribe el cliente. Es un agujero conocido, documentado en
`SecurityConfig`, y **este panel no lo toca ni lo usa**.

El panel **no guarda ni acepta un UUID de admin**. Del login del backend obtiene una **sesión
opaca** (header `X-Auth-Token`, Spring Session sobre Redis) y esa sesión es la que viaja en cada
llamada. `ActorAutenticadoArgumentResolver` prefiere la sesión sobre el header, así que el actor
que ve el backend es el usuario real que se autenticó, no un UUID que alguien eligió.

Consecuencia concreta: **el navegador nunca ve ni manda un UUID**, y no hay ningún campo del
panel donde se pueda escribir uno. La prueba de humo lo verifica
(`la sesion viaja como X-Auth-Token, y X-Actor-Id nunca se manda`).

La sesión se guarda en una cookie `__Host-` con `HttpOnly`, `Secure` y `SameSite=Strict`, y hay
token CSRF de doble envío en cada formulario.

---

## Cómo se entra

Hacen falta cuatro cosas:

0. **El ayudante local corriendo:** `python admin-panel/scripts/abrir-panel.py`, y abrir
   `http://127.0.0.1:8788`. Sin él, la URL responde 403 (es `AWS_IAM`).
1. **La clave del panel:**
   ```bash
   MSYS_NO_PATHCONV=1 aws ssm get-parameter --name /renaser/prod/ADMIN_PANEL_CLAVE \
     --with-decryption --profile renaser --region us-east-1 \
     --query Parameter.Value --output text
   ```
2. **El correo** de una cuenta de Renaser OS con rol `ADMIN` o `ALCHEMIST`.
3. **Su contraseña.**

Dentro hay dos pantallas: `/solicitudes` (la bandeja) y `/diagnostico` (si el panel llega al
backend y cuánto tarda). Rechazar exige escribir un motivo — es lo que pide el backend.

Para cambiar la clave: se escribe la nueva en Parameter Store y se corre
`RECARGAR_CLAVE=1 bash scripts/desplegar.sh`.

---

## Cómo se despliega y cómo se actualiza

Todo desde Git Bash, con el perfil `renaser` explícito (las variables de entorno `AWS_*` de la
sesión pisan el perfil por defecto y ya provocaron escribir en otra cuenta).

```bash
# Abrir el panel (lo habitual del día a día):
python admin-panel/scripts/abrir-panel.py     # y abrir http://127.0.0.1:8788

# Una sola vez: crea SG, regla de ingress, rol, clave, función y Function URL.
# Es idempotente: si algo ya existe, lo reutiliza.
bash admin-panel/scripts/crear-infra.sh

# Cada vez que cambia src/index.mjs:
bash admin-panel/scripts/desplegar.sh

# Prueba de humo, sin AWS y sin el backend real (24 verificaciones):
node admin-panel/scripts/prueba-local.mjs
```

| Script | Qué hace |
|---|---|
| `scripts/crear-infra.sh` | Crea la infraestructura completa. Verifica primero que la CLI resolvió la cuenta 302277511407 y aborta si no |
| `scripts/desplegar.sh` | Solo actualiza el código. Con `RECARGAR_CLAVE=1` recalcula el verificador desde Parameter Store |
| `scripts/abrir-panel.py` | Ayudante local que firma SigV4 y deja abrir el panel en el navegador |
| `scripts/prueba-local.mjs` | Levanta un backend de mentira y corre el handler contra él |
| `scripts/empaquetar.mjs` | Arma el `.zip` (en esta máquina no hay binario `zip`) |
| `scripts/verificador.mjs` | Convierte la clave en su verificador PBKDF2 |

Logs: `/aws/lambda/renaser-admin-panel` en CloudWatch. **No se registra ni un correo, ni un
nombre, ni un token** — solo códigos HTTP y rutas.

**Mantenimiento:** la variable `BACKEND_BASE_URL` tiene la IP **privada** de la EC2
(`172.31.26.17`), que sobrevive a un stop/start de la instancia pero **no** a reemplazarla por
una nueva. Si algún día se recrea la EC2, hay que actualizar esa variable — `/diagnostico` es lo
que lo delata: deja de responder. La IP pública no se usa en ningún lado.

---

## Qué se creó en AWS

| Recurso | Nombre / id | Costo mensual |
|---|---|---|
| Security group | `renaser-admin-panel-lambda` — `sg-08897be53ffe6faec` | 0 USD |
| Regla de ingress | 8080 en `sg-0ba472486a99c47a6`, origen el SG de arriba | 0 USD |
| Rol IAM | `renaser-admin-panel-lambda` + `AWSLambdaVPCAccessExecutionRole` | 0 USD |
| Función Lambda | `renaser-admin-panel` | ~0 USD (capa gratuita) |
| Function URL | `AuthType AWS_IAM`, sin política de recurso pública | 0 USD |
| Parámetro SSM | `/renaser/prod/ADMIN_PANEL_CLAVE` (SecureString, nivel estándar) | 0 USD |
| Log group | `/aws/lambda/renaser-admin-panel` | ~0 USD |

**No se creó** ningún NAT gateway (~32 USD/mes), ningún endpoint de interfaz (~7,30 USD/mes),
ningún balanceador (~16 USD/mes), ningún dominio ni certificado.

El rol de la Lambda **no tiene permisos de datos**: ni SSM, ni S3, ni base de datos. Solo lo que
hace falta para crear su ENI en la VPC y escribir logs.

---

## Lo que este panel NO arregla, y hay que saberlo

- **`/api/v1/account-requests/**` sigue en `permitAll()`.** Cualquiera que llegue al 8080 y sepa
  el UUID de un admin puede seguir aprobando solicitudes con `X-Actor-Id`, sin pasar por acá.
  Este panel no usa ese camino, pero tampoco lo cierra: cerrarlo es la fase 4 de
  `docs/MODULO_AUTH.md`.
- **El backend no limita los intentos de login.** Está descrito arriba; la clave del panel lo
  compensa desde afuera, no desde el backend.
- **El backend sigue sin TLS.** El tramo Lambda → EC2 es HTTP plano, aunque sea privado dentro
  de la VPC.

---

## Si algún día se prefiere la URL pública

Es una decisión del dueño del proyecto, no del despliegue, porque implica **desactivar Lambda
Block Public Access** para esta función (ver `docs/BITACORA_ERRORES.md` E-130). Si se toma:

1. Actualizar la AWS CLI — la instalada (2.34.47) todavía no trae las operaciones de Block Public
   Access, así que hoy el ajuste **no se puede tocar desde esta máquina**.
2. Permitir la política pública y el acceso público **solo para `renaser-admin-panel`**, nunca a
   nivel cuenta.
3. `aws lambda update-function-url-config --function-name renaser-admin-panel --auth-type NONE`
   y volver a poner el permiso:
   ```bash
   aws lambda add-permission \
     --function-name renaser-admin-panel \
     --statement-id PermitirFunctionUrl \
     --action lambda:InvokeFunctionUrl \
     --principal '*' --function-url-auth-type NONE
   ```
4. Dejar de usar `abrir-panel.py`: la URL se abre directo.

**Lo que se gana:** entrar desde cualquier dispositivo, sin ayudante local. **Lo que se pierde:**
la capa de credenciales AWS. El panel quedaría defendido solo por la clave del panel y por el
login de Renaser — que sigue siendo bastante, pero es una capa menos sobre algo que aprueba
cuentas. Por eso no se hizo por cuenta propia.

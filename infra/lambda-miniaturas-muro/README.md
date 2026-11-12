# Lambda de miniaturas del Muro (spec 049, D-339)

Al llegar una foto a `s3://renaser90dias-prod/muro/fotos/<autor>/<id>`, arma `muro/miniaturas/<autor>/<id>.webp` (480 px). Al
borrarse la foto, borra la miniatura. El backend firma la miniatura por convención (`MiniaturaDeFoto`): no hay aviso entre los dos.

## Probar y construir en local

```bash
cd infra/lambda-miniaturas-muro
npm ci && npm test             # pruebas (node --test), con las dependencias de package-lock.json
bash construir.sh              # lambda.zip para Node 22 arm64, instalado desde package-lock.json
```

Las pruebas van **antes** de construir: usan los binarios de `sharp` de tu máquina (x64) y `construir.sh` arma el zip aparte,
en `build/`, con los de linux-arm64. `@aws-sdk/client-s3` está como `devDependency` (las pruebas lo importan; en AWS ya viene en
el runtime de Lambda, así que `--omit=dev` lo deja fuera del zip). Si cambias `package.json`, actualiza el lock con
`npm install --package-lock-only` (una prueba de `scripts/lambda-miniaturas-ci.test.mjs` falla si quedan distintos).

## Despliegue automático (D-351)

Cada push a `master` que toque esta carpeta (menos este README), o **Actions → Lambda de miniaturas → Run workflow** desde `master`,
corre `.github/workflows/lambda-miniaturas.yml`:

1. `npm ci` y `node --test` (Node 22, x64). Si fallan, no se construye ni se sube nada.
2. `construir.sh`: `lambda.zip` con `sharp` para linux-arm64 (y se niega a seguir si el binario no es el de arm64).
3. OIDC con el mismo rol del CD (`vars.AWS_ROLE_ARN`). Si la variable falta, o la corrida no es de `master`, avisa y termina sin desplegar.
4. `aws lambda update-function-code --function-name renaser-miniaturas-muro` (región `us-east-1`) y `aws lambda wait function-updated-v2`.
5. Humo, sin tocar datos: la función está `Active`, su `CodeSha256` es el del zip que se subió, sigue en `nodejs22.x` / `arm64`, y una
   invocación con una clave fuera de `muro/fotos/` (que la Lambda ignora sin llamar a S3) devuelve `{"procesadas":1}` sin
   `FunctionError`. Esa invocación carga `sharp` en arm64 de verdad: es lo que se rompe cuando una subida de dependencias trae el binario equivocado.

Un solo despliegue a la vez (los nuevos esperan; ninguno se cancela a medias). El workflow solo cambia el **código**: el alta de la función,
de su rol y del aviso del bucket sigue siendo `desplegar.sh` (una vez, a mano).

### Una vez, antes del primer despliegue: el permiso del rol del CI

El rol `renaser-github-actions` no tenía permisos de Lambda. Lo aplica el dueño, con el perfil `prod`; es un simulacro mientras no se pase `--aplicar`:

```bash
export RENASER_CUENTA_AWS=<id de 12 dígitos de la cuenta de producción>   # el script lo compara con la cuenta del perfil
bash infra/lambda-miniaturas-muro/permiso-del-ci.sh                       # muestra la política y no cambia nada
bash infra/lambda-miniaturas-muro/permiso-del-ci.sh --aplicar             # la pone
```

Pone la política en línea `actualizar-lambda-miniaturas` (`permiso-del-ci.plantilla.json`; la cuenta no está escrita en el repositorio, el
script la toma de AWS): `lambda:UpdateFunctionCode`, `lambda:GetFunction` (el `wait` la consulta), `lambda:GetFunctionConfiguration` y
`lambda:InvokeFunction` (el humo), **solo** sobre `arn:aws:lambda:us-east-1:<cuenta>:function:renaser-miniaturas-muro`. Nada de
`UpdateFunctionConfiguration`, `iam:PassRole` ni S3: el rol no puede cambiar el rol de la función, ni su disparador, ni leer fotos.
Las otras políticas del rol no se tocan. Es idempotente (si ya está igual, no hace nada; si difiere, guarda la anterior en
`~/.renaser/respaldos`, fuera del repositorio).

**Vuelta atrás:** `bash infra/lambda-miniaturas-muro/permiso-del-ci.sh --revertir --aplicar` borra solo esa política en línea.

Sin el permiso, el paso «Subir el código a la Lambda» falla con `AccessDenied` y el mensaje dice qué correr. Tras aplicarlo, se
vuelve a lanzar desde **Run workflow** (los pasos anteriores a AWS ya habrán pasado).

### Cuando sale rojo

- **Pruebas o construcción:** la Lambda de producción sigue con el código de antes. Se arregla y se vuelve a empujar.
- **`AccessDenied` al subir:** falta el permiso de arriba.
- **Humo rojo (estado, código o invocación):** la versión nueva **ya está** en AWS (la función no usa alias ni versiones publicadas, y el
  aviso del bucket apunta a `$LATEST`). Para volver: `git revert <commit>` en `master`; ese push despliega el código anterior. A mano:
  `git checkout <commit bueno> -- infra/lambda-miniaturas-muro && bash construir.sh && aws lambda update-function-code --function-name renaser-miniaturas-muro --zip-file fileb://lambda.zip --profile prod --region us-east-1`.
- Los logs de la función: CloudWatch `/aws/lambda/renaser-miniaturas-muro`.

## A mano (el alta, o sin CI)

```bash
bash construir.sh
bash desplegar.sh              # crea o actualiza la función, su rol y el aviso del bucket (AWS prod, lo corre el dueño)
bash rellenar.sh               # arma la miniatura de las fotos que ya existían
```

Logs: CloudWatch `/aws/lambda/renaser-miniaturas-muro`.

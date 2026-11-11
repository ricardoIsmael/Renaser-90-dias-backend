# Lambda de miniaturas del Muro (spec 049, D-339)

Al llegar una foto a `s3://renaser90dias-prod/muro/fotos/<autor>/<id>`, arma `muro/miniaturas/<autor>/<id>.webp` (480 px). Al
borrarse la foto, borra la miniatura. El backend firma la miniatura por convención (`MiniaturaDeFoto`): no hay aviso entre los dos.

```bash
cd infra/lambda-miniaturas-muro
npm install && npm test        # pruebas (node --test)
bash construir.sh              # lambda.zip para Node 22 arm64
bash desplegar.sh              # crea o actualiza la función, su rol y el aviso del bucket (AWS prod, lo corre el dueño)
bash rellenar.sh               # arma la miniatura de las fotos que ya existían
```
Logs: CloudWatch `/aws/lambda/renaser-miniaturas-muro`.

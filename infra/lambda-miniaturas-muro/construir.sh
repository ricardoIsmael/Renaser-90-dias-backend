#!/usr/bin/env bash
# Arma lambda.zip para Node.js 22 en arm64 (Graviton): instala sharp para linux-arm64 aunque se corra en una laptop x86.
# El SDK de AWS (@aws-sdk/client-s3) ya viene en el runtime de Lambda: no se empaqueta (en package.json va como devDependency,
# solo para las pruebas, y `--omit=dev` lo deja fuera).
# Se instala con `npm ci` desde package-lock.json: el zip lleva exactamente las versiones que probaron las pruebas.
# Lo corre el dueño a mano o el workflow lambda-miniaturas.yml (que corre las pruebas ANTES, con las dependencias de esta máquina).
set -euo pipefail
cd "$(dirname "$0")"
rm -rf build lambda.zip && mkdir build
cp index.mjs miniatura.mjs package.json package-lock.json build/
(cd build && npm ci --omit=dev --no-audit --no-fund --os=linux --cpu=arm64 --libc=glibc >/dev/null)
# Lambda corre sobre glibc (Amazon Linux): con package-lock.json npm 10 no filtra por libc y trae también los binarios de musl
# (Alpine, ~18 MB), que nunca se cargan. Se quitan para que el zip pese lo mismo que sin lock (~12 MB).
rm -rf build/node_modules/@img/*linuxmusl*
(cd build && zip -qr ../lambda.zip .)
rm -rf build
# Guarda: si el binario de sharp no es el de arm64, la función no arrancaría en AWS (Runtime.ImportModuleError). Se ve acá, no en producción.
# (La lista va a una variable: `unzip | grep -q` con pipefail da 141 cuando grep corta la lectura.)
CONTENIDO=$(unzip -Z1 lambda.zip)
grep -q '^node_modules/@img/sharp-linux-arm64/' <<<"$CONTENIDO" || { echo "ERROR: lambda.zip no trae @img/sharp-linux-arm64" >&2; exit 1; }
if grep -q '^node_modules/@img/sharp-linux-x64/' <<<"$CONTENIDO"; then echo "ERROR: lambda.zip trae el binario de x64 de sharp" >&2; exit 1; fi
echo "lambda.zip listo ($(du -h lambda.zip | cut -f1))"

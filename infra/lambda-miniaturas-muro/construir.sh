#!/usr/bin/env bash
# Arma lambda.zip para Node.js 22 en arm64 (Graviton): instala sharp para linux-arm64 aunque se corra en una laptop x86.
# El SDK de AWS (@aws-sdk/client-s3) ya viene en el runtime de Lambda: no se empaqueta.
set -euo pipefail
cd "$(dirname "$0")"
rm -rf build lambda.zip && mkdir build
cp index.mjs miniatura.mjs package.json build/
(cd build && npm install --omit=dev --no-audit --no-fund --os=linux --cpu=arm64 --libc=glibc >/dev/null)
(cd build && zip -qr ../lambda.zip .)
rm -rf build
echo "lambda.zip listo ($(du -h lambda.zip | cut -f1))"

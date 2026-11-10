import assert from 'node:assert/strict';
import { test } from 'node:test';
import sharp from 'sharp';

import { achicar, ANCHO_MINIATURA_PX, rutaDeLaMiniatura } from './miniatura.mjs';
import { handler } from './index.mjs';

test('la ruta es la misma que firma el backend (MiniaturaDeFoto.rutaDe)', () => {
  assert.equal(rutaDeLaMiniatura('muro/fotos/u-1/f-2'), 'muro/miniaturas/u-1/f-2.webp');
  assert.equal(rutaDeLaMiniatura('muro/videos/u-1/v-2'), null);
  assert.equal(rutaDeLaMiniatura('muro/miniaturas/u-1/f-2.webp'), null); // no se dispara sobre su propia salida
  assert.equal(rutaDeLaMiniatura('muro/fotos/'), null);
});

const foto = (ancho, alto) => sharp({ create: { width: ancho, height: alto, channels: 3, background: { r: 200, g: 160, b: 60 } } }).jpeg().toBuffer();

test('achica a 480 px de ancho en WebP, mucho más liviana', async () => {
  const grande = await foto(1440, 1800);
  const chica = await achicar(sharp, grande);
  const meta = await sharp(chica).metadata();
  assert.equal(meta.format, 'webp');
  assert.equal(meta.width, ANCHO_MINIATURA_PX);
  assert.equal(meta.height, 600);
});

test('una foto más chica que 480 px no se agranda', async () => {
  const meta = await sharp(await achicar(sharp, await foto(300, 200))).metadata();
  assert.equal(meta.width, 300);
});

function s3DeMentira(bytes) {
  const pedidos = [];
  return {
    pedidos,
    async send(c) {
      pedidos.push({ tipo: c.constructor.name, ...c.input });
      if (c.constructor.name === 'GetObjectCommand') return { Body: { transformToByteArray: async () => new Uint8Array(bytes) } };
      return {};
    },
  };
}

test('foto nueva: baja la original y sube la miniatura con su nombre fijo', async () => {
  const s3 = s3DeMentira(await foto(1440, 1440));
  await handler({ Records: [{ eventName: 'ObjectCreated:Put', s3: { bucket: { name: 'b' }, object: { key: 'muro/fotos/u-1/f-2' } } }] }, {}, s3);
  assert.deepEqual(s3.pedidos.map(p => p.tipo), ['GetObjectCommand', 'PutObjectCommand']);
  assert.equal(s3.pedidos[1].Key, 'muro/miniaturas/u-1/f-2.webp');
  assert.equal(s3.pedidos[1].ContentType, 'image/webp');
});

test('foto borrada: borra su miniatura; una clave ajena se ignora', async () => {
  const s3 = s3DeMentira(Buffer.alloc(0));
  await handler({ Records: [
    { eventName: 'ObjectRemoved:Delete', s3: { bucket: { name: 'b' }, object: { key: 'muro/fotos/u-1/f-2' } } },
    { eventName: 'ObjectCreated:Put', s3: { bucket: { name: 'b' }, object: { key: 'muro/videos/u-1/v' } } },
  ] }, {}, s3);
  assert.deepEqual(s3.pedidos.map(p => `${p.tipo} ${p.Key}`), ['DeleteObjectCommand muro/miniaturas/u-1/f-2.webp']);
});

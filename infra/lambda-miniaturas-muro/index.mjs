import { DeleteObjectCommand, GetObjectCommand, PutObjectCommand, S3Client } from '@aws-sdk/client-s3';
import sharp from 'sharp';

import { achicar, rutaDeLaMiniatura } from './miniatura.mjs';

/**
 * Lambda `renaser-miniaturas-muro` (spec 049, D-339). La dispara S3 (prefijo `muro/fotos/`):
 * - ObjectCreated: baja la foto, arma la miniatura WebP de 480 px y la sube a `muro/miniaturas/…webp`.
 * - ObjectRemoved: borra su miniatura (que no quede huérfana cuando se borra la publicación).
 * El backend no se entera: firma la miniatura por convención y la app cae a la foto completa si todavía no existe.
 * Un error se registra y se lanza (S3 reintenta la invocación asíncrona dos veces).
 */
const s3 = new S3Client({});

/** Lo que hace la Lambda, con el cliente de S3 inyectable (las pruebas pasan uno de mentira). */
export async function procesar(evento, cliente) {
  const registros = evento?.Records ?? [];
  for (const r of registros) {
    const bucket = r.s3?.bucket?.name;
    const clave = decodeURIComponent((r.s3?.object?.key ?? '').replace(/\+/g, ' '));
    const destino = rutaDeLaMiniatura(clave);
    if (!bucket || !destino) continue;
    if (String(r.eventName ?? '').startsWith('ObjectRemoved')) {
      await cliente.send(new DeleteObjectCommand({ Bucket: bucket, Key: destino }));
      console.log(JSON.stringify({ accion: 'borrada', clave, destino }));
      continue;
    }
    const original = await cliente.send(new GetObjectCommand({ Bucket: bucket, Key: clave }));
    const bytes = Buffer.from(await original.Body.transformToByteArray());
    const chica = await achicar(sharp, bytes);
    await cliente.send(new PutObjectCommand({
      Bucket: bucket, Key: destino, Body: chica, ContentType: 'image/webp', CacheControl: 'private, max-age=31536000, immutable',
    }));
    console.log(JSON.stringify({ accion: 'creada', clave, destino, antes: bytes.length, despues: chica.length }));
  }
  return { procesadas: registros.length };
}

/**
 * El punto de entrada de AWS. Lambda llama `handler(evento, contexto, callback)`: el tercer argumento es una función, así que el
 * cliente de S3 NO puede ir ahí (en producción llegaba el callback y todo fallaba con «cliente.send is not a function»).
 */
export const handler = evento => procesar(evento, s3);

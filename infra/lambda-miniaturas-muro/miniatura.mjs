/**
 * Lo puro de la Lambda (spec 049, D-339): de qué foto sale qué miniatura, y cómo se achica. Sin AWS: lo prueban `node --test`.
 * La ruta tiene que coincidir con `MiniaturaDeFoto.rutaDe` del backend (Java): muro/fotos/<autor>/<id> → muro/miniaturas/<autor>/<id>.webp
 */
export const PREFIJO_FOTOS = 'muro/fotos/';
export const PREFIJO_MINIATURAS = 'muro/miniaturas/';
/** 480 px de ancho: la fila del Muro la dibuja a 66 px (≈ 200 px reales en un teléfono de 3x) con margen para pantallas grandes. */
export const ANCHO_MINIATURA_PX = 480;
export const CALIDAD_WEBP = 70;

export function rutaDeLaMiniatura(rutaDeLaFoto) {
  if (typeof rutaDeLaFoto !== 'string' || !rutaDeLaFoto.startsWith(PREFIJO_FOTOS) || rutaDeLaFoto.length === PREFIJO_FOTOS.length) return null;
  return `${PREFIJO_MINIATURAS}${rutaDeLaFoto.slice(PREFIJO_FOTOS.length)}.webp`;
}

/** Achica sin agrandar, respeta la orientación EXIF y quita los metadatos (la ubicación GPS de la foto no viaja). */
export async function achicar(sharp, bytes) {
  return sharp(bytes, { failOn: 'none' })
    .rotate()
    .resize({ width: ANCHO_MINIATURA_PX, withoutEnlargement: true })
    .webp({ quality: CALIDAD_WEBP })
    .toBuffer();
}

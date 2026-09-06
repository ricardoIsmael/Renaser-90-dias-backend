/**
 * Arma el .zip que se sube a Lambda, sin depender de `zip` ni de `Compress-Archive`.
 *
 * Existe porque en esta maquina (Git Bash sobre Windows) no hay binario `zip`, y
 * `Compress-Archive` de PowerShell escribe las rutas con `\` en los zips anidados, que es
 * justo lo que Lambda no sabe leer. Node ya trae `zlib`, asi que armar el contenedor a mano
 * sale mas barato que agregar una dependencia.
 *
 * Uso: node scripts/empaquetar.mjs <destino.zip> <archivo> [<archivo>...]
 */

import { deflateRawSync } from 'node:zlib';
import { readFileSync, writeFileSync } from 'node:fs';
import { basename } from 'node:path';

const [destino, ...archivos] = process.argv.slice(2);
if (!destino || archivos.length === 0) {
    console.error('uso: node scripts/empaquetar.mjs <destino.zip> <archivo>...');
    process.exit(1);
}

const TABLA_CRC = (() => {
    const tabla = new Uint32Array(256);
    for (let i = 0; i < 256; i += 1) {
        let valor = i;
        for (let bit = 0; bit < 8; bit += 1) {
            valor = valor & 1 ? 0xedb88320 ^ (valor >>> 1) : valor >>> 1;
        }
        tabla[i] = valor >>> 0;
    }
    return tabla;
})();

function crc32(contenido) {
    let acumulado = 0xffffffff;
    for (const byte of contenido) {
        acumulado = TABLA_CRC[(acumulado ^ byte) & 0xff] ^ (acumulado >>> 8);
    }
    return (acumulado ^ 0xffffffff) >>> 0;
}

const locales = [];
const centrales = [];
let desplazamiento = 0;

for (const ruta of archivos) {
    const nombre = Buffer.from(basename(ruta), 'utf8');
    const crudo = readFileSync(ruta);
    const comprimido = deflateRawSync(crudo, { level: 9 });
    const suma = crc32(crudo);

    const cabeceraLocal = Buffer.alloc(30);
    cabeceraLocal.writeUInt32LE(0x04034b50, 0);   // firma
    cabeceraLocal.writeUInt16LE(20, 4);            // version necesaria
    cabeceraLocal.writeUInt16LE(0, 6);             // banderas
    cabeceraLocal.writeUInt16LE(8, 8);             // metodo: deflate
    cabeceraLocal.writeUInt16LE(0, 10);            // hora (fija: zip reproducible)
    cabeceraLocal.writeUInt16LE(0x21, 12);         // fecha (1980-01-01)
    cabeceraLocal.writeUInt32LE(suma, 14);
    cabeceraLocal.writeUInt32LE(comprimido.length, 18);
    cabeceraLocal.writeUInt32LE(crudo.length, 22);
    cabeceraLocal.writeUInt16LE(nombre.length, 26);
    cabeceraLocal.writeUInt16LE(0, 28);
    locales.push(cabeceraLocal, nombre, comprimido);

    const cabeceraCentral = Buffer.alloc(46);
    cabeceraCentral.writeUInt32LE(0x02014b50, 0);
    cabeceraCentral.writeUInt16LE(0x031e, 4);      // creado en unix, zip 3.0
    cabeceraCentral.writeUInt16LE(20, 6);
    cabeceraCentral.writeUInt16LE(0, 8);
    cabeceraCentral.writeUInt16LE(8, 10);
    cabeceraCentral.writeUInt16LE(0, 12);
    cabeceraCentral.writeUInt16LE(0x21, 14);
    cabeceraCentral.writeUInt32LE(suma, 16);
    cabeceraCentral.writeUInt32LE(comprimido.length, 20);
    cabeceraCentral.writeUInt32LE(crudo.length, 24);
    cabeceraCentral.writeUInt16LE(nombre.length, 28);
    // El `>>> 0` no es decorativo: en JS los operadores de bits trabajan sobre enteros con
    // signo de 32 bits, y `0o100644 << 16` da -2119958528. Sin reinterpretarlo como sin signo,
    // `writeUInt32LE` explota con ERR_OUT_OF_RANGE.
    cabeceraCentral.writeUInt32LE((0o100644 << 16) >>> 0, 38); // permisos: -rw-r--r--
    cabeceraCentral.writeUInt32LE(desplazamiento, 42);
    centrales.push(cabeceraCentral, nombre);

    desplazamiento += cabeceraLocal.length + nombre.length + comprimido.length;
}

const directorio = Buffer.concat(centrales);
const fin = Buffer.alloc(22);
fin.writeUInt32LE(0x06054b50, 0);
fin.writeUInt16LE(archivos.length, 8);
fin.writeUInt16LE(archivos.length, 10);
fin.writeUInt32LE(directorio.length, 12);
fin.writeUInt32LE(desplazamiento, 16);

writeFileSync(destino, Buffer.concat([...locales, directorio, fin]));
console.log(`${destino} — ${archivos.length} archivo(s)`);

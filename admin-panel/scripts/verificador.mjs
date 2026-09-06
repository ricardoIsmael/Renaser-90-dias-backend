/**
 * Convierte la clave del panel en un VERIFICADOR PBKDF2, que es lo unico que se guarda en la
 * variable de entorno de la Lambda.
 *
 * La clave en claro vive solo en Parameter Store (`/renaser/prod/ADMIN_PANEL_CLAVE`). La
 * Lambda corre dentro de la VPC sin salida a internet, asi que no puede leer Parameter Store
 * en caliente sin un endpoint de interfaz de ~7 USD al mes; guardar un verificador en vez de
 * la clave resuelve lo mismo sin ese gasto y sin poner el secreto en claro en ningun lado.
 *
 * Uso: la clave llega por stdin (nunca por argumento — los argumentos quedan en el historial
 * del shell y en la lista de procesos).
 *   echo -n "$CLAVE" | node scripts/verificador.mjs
 */

import { pbkdf2Sync, randomBytes } from 'node:crypto';

const ITERACIONES = 600_000;   // recomendacion OWASP 2023 para PBKDF2-HMAC-SHA256
const LARGO_HASH = 32;

const trozos = [];
for await (const trozo of process.stdin) {
    trozos.push(trozo);
}
const clave = Buffer.concat(trozos).toString('utf8').replace(/\r?\n$/, '');

if (!clave) {
    console.error('No llego ninguna clave por stdin.');
    process.exit(1);
}

const sal = randomBytes(16);
const hash = pbkdf2Sync(clave, sal, ITERACIONES, LARGO_HASH, 'sha256');
process.stdout.write(`pbkdf2$sha256$${ITERACIONES}$${sal.toString('base64')}$${hash.toString('base64')}`);

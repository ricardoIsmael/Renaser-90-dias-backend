#!/usr/bin/env node
// Compara docs/db/ER_BD_NUEVA.drawio contra el esquema real de src/main/resources/db/migration.
// Existe porque el ER se desfasó en silencio tras V2, V3 y V8: nadie se entera hasta que
// alguien lee el diagrama y programa contra una tabla que ya no es la que está en la base.
//
//   node docs/db/verificar-er-vs-sql.mjs
//
// Sale con código 1 si hay divergencia. Pensado para correrse al agregar una migración.
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const MIG = path.join(ROOT, 'src/main/resources/db/migration');
const ER = path.join(ROOT, 'docs/db/ER_BD_NUEVA.drawio');

function leerEsquemaSql() {
  const sql = fs.readdirSync(MIG).filter((f) => f.endsWith('.sql')).sort()
    .map((f) => fs.readFileSync(path.join(MIG, f), 'utf8')).join('\n');

  const tablas = {};
  // El cierre NO es siempre `);`: V1 usa `) WITH (fillfactor = 70);` en las tablas calientes.
  const crear = /CREATE TABLE (?:IF NOT EXISTS )?(?:public\.)?([a-z_0-9]+)\s*\(([\s\S]*?)\n\)[^;]*;/gi;
  for (const m of sql.matchAll(crear)) {
    const columnas = [];
    for (const cruda of m[2].split('\n')) {
      const linea = cruda.trim();
      if (!linea || linea.startsWith('--')) continue;
      if (/^(PRIMARY KEY|FOREIGN KEY|UNIQUE|CONSTRAINT|CHECK|EXCLUDE|REFERENCES|ON DELETE|ON UPDATE|\))/i.test(linea)) continue;
      const col = linea.match(/^"?([a-z_0-9]+)"?\s+[a-z]/i);
      if (col) columnas.push(col[1].toLowerCase());
    }
    tablas[m[1].toLowerCase()] = [...new Set(columnas)];
  }
  for (const m of sql.matchAll(/ALTER TABLE (?:IF EXISTS )?(?:public\.)?([a-z_0-9]+)\s+ADD COLUMN (?:IF NOT EXISTS )?"?([a-z_0-9]+)"?/gi)) {
    const t = tablas[m[1].toLowerCase()];
    if (t && !t.includes(m[2].toLowerCase())) t.push(m[2].toLowerCase());
  }
  for (const m of sql.matchAll(/ALTER TABLE (?:IF EXISTS )?(?:public\.)?([a-z_0-9]+)\s+DROP COLUMN (?:IF EXISTS )?"?([a-z_0-9]+)"?/gi)) {
    const clave = m[1].toLowerCase();
    if (tablas[clave]) tablas[clave] = tablas[clave].filter((c) => c !== m[2].toLowerCase());
  }
  // RENAME COLUMN: la columna no se agrega ni se quita, cambia de nombre en su lugar. Sin esto
  // el script reporta la vieja como faltante y la nueva como sobrante -- dos falsos positivos
  // por cada rename (paso con V11, supabase_user_id -> usuario_id).
  for (const m of sql.matchAll(/ALTER TABLE (?:IF EXISTS )?(?:public\.)?([a-z_0-9]+)\s+RENAME COLUMN\s+"?([a-z_0-9]+)"?\s+TO\s+"?([a-z_0-9]+)"?/gi)) {
    const t = tablas[m[1].toLowerCase()];
    if (!t) continue;
    const i = t.indexOf(m[2].toLowerCase());
    if (i >= 0) t[i] = m[3].toLowerCase();
  }
  // RENAME TO: la tabla entera cambia de nombre.
  for (const m of sql.matchAll(/ALTER TABLE (?:IF EXISTS )?(?:public\.)?([a-z_0-9]+)\s+RENAME TO\s+"?([a-z_0-9]+)"?/gi)) {
    const viejo = m[1].toLowerCase();
    if (!tablas[viejo]) continue;
    tablas[m[2].toLowerCase()] = tablas[viejo];
    delete tablas[viejo];
  }
  return tablas;
}

function leerDiagrama() {
  const xml = fs.readFileSync(ER, 'utf8');
  const celdas = [...xml.matchAll(/<mxCell\s+id="([^"]*)"\s+value="([^"]*)"([^>]*?)(?:\/>|>)/g)];
  const tablas = {};
  const idATabla = {};
  for (const [, id, valor, resto] of celdas) {
    if (!/swimlane/.test(resto) || !/childLayout=stackLayout/.test(resto)) continue;
    const nombre = valor.replace(/&nbsp;/g, ' ').replace(/<[^>]+>/g, '')
      .replace(/[^\x20-\x7E]/g, '').trim().toLowerCase();
    tablas[nombre] = [];
    idATabla[id] = nombre;
  }
  for (const [, , valor, resto] of celdas) {
    const padre = resto.match(/parent="([^"]+)"/);
    if (!padre) continue;
    const tabla = idATabla[padre[1]];
    if (!tabla) continue;
    let v = valor.replace(/&nbsp;/g, ' ').replace(/<[^>]+>/g, '').replace(/&amp;/g, '&').trim();
    if (!v || /^[-\u2500_]+$/.test(v)) continue;
    v = v.replace(/^(?:PK|FK|UQ)(?:,(?:PK|FK|UQ))*\s+/i, '');  // el ER marca las PK compuestas como "PK,FK"
    const i = v.indexOf(':');
    if (i < 0) continue;
    const col = v.slice(0, i).trim().toLowerCase();
    if (/^[a-z_0-9]+$/.test(col)) tablas[tabla].push(col);
  }
  return tablas;
}

const sql = leerEsquemaSql();
const er = leerDiagrama();
const problemas = [];

for (const t of Object.keys(sql).sort()) {
  if (!(t in er)) problemas.push(`tabla "${t}" existe en las migraciones y falta en el ER`);
}
for (const t of Object.keys(er).sort()) {
  if (t.startsWith('view ')) continue; // las vistas se dibujan a mano, sin CREATE TABLE
  if (!(t in sql)) problemas.push(`tabla "${t}" está dibujada en el ER y no existe en las migraciones`);
}
for (const t of Object.keys(er).sort()) {
  if (!sql[t]) continue;
  const faltan = sql[t].filter((c) => !er[t].includes(c));
  const sobran = er[t].filter((c) => !sql[t].includes(c));
  if (faltan.length) problemas.push(`${t}: columnas en la base que faltan en el ER -> ${faltan.join(', ')}`);
  if (sobran.length) problemas.push(`${t}: columnas dibujadas en el ER que no existen en la base -> ${sobran.join(', ')}`);
}

console.log(`Migraciones: ${Object.keys(sql).length} tablas | ER: ${Object.keys(er).length} entidades dibujadas`);
if (problemas.length === 0) {
  console.log('El ER coincide con el esquema real.');
  process.exit(0);
}
console.log(`\n${problemas.length} divergencia(s):`);
for (const p of problemas) console.log(' -', p);
process.exit(1);

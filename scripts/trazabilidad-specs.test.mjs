/**
 * Pruebas de `trazabilidad-specs.mjs`. Sin dependencias: `node --test scripts/trazabilidad-specs.test.mjs`.
 */
import assert from 'node:assert/strict';
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { after, describe, it } from 'node:test';
import { buscarPruebas, evaluar, leerMarcas, leerRequisitos, main, tablaEnMarkdown } from './trazabilidad-specs.mjs';

const raices = [];
function carpetaTemporal() {
  const dir = mkdtempSync(join(tmpdir(), 'trazabilidad-'));
  raices.push(dir);
  return dir;
}
after(() => raices.forEach((d) => rmSync(d, { recursive: true, force: true })));

function escribir(raiz, ruta, texto) {
  const archivo = join(raiz, ruta);
  mkdirSync(join(archivo, '..'), { recursive: true });
  writeFileSync(archivo, texto);
}

const SPEC = `# Spec 900

- **FR-001**: La app MUST mostrar los pasos.
- **FR-002**: El servidor MUST guardar
  el origen del dato en dos renglones.
- **FR-003**: Que el dueño apruebe el texto. [sin prueba automática: lo aprueba una persona]

### SC-001 Cierra el día

| SC-002 | Tarda menos de un segundo |

Esto solo menciona FR-001 y SC-009 en un párrafo y no los define.
`;

describe('leerRequisitos', () => {
  it('lee viñetas, encabezados y tablas, y no las menciones', () => {
    const { requisitos, repetidos } = leerRequisitos(SPEC);
    assert.deepEqual(requisitos.map((r) => r.id), ['FR-001', 'FR-002', 'FR-003', 'SC-001', 'SC-002']);
    assert.deepEqual(repetidos, []);
  });

  it('junta los renglones de continuación en el resumen', () => {
    const fr2 = leerRequisitos(SPEC).requisitos.find((r) => r.id === 'FR-002');
    assert.match(fr2.resumen, /origen del dato/);
  });

  it('marca como exento el requisito que dice por qué no tiene prueba automática', () => {
    const { requisitos } = leerRequisitos(SPEC);
    assert.equal(requisitos.find((r) => r.id === 'FR-003').exento, 'lo aprueba una persona');
    assert.equal(requisitos.find((r) => r.id === 'FR-001').exento, null);
  });

  it('avisa del requisito definido dos veces', () => {
    const { repetidos } = leerRequisitos('- **FR-001**: uno\n- **FR-001**: otra vez\n');
    assert.deepEqual(repetidos, ['FR-001']);
  });
});

describe('leerMarcas', () => {
  it('lee una marca y varias en la misma línea', () => {
    const marcas = leerMarcas('// spec 037 FR-005\n// spec 037 FR-006, SC-002 y FR-007\n# Spec 036 SC-001');
    assert.deepEqual(
      marcas.map((m) => `${m.spec} ${m.req}@${m.linea}`),
      ['037 FR-005@1', '037 FR-006@2', '037 SC-002@2', '037 FR-007@2', '036 SC-001@3'],
    );
  });

  it('no toma como marca un texto sin número de spec', () => {
    assert.deepEqual(leerMarcas('// cubre FR-001\n// spec FR-001'), []);
  });
});

function armar({ pruebasDelBackend = {}, pruebasDeLaApp = {}, specs = { '900-ejemplo': SPEC } } = {}) {
  const backend = carpetaTemporal();
  const app = carpetaTemporal();
  for (const [nombre, texto] of Object.entries(specs)) escribir(backend, `specs/${nombre}/spec.md`, texto);
  for (const [ruta, texto] of Object.entries(pruebasDelBackend)) escribir(backend, ruta, texto);
  for (const [ruta, texto] of Object.entries(pruebasDeLaApp)) escribir(app, ruta, texto);
  return { backend, app };
}

describe('evaluar', () => {
  it('cubre con pruebas del backend y de la app, exenta y señala lo que falta', () => {
    const { backend, app } = armar({
      pruebasDelBackend: { 'src/test/java/PasosTest.java': '// spec 900 FR-001\nclass PasosTest {}' },
      pruebasDeLaApp: { '.maestro/pasos.yaml': '# spec 900 SC-001\n- launchApp' },
    });
    const { specs, configuracion } = evaluar({ backend, entradas: ['900'], pruebas: buscarPruebas({ backend, app }) });
    assert.deepEqual(configuracion, []);
    const estado = Object.fromEntries(specs[0].filas.map((f) => [f.id, f.estado]));
    assert.deepEqual(estado, { 'FR-001': 'cubierto', 'FR-002': 'FALTA', 'FR-003': 'exento', 'SC-001': 'cubierto', 'SC-002': 'FALTA' });
  });

  it('una marca fuera de las carpetas de pruebas no cuenta', () => {
    const { backend } = armar({ pruebasDelBackend: { 'src/main/java/Pasos.java': '// spec 900 FR-001' } });
    const { specs } = evaluar({ backend, entradas: ['900'], pruebas: buscarPruebas({ backend, app: null }) });
    assert.equal(specs[0].filas.find((f) => f.id === 'FR-001').estado, 'FALTA');
  });

  it('una marca a un requisito que la spec no define queda huérfana', () => {
    const { backend } = armar({ pruebasDelBackend: { 'src/test/java/PasosTest.java': '// spec 900 FR-099' } });
    const { specs } = evaluar({ backend, entradas: ['900'], pruebas: buscarPruebas({ backend, app: null }) });
    assert.deepEqual(specs[0].huerfanas.map((h) => h.req), ['FR-099']);
  });

  it('una marca de otra spec no cubre esta', () => {
    const { backend } = armar({ pruebasDelBackend: { 'src/test/java/PasosTest.java': '// spec 901 FR-001' } });
    const { specs } = evaluar({ backend, entradas: ['900'], pruebas: buscarPruebas({ backend, app: null }) });
    assert.equal(specs[0].filas.find((f) => f.id === 'FR-001').estado, 'FALTA');
  });

  it('reporta como configuración una spec sin carpeta, ambigua o sin requisitos', () => {
    const { backend } = armar({ specs: { '900-uno': SPEC, '900-dos': SPEC, '901-vacia': '# nada' } });
    const { configuracion } = evaluar({ backend, entradas: ['902', '900', '901'], pruebas: new Map() });
    assert.equal(configuracion.length, 3);
    assert.match(configuracion[0], /no existe specs\/902-/);
    assert.match(configuracion[1], /ambigua/);
    assert.match(configuracion[2], /no define ningún FR/);
  });
});

describe('main', () => {
  const captura = () => {
    const salida = { out: [], err: [] };
    return { salida, io: { out: (t) => salida.out.push(t), err: (t) => salida.err.push(t) } };
  };
  const SPEC_CORTA = '- **FR-001**: Una cosa.\n';

  it('sale con 0 cuando todo requisito exigido tiene su prueba', () => {
    const { backend } = armar({ specs: { '900-ejemplo': SPEC_CORTA }, pruebasDelBackend: { 'src/test/A.java': '// spec 900 FR-001' } });
    const { salida, io } = captura();
    assert.equal(main(['--backend', backend, '--spec', '900'], io), 0);
    assert.match(salida.out.join('\n'), /OK · 1 spec/);
  });

  it('sale con 1 si falta una prueba o si hay una marca huérfana', () => {
    const sinPrueba = armar({ specs: { '900-ejemplo': SPEC_CORTA } });
    assert.equal(main(['--backend', sinPrueba.backend, '--spec', '900'], captura().io), 1);
    const huerfana = armar({ specs: { '900-ejemplo': SPEC_CORTA }, pruebasDelBackend: { 'src/test/A.java': '// spec 900 FR-001, FR-002' } });
    assert.equal(main(['--backend', huerfana.backend, '--spec', '900'], captura().io), 1);
  });

  it('sale con 2 si la configuración está mal', () => {
    const { backend } = armar({ specs: { '900-ejemplo': SPEC_CORTA } });
    assert.equal(main(['--backend', backend, '--spec', '777'], captura().io), 2);
    assert.equal(main(['--backend', backend, '--lista', join(backend, 'no-existe.json')], captura().io), 2);
    assert.equal(main(['--backend', backend, '--spec', '900', '--opcion-rara'], captura().io), 2);
    assert.equal(main(['--backend', backend, '--spec', '900', join(backend, 'no-existe')], captura().io), 2);
  });

  it('lee la lista de specs exigidas de specs/trazabilidad.json', () => {
    const { backend } = armar({ specs: { '900-ejemplo': SPEC_CORTA }, pruebasDelBackend: { 'src/test/A.java': '// spec 900 FR-001' } });
    escribir(backend, 'specs/trazabilidad.json', '["900"]');
    assert.equal(main(['--backend', backend], captura().io), 0);
    escribir(backend, 'specs/trazabilidad.json', '{"no": "es un arreglo"}');
    assert.equal(main(['--backend', backend], captura().io), 2);
  });

  it('escribe la tabla en Markdown con el requisito faltante en negrita', () => {
    const { backend } = armar({ specs: { '900-ejemplo': SPEC_CORTA } });
    const { specs } = evaluar({ backend, entradas: ['900'], pruebas: new Map() });
    assert.match(tablaEnMarkdown(specs[0]), /\| FR-001 \| Una cosa\. \| — \| \*\*FALTA\*\* \|/);
  });
});

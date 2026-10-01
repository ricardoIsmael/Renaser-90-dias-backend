// Prueba de carga de la API de Renaser 90 días (D-238). Ver README.md de esta carpeta.
//
// Reproduce lo que hace la app de un aprendiz: abrir la app (Hoy), Entrenamiento (hábitos del día y
// marcar uno, con evidencia de foto SIN subir el archivo), Plan (rocas), Ranking (personal y de
// grupos), Comunidad (muro, comentarios, reacciones, publicar), Chat humano (conversaciones,
// mensajes, enviar texto), Notificaciones y Perfil. Las llamadas, cuerpos y pesos salen del código
// de la app (`origin/master` del frontend, 2026-10-01).
//
// NO toca la IA: ni Renasia/SER (`/renasia/**`, incluido `GET /renasia/memoria` que la pantalla Yo
// pide), ni la voz, ni el Espejo. Correr SOLO contra una réplica, nunca contra producción: el guion
// escribe (completa hábitos, manda mensajes, publica en el muro).
//
// Uso:  k6 run -e BASE=http://<replica>:8080 -e ESCENARIO=carga -e PASS_FILE=<archivo> renaser.js
//       ESCENARIO = carga | estres | pico | humo
import http from 'k6/http';
import { sleep } from 'k6';
import { Counter } from 'k6/metrics';
import exec from 'k6/execution';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.1.0/index.js';

const BASE = (__ENV.BASE || 'http://localhost:8080').replace(/\/$/, '') + '/api/v1';
const ESCENARIO = __ENV.ESCENARIO || 'humo';
// La contraseña de prueba se lee de un archivo (600) para que no quede en el historial del shell.
const PASS = __ENV.PASS_FILE ? open(__ENV.PASS_FILE).trim() : (__ENV.PASS || '');
// Cuentas de la réplica: carga-aprendiz-01@carga.test ... (ver README, paso "preparar la réplica").
const CUENTAS = parseInt(__ENV.CUENTAS || '12', 10);
const PREFIJO_CUENTA = __ENV.PREFIJO_CUENTA || 'carga-aprendiz-';
const DOMINIO_CUENTA = __ENV.DOMINIO_CUENTA || 'carga.test';
const COHORTE = __ENV.COHORTE || '';
const PAUSA_MIN = parseFloat(__ENV.PAUSA_MIN || '3');
const PAUSA_MAX = parseFloat(__ENV.PAUSA_MAX || '10');

const ESCENARIOS = {
  humo: {
    executor: 'constant-vus', vus: 3, duration: '1m',
  },
  // (a) Carga escalonada: 25 -> 50 -> 100 -> 200 usuarios activos a la vez, 5 min cada escalón.
  carga: {
    executor: 'ramping-vus', startVUs: 0, gracefulRampDown: '20s',
    stages: [
      { duration: '30s', target: 25 }, { duration: '5m', target: 25 },
      { duration: '30s', target: 50 }, { duration: '5m', target: 50 },
      { duration: '30s', target: 100 }, { duration: '5m', target: 100 },
      { duration: '30s', target: 200 }, { duration: '5m', target: 200 },
      { duration: '30s', target: 0 },
    ],
  },
  // (b) Estrés: +100 usuarios cada 2 min hasta saturar de verdad. NO corta por lentitud: sigue
  // subiendo aunque el p95 pase de 2 s, y solo corta solo si los errores acumulados pasan el 10 %
  // (pedido del dueño, 2026-10-01: encontrar dónde se pone lento, dónde falla y dónde colapsa).
  estres: {
    executor: 'ramping-vus', startVUs: 0, gracefulRampDown: '10s',
    // ESTRES_INICIO permite una segunda pasada fina alrededor del quiebre (p. ej. 300 + 25 * i).
    stages: Array.from({ length: parseInt(__ENV.ESTRES_ESCALONES || '15', 10) }, (_, i) => {
      const n = parseInt(__ENV.ESTRES_INICIO || '0', 10) + (i + 1) * parseInt(__ENV.ESTRES_PASO || '100', 10);
      return [{ duration: '30s', target: n }, { duration: '1m30s', target: n }];
    }).flat(),
  },
  // (c) Pico: de 0 a 150 en 30 s (todos abren la app a la vez), se sostiene 3 min.
  pico: {
    executor: 'ramping-vus', startVUs: 0, gracefulRampDown: '10s',
    stages: [
      { duration: '30s', target: 150 }, { duration: '3m', target: 150 }, { duration: '30s', target: 0 },
    ],
  },
};

const MAX_VUS = { humo: 3, carga: 200, estres: 1500, pico: 150 }[ESCENARIO] || 50;
const SESIONES = parseInt(__ENV.SESIONES || String(Math.min(MAX_VUS, 300)), 10);

// Todas las rutas que el guion llama, para tener p50/p95/p99 por endpoint en el resumen.
const ENDPOINTS = [
  'POST /auth/login', 'GET /home', 'GET /habit-tracks/today', 'GET /rocks/today', 'GET /wall',
  'GET /mapa-renacimiento', 'GET /mentor/context', 'GET /onboarding/state', 'GET /radar/latest',
  'GET /calendar/events', 'GET /rocks/upcoming', 'GET /rocks/tomorrow', 'GET /habit-preferences',
  'GET /habits', 'GET /habit-unlocks', 'GET /evidence', 'GET /spirit-audio/status',
  'GET /audio-therapy/status', 'POST /habit-tracks/{id}/complete',
  'POST /habit-tracks/{id}/evidence/upload-url', 'POST /habit-tracks/{id}/evidence',
  'GET /rocks/master', 'GET /rocks/monthly/plan', 'GET /rocks/weekly', 'GET /ranking',
  'GET /me/cell', 'GET /me/cells', 'GET /ranking/groups', 'GET /wall/{id}/comments',
  'POST /wall/{id}/react', 'POST /wall/{id}/comments', 'GET /wall/categories',
  'POST /wall/media/upload-url', 'POST /wall', 'GET /chat/conversations', 'GET /chat/members',
  'GET /chat/conversations/{id}/messages', 'POST /chat/conversations/{id}/read',
  'GET /chat/conversations/{id}/presence', 'POST /chat/conversations/{id}/messages',
  'GET /notifications', 'PUT /notifications/{id}/read', 'GET /me/caja', 'POST /users/me',
  'GET /profile/logros',
];

const umbralesPorEndpoint = {};
for (const n of ENDPOINTS) umbralesPorEndpoint[`http_req_duration{name:${n}}`] = ['max>=0'];

const UMBRALES = ESCENARIO === 'estres'
  ? {
    http_req_failed: [{ threshold: 'rate<0.10', abortOnFail: true, delayAbortEval: '1m' }],
    http_req_duration: ['p(95)<2000'],
  }
  : { http_req_failed: ['rate<0.01'], http_req_duration: ['p(95)<1000'] };

export const options = {
  scenarios: { [ESCENARIO]: ESCENARIOS[ESCENARIO] },
  thresholds: Object.assign({}, umbralesPorEndpoint, UMBRALES),
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max', 'count'],
  setupTimeout: '10m',
  teardownTimeout: '5m',
  discardResponseBodies: false,
  noConnectionReuse: false,
};

// Una escritura que el negocio rechaza (hábito ya completado, plazo vencido) es una respuesta
// correcta del servidor, no un error de capacidad: no cuenta en http_req_failed, se cuenta aparte.
const ESCRITURA_OK = http.expectedStatuses({ min: 200, max: 299 }, 400, 404, 409, 422);
const rechazosDeNegocio = new Counter('rechazos_de_negocio');

function cabeceras(token) {
  return {
    'X-Auth-Token': token,
    Accept: 'application/json',
    'Content-Type': 'application/json',
    'Accept-Encoding': 'gzip',
  };
}

function pedido(token, metodo, ruta, nombre, cuerpo, escritura) {
  const params = { headers: cabeceras(token), tags: { name: nombre }, timeout: '30s' };
  if (escritura) params.responseCallback = ESCRITURA_OK;
  return [metodo, BASE + ruta, cuerpo === undefined ? null : JSON.stringify(cuerpo), params];
}

function lote(token, lista) {
  return http.batch(lista.map(([m, r, n]) => pedido(token, m, r, n)));
}

function enviar(token, metodo, ruta, nombre, cuerpo) {
  const [m, url, body, params] = pedido(token, metodo, ruta, nombre, cuerpo, metodo !== 'GET');
  const r = http.request(m, url, body, params);
  if (r.status >= 400 && r.status < 500) rechazosDeNegocio.add(1, { name: nombre });
  return r;
}

function json(r) {
  try { return r.json(); } catch (_) { return null; }
}

function alAzar(lista) {
  return lista && lista.length ? lista[Math.floor(Math.random() * lista.length)] : null;
}

function pausa() {
  sleep(PAUSA_MIN + Math.random() * (PAUSA_MAX - PAUSA_MIN));
}

// ------------------------------------------------------------------------------------------------
// Sesiones: se inician una vez en setup() y se reparten entre los usuarios virtuales. La sesión
// de la app dura 30 días, así que un usuario real casi nunca hace login; lo que pesa es el resto.
// Cada login sale con su propia X-Forwarded-For para no chocar con el tope de 50 intentos por
// hora y por IP del login (AutenticacionService), que contaría a toda la prueba como una sola IP.
export function setup() {
  if (!PASS) throw new Error('Falta la contraseña de prueba: -e PASS_FILE=<archivo>');
  const corrida = Math.floor(Math.random() * 200) + 20;
  const sesiones = [];
  for (let i = 0; i < SESIONES; i += 10) {
    const pedidos = [];
    for (let j = i; j < Math.min(i + 10, SESIONES); j++) {
      const n = String((j % CUENTAS) + 1).padStart(2, '0');
      pedidos.push(['POST', BASE + '/auth/login',
        JSON.stringify({ email: `${PREFIJO_CUENTA}${n}@${DOMINIO_CUENTA}`, contrasena: PASS }),
        {
          headers: {
            'Content-Type': 'application/json', Accept: 'application/json',
            'X-Forwarded-For': `10.${corrida}.${Math.floor(j / 250)}.${(j % 250) + 1}`,
          },
          tags: { name: 'POST /auth/login' },
        }]);
    }
    for (const r of http.batch(pedidos)) {
      const token = r.headers['X-Auth-Token'];
      if (r.status === 200 && token) sesiones.push(token);
    }
  }
  if (sesiones.length === 0) throw new Error('Ningún login funcionó: revisar cuentas y contraseña');
  console.log(`Sesiones iniciadas: ${sesiones.length} de ${SESIONES}`);
  return { sesiones };
}

export function teardown(data) {
  for (let i = 0; i < data.sesiones.length; i += 20) {
    http.batch(data.sesiones.slice(i, i + 20).map((t) => ['POST', BASE + '/auth/logout', null,
      { headers: cabeceras(t), tags: { name: 'POST /auth/logout' } }]));
  }
}

// ------------------------------------------------------------------------------------------------
// Pantallas. Cada una dispara EN PARALELO lo que la app dispara al abrirla (http.batch), y después,
// a veces, la acción que el usuario hace ahí.

function hoy(t) {
  const lista = [
    ['GET', '/home', 'GET /home'], ['GET', '/habit-tracks/today', 'GET /habit-tracks/today'],
    ['GET', '/rocks/today', 'GET /rocks/today'], ['GET', '/wall', 'GET /wall'],
    ['GET', '/mapa-renacimiento', 'GET /mapa-renacimiento'], ['GET', '/mentor/context', 'GET /mentor/context'],
  ];
  // Un tercio de las veces es "abrir la app": se suman los componentes globales del arranque.
  if (Math.random() < 0.33) {
    const desde = new Date();
    const hasta = new Date(desde.getTime() + 7 * 86400000);
    lista.push(
      ['GET', '/onboarding/state', 'GET /onboarding/state'], ['GET', '/radar/latest', 'GET /radar/latest'],
      ['GET', `/calendar/events?from=${desde.toISOString()}&to=${hasta.toISOString()}`, 'GET /calendar/events'],
      ['GET', '/rocks/upcoming', 'GET /rocks/upcoming'], ['GET', '/rocks/tomorrow', 'GET /rocks/tomorrow'],
      ['GET', '/habit-preferences', 'GET /habit-preferences'],
    );
  }
  lote(t, lista);
}

function entrenamiento(t) {
  const r = lote(t, [
    ['GET', '/habit-tracks/today', 'GET /habit-tracks/today'], ['GET', '/habits', 'GET /habits'],
    ['GET', '/habit-preferences', 'GET /habit-preferences'], ['GET', '/habit-unlocks', 'GET /habit-unlocks'],
    ['GET', '/rocks/today', 'GET /rocks/today'], ['GET', '/evidence?tipoDestino=ROCA_DIARIA', 'GET /evidence'],
    ['GET', '/spirit-audio/status', 'GET /spirit-audio/status'],
    ['GET', '/audio-therapy/status', 'GET /audio-therapy/status'],
  ]);
  if (Math.random() >= 0.5) return;
  const tracks = json(r[0]);
  const pendiente = alAzar((Array.isArray(tracks) ? tracks : []).filter((x) => x.estado === 'PENDIENTE'));
  if (!pendiente) return;
  sleep(1 + Math.random() * 2);
  if (Math.random() < 0.4) {
    // Evidencia con foto: se pide la URL de subida y se registra la evidencia, SIN el PUT a S3.
    const u = json(enviar(t, 'POST', `/habit-tracks/${pendiente.id}/evidence/upload-url`,
      'POST /habit-tracks/{id}/evidence/upload-url', { tipoContenido: 'image/jpeg' }));
    if (u && u.ruta) {
      enviar(t, 'POST', `/habit-tracks/${pendiente.id}/evidence`, 'POST /habit-tracks/{id}/evidence', {
        tipo: 'FOTO', bucket: u.bucket, rutaStorage: u.ruta, contenidoTexto: null,
        timestampExif: null, gpsLat: null, gpsLng: null,
      });
    }
  }
  const cuerpo = { respuestaTexto: null, calificacionProductividad: null };
  if (pendiente.medicion) cuerpo.valorMedido = 3.5;
  enviar(t, 'POST', `/habit-tracks/${pendiente.id}/complete`, 'POST /habit-tracks/{id}/complete', cuerpo);
}

function plan(t) {
  lote(t, [
    ['GET', '/habits', 'GET /habits'], ['GET', '/habit-preferences', 'GET /habit-preferences'],
    ['GET', '/habit-unlocks', 'GET /habit-unlocks'], ['GET', '/home', 'GET /home'],
    ['GET', '/rocks/master', 'GET /rocks/master'], ['GET', '/rocks/monthly/plan', 'GET /rocks/monthly/plan'],
    ['GET', '/mapa-renacimiento', 'GET /mapa-renacimiento'], ['GET', '/rocks/weekly', 'GET /rocks/weekly'],
  ]);
}

function ranking(t) {
  const lista = [
    ['GET', '/ranking', 'GET /ranking'], ['GET', '/me/cell', 'GET /me/cell'], ['GET', '/me/cells', 'GET /me/cells'],
  ];
  if (COHORTE) {
    const mes = new Date().toISOString().slice(0, 7);
    lista.push(['GET', `/ranking/groups?cohortId=${COHORTE}&month=${mes}`, 'GET /ranking/groups']);
  }
  lote(t, lista);
}

function comunidad(t) {
  const r = lote(t, [['GET', '/wall', 'GET /wall'], ['GET', '/me/cells', 'GET /me/cells']]);
  const muro = json(r[0]);
  const post = alAzar(muro && muro.posts);
  const dado = Math.random();
  if (post && dado < 0.5) {
    sleep(1 + Math.random() * 3);
    lote(t, [['GET', `/wall/${post.id}/comments`, 'GET /wall/{id}/comments']]);
    if (Math.random() < 0.1) {
      enviar(t, 'POST', `/wall/${post.id}/comments`, 'POST /wall/{id}/comments', { text: 'Vamos con todo (prueba de carga)' });
    }
  } else if (post && dado < 0.75) {
    enviar(t, 'POST', `/wall/${post.id}/react`, 'POST /wall/{id}/react', { type: 'LIKE' });
  } else if (dado > 0.97) {
    lote(t, [['GET', '/wall/categories', 'GET /wall/categories'], ['GET', '/me/cell', 'GET /me/cell']]);
    const u = json(enviar(t, 'POST', '/wall/media/upload-url', 'POST /wall/media/upload-url', { tipoContenido: 'image/jpeg' }));
    if (u && u.ruta) {
      enviar(t, 'POST', '/wall', 'POST /wall', {
        text: 'Avance del día (prueba de carga)', media: [{ url: u.ruta, mimeType: 'image/jpeg' }], category: null,
      });
    }
  }
}

function chat(t) {
  const r = lote(t, [
    ['GET', '/chat/conversations', 'GET /chat/conversations'], ['GET', '/chat/members?limit=30', 'GET /chat/members'],
    ['GET', '/me/cell', 'GET /me/cell'], ['GET', '/me/cells', 'GET /me/cells'],
  ]);
  const lista = json(r[0]);
  // Solo grupos y directos: el soporte y el global no son la conversación diaria de un aprendiz.
  const conv = alAzar((Array.isArray(lista) ? lista : [])
    .map((x) => x.conversation).filter((c) => c && (c.type === 'CELL' || c.type === 'DIRECT')));
  if (!conv) return;
  sleep(1 + Math.random() * 2);
  lote(t, [
    ['GET', `/chat/conversations/${conv.id}/messages?limit=30`, 'GET /chat/conversations/{id}/messages'],
    ['GET', `/chat/conversations/${conv.id}/presence`, 'GET /chat/conversations/{id}/presence'],
  ]);
  enviar(t, 'POST', `/chat/conversations/${conv.id}/read`, 'POST /chat/conversations/{id}/read');
  if (Math.random() < 0.35) {
    sleep(2 + Math.random() * 4);
    enviar(t, 'POST', `/chat/conversations/${conv.id}/messages`, 'POST /chat/conversations/{id}/messages',
      { type: 'TEXT', text: 'Hola equipo, hoy cumplí (prueba de carga)' });
  }
}

function notificaciones(t) {
  const r = lote(t, [['GET', '/notifications', 'GET /notifications']]);
  const n = json(r[0]);
  const una = alAzar(n && n.items);
  if (una && Math.random() < 0.2) enviar(t, 'PUT', `/notifications/${una.id}/read`, 'PUT /notifications/{id}/read');
}

function perfil(t) {
  lote(t, [
    ['GET', '/home', 'GET /home'], ['GET', '/evidence', 'GET /evidence'],
    ['GET', '/onboarding/state', 'GET /onboarding/state'], ['GET', '/mapa-renacimiento', 'GET /mapa-renacimiento'],
    ['GET', '/me/caja', 'GET /me/caja'], ['GET', '/mentor/context', 'GET /mentor/context'],
    ['POST', '/users/me', 'POST /users/me'], ['GET', '/profile/logros', 'GET /profile/logros'],
  ]);
}

// Pesos: más lecturas que escrituras, y Hoy/Entrenamiento son lo que más se abre.
const PANTALLAS = [
  [30, hoy], [20, entrenamiento], [15, comunidad], [12, chat], [8, plan], [8, ranking], [5, perfil], [2, notificaciones],
];
const TOTAL = PANTALLAS.reduce((s, [p]) => s + p, 0);

export default function (data) {
  const token = data.sesiones[(exec.vu.idInTest - 1) % data.sesiones.length];
  let dado = Math.random() * TOTAL;
  for (const [peso, pantalla] of PANTALLAS) {
    dado -= peso;
    if (dado < 0) { pantalla(token); break; }
  }
  pausa();
}

export function handleSummary(data) {
  const sello = new Date().toISOString().replace(/[:.]/g, '-');
  const dir = __ENV.RESULTADOS || '.';
  return {
    stdout: textSummary(data, { indent: ' ', enableColors: false }),
    [`${dir}/resumen-${ESCENARIO}-${sello}.json`]: JSON.stringify(data, null, 1),
  };
}

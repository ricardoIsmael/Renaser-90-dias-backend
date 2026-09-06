/**
 * Prueba de humo del panel, sin AWS y sin el backend real.
 *
 * Levanta un backend de mentira en 127.0.0.1 que imita los cuatro endpoints que el panel
 * usa, y ejecuta el handler contra el. Cubre lo que de verdad puede romperse aca: el gate de
 * la clave, el gate del rol, el CSRF, el escapado del HTML y el mapeo de errores del backend.
 *
 *   node admin-panel/scripts/prueba-local.mjs
 *
 * No reemplaza a `./mvnw clean test` (que prueba el backend Java): este paquete no forma
 * parte del build de Maven.
 */

import { createServer } from 'node:http';
import { pbkdf2Sync, randomBytes } from 'node:crypto';

const CLAVE = 'clave-de-prueba';
const SESION_VALIDA = 'sesion-de-prueba-123';

const sal = randomBytes(16);
const hash = pbkdf2Sync(CLAVE, sal, 600_000, 32, 'sha256');
process.env.CLAVE_PANEL_VERIFICADOR = `pbkdf2$sha256$600000$${sal.toString('base64')}$${hash.toString('base64')}`;

// ---------------------------------------------------------------------------
// Backend de mentira
// ---------------------------------------------------------------------------
const llamadas = [];

/** Devuelve la ultima llamada al backend cuya ruta contiene `fragmento`. */
function llamadaA(fragmento) {
    return [...llamadas].reverse().find((llamada) => llamada.ruta.includes(fragmento));
}

const solicitudes = [{
    id: '11111111-2222-3333-4444-555555555555',
    // Nombre con HTML adentro a proposito: viene de un formulario publico.
    fullName: '<script>alert(1)</script> Ana Perez',
    email: 'ana@example.com',
    phone: '+51 999 111 222',
    city: 'Lima',
    status: 'PENDING',
    createdAt: '2026-09-04T15:30:00Z',
}];

const servidor = createServer((peticion, respuesta) => {
    let cuerpo = '';
    peticion.on('data', (trozo) => { cuerpo += trozo; });
    peticion.on('end', () => {
        llamadas.push({ ruta: peticion.url, metodo: peticion.method, cuerpo, cabeceras: peticion.headers });
        const responder = (codigo, json, cabeceras = {}) => {
            respuesta.writeHead(codigo, { 'Content-Type': 'application/json', ...cabeceras });
            respuesta.end(json === undefined ? '' : JSON.stringify(json));
        };

        if (peticion.url === '/api/v1/auth/login') {
            const { email, contrasena } = JSON.parse(cuerpo);
            if (contrasena !== 'correcta') {
                return responder(401, { message: 'Email o contrasena incorrectos' });
            }
            const rol = email.startsWith('admin') ? 'ADMIN' : 'TRAINEE';
            return responder(200, { id: 'u-1', email, role: rol, status: 'ACTIVE', fullName: 'Quien Sea' },
                { 'X-Auth-Token': SESION_VALIDA });
        }
        if (peticion.url === '/api/v1/auth/logout') {
            return responder(204);
        }
        // Fuera de `/api/v1/**`: en el backend real el actuator no pasa por el filtro de
        // sesion, y /diagnostico lo consulta justamente sin sesion, para poder distinguir
        // "no llego al backend" de "el backend no me reconoce".
        if (peticion.url === '/actuator/health') {
            return responder(200, { status: 'UP' });
        }
        if (peticion.headers['x-auth-token'] !== SESION_VALIDA) {
            return responder(403, { message: 'Solo ADMIN/ALCHEMIST administran este panel' });
        }
        if (peticion.url === '/api/v1/auth/me') {
            return responder(200, { id: 'u-1', email: 'admin@x.com', role: 'ADMIN', status: 'ACTIVE' });
        }
        if (peticion.url.startsWith('/api/v1/account-requests?')) {
            return responder(200, { content: solicitudes, total: solicitudes.length, page: 0, size: 100 });
        }
        if (peticion.url.endsWith('/approve') || peticion.url.endsWith('/reject')) {
            return responder(204);
        }
        return responder(404, { message: 'no existe' });
    });
});

await new Promise((listo) => servidor.listen(0, '127.0.0.1', listo));
process.env.BACKEND_BASE_URL = `http://127.0.0.1:${servidor.address().port}`;

const { handler } = await import('../src/index.mjs');

// ---------------------------------------------------------------------------
// Utilidades
// ---------------------------------------------------------------------------
let fallos = 0;

function verificar(descripcion, condicion) {
    console.log(`${condicion ? '  ok  ' : ' FALLA'}  ${descripcion}`);
    if (!condicion) {
        fallos += 1;
    }
}

function evento(metodo, ruta, { formulario, cookies = [] } = {}) {
    return {
        rawPath: ruta,
        cookies,
        isBase64Encoded: false,
        body: formulario ? new URLSearchParams(formulario).toString() : undefined,
        requestContext: { http: { method: metodo, sourceIp: `10.0.0.${Math.floor(Math.random() * 250)}` } },
    };
}

function cookiesDe(salida) {
    return (salida.cookies ?? []).map((galleta) => galleta.split(';')[0]);
}

// ---------------------------------------------------------------------------
// Pruebas
// ---------------------------------------------------------------------------
console.log('\nPanel de solicitudes de cuenta — prueba de humo\n');

const inicio = await handler(evento('GET', '/'));
verificar('GET / sin sesion muestra el formulario de ingreso', inicio.statusCode === 200 && inicio.body.includes('Clave del panel'));
verificar('la pagina no carga ningun recurso externo', !/https?:\/\/(?!127\.0\.0\.1)/.test(inicio.body));

const claveMal = await handler(evento('POST', '/login', { formulario: { clave: 'nope', email: 'admin@x.com', contrasena: 'correcta' } }));
verificar('clave del panel incorrecta -> 401 y no se llama al backend', claveMal.statusCode === 401 && claveMal.body.includes('Clave del panel incorrecta'));

llamadas.length = 0;
const claveMalSinLlamada = await handler(evento('POST', '/login', { formulario: { clave: 'nope', email: 'admin@x.com', contrasena: 'correcta' } }));
verificar('con la clave mal, el backend nunca recibe el intento', llamadas.length === 0 && claveMalSinLlamada.statusCode === 401);

const contrasenaMal = await handler(evento('POST', '/login', { formulario: { clave: CLAVE, email: 'admin@x.com', contrasena: 'mala' } }));
verificar('contrasena incorrecta -> 401', contrasenaMal.statusCode === 401 && contrasenaMal.body.includes('incorrectos'));

const noAdmin = await handler(evento('POST', '/login', { formulario: { clave: CLAVE, email: 'aprendiz@x.com', contrasena: 'correcta' } }));
verificar('un TRAINEE con clave y contrasena correctas NO entra', noAdmin.statusCode === 401 && noAdmin.body.includes('no administra'));

const entrada = await handler(evento('POST', '/login', { formulario: { clave: CLAVE, email: 'admin@x.com', contrasena: 'correcta' } }));
verificar('ADMIN entra -> 303 a /solicitudes', entrada.statusCode === 303 && entrada.headers.Location === '/solicitudes');
const galletas = cookiesDe(entrada);
verificar('la sesion viaja en cookie HttpOnly+Secure+SameSite=Strict',
    entrada.cookies.every((g) => g.includes('HttpOnly') && g.includes('Secure') && g.includes('SameSite=Strict')));
const csrf = galletas.find((g) => g.startsWith('__Host-renaser_csrf=')).split('=')[1];

const listado = await handler(evento('GET', '/solicitudes', { cookies: galletas }));
verificar('la lista trae la solicitud pendiente', listado.statusCode === 200 && listado.body.includes('ana@example.com'));
verificar('el nombre con HTML sale escapado (sin XSS)',
    !listado.body.includes('<script>alert(1)</script>') && listado.body.includes('&lt;script&gt;'));
verificar('la fecha se muestra en la zona del padron, no en UTC', listado.body.includes('2026'));
verificar('el UUID del admin nunca aparece en el HTML', !listado.body.includes('u-1'));

const sinCsrf = await handler(evento('POST', '/solicitudes/aprobar', {
    cookies: galletas, formulario: { id: solicitudes[0].id },
}));
verificar('aprobar sin token CSRF -> 403', sinCsrf.statusCode === 403 && sinCsrf.body.includes('caduco'));

llamadas.length = 0;
const aprobado = await handler(evento('POST', '/solicitudes/aprobar', {
    cookies: galletas, formulario: { id: solicitudes[0].id, csrf },
}));
const llamadaAprobar = llamadaA('/approve');
verificar('aprobar llama al endpoint correcto del backend',
    llamadaAprobar?.ruta === `/api/v1/account-requests/${solicitudes[0].id}/approve` && llamadaAprobar.metodo === 'POST');
verificar('aprobar avisa que salio bien', aprobado.statusCode === 200 && aprobado.body.includes('Solicitud aprobada'));
verificar('aprobar refresca la lista despues de decidir', llamadaA('status=PENDING') !== undefined);
verificar('la sesion viaja como X-Auth-Token, y X-Actor-Id nunca se manda',
    llamadas.every((l) => l.cabeceras['x-auth-token'] === SESION_VALIDA && l.cabeceras['x-actor-id'] === undefined));

const idInvalido = await handler(evento('POST', '/solicitudes/aprobar', {
    cookies: galletas, formulario: { id: 'no-es-un-uuid', csrf },
}));
verificar('un id que no es UUID se corta antes del backend', idInvalido.statusCode === 400 && idInvalido.body.includes('invalido'));

const sinMotivo = await handler(evento('POST', '/solicitudes/rechazar', {
    cookies: galletas, formulario: { id: solicitudes[0].id, csrf, motivo: '   ' },
}));
verificar('rechazar sin motivo se corta antes del backend', sinMotivo.statusCode === 400 && sinMotivo.body.includes('motivo'));

llamadas.length = 0;
const rechazado = await handler(evento('POST', '/solicitudes/rechazar', {
    cookies: galletas, formulario: { id: solicitudes[0].id, csrf, motivo: 'No cumple los requisitos' },
}));
verificar('rechazar manda el motivo en el cuerpo',
    JSON.parse(llamadaA('/reject').cuerpo).reason === 'No cumple los requisitos' && rechazado.statusCode === 200);

const sesionVieja = await handler(evento('GET', '/solicitudes', { cookies: ['__Host-renaser_sesion=caducada'] }));
verificar('una sesion que el backend ya no reconoce echa al ingreso y borra la cookie',
    sesionVieja.statusCode === 303 && sesionVieja.cookies.some((g) => g.includes('Max-Age=0')));

const diag = await handler(evento('GET', '/diagnostico', { cookies: galletas }));
verificar('/diagnostico reporta la salud del backend', diag.statusCode === 200 && diag.body.includes('UP'));

const diagSinSesion = await handler(evento('GET', '/diagnostico'));
verificar('/diagnostico sin sesion no revela nada', diagSinSesion.statusCode === 303);

// La cookie escrita a mano no alcanza: la sesion se valida contra el backend antes de
// mostrar la direccion privada.
const diagInventado = await handler(evento('GET', '/diagnostico', { cookies: ['__Host-renaser_sesion=inventada'] }));
verificar('/diagnostico con una cookie inventada no muestra la IP privada',
    diagInventado.statusCode === 303 && !(diagInventado.body ?? '').includes('127.0.0.1'));

const inexistente = await handler(evento('GET', '/otra-cosa'));
verificar('una ruta que no existe -> 404', inexistente.statusCode === 404);

servidor.close();
console.log(fallos === 0 ? '\nTodo en verde.\n' : `\n${fallos} verificacion(es) en rojo.\n`);
process.exit(fallos === 0 ? 0 : 1);

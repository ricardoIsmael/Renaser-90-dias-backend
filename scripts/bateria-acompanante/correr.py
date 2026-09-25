#!/usr/bin/env python3
"""Bateria de preguntas al acompanante por el chat de la app en el emulador.

Sin credenciales: escribe cada pregunta en el panel del chat como lo haria una persona, espera la
respuesta que el backend guarda en mensajes_renasia y anota las propuestas que se crearon. Solo toca
CONFIRMAR en el caso marcado ("confirmar": true); las demas propuestas vencen solas a los 10 min.

Uso: correr.py [desde_id] [hasta_id]   o   correr.py 5,6,7,14   (una lista de ids, en ese orden)
Agrega a resultados.jsonl, junto a este archivo (o al que diga BATERIA_SALIDA).

Requisitos: el emulador con la app en Hoy y la sesion iniciada, el backend local en :8080 y la base
Docker `renaser-db`. `RENASER_ACTOR` (obligatoria) es el id del participante de prueba en esa base.
Despues, `instrucciones-calificador.md` y `hechos.md` son lo que se les da a los agentes que califican.
Lo que se aprendio al armarlo (la recarga "rr", el autocorrector): BITACORA_ERRORES E-272.
"""
import difflib
import json
import os
import re
import subprocess
import sys
import time
import unicodedata

BASE = os.path.dirname(os.path.abspath(__file__))
ACTOR = os.environ.get('RENASER_ACTOR', '')
if not re.fullmatch(r'[0-9a-f-]{36}', ACTOR):
    # Sin valor por defecto a proposito: el id de un usuario de la base no se commitea (E-270).
    sys.exit('Falta RENASER_ACTOR: el id del participante de prueba en la base local.')
SALIDA = os.path.join(BASE, os.environ.get('BATERIA_SALIDA', 'resultados.jsonl'))
LANZADOR_DEL_CHAT = (965, 2064)

LARGO = ("hola, hoy fue un dia muy pesado. me levante tarde porque anoche no pude dormir pensando en el "
         "trabajo, llegue tarde a la oficina, mi jefe me llamo la atencion delante de todos y senti que no "
         "servia para nada. en el almuerzo casi no comi porque tenia una reunion tras otra, y en la tarde "
         "me dolia la cabeza. cuando volvi a casa tenia que cocinar, lavar la ropa y ayudar a mi hermano con "
         "sus tareas, asi que no hice ninguno de mis habitos. siento que estoy fallando en el programa y que "
         "todos los demas avanzan menos yo. a veces pienso que no tengo la disciplina que se necesita, pero "
         "tampoco quiero rendirme porque ya llevo casi tres semanas y he visto cambios pequenos, como que "
         "ahora tomo mas agua y camino un poco mas. mi pareja dice que estoy mas tranquilo, aunque yo no lo "
         "siento asi todos los dias. manana tambien va a ser un dia largo: tengo una presentacion importante "
         "a las nueve y despues una capacitacion hasta las cinco. no se si voy a tener tiempo para la ducha "
         "fria en la manana ni para caminar en la tarde. tampoco quiero perder mi racha otra vez, porque ya "
         "la perdi la semana pasada y me desanimo mucho. entonces te pregunto algo concreto: puedo apagar la "
         "ducha fria solo manana, sin que me afecte el resto de la semana?")


def sh(args, timeout=60):
    return subprocess.run(args, capture_output=True, text=True, timeout=timeout).stdout


def db(sql):
    return sh(['docker', 'exec', 'renaser-db', 'psql', '-U', 'postgres', '-d', 'renaser', '-At', '-c', sql])


def db_json(sql):
    salida = db(sql).strip()
    return json.loads(salida) if salida else []


def ui():
    return sh(['adb', 'exec-out', 'uiautomator', 'dump', '/dev/tty'], timeout=40)


def nodos(xml, patron):
    encontrados = []
    for m in re.finditer(r'<node [^>]*>', xml):
        nodo = m.group(0)
        if re.search(patron, nodo):
            b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', nodo)
            if b:
                x1, y1, x2, y2 = map(int, b.groups())
                encontrados.append(((x1 + x2) // 2, (y1 + y2) // 2))
    return encontrados


def tocar(x, y):
    sh(['adb', 'shell', 'input', 'tap', str(x), str(y)])


def abrir_panel():
    for _ in range(3):
        if nodos(ui(), r'content-desc="Enviar pregunta"'):
            return True
        tocar(*LANZADOR_DEL_CHAT)
        time.sleep(2.5)
    return bool(nodos(ui(), r'content-desc="Enviar pregunta"'))


def trozos(texto, largo=120):
    """Trozos con a lo sumo UNA 'r' cada uno, que se escriben con una pausa entre si.

    'adb input text' manda eventos de tecla, y el Modal del chat de React Native reenvia las teclas a
    la Activity (para el menu de desarrollo): dos 'r' en menos de 200 ms son el atajo "rr" y RECARGAN
    la app (2026-09-25: "renacer" cerraba el panel). Tambien se corta por largo, nunca dentro de un %s.
    """
    salida, actual = [], ''
    for letra in texto:
        if (letra in 'rR' and any(c in 'rR' for c in actual)) or len(actual) >= largo:
            salida.append(actual)
            actual = ''
        actual += letra
    salida.append(actual)
    return [t for t in salida if t]


def teclado_abierto():
    return 'mInputShown=true' in sh(['adb', 'shell', 'dumpsys', 'input_method'])


def campo_del_chat(xml):
    """(x1, y1, x2, y2, texto) del campo del chat, o None."""
    m = re.search(r'<node [^>]*class="android.widget.EditText"[^>]*>', xml)
    if not m:
        return None
    nodo = m.group(0)
    b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', nodo)
    t = re.search(r' text="([^"]*)"', nodo)
    return (*map(int, b.groups()), t.group(1) if t else '')


def enfocar_campo():
    """Toca el campo por la izquierda y confirma que se abrio el teclado.

    Sin foco, lo que se escribe le llega a la app como teclas sueltas, y en desarrollo "r r" RECARGA
    React Native (paso el 2026-09-25: pantalla en blanco y panel cerrado). Por eso nunca se escribe
    sin teclado abierto. El centro del campo tambien fallo: se toca a 80 px de su borde izquierdo.
    """
    for _ in range(3):
        campo = campo_del_chat(ui())
        if not campo:
            abrir_panel()
            continue
        x1, y1, x2, y2, _texto = campo
        tocar(x1 + 80, (y1 + y2) // 2)
        time.sleep(1.5)
        if teclado_abierto():
            return campo
    raise RuntimeError('no pude enfocar el campo del chat')


def escribir_y_enviar(texto):
    x1, y1, x2, y2, previo = enfocar_campo()
    if previo and not previo.startswith('Escr'):
        sh(['adb', 'shell', 'input', 'keyevent', '123'] + ['67'] * (len(previo) + 5))
    for i, trozo in enumerate(trozos(texto)):
        if i % 5 == 0 and not teclado_abierto():
            raise RuntimeError('se cerro el teclado mientras escribia')
        sh(['adb', 'shell', "input text '" + trozo.replace(' ', '%s') + "'"], timeout=180)
        time.sleep(0.3)
    time.sleep(0.6)
    for _ in range(3):
        xml = ui()
        campo = campo_del_chat(xml)
        boton = nodos(xml, r'content-desc="Enviar pregunta"')
        # El autocorrector del teclado cambia palabras ("termina" -> "terminar"): basta un parecido
        # alto; lo que de verdad se envio queda en 'guardado'.
        if campo and boton and difflib.SequenceMatcher(None, normal(campo[4]), normal(texto)).ratio() >= 0.85:
            tocar(*boton[-1])
            return
        time.sleep(1)
    raise RuntimeError('el texto no quedo en el campo o no hay boton de enviar')


def mensajes_desde(t0):
    return db_json("select coalesce(json_agg(json_build_object('rol', rol, 'contenido', contenido) "
                   "order by creado_en), '[]') from renaser.mensajes_renasia "
                   f"where usuario_id='{ACTOR}' and agente='COMPANION' and creado_en > '{t0}'")


def propuestas_desde(t0):
    return db_json("select coalesce(json_agg(json_build_object('id', id, 'herramienta', herramienta, "
                   "'estado', estado, 'resumen', resumen, 'argumentos', argumentos) order by creada_en), '[]') "
                   f"from renaser.propuestas_acompanante where participante_id='{ACTOR}' and creada_en > '{t0}'")


def esperar_respuesta(t0, limite_s=120):
    fin = time.time() + limite_s
    while time.time() < fin:
        filas = mensajes_desde(t0)
        respuestas = [f['contenido'] for f in filas if f['rol'] == 'ASISTENTE']
        if respuestas:
            return '\n'.join(respuestas), [f['contenido'] for f in filas if f['rol'] == 'USUARIO']
        time.sleep(1)
    return None, [f['contenido'] for f in mensajes_desde(t0) if f['rol'] == 'USUARIO']


def normal(texto):
    sin_tildes = unicodedata.normalize('NFD', texto or '')
    return ''.join(c for c in sin_tildes if unicodedata.category(c) != 'Mn').lower()


def evaluar(caso, respuesta, propuestas):
    motivos = []
    r = normal(respuesta)
    esperada = caso.get('propuesta')
    herramientas = [p['herramienta'] for p in propuestas]
    if esperada is False and propuestas:
        motivos.append('no debia proponer y propuso: ' + ', '.join(herramientas))
    if isinstance(esperada, str) and esperada not in herramientas:
        motivos.append('esperaba ' + esperada + ' y hubo: ' + (', '.join(herramientas) or 'ninguna'))
    if 'max_propuestas' in caso and len(propuestas) > caso['max_propuestas']:
        motivos.append(f'demasiadas propuestas: {len(propuestas)}')
    for grupo in caso.get('contiene', []):
        if not any(normal(alt) in r for alt in grupo):
            motivos.append('falta alguno de: ' + ' | '.join(grupo))
    for prohibido in caso.get('no_contiene', []) + ['911']:
        if normal(prohibido) in r:
            motivos.append('contiene lo prohibido: ' + prohibido)
    return motivos


def confirmar_ultima(t0):
    candidatos = nodos(ui(), r'text="CONFIRMAR"')
    if not candidatos:
        return 'sin boton CONFIRMAR en pantalla'
    tocar(*max(candidatos, key=lambda p: p[1]))
    for _ in range(12):
        time.sleep(1)
        estados = [p['estado'] for p in propuestas_desde(t0)]
        if estados and estados[-1] != 'PENDIENTE':
            return 'estado tras confirmar: ' + estados[-1]
    return 'sigue PENDIENTE tras tocar CONFIRMAR'


def asegurar_cuota():
    """El dueño autorizo (2026-09-25) poner en cero el contador diario del chat de SU usuario de prueba
    en el Redis local, en vez de reiniciar el backend con RENASIA_LIMITE_DIARIO. Solo esta clave, solo hoy."""
    clave = 'renasia:cuota:' + ACTOR + ':' + time.strftime('%Y-%m-%d')
    usado = sh(['docker', 'exec', 'renaser-redis', 'redis-cli', 'get', clave]).strip()
    if usado.isdigit() and int(usado) >= 23:
        sh(['docker', 'exec', 'renaser-redis', 'redis-cli', 'del', clave])
        print(f"[{time.strftime('%H:%M:%S')}] contador del chat en {usado}: puesto en cero", flush=True)


def main():
    casos = json.load(open(os.path.join(BASE, 'preguntas.json'), encoding='utf-8'))
    if len(sys.argv) > 1 and ',' in sys.argv[1]:
        orden = [int(i) for i in sys.argv[1].split(',')]
        elegidos = [c for i in orden for c in casos if c['id'] == i]
    else:
        desde = int(sys.argv[1]) if len(sys.argv) > 1 else 1
        hasta = int(sys.argv[2]) if len(sys.argv) > 2 else 10 ** 6
        elegidos = [c for c in casos if desde <= c['id'] <= hasta]
    if not abrir_panel():
        sys.exit('no pude abrir el panel del chat')
    sin_respuesta_seguidas = 0
    for caso in elegidos:
        texto = LARGO if caso['q'] == 'LARGO' else caso['q']
        asegurar_cuota()
        t0 = db('select now()').strip()
        inicio = time.time()
        try:
            escribir_y_enviar(texto)
        except RuntimeError as falla:
            print(f"[{time.strftime('%H:%M:%S')}] #{caso['id']:>3} reintento: {falla}", flush=True)
            subprocess.run(f"adb exec-out screencap -p > {BASE}/reintento-{caso['id']}.png", shell=True)
            abrir_panel()
            escribir_y_enviar(texto)
        respuesta, oidos = esperar_respuesta(t0)
        segundos = round(time.time() - inicio, 1)
        props = propuestas_desde(t0)
        extra = confirmar_ultima(t0) if caso.get('confirmar') and props else None
        props = propuestas_desde(t0) if extra else props
        motivos = ['sin respuesta guardada en 120 s'] if respuesta is None else evaluar(caso, respuesta, props)
        fila = {'id': caso['id'], 'grupo': caso['grupo'], 'q': texto[:160], 'guardado': oidos[:1],
                'respuesta': respuesta, 'propuestas': props, 'segundos': segundos,
                'ok': not motivos, 'motivos': motivos, 'confirmacion': extra, 'nota': caso.get('nota')}
        with open(SALIDA, 'a', encoding='utf-8') as f:
            f.write(json.dumps(fila, ensure_ascii=False) + '\n')
        marca = 'OK ' if not motivos else 'MAL'
        print(f"[{time.strftime('%H:%M:%S')}] #{caso['id']:>3} {marca} {segundos:>5}s "
              f"props={[p['herramienta'] for p in props]} {'; '.join(motivos)}", flush=True)
        if respuesta is None:
            subprocess.run(f"adb exec-out screencap -p > {BASE}/sin-respuesta-{caso['id']}.png", shell=True)
            sin_respuesta_seguidas += 1
            if sin_respuesta_seguidas >= 3:
                sys.exit('tres preguntas seguidas sin respuesta: corto (backend caido o tope diario)')
        else:
            sin_respuesta_seguidas = 0
        time.sleep(1.5)


if __name__ == '__main__':
    main()

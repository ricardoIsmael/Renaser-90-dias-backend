# -*- coding: utf-8 -*-
"""Trocea las transcripciones y las indexa en la base de conocimiento de Renasia.

POR QUE HAY QUE TROCEAR
  Cada texto que se indexa produce UN vector. Una clase entera de 6.000 palabras da un vector
  que es el promedio de todo lo que se hablo ahi: no se parece con precision a nada. Ademas el
  modelo de embeddings acepta ~2.048 tokens y una transcripcion completa son ~9.000: mandarla
  entera no queda peor, falla.

POR QUE LOS PEDAZOS SE SOLAPAN
  Un corte seco parte ideas al medio. Con solape, la frase que quedo cortada aparece completa
  en el pedazo siguiente, asi que la busqueda la encuentra igual.

POR QUE CADA CHUNK VA ATADO A SU LECCION
  `leccionId` es lo que hace que la recuperacion respete el gate de dia de programa: un aprendiz
  no puede recibir como cita el contenido de una leccion que todavia no desbloqueo. Los chunks
  con leccionId nulo NO se filtran nunca — por eso no se usa nulo aca.

Uso:
    python indexar.py <url_base> <token_sesion> [--limite N] [--desde N]
"""
import io, json, glob, os, sys, time, urllib.request, urllib.error

PALABRAS_POR_CHUNK = 450
SOLAPE = 60
DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'transcripciones', 'salida')


def trocear(texto):
    palabras = texto.split()
    if not palabras:
        return []
    pedazos, i = [], 0
    paso = PALABRAS_POR_CHUNK - SOLAPE
    while i < len(palabras):
        pedazos.append(' '.join(palabras[i:i + PALABRAS_POR_CHUNK]))
        if i + PALABRAS_POR_CHUNK >= len(palabras):
            break
        i += paso
    return pedazos


def publicar(url, token, cuerpo, reintentos=3):
    datos = json.dumps(cuerpo, ensure_ascii=False).encode('utf-8')
    for intento in range(reintentos):
        pedido = urllib.request.Request(url + '/api/v1/admin/conocimiento', data=datos, method='POST')
        pedido.add_header('Content-Type', 'application/json')
        pedido.add_header('X-Auth-Token', token)
        try:
            with urllib.request.urlopen(pedido, timeout=90) as r:
                return r.status, None
        except urllib.error.HTTPError as e:
            detalle = e.read().decode('utf-8', 'replace')[:200]
            # 4xx no se reintenta: el pedido esta mal y va a fallar igual
            if e.code < 500:
                return e.code, detalle
            if intento == reintentos - 1:
                return e.code, detalle
        except Exception as e:
            if intento == reintentos - 1:
                return 0, str(e)[:200]
        time.sleep(2 * (intento + 1))
    return 0, 'agotados los reintentos'


def main():
    url, token = sys.argv[1], sys.argv[2]
    limite = int(sys.argv[sys.argv.index('--limite') + 1]) if '--limite' in sys.argv else None
    desde = int(sys.argv[sys.argv.index('--desde') + 1]) if '--desde' in sys.argv else 0

    archivos = sorted(glob.glob(os.path.join(DIR, '*.json')))
    if limite:
        archivos = archivos[desde:desde + limite]
    else:
        archivos = archivos[desde:]

    total_ok = total_error = 0
    errores = []
    for n, ruta in enumerate(archivos, 1):
        d = json.load(io.open(ruta, encoding='utf-8'))
        pedazos = trocear(d['texto'])
        ok = err = 0
        for idx, pedazo in enumerate(pedazos):
            estado, detalle = publicar(url, token, {
                'tipoFuente': 'TRANSCRIPCION_CLASE',
                'clase': d['curso'],
                'documentoId': d['video_id'],
                'leccionId': d['leccion_id'],
                'contenido': pedazo,
                'metadatos': {
                    'titulo': d['titulo'],
                    'parte': '%d/%d' % (idx + 1, len(pedazos)),
                },
            })
            if estado in (200, 201):
                ok += 1
            else:
                err += 1
                if len(errores) < 5:
                    errores.append('HTTP %s -> %s' % (estado, detalle))
        total_ok += ok
        total_error += err
        print('[%3d/%d] %-45s %2d chunks  ok=%d err=%d' % (
            n, len(archivos), d['titulo'][:45], len(pedazos), ok, err), flush=True)
        if err and total_error > 20:
            print('DEMASIADOS ERRORES, se corta.', flush=True)
            break

    print('\n===== RESUMEN =====', flush=True)
    print('chunks indexados: %d   fallidos: %d' % (total_ok, total_error), flush=True)
    for e in errores:
        print('  ' + e, flush=True)


if __name__ == '__main__':
    main()

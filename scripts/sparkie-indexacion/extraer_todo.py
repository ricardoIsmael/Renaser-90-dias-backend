# -*- coding: utf-8 -*-
"""Extrae la transcripcion de las 124 lecciones con video de YouTube.

Reanudable: si el .json de una leccion ya existe, no la vuelve a pedir. Un video
caido o sin subtitulos NO corta la corrida -- se anota en el reporte y sigue.
"""
import io, json, os, re, subprocess, sys, time

BASE = os.path.dirname(os.path.abspath(__file__))
DIR = os.path.join(BASE, 'transcripciones')
OUT = os.path.join(DIR, 'salida')
VTT = os.path.join(DIR, 'vtt')
os.makedirs(OUT, exist_ok=True)
os.makedirs(VTT, exist_ok=True)


def id_de_youtube(url):
    m = re.search(r'(?:youtu\.be/|[?&]v=|/embed/|/shorts/)([A-Za-z0-9_-]{11})', url)
    return m.group(1) if m else None


def limpiar(path):
    """VTT de subtitulo automatico -> prosa. Deduplica la ventana deslizante."""
    texto = io.open(path, encoding='utf-8', errors='replace').read()
    lineas = []
    for linea in texto.splitlines():
        linea = linea.strip()
        if not linea or linea.startswith(('WEBVTT', 'Kind:', 'Language:', 'NOTE')):
            continue
        if '-->' in linea or re.fullmatch(r'\d+', linea):
            continue
        linea = re.sub(r'<[^>]+>', '', linea)
        linea = re.sub(r'\[[^\]]*\]', ' ', linea)   # [Musica], [Aplausos]
        linea = re.sub(r'\s+', ' ', linea).strip()
        if linea:
            lineas.append(linea)
    salida = []
    for l in lineas:
        if salida and (l == salida[-1] or l in salida[-1]):
            continue
        if salida and salida[-1] in l:
            salida[-1] = l
            continue
        salida.append(l)
    return ' '.join(salida)


def bajar(vid):
    """Devuelve la ruta del .vtt, o None. Prueba es-orig y cae a es."""
    for lang in ('es-orig', 'es'):
        destino = os.path.join(VTT, '%s.%s.vtt' % (vid, lang))
        if os.path.exists(destino):
            return destino
        # player_client=android es OBLIGATORIO: con el cliente por defecto y sin un runtime
        # de JavaScript instalado, YouTube responde "This video is not available" para videos
        # que en realidad estan perfectos (verificado contra QBJEp2iiZow, la clase del dia 2,
        # que reproduce sin problema en la app). El mensaje engania: no es un video borrado.
        cmd = ['yt-dlp', '--skip-download', '--write-auto-subs', '--sub-langs', lang,
               '--sub-format', 'vtt', '--no-warnings', '--no-progress',
               '--extractor-args', 'youtube:player_client=android',
               '-o', os.path.join(VTT, '%(id)s.%(ext)s'), 'https://youtu.be/' + vid]
        try:
            r = subprocess.run(cmd, capture_output=True, text=True, timeout=180)
        except subprocess.TimeoutExpired:
            return None
        if os.path.exists(destino):
            return destino
        if 'not available' in (r.stdout + r.stderr) or 'Private video' in (r.stdout + r.stderr):
            return None
    return None


filas = [l.rstrip('\n').split('\t') for l in io.open(os.path.join(DIR, 'lecciones.tsv'), encoding='utf-8') if l.strip()]
print('Lecciones a procesar: %d' % len(filas), flush=True)

ok, sin_subs, sin_id, saltadas = 0, [], [], 0
for i, fila in enumerate(filas, 1):
    if len(fila) < 5:
        continue
    lid, curso, titulo, len_html, url = fila[0], fila[1], fila[2], int(fila[3]), fila[4]
    destino_json = os.path.join(OUT, lid + '.json')
    if os.path.exists(destino_json):
        saltadas += 1
        ok += 1
        continue

    vid = id_de_youtube(url)
    if not vid:
        sin_id.append((titulo, url))
        print('[%3d/%d] SIN ID   %s | %s' % (i, len(filas), titulo[:45], url), flush=True)
        continue

    ruta = bajar(vid)
    if not ruta:
        sin_subs.append((curso, titulo, vid, url))
        print('[%3d/%d] FALLA    %s (%s)' % (i, len(filas), titulo[:45], vid), flush=True)
        continue

    texto = limpiar(ruta)
    registro = {
        'leccion_id': lid, 'curso': curso, 'titulo': titulo,
        'video_id': vid, 'video_url': url,
        'largo_cuerpo_html': len_html,
        'palabras': len(texto.split()), 'caracteres': len(texto),
        'texto': texto,
    }
    datos = json.dumps(registro, ensure_ascii=False, indent=1).encode('utf-8')
    tmp = destino_json + '.tmp'
    with open(tmp, 'wb') as f:
        f.write(datos)
    os.replace(tmp, destino_json)
    ok += 1
    print('[%3d/%d] OK %6d palabras  %s' % (i, len(filas), registro['palabras'], titulo[:45]), flush=True)
    time.sleep(1.0)

print('\n===== RESUMEN =====', flush=True)
print('Con transcripcion : %d  (reusadas de una corrida previa: %d)' % (ok, saltadas), flush=True)
print('Sin subtitulos/caidos: %d' % len(sin_subs), flush=True)
print('URL no parseable  : %d' % len(sin_id), flush=True)
for c, t, v, u in sin_subs:
    print('   FALLA: [%s] %s -> %s' % (c, t, u), flush=True)
for t, u in sin_id:
    print('   SIN ID: %s -> %s' % (t, u), flush=True)

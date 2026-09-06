# -*- coding: utf-8 -*-
"""Convierte un .vtt de subtitulos automaticos de YouTube en prosa plana.

Los subtitulos automaticos usan una ventana que se desplaza: cada cue repite la
linea anterior y agrega una palabra. Sin deduplicar, el texto sale ~3x mas largo
y con cada frase escrita varias veces -- inservible para indexar.
"""
import io, re, sys

def limpiar(path):
    texto = io.open(path, encoding='utf-8', errors='replace').read()
    lineas = []
    for linea in texto.splitlines():
        linea = linea.strip()
        if not linea or linea.startswith(('WEBVTT', 'Kind:', 'Language:', 'NOTE')):
            continue
        if '-->' in linea:
            continue
        if re.fullmatch(r'\d+', linea):
            continue
        linea = re.sub(r'<[^>]+>', '', linea)        # <00:00:01.234>, <c>, </c>
        linea = re.sub(r'\s+', ' ', linea).strip()
        if linea:
            lineas.append(linea)

    # Dedup de la ventana deslizante: se descarta la linea si ya esta contenida
    # en la cola de lo acumulado, y si es extension de la anterior se reemplaza.
    salida = []
    for l in lineas:
        if salida and (l == salida[-1] or l in salida[-1]):
            continue
        if salida and salida[-1] in l:
            salida[-1] = l
            continue
        salida.append(l)
    return ' '.join(salida)

for p in sys.argv[1:]:
    txt = limpiar(p)
    dest = p.replace('.es-orig.vtt', '.txt')
    io.open(dest, 'w', encoding='utf-8').write(txt)
    print("%s -> %s | %d palabras, %d chars" % (p, dest, len(txt.split()), len(txt)))

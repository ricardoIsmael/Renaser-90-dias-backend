#!/usr/bin/env python3
"""Resume por escalón la salida CSV de k6 (`--out csv=archivo.csv.gz`).

Para cada tramo sostenido del escenario (sin las rampas) da: usuarios virtuales, pedidos por
segundo, p50/p95/p99 de todos los pedidos, % de errores (http_req_failed), y los endpoints con peor
p95 en ese tramo. Sin dependencias fuera de la biblioteca estándar.

Uso: python3 analizar.py carga|estres|pico archivo.csv.gz [paso_estres=100] [inicio_estres=0]
"""
import csv
import gzip
import sys
from collections import defaultdict


def percentil(valores, p):
    if not valores:
        return float('nan')
    v = sorted(valores)
    k = (len(v) - 1) * p / 100
    i = int(k)
    return v[i] if i + 1 >= len(v) else v[i] + (v[i + 1] - v[i]) * (k - i)


def tramos(escenario, paso, inicio=0):
    """(etiqueta, desde_s, hasta_s) de cada tramo sostenido, relativo al arranque del escenario."""
    if escenario == 'carga':
        return [('25 usuarios', 30, 330), ('50 usuarios', 360, 660),
                ('100 usuarios', 690, 990), ('200 usuarios', 1020, 1320)]
    if escenario == 'estres':
        return [(f'{inicio + (i + 1) * paso} usuarios', i * 120 + 30, i * 120 + 120) for i in range(30)]
    if escenario == 'pico':
        return [('subida 0->150 (30 s)', 0, 30), ('150 sostenidos, 1er minuto', 30, 90),
                ('150 sostenidos, resto', 90, 210)]
    raise SystemExit('escenario desconocido')


def main():
    escenario, archivo = sys.argv[1], sys.argv[2]
    paso = int(sys.argv[3]) if len(sys.argv) > 3 else 100
    inicio = int(sys.argv[4]) if len(sys.argv) > 4 else 0
    dur = defaultdict(list)          # tramo -> duraciones
    por_ep = defaultdict(lambda: defaultdict(list))
    fallas = defaultdict(lambda: [0, 0])
    estados = defaultdict(lambda: defaultdict(int))
    vus = defaultdict(list)
    t0 = None
    filas = []
    with gzip.open(archivo, 'rt') as f:
        for r in csv.DictReader(f):
            m = r['metric_name']
            if m not in ('http_req_duration', 'http_req_failed', 'vus'):
                continue
            ts = float(r['timestamp'])
            if m == 'http_req_duration' and r['name'] not in ('POST /auth/login', 'POST /auth/logout') and t0 is None:
                t0 = ts
            filas.append((m, ts, float(r['metric_value']), r['name'], r['status']))
    if t0 is None:
        raise SystemExit('sin datos')
    defs = tramos(escenario, paso, inicio)
    for m, ts, val, nombre, estado in filas:
        if nombre in ('POST /auth/login', 'POST /auth/logout'):
            continue
        rel = ts - t0
        for etiqueta, desde, hasta in defs:
            if desde <= rel < hasta:
                if m == 'http_req_duration':
                    dur[etiqueta].append(val)
                    por_ep[etiqueta][nombre].append(val)
                    estados[etiqueta][estado] += 1
                elif m == 'http_req_failed':
                    fallas[etiqueta][0] += int(val)
                    fallas[etiqueta][1] += 1
                elif m == 'vus':
                    vus[etiqueta].append(val)
                break
    print(f'{"tramo":28} {"VUs":>5} {"req/s":>7} {"p50":>7} {"p95":>7} {"p99":>7} {"error%":>7} {"capac%":>7}  peores p95')
    for etiqueta, desde, hasta in defs:
        d = dur.get(etiqueta)
        if not d:
            continue
        f, n = fallas[etiqueta]
        peores = sorted(((percentil(v, 95), ep) for ep, v in por_ep[etiqueta].items() if len(v) >= 5),
                        reverse=True)[:4]
        texto = ', '.join(f'{ep} {p:.0f}' for p, ep in peores)
        vu = max(vus[etiqueta]) if vus[etiqueta] else 0
        # Errores de CAPACIDAD: corte/timeout (0), 429 y 5xx. Un 403 o 400 es una respuesta de negocio.
        cap = sum(c for e, c in estados[etiqueta].items() if e in ('0', '429') or e.startswith('5'))
        print(f'{etiqueta:28} {vu:5.0f} {len(d) / (hasta - desde):7.1f} {percentil(d, 50):7.0f} '
              f'{percentil(d, 95):7.0f} {percentil(d, 99):7.0f} {100 * f / max(n, 1):7.2f} {100 * cap / len(d):7.2f}  {texto}')
        malos = {k: v for k, v in estados[etiqueta].items() if not k.startswith('2')}
        if malos:
            print(f'{"":28} estados no-2xx: {dict(malos)}')


if __name__ == '__main__':
    main()

#!/usr/bin/env python3
"""Mide el router rapido de intencion (fase 6, ClasificarIntencionPort) con embeddings de Gemini.

Mismo algoritmo que rag.domain.model.intencion.ComparacionSemantica: coseno, cada etiqueta vale su
referencia mas parecida, margen = primera - segunda. Mide DOS cosas por separado:
  1) que herramienta pide el mensaje (intencion), contra frases de referencia por intencion;
  2) a cual de los habitos de HOY se refiere (conjunto cerrado), para los que marcan un habito.

    GOOGLE_GENAI_API_KEY=... python3 scripts/ia/router_eval.py [--task SEMANTIC_SIMILARITY]

Las frases de prueba NO repiten las de referencia (si no, la medicion se miente sola).
Los habitos son un FIXTURE de prueba, no el catalogo real del programa.
"""
import argparse
import json
import math
import os
import statistics
import sys
import time
import urllib.error
import urllib.request

MODELO = "gemini-embedding-001"
URL = f"https://generativelanguage.googleapis.com/v1beta/models/{MODELO}:batchEmbedContents"

REFERENCIAS = {
    "consultar_habitos_del_dia": [
        "que habitos tengo hoy", "que me falta hacer hoy", "como voy con mi dia",
        "cuales son mis tareas de hoy", "que me toca ahora",
    ],
    "consultar_puntos_en_juego": [
        "cuantos puntos puedo ganar hoy", "cuantos puntos me quedan", "cuanto estoy por perder",
        "cuantos puntos tengo en juego",
    ],
    "consultar_tiempo_para_puntos": [
        "llego a tiempo", "cuanto tiempo me queda", "cuanto pierdo si lo hago mas tarde",
        "hasta que hora tengo", "que se me vence primero",
    ],
    "consultar_horarios": [
        "a que hora me toca meditar", "cual es mi horario de manana", "cuantos cambios de horario me quedan",
        "a que hora tengo mis habitos el jueves",
    ],
    "marcar_habito_completado": [
        "ya lo hice marcalo", "listo ya termine ese habito", "registra que ya cumpli", "ponlo como hecho",
        "acabo de terminar",
    ],
    "conversar": [
        "hola", "buenos dias", "me siento cansado", "que es el programa", "gracias",
        "tengo una duda sobre la leccion", "estoy desanimado",
    ],
}

HABITOS_DE_HOY = {
    "r-agua": "Tomar 2 litros de agua",
    "r-leer": "Leer 10 paginas",
    "r-meditar": "Meditar 10 minutos",
    "r-caminar": "Caminar 30 minutos",
}

# (texto, intencion esperada, habito esperado o None). Incluye errores tipicos de STT y trampas.
CASOS = [
    ("oe que me falta", "consultar_habitos_del_dia", None),
    ("ke me falta asel hoy", "consultar_habitos_del_dia", None),
    ("que tengo pendiente pe", "consultar_habitos_del_dia", None),
    ("cuantos puntos ay en juego", "consultar_puntos_en_juego", None),
    ("cuanto puedo sumar todavia", "consultar_puntos_en_juego", None),
    ("alcanzo a hacerlo antes que se venza", "consultar_tiempo_para_puntos", None),
    ("si medito a las nueve pierdo algo", "consultar_tiempo_para_puntos", None),
    ("cual se me vence mas pronto", "consultar_tiempo_para_puntos", None),
    ("a que hora es mi caminata el viernes", "consultar_horarios", None),
    ("cuantas veces mas puedo cambiar mis horarios", "consultar_horarios", None),
    ("ya tome mi agua marcalo", "marcar_habito_completado", "r-agua"),
    ("ya tome awa", "marcar_habito_completado", "r-agua"),
    ("listo ya lei mis diez paginas", "marcar_habito_completado", "r-leer"),
    ("ya ley", "marcar_habito_completado", "r-leer"),
    ("termine de meditar ponlo", "marcar_habito_completado", "r-meditar"),
    ("ya medite", "marcar_habito_completado", "r-meditar"),
    ("acabo de caminar media hora", "marcar_habito_completado", "r-caminar"),
    ("ya camine registralo", "marcar_habito_completado", "r-caminar"),
    ("hola que tal", "conversar", None),
    ("hoy me siento cansado la verdad", "conversar", None),
    ("de que trata el programa de noventa dias", "conversar", None),
    ("voy a tomar agua mas tarde", "conversar", None),
    ("deberia leer hoy", "conversar", None),
    ("mmm eh", "conversar", None),
]


def embeber(textos, api_key, tarea):
    cuerpo = {"requests": [{"model": f"models/{MODELO}", "content": {"parts": [{"text": t}]},
                            "taskType": tarea, "outputDimensionality": 768} for t in textos]}
    pedido = urllib.request.Request(URL, data=json.dumps(cuerpo).encode(), method="POST",
                                    headers={"Content-Type": "application/json", "x-goog-api-key": api_key})
    with urllib.request.urlopen(pedido, timeout=60) as r:
        return [e["values"] for e in json.load(r)["embeddings"]]


def coseno(a, b):
    p = sum(x * y for x, y in zip(a, b))
    na = math.sqrt(sum(x * x for x in a))
    nb = math.sqrt(sum(y * y for y in b))
    return 0.0 if na == 0 or nb == 0 else p / (na * nb)


def mejor(vector, referencias):
    """referencias: lista de (etiqueta, vector). Devuelve (etiqueta, similitud, margen)."""
    por_etiqueta = {}
    for etiqueta, v in referencias:
        por_etiqueta[etiqueta] = max(por_etiqueta.get(etiqueta, -2), coseno(vector, v))
    orden = sorted(por_etiqueta.items(), key=lambda kv: kv[1], reverse=True)
    margen = orden[0][1] - orden[1][1] if len(orden) > 1 else orden[0][1]
    return orden[0][0], orden[0][1], margen


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--task", default="SEMANTIC_SIMILARITY",
                        help="taskType de Gemini (el backend hoy usa RETRIEVAL_DOCUMENT)")
    args = parser.parse_args()
    api_key = os.environ.get("GOOGLE_GENAI_API_KEY")
    if not api_key:
        sys.exit("Falta GOOGLE_GENAI_API_KEY")

    etiquetas = [(e, f) for e, frases in REFERENCIAS.items() for f in frases]
    inicio = time.perf_counter()
    v_refs = embeber([f for _, f in etiquetas], api_key, args.task)
    v_habitos = embeber(list(HABITOS_DE_HOY.values()), api_key, args.task)
    v_casos = embeber([c[0] for c in CASOS], api_key, args.task)
    ms_total = (time.perf_counter() - inicio) * 1000

    refs = [(etiquetas[i][0], v_refs[i]) for i in range(len(etiquetas))]
    refs_habitos = [(list(HABITOS_DE_HOY)[i], v_habitos[i]) for i in range(len(HABITOS_DE_HOY))]

    filas = []
    for (texto, esperada, habito), v in zip(CASOS, v_casos):
        intencion, sim, margen = mejor(v, refs)
        h = mejor(v, refs_habitos) if esperada == "marcar_habito_completado" else None
        ok_int = intencion == esperada
        ok_hab = h is None or h[0] == habito
        filas.append((texto, esperada, intencion, sim, margen, h, ok_int and ok_hab))
        marca = "OK " if ok_int and ok_hab else "MAL"
        extra = f" habito={h[0]}({h[1]:.3f}/{h[2]:.3f})" if h else ""
        print(f"{marca} sim={sim:.3f} margen={margen:.3f} {intencion:<30}{extra}  <- {texto!r}")

    n = len(filas)
    aciertos = sum(1 for f in filas if f[6])
    print(f"\ntaskType {args.task} · {n} casos · embeddings en {ms_total:.0f} ms (3 lotes)")
    print(f"exactitud total: {aciertos}/{n} ({aciertos / n:.0%})")
    # Umbrales candidatos: cuantos casos pasarian el umbral y cuantos de esos serian errores.
    for umbral_margen in (0.02, 0.04, 0.06, 0.08):
        sobre = [f for f in filas if f[4] >= umbral_margen]
        errores = [f for f in sobre if not f[6]]
        marcas_mal = [f for f in errores if f[2] == "marcar_habito_completado"]
        print(f"margen >= {umbral_margen:.2f}: decide solo en {len(sobre)}/{n}, "
              f"errores {len(errores)}, marcas equivocadas {len(marcas_mal)}")


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Mide si Jev (TypeSafe AI) sirve como router del tool calling del acompanante, en es-PE.

Ver docs/arquitectura/PROPUESTA_JEV_ROUTER_TOOL_CALLING.md. Sin dependencias: HTTP directo,
igual que lo haria el adaptador de Spring (no hay SDK Java).

    TYPESAFE_API_KEY=... python3 scripts/ia/jev_eval.py [--umbral 0.8]

Los habitos de abajo son un FIXTURE de prueba, no el catalogo real del programa.
"""
import argparse
import json
import os
import statistics
import sys
import time
import urllib.error
import urllib.request

ENDPOINT = "https://api.typesafe.ai/v1/systemone"
MODELO = "jev-1.13.0"  # fijado: los umbrales valen para una version, el alias jev-latest se mueve

HABITOS_DE_HOY = {
    "r-agua": "Tomar 2 litros de agua",
    "r-leer": "Leer 10 paginas",
    "r-meditar": "Meditar 10 minutos",
    "r-caminar": "Caminar 30 minutos",
}

INTENCIONES = {
    "consultar_habitos_del_dia": "Quiere saber que habitos le tocan hoy, cuales le faltan o como va su dia",
    "consultar_puntos_en_juego": "Quiere saber cuantos puntos puede ganar o esta por perder hoy",
    "marcar_habito_completado": "Dice que YA hizo un habito y quiere que se registre como hecho",
    "conversar": "Saluda, cuenta como se siente o pregunta algo que no es sobre sus habitos ni puntos",
    "no_se_entiende": "El texto no tiene sentido o no se puede saber que pide",
}

# (texto, intencion esperada, registro esperado o None). Incluye errores tipicos de STT.
CASOS = [
    ("que me toca hoy", "consultar_habitos_del_dia", None),
    ("oe que habitos me faltan", "consultar_habitos_del_dia", None),
    ("como voy con mis habitos del dia", "consultar_habitos_del_dia", None),
    ("ke me falta asel hoy", "consultar_habitos_del_dia", None),
    ("cuantos puntos puedo ganar todavia", "consultar_puntos_en_juego", None),
    ("cuanto estoy por perder si no hago nada", "consultar_puntos_en_juego", None),
    ("cuantos puntos ay en juego", "consultar_puntos_en_juego", None),
    ("ya tome mi agua marcalo", "marcar_habito_completado", "r-agua"),
    ("ya tome awa", "marcar_habito_completado", "r-agua"),
    ("listo ya lei mis diez paginas", "marcar_habito_completado", "r-leer"),
    ("ya ley", "marcar_habito_completado", "r-leer"),
    ("termine de meditar ponlo como hecho", "marcar_habito_completado", "r-meditar"),
    ("ya medite", "marcar_habito_completado", "r-meditar"),
    ("acabo de caminar media hora", "marcar_habito_completado", "r-caminar"),
    ("ya camine registralo", "marcar_habito_completado", "r-caminar"),
    ("hola buenas", "conversar", None),
    ("hoy me siento cansado la verdad", "conversar", None),
    ("que es el programa de 90 dias", "conversar", None),
    ("voy a tomar agua mas tarde", "conversar", None),  # intencion futura: NO debe marcar
    ("deberia leer hoy?", "conversar", None),  # pregunta: NO debe marcar
    ("asdf qwe", "no_se_entiende", None),
    ("mmm eh", "no_se_entiende", None),
]


def preguntar(api_key, texto):
    cuerpo = {
        "model": MODELO,
        "state": {"mensaje_del_aprendiz": texto, "habitos_de_hoy": list(HABITOS_DE_HOY.values())},
        "questions": {
            "intencion": {
                "type": "choice",
                "instructions": "Que le pide el aprendiz a su asistente de habitos con `mensaje_del_aprendiz`? "
                                "El texto viene de reconocimiento de voz y puede tener errores de ortografia.",
                "criteria": INTENCIONES,
            },
            "habito": {
                "type": "choice",
                "instructions": "A cual de `habitos_de_hoy` se refiere `mensaje_del_aprendiz`?",
                "criteria": {**HABITOS_DE_HOY, "ninguno": "No menciona ninguno de esos habitos"},
            },
            "pide_marcar": {
                "type": "noul",
                "instructions": "Afirma el aprendiz que YA completo un habito y pide registrarlo? "
                                "Una intencion futura o una pregunta NO cuenta.",
            },
        },
    }
    pedido = urllib.request.Request(
        ENDPOINT, data=json.dumps(cuerpo).encode(), method="POST",
        headers={"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"})
    inicio = time.perf_counter()
    with urllib.request.urlopen(pedido, timeout=10) as respuesta:
        datos = json.load(respuesta)
    return datos, (time.perf_counter() - inicio) * 1000


def evaluar(caso, datos, umbral):
    texto, intencion_esperada, registro_esperado = caso
    respuestas = datos["answers"]
    intencion = respuestas["intencion"]
    ok = intencion["choice"] == intencion_esperada
    if ok and registro_esperado:
        ok = respuestas["habito"]["choice"] == registro_esperado
    seguro = intencion["confidence"] >= umbral
    marca_mal = (seguro and intencion["choice"] == "marcar_habito_completado"
                 and (intencion_esperada != "marcar_habito_completado"
                      or respuestas["habito"]["choice"] != registro_esperado))
    return ok, seguro, marca_mal


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--umbral", type=float, default=0.8)
    args = parser.parse_args()
    api_key = os.environ.get("TYPESAFE_API_KEY")
    if not api_key:
        sys.exit("Falta TYPESAFE_API_KEY")

    latencias, aciertos, seguros, aciertos_seguros, marcas_mal = [], 0, 0, 0, 0
    for caso in CASOS:
        try:
            datos, ms = preguntar(api_key, caso[0])
        except urllib.error.HTTPError as error:
            sys.exit(f"HTTP {error.code}: {error.read().decode()}")
        ok, seguro, marca_mal = evaluar(caso, datos, args.umbral)
        latencias.append(ms)
        aciertos += ok
        seguros += seguro
        aciertos_seguros += ok and seguro
        marcas_mal += marca_mal
        r = datos["answers"]
        print(f"{'OK ' if ok else 'MAL'} {ms:6.0f}ms conf={r['intencion']['confidence']:.2f} "
              f"{r['intencion']['choice']:<27} {r['habito']['choice']:<10} "
              f"marcar={r['pide_marcar']['noul']:.2f}  <- {caso[0]!r}")

    n = len(CASOS)
    print(f"\nmodelo {MODELO} · umbral {args.umbral}")
    print(f"exactitud total         {aciertos}/{n} ({aciertos / n:.0%})")
    print(f"sobre el umbral         {seguros}/{n} (el resto se abstiene: pide aclaracion o pasa al LLM)")
    if seguros:
        print(f"exactitud sobre umbral  {aciertos_seguros}/{seguros} ({aciertos_seguros / seguros:.0%})  meta >= 95%")
    print(f"marcas equivocadas      {marcas_mal}  meta = 0")
    cuantiles = statistics.quantiles(latencias, n=20)
    print(f"latencia p50/p95        {statistics.median(latencias):.0f} / {cuantiles[18]:.0f} ms  meta p95 < 500")


if __name__ == "__main__":
    main()

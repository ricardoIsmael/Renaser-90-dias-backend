package com.renaser.os.habits.infrastructure.adapter.in.rest.carrera;

/** La ruta de una carrera (contrato §3): la polilínea codificada (algoritmo de Google) y cuántos puntos tiene. */
public record RutaDeCarreraResponse(String polyline, int puntos) {
}

package com.renaser.os.onboarding.domain.model.diagnostico;

import java.util.ArrayList;
import java.util.List;

/** Separa el texto de una hoja en registros de celdas: comillas, saltos de línea dentro de una celda y el separador de toda la hoja (tabulador, punto y coma o coma). */
final class HojaDeCalculo {

    private HojaDeCalculo() {
    }

    /** Separa el texto en registros de celdas, entendiendo comillas, saltos de línea dentro de una celda y el separador de toda la hoja. */
    static List<List<String>> registros(String texto) {
        String t = texto.startsWith("﻿") ? texto.substring(1) : texto;
        char separador = separadorDe(t);
        List<List<String>> registros = new ArrayList<>();
        List<String> fila = new ArrayList<>();
        StringBuilder celda = new StringBuilder();
        boolean entreComillas = false;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (entreComillas) {
                if (c == '"' && i + 1 < t.length() && t.charAt(i + 1) == '"') {
                    celda.append('"');
                    i++;
                } else if (c == '"') {
                    entreComillas = false;
                } else {
                    celda.append(c);
                }
            } else if (c == '"' && celda.toString().isBlank()) {
                entreComillas = true;
                celda.setLength(0);
            } else if (c == separador) {
                fila.add(celda.toString());
                celda.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < t.length() && t.charAt(i + 1) == '\n') {
                    i++;
                }
                fila.add(celda.toString());
                celda.setLength(0);
                registros.add(fila);
                fila = new ArrayList<>();
            } else {
                celda.append(c);
            }
        }
        if (celda.length() > 0 || !fila.isEmpty()) {
            fila.add(celda.toString());
            registros.add(fila);
        }
        return registros;
    }

    /** El separador de la hoja: el que más se repite en la cabecera (tabulador, punto y coma o coma). */
    private static char separadorDe(String t) {
        int fin = t.indexOf('\n');
        String cabecera = fin < 0 ? t : t.substring(0, fin);
        long tabs = cabecera.chars().filter(c -> c == '\t').count();
        long puntos = cabecera.chars().filter(c -> c == ';').count();
        long comas = cabecera.chars().filter(c -> c == ',').count();
        if (tabs >= puntos && tabs >= comas && tabs > 0) {
            return '\t';
        }
        return puntos > comas ? ';' : ',';
    }
}

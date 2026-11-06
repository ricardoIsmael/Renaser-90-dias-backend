package com.renaser.os.calendar.infrastructure.adapter.in.rest.evento;

import com.renaser.os.calendar.application.ports.in.audiencia.AudienciaRechazadaException;
import com.renaser.os.calendar.application.ports.in.audiencia.PedidoDeAudiencia;
import com.renaser.os.calendar.domain.model.evento.audiencia.ColorDeAudiencia;
import com.renaser.os.calendar.domain.model.evento.audiencia.ParteDeAudiencia;
import com.renaser.os.calendar.domain.model.evento.audiencia.TipoDeParte;

import java.util.List;
import java.util.UUID;

/**
 * La audiencia elegida en el cable (spec 044, contrato §1). Los nombres siguen al resto del contrato de eventos (en inglés): {@code PERSON},
 * {@code MY_LEARNERS}, {@code GROUP}, {@code MENTORS}, {@code TRAFFIC_LIGHT} (con {@code GREEN}, {@code YELLOW} o {@code RED}) y {@code STAFF}.
 */
record AudienciaWire(List<ParteWire> parts, Boolean recalculate) {

    record ParteWire(String kind, String id, String color) {
    }

    PedidoDeAudiencia aPedido() {
        List<ParteDeAudiencia> partes = parts == null ? List.of() : parts.stream().map(AudienciaWire::aParte).toList();
        return new PedidoDeAudiencia(partes, Boolean.TRUE.equals(recalculate));
    }

    private static ParteDeAudiencia aParte(ParteWire p) {
        if (p == null || p.kind() == null) {
            throw AudienciaRechazadaException.invalida("Falta el tipo de una de las opciones elegidas.");
        }
        try {
            TipoDeParte tipo = fromWireTipo(p.kind());
            UUID id = p.id() == null || p.id().isBlank() ? null : UUID.fromString(p.id());
            ColorDeAudiencia color = p.color() == null || p.color().isBlank() ? null : fromWireColor(p.color());
            return new ParteDeAudiencia(tipo, id, color);
        } catch (IllegalArgumentException mal) {
            throw AudienciaRechazadaException.invalida("Una de las opciones elegidas no se entiende.");
        }
    }

    static TipoDeParte fromWireTipo(String kind) {
        return switch (kind) {
            case "PERSON" -> TipoDeParte.PERSONA;
            case "MY_LEARNERS" -> TipoDeParte.MIS_ALUMNOS;
            case "GROUP" -> TipoDeParte.GRUPO;
            case "MENTORS" -> TipoDeParte.MENTORES;
            case "TRAFFIC_LIGHT" -> TipoDeParte.COLOR;
            case "STAFF" -> TipoDeParte.PERSONAL;
            default -> throw new IllegalArgumentException("kind desconocido: " + kind);
        };
    }

    static String toWireTipo(TipoDeParte tipo) {
        return switch (tipo) {
            case PERSONA -> "PERSON";
            case MIS_ALUMNOS -> "MY_LEARNERS";
            case GRUPO -> "GROUP";
            case MENTORES -> "MENTORS";
            case COLOR -> "TRAFFIC_LIGHT";
            case PERSONAL -> "STAFF";
        };
    }

    static ColorDeAudiencia fromWireColor(String color) {
        return switch (color) {
            case "GREEN" -> ColorDeAudiencia.VERDE;
            case "YELLOW" -> ColorDeAudiencia.AMARILLO;
            case "RED" -> ColorDeAudiencia.ROJO;
            default -> throw new IllegalArgumentException("color desconocido: " + color);
        };
    }

    static String toWireColor(ColorDeAudiencia color) {
        return switch (color) {
            case VERDE -> "GREEN";
            case AMARILLO -> "YELLOW";
            case ROJO -> "RED";
        };
    }
}

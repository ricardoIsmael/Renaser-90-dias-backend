package com.renaser.os.calendar.application.services;

import com.renaser.os.calendar.application.ports.in.meet.ResumenDeMeet;
import com.renaser.os.calendar.domain.model.evento.Evento;
import com.renaser.os.calendar.domain.model.meet.AccesoSalaMeet;
import com.renaser.os.calendar.domain.model.meet.EstadoSalaMeet;
import com.renaser.os.calendar.domain.model.meet.SalaMeet;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * Arma lo que ve Administración de las salas de un evento (spec 040, FR-019 y FR-023): el peor estado de sus fechas y una frase corta, sin
 * lenguaje técnico. Solo cuentan las fechas que todavía no pasaron (o, si ya pasaron todas, las que hay).
 */
final class ResumenesDeMeet {

    static final String SALA_CON_PERMISO_SIN_ABRIR = "La sala quedó con permiso; no pudimos dejarla abierta.";
    static final String SALA_ABIERTA_SIN_PERMISO = "La sala quedó abierta; no pudimos dejarla con permiso.";
    static final String SALA_SIN_COMPROBAR = "No pudimos comprobar quién puede entrar a la sala.";
    static final String FALLIDA = "No pudimos crear el enlace de Meet.";
    static final String CREANDOSE = "El enlace de Meet todavía se está creando.";
    static final String SIN_SINCRONIZAR = "Los cambios todavía no llegaron a Google.";

    private ResumenesDeMeet() {
    }

    /** {@code null} si el evento no pide Meet automático o todavía no tiene ninguna sala. */
    static ResumenDeMeet de(Evento evento, List<SalaMeet> salas, Instant ahora) {
        if (!evento.usaSalaDeMeetAutomatica()) {
            return null;
        }
        LocalDate hoy = ahora.atZone(evento.timezone()).toLocalDate();
        List<SalaMeet> vivas = salas.stream().filter(s -> s.estado() != EstadoSalaMeet.BORRADO).toList();
        List<SalaMeet> futuras = vivas.stream().filter(s -> !s.fechaOcurrencia().isBefore(hoy)).toList();
        List<SalaMeet> cuentan = futuras.isEmpty() ? vivas : futuras;
        if (cuentan.isEmpty()) {
            return new ResumenDeMeet(EstadoSalaMeet.PENDIENTE, evento.accesoSalaMeet(), false, CREANDOSE, 0, 0);
        }
        SalaMeet peor = cuentan.stream().max(Comparator.comparingInt(ResumenesDeMeet::gravedad)).orElseThrow();
        int conProblema = (int) cuentan.stream().filter(s -> gravedad(s) > 0).count();
        int conSala = (int) cuentan.stream().filter(s -> s.enlace() != null).count();
        boolean accesoAplicado = cuentan.stream().noneMatch(s -> s.estado() == EstadoSalaMeet.CREADO && s.accesoPendiente());
        return new ResumenDeMeet(peor.estado(), evento.accesoSalaMeet(), accesoAplicado, mensajeDe(peor), conSala, conProblema);
    }

    /** Cuánto pesa una fila para decidir el estado del evento: 0 = sin problema. */
    private static int gravedad(SalaMeet sala) {
        return switch (sala.estado()) {
            case FALLIDO -> 3;
            case PENDIENTE -> 2;
            case PENDIENTE_DE_SINCRONIZAR -> 2;
            case CREADO -> sala.accesoPendiente() ? 1 : 0;
            case BORRADO -> 0;
        };
    }

    private static String mensajeDe(SalaMeet peor) {
        return switch (peor.estado()) {
            case FALLIDO -> FALLIDA;
            case PENDIENTE -> CREANDOSE;
            case PENDIENTE_DE_SINCRONIZAR -> SIN_SINCRONIZAR;
            case CREADO -> peor.accesoPendiente() ? mensajeDelAcceso(peor) : null;
            case BORRADO -> null;
        };
    }

    private static String mensajeDelAcceso(SalaMeet sala) {
        if (sala.accesoConfirmado() == null) {
            return SALA_SIN_COMPROBAR;
        }
        return sala.accesoElegido() == AccesoSalaMeet.ABIERTA ? SALA_CON_PERMISO_SIN_ABRIR : SALA_ABIERTA_SIN_PERMISO;
    }
}

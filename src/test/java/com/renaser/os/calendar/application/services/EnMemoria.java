package com.renaser.os.calendar.application.services;

import com.renaser.os.calendar.application.ports.out.evento.LoadEventoPort;
import com.renaser.os.calendar.application.ports.out.evento.LoadExcepcionPort;
import com.renaser.os.calendar.application.ports.out.evento.SaveEventoPort;
import com.renaser.os.calendar.application.ports.out.meet.AuditoriaGooglePort;
import com.renaser.os.calendar.application.ports.out.meet.LoadCuentaGooglePort;
import com.renaser.os.calendar.application.ports.out.meet.LoadSalaMeetPort;
import com.renaser.os.calendar.application.ports.out.meet.SaveCuentaGooglePort;
import com.renaser.os.calendar.application.ports.out.meet.SaveSalaMeetPort;
import com.renaser.os.calendar.domain.model.evento.EstadoEvento;
import com.renaser.os.calendar.domain.model.evento.Evento;
import com.renaser.os.calendar.domain.model.evento.EventoId;
import com.renaser.os.calendar.domain.model.evento.Excepcion;
import com.renaser.os.calendar.domain.model.meet.CuentaDeGoogle;
import com.renaser.os.calendar.domain.model.meet.EstadoSalaMeet;
import com.renaser.os.calendar.domain.model.meet.RegistroDeAuditoriaGoogle;
import com.renaser.os.calendar.domain.model.meet.SalaMeet;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Los puertos de salida de las salas de Meet, en memoria, con la misma semántica que sus consultas SQL (spec 040). */
final class EnMemoria {

    private EnMemoria() {
    }

    static final class Eventos implements LoadEventoPort, SaveEventoPort, LoadExcepcionPort {

        final Map<EventoId, Evento> porId = new LinkedHashMap<>();
        final Map<EventoId, List<Excepcion>> excepciones = new HashMap<>();
        /** Cada vez que alguien pidió cambiar el enlace del evento. */
        final List<String> enlacesFijados = new ArrayList<>();

        @Override
        public Optional<Evento> byId(EventoId id) {
            return Optional.ofNullable(porId.get(id));
        }

        @Override
        public List<Evento> candidatosParaVisor(Instant desde, Instant hasta) {
            return porId.values().stream().filter(e -> e.estado() == EstadoEvento.PUBLICADO).toList();
        }

        @Override
        public List<Evento> candidatosParaRecordatorios(Instant ahora, Instant hastaMax, Instant desdeAnuncio) {
            return List.of();
        }

        @Override
        public Evento guardar(Evento evento) {
            porId.put(evento.id(), evento);
            return evento;
        }

        @Override
        public void eliminar(EventoId id) {
            porId.remove(id);
        }

        @Override
        public void fijarEnlaceDeMeet(EventoId id, String enlace) {
            Evento evento = porId.get(id);
            if (evento != null && evento.usaSalaDeMeetAutomatica()) {
                evento.fijarEnlaceDeMeet(enlace);
                enlacesFijados.add(enlace);
            }
        }

        @Override
        public List<Excepcion> porEvento(EventoId eventoId) {
            return excepciones.getOrDefault(eventoId, List.of());
        }

        @Override
        public Map<EventoId, List<Excepcion>> porEventos(Set<EventoId> eventoIds) {
            return eventoIds.stream().collect(Collectors.toMap(id -> id, this::porEvento));
        }

        List<EventoId> conSalaAutomatica() {
            return porId.values().stream().filter(Evento::usaSalaDeMeetAutomatica).map(Evento::id).toList();
        }
    }

    static final class Salas implements LoadSalaMeetPort, SaveSalaMeetPort {

        private final Map<UUID, SalaMeet> porId = new LinkedHashMap<>();
        private final Eventos eventos;

        Salas(Eventos eventos) {
            this.eventos = eventos;
        }

        @Override
        public List<SalaMeet> porEvento(EventoId eventoId) {
            return porId.values().stream().filter(s -> s.eventoId().equals(eventoId))
                    .sorted(Comparator.comparing(SalaMeet::fechaOcurrencia)).toList();
        }

        @Override
        public Map<EventoId, List<SalaMeet>> porEventos(Set<EventoId> eventoIds) {
            return eventoIds.stream().collect(Collectors.toMap(id -> id, this::porEvento));
        }

        @Override
        public List<EventoId> eventosConTrabajoPendiente(Instant ahora, int limite) {
            return porId.values().stream().filter(s -> s.tocaReintentar(ahora)).map(SalaMeet::eventoId).distinct().limit(limite).toList();
        }

        @Override
        public List<EventoId> eventosConSalaAutomatica(Instant ahora) {
            return eventos.conSalaAutomatica();
        }

        @Override
        public SalaMeet guardar(SalaMeet sala) {
            porId.put(sala.id(), sala);
            return sala;
        }

        @Override
        public void marcarParaSincronizar(EventoId eventoId, Instant ahora) {
            porEvento(eventoId).forEach(s -> s.pedirSincronizar(ahora));
        }

        @Override
        public void reencolarLoPendiente(Instant ahora) {
            porId.values().forEach(s -> s.reencolar(ahora));
        }

        @Override
        public void detenerLoPendiente(String motivo, Instant ahora) {
            for (SalaMeet sala : porId.values()) {
                if (sala.estado().faltaTrabajo() || sala.accesoPendiente()) {
                    if (sala.estado() == EstadoSalaMeet.PENDIENTE) {
                        sala.fallarAlCrear(motivo, ahora, null);
                    } else {
                        sala.fallarAlSincronizar(motivo, ahora, null);
                    }
                }
            }
        }

        @Override
        public int purgarLapidasAnterioresA(Instant limite) {
            List<UUID> viejas = porId.values().stream()
                    .filter(s -> s.estado() == EstadoSalaMeet.BORRADO && s.actualizadaEn().isBefore(limite)).map(SalaMeet::id).toList();
            viejas.forEach(porId::remove);
            return viejas.size();
        }

        List<SalaMeet> todas() {
            return new ArrayList<>(porId.values());
        }

        Optional<SalaMeet> deLaFecha(EventoId eventoId, LocalDate fecha) {
            return porEvento(eventoId).stream().filter(s -> s.fechaOcurrencia().equals(fecha)).findFirst();
        }
    }

    static final class Cuenta implements LoadCuentaGooglePort, SaveCuentaGooglePort {

        CuentaDeGoogle actual;

        @Override
        public Optional<CuentaDeGoogle> cargar() {
            return Optional.ofNullable(actual);
        }

        @Override
        public CuentaDeGoogle guardar(CuentaDeGoogle cuenta) {
            actual = cuenta;
            return cuenta;
        }

        @Override
        public void borrar() {
            actual = null;
        }
    }

    static final class Auditoria implements AuditoriaGooglePort {

        final List<RegistroDeAuditoriaGoogle> filas = new ArrayList<>();

        @Override
        public void registrar(RegistroDeAuditoriaGoogle registro) {
            filas.add(registro);
        }

        @Override
        public List<RegistroDeAuditoriaGoogle> ultimos(int cuantos) {
            return filas.stream().sorted(Comparator.comparing(RegistroDeAuditoriaGoogle::en).reversed()).limit(cuantos).toList();
        }

        @Override
        public int purgarAnterioresA(Instant limite) {
            int antes = filas.size();
            filas.removeIf(f -> f.en().isBefore(limite));
            return antes - filas.size();
        }
    }
}

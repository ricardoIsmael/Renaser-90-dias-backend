package com.renaser.os.calendar.application.services;

import com.renaser.os.calendar.application.ports.out.audiencia.AudienciaElegidaPort;
import com.renaser.os.calendar.application.ports.out.confirmacion.LoadConfirmacionPort;
import com.renaser.os.calendar.domain.model.confirmacion.Confirmacion;
import com.renaser.os.calendar.domain.model.confirmacion.EstadoConfirmacion;
import com.renaser.os.calendar.domain.model.evento.Evento;
import com.renaser.os.calendar.domain.model.evento.EventoId;
import com.renaser.os.calendar.domain.model.evento.RolUsuario;
import com.renaser.os.calendar.domain.model.evento.TipoAudiencia;
import com.renaser.os.calendar.domain.model.evento.TipoEvento;
import com.renaser.os.calendar.domain.model.evento.TipoUbicacion;
import com.renaser.os.calendar.domain.model.evento.audiencia.AudienciaElegida;
import com.renaser.os.calendar.domain.model.evento.audiencia.ColorDeAudiencia;
import com.renaser.os.calendar.domain.model.evento.audiencia.ParteDeAudiencia;
import com.renaser.os.calendar.domain.model.recordatorio.RecordatorioEvento;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Spec 044: al despachar, quien salió de la audiencia elegida o dijo «No voy» no recibe el recordatorio; los eventos de siempre no cambian. */
class AudienciaDeLosAvisosTest {

    private final MundoDeAudiencia m = new MundoDeAudiencia();
    private final FixedClock clock = FixedClock.at(MundoDeAudiencia.AHORA);
    private final Instant inicio = MundoDeAudiencia.AHORA.plusSeconds(86_400);
    private final Map<EventoId, AudienciaElegida> guardadas = new HashMap<>();
    private final List<Confirmacion> respuestas = new ArrayList<>();
    private long siguienteId = 1;

    private final AudienciaElegidaPort puerto = new AudienciaElegidaPort() {
        @Override
        public AudienciaElegida de(EventoId evento) {
            return guardadas.getOrDefault(evento, AudienciaElegida.vacia());
        }

        @Override
        public Map<EventoId, AudienciaElegida> deEventos(Collection<EventoId> eventos) {
            Map<EventoId, AudienciaElegida> r = new HashMap<>();
            eventos.forEach(e -> Optional.ofNullable(guardadas.get(e)).ifPresent(a -> r.put(e, a)));
            return r;
        }

        @Override
        public void reemplazar(EventoId evento, AudienciaElegida audiencia) {
            guardadas.put(evento, audiencia);
        }
    };

    private final LoadConfirmacionPort confirmaciones = new LoadConfirmacionPort() {
        @Override
        public Map<String, EstadoConfirmacion> paraVisor(UserId usuarioId, Set<EventoId> eventoIds) {
            return Map.of();
        }

        @Override
        public Set<String> confirmadosAsistencia(EventoId eventoId, List<Instant> ocurrencias) {
            return Set.of();
        }

        @Override
        public Optional<EstadoConfirmacion> estadoDe(EventoId eventoId, Instant inicioOcurrencia, UserId usuarioId) {
            return Optional.empty();
        }

        @Override
        public List<Confirmacion> deOcurrencia(EventoId eventoId, Instant inicioOcurrencia) {
            return respuestas.stream().filter(c -> c.eventoId().equals(eventoId) && c.inicioOcurrencia().equals(inicioOcurrencia)).toList();
        }
    };

    private final AudienciaDelEventoService audiencia = new AudienciaDelEventoService(null, null, null, null, null, null, null, puerto,
            m.servicio);
    private final AudienciaDeLosAvisos avisos = new AudienciaDeLosAvisos(audiencia, confirmaciones);

    private Evento evento(TipoAudiencia tipo, UserId creador) {
        return Evento.crear(EventoId.of(UUID.randomUUID()), "Sesión", null, inicio, 60, ZoneId.of("America/Lima"), TipoUbicacion.MEET,
                "https://meet.google.com/abc", tipo, null, null, null, TipoEvento.SESION_ESPECIAL, false, false, false, null, Set.of(),
                List.of(), creador, clock, Formacion.ESCUELA, null);
    }

    private RecordatorioEvento aviso(Evento evento, UserId persona) {
        return RecordatorioEvento.rehydrate(siguienteId++, evento.id(), inicio, persona, inicio.minusSeconds(600), null, null,
                MundoDeAudiencia.AHORA);
    }

    private Evento conRojos() {
        Evento e = evento(TipoAudiencia.SELECCION, m.lider);
        guardadas.put(e.id(), m.servicio.armar(m.lider, RolUsuario.MENTOR_LEAD, Formacion.ESCUELA,
                List.of(ParteDeAudiencia.color(ColorDeAudiencia.ROJO)), null, AudienciaElegida.vacia(), true, MundoDeAudiencia.AHORA));
        return e;
    }

    // spec 044 FR-020, SC-002
    @Test
    @DisplayName("FR-020: los destinatarios son los invitados y quien creó el evento")
    void destinatarios() {
        Evento e = conRojos();

        assertThat(audiencia.destinatarios(e)).containsExactlyInAnyOrder(m.rojo1, m.rojo2, m.deOtroGrupo, m.lider);
    }

    // spec 044 FR-022
    @Test
    @DisplayName("FR-022: a quien dijo «No voy» se le cancela el recordatorio con dijo_no_voy; a los demás no")
    void quienDijoNoVoy() {
        Evento e = conRojos();
        respuestas.add(new Confirmacion(e.id(), inicio, m.rojo1, EstadoConfirmacion.NO_ASISTE, MundoDeAudiencia.AHORA, MundoDeAudiencia.AHORA));
        respuestas.add(new Confirmacion(e.id(), inicio, m.rojo2, EstadoConfirmacion.ASISTE, MundoDeAudiencia.AHORA, MundoDeAudiencia.AHORA));
        RecordatorioEvento delQueNoVa = aviso(e, m.rojo1);
        RecordatorioEvento delQueVa = aviso(e, m.rojo2);
        RecordatorioEvento delQueNoRespondio = aviso(e, m.deOtroGrupo);

        Map<Long, String> motivos = avisos.aCancelar(List.of(delQueNoVa, delQueVa, delQueNoRespondio), Map.of(e.id(), Optional.of(e)));

        assertThat(motivos).containsExactly(Map.entry(delQueNoVa.id(), RecordatorioEvento.MOTIVO_DIJO_NO_VOY));
    }

    // spec 044 FR-023
    @Test
    @DisplayName("FR-023: quien ya no está en la audiencia (salió del grupo) no recibe el recordatorio")
    void quienSalioDeLaAudiencia() {
        Evento e = evento(TipoAudiencia.SELECCION, m.mentor);
        guardadas.put(e.id(), m.servicio.armar(m.mentor, RolUsuario.MENTOR, Formacion.ESCUELA,
                List.of(ParteDeAudiencia.sola(com.renaser.os.calendar.domain.model.evento.audiencia.TipoDeParte.MIS_ALUMNOS)), m.mentor,
                AudienciaElegida.vacia(), true, MundoDeAudiencia.AHORA));
        RecordatorioEvento delQueSalio = aviso(e, m.verde);
        m.integrantes.get(m.grupo).remove(m.verde);

        Map<Long, String> motivos = avisos.aCancelar(List.of(delQueSalio, aviso(e, m.rojo1)), Map.of(e.id(), Optional.of(e)));

        assertThat(motivos).containsExactly(Map.entry(delQueSalio.id(), RecordatorioEvento.MOTIVO_FUERA_DE_LA_AUDIENCIA));
    }

    // spec 044 FR-015, SC-008, FR-031
    @Test
    @DisplayName("FR-015: un evento de los de siempre no se toca, aunque alguien haya dicho «No voy»")
    void losEventosDeSiempreNoCambian() {
        Evento e = evento(TipoAudiencia.TODOS, m.admin);
        respuestas.add(new Confirmacion(e.id(), inicio, m.rojo1, EstadoConfirmacion.NO_ASISTE, MundoDeAudiencia.AHORA, MundoDeAudiencia.AHORA));

        assertThat(avisos.aCancelar(List.of(aviso(e, m.rojo1)), Map.of(e.id(), Optional.of(e)))).isEmpty();
    }
}

package com.renaser.os.calendar.infrastructure.adapter.in.rest.evento;

import com.renaser.os.calendar.application.ports.in.asistencia.PasarListaUseCase.ListaDeAsistencia;
import com.renaser.os.calendar.application.ports.in.asistencia.PersonaConvocada;
import com.renaser.os.calendar.application.ports.in.asistencia.VerRespuestasDelEventoUseCase.RespuestasDelEvento;
import com.renaser.os.calendar.domain.model.asistencia.MarcaDeAsistencia;
import com.renaser.os.calendar.domain.model.asistencia.RespuestaAnterior;
import com.renaser.os.calendar.domain.model.confirmacion.EstadoConfirmacion;

import java.time.Instant;
import java.util.List;

/**
 * Lo que devuelven los endpoints de asistencia (D-256, {@code docs/api/CONTRATO_ASISTENCIA_EVENTOS.md}).
 * Mapeo a mano, campo por campo: nada del dominio llega al cliente sin pasar por acá.
 */
final class AsistenciaResponses {

    private AsistenciaResponses() {
    }

    /** {@code status}: GOING, NOT_GOING, MAYBE o {@code null} (no respondió). */
    record RespuestaWire(String status, String at) {
    }

    record PersonaRespuestaWire(String userId, String fullName, String avatarUrl, String status, String respondedAt,
                                List<RespuestaWire> history) {
    }

    record RespuestasWire(String occurrenceStart, List<PersonaRespuestaWire> people) {
    }

    /** {@code estado}: A_TIEMPO, TARDE o {@code null} (ausente / sin marcar). */
    record PersonaListaWire(String userId, String fullName, String avatarUrl, String status, String respondedAt,
                            String estado, String markedAt) {
    }

    record CierreWire(String at, String byUserId, String byName) {
    }

    record ListaWire(String occurrenceStart, String opensAt, String closesAt, boolean open, CierreWire closed,
                     List<PersonaListaWire> people) {
    }

    static RespuestasWire de(RespuestasDelEvento r) {
        return new RespuestasWire(r.inicioOcurrencia().toString(),
                r.personas().stream().map(AsistenciaResponses::respuestaDe).toList());
    }

    static ListaWire de(ListaDeAsistencia l) {
        CierreWire cierre = l.cierre() == null ? null : new CierreWire(l.cierre().cerradaEn().toString(),
                l.cierre().cerradaPor() == null ? null : l.cierre().cerradaPor().toString(), l.cerradaPorNombre());
        return new ListaWire(l.inicioOcurrencia().toString(), l.ventana().abre().toString(),
                l.ventana().cierra().toString(), l.abiertaAhora(), cierre,
                l.personas().stream().map(AsistenciaResponses::filaDe).toList());
    }

    static PersonaListaWire filaDe(PersonaConvocada p) {
        MarcaDeAsistencia marca = p.marca();
        return new PersonaListaWire(p.id().toString(), p.nombre(), p.avatarUrl(), estado(p.respuesta()),
                texto(p.respondidaEn()), marca == null ? null : marca.estado().name(),
                marca == null ? null : marca.marcadoEn().toString());
    }

    private static PersonaRespuestaWire respuestaDe(PersonaConvocada p) {
        List<RespuestaWire> historial = p.historial().stream()
                .map((RespuestaAnterior r) -> new RespuestaWire(estado(r.estado()), r.registradaEn().toString()))
                .toList();
        return new PersonaRespuestaWire(p.id().toString(), p.nombre(), p.avatarUrl(), estado(p.respuesta()),
                texto(p.respondidaEn()), historial);
    }

    private static String estado(EstadoConfirmacion estado) {
        return estado == null ? null : EventoWireMapper.toWireEstadoConfirmacion(estado);
    }

    private static String texto(Instant instante) {
        return instante == null ? null : instante.toString();
    }
}

package com.renaser.os.calendar.infrastructure.adapter.out.persistence.evento;

import com.renaser.os.calendar.domain.model.evento.EstadoEvento;
import com.renaser.os.calendar.domain.model.evento.Evento;
import com.renaser.os.calendar.domain.model.evento.EventoId;
import com.renaser.os.calendar.domain.model.evento.FrecuenciaRecurrencia;
import com.renaser.os.calendar.domain.model.evento.Recurrencia;
import com.renaser.os.calendar.domain.model.evento.ReglaRecordatorio;
import com.renaser.os.calendar.domain.model.evento.RolUsuario;
import com.renaser.os.calendar.domain.model.evento.TipoAudiencia;
import com.renaser.os.calendar.domain.model.evento.TipoEvento;
import com.renaser.os.calendar.domain.model.evento.TipoReglaRecordatorio;
import com.renaser.os.calendar.domain.model.evento.TipoUbicacion;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
class EventoPersistenceMapper {

    private final RolesCatalogoCache rolesCatalogo;

    EventoPersistenceMapper(RolesCatalogoCache rolesCatalogo) {
        this.rolesCatalogo = rolesCatalogo;
    }

    Evento toDomain(EventoJpaEntity e, RecurrenciaJpaEntity recurrenciaJpa,
                     List<DiaSemanaRecurrenciaJpaEntity> diasSemana, List<RolDestinoEventoJpaEntity> rolesDestino,
                     List<ReglaRecordatorioEventoJpaEntity> reglas) {
        Recurrencia recurrencia = recurrenciaJpa == null ? null : toDomainRecurrencia(recurrenciaJpa, diasSemana);
        Set<RolUsuario> roles = rolesDestino.stream().map(r -> rolesCatalogo.rolDe(r.getRolId())).collect(Collectors.toSet());
        List<ReglaRecordatorio> reglasRecordatorio = reglas.stream().map(this::toDomainRegla).toList();

        return Evento.rehydrate(EventoId.of(e.getId()), e.getTitulo(), e.getDescripcion(), e.getPortadaRuta(),
                e.getIniciaEn(), e.getDuracionMinutos(), ZoneId.of(e.getTimezone()), toDomainUbicacion(e.getTipoUbicacion()),
                e.getValorUbicacion(), toDomainAudiencia(e.getTipoAudiencia()),
                e.getNivelMinimoId() == null ? null : e.getNivelMinimoId().intValue(), e.getCursoId(),
                e.getCelulaDestinoId(), toDomainEstado(e.getEstado()), toDomainTipoEvento(e.getTipoEvento()),
                e.isNotificarAlCrear(), e.isRecordarPorEmail(), e.isRecordatoriosPersonalizados(), recurrencia, roles,
                reglasRecordatorio, e.getCreadoPor() == null ? null : UserId.of(e.getCreadoPor()), e.getCreadoEn(),
                e.getActualizadoEn());
    }

    EventoJpaEntity toEntity(Evento ev) {
        return new EventoJpaEntity(ev.id().value(), ev.titulo(), ev.descripcion(), ev.portadaRuta(), ev.iniciaEn(),
                ev.duracionMinutos(), ev.timezone().getId(), toJpaUbicacion(ev.tipoUbicacion()), ev.valorUbicacion(),
                toJpaAudiencia(ev.tipoAudiencia()), ev.nivelMinimoId() == null ? null : ev.nivelMinimoId().shortValue(),
                ev.cursoId(), ev.celulaDestinoId(), toJpaEstado(ev.estado()), toJpaTipoEvento(ev.tipoEvento()),
                ev.notificarAlCrear(), ev.recordarPorEmail(), ev.recordatoriosPersonalizados(),
                ev.creadoPor() == null ? null : ev.creadoPor().value(), ev.creadoEn(), ev.actualizadoEn());
    }

    RecurrenciaJpaEntity toEntityRecurrencia(EventoId eventoId, Recurrencia r) {
        return new RecurrenciaJpaEntity(eventoId.value(), toJpaFrecuencia(r.frecuencia()), (short) r.intervalo(),
                r.hasta(), r.repeticiones() == null ? null : r.repeticiones().shortValue());
    }

    List<DiaSemanaRecurrenciaJpaEntity> toEntityDiasSemana(EventoId eventoId, Set<DayOfWeek> dias) {
        return dias.stream().map(d -> new DiaSemanaRecurrenciaJpaEntity(eventoId.value(), aDiaSemanaBd(d))).toList();
    }

    List<RolDestinoEventoJpaEntity> toEntityRolesDestino(EventoId eventoId, Set<RolUsuario> roles) {
        return roles.stream()
                .map(r -> new RolDestinoEventoJpaEntity(eventoId.value(), rolesCatalogo.idDe(r)))
                .toList();
    }

    List<ReglaRecordatorioEventoJpaEntity> toEntityReglas(EventoId eventoId, List<ReglaRecordatorio> reglas) {
        return reglas.stream().map(r -> new ReglaRecordatorioEventoJpaEntity(eventoId.value(), (short) r.orden(),
                toJpaTipoRegla(r.tipo()), r.valorNumero(), r.valorHora())).toList();
    }

    private Recurrencia toDomainRecurrencia(RecurrenciaJpaEntity r, List<DiaSemanaRecurrenciaJpaEntity> dias) {
        Set<DayOfWeek> diasSemana = dias.stream().map(d -> deDiaSemanaBd(d.getDiaSemana())).collect(Collectors.toSet());
        return new Recurrencia(toDomainFrecuencia(r.getFrecuencia()), r.getIntervalo(), r.getHasta(),
                r.getRepeticiones() == null ? null : r.getRepeticiones().intValue(), diasSemana);
    }

    private ReglaRecordatorio toDomainRegla(ReglaRecordatorioEventoJpaEntity r) {
        return new ReglaRecordatorio(r.getOrden(), toDomainTipoRegla(r.getTipoRegla()), r.getValorNumero(), r.getValorHora());
    }

    /** dominio: DayOfWeek ISO (1=lunes..7=domingo) -> BD: smallint (0=domingo..6=sabado, convencion del baseline). */
    private static short aDiaSemanaBd(DayOfWeek dia) {
        return (short) (dia.getValue() % 7);
    }

    private static DayOfWeek deDiaSemanaBd(short bd) {
        return DayOfWeek.of(bd == 0 ? 7 : bd);
    }

    // ─── Enums ──────────────────────────────────────────────────────────────────

    private static TipoUbicacion toDomainUbicacion(TipoUbicacionJpa j) {
        return TipoUbicacion.valueOf(j.name());
    }

    private static TipoUbicacionJpa toJpaUbicacion(TipoUbicacion d) {
        return TipoUbicacionJpa.valueOf(d.name());
    }

    private static TipoAudiencia toDomainAudiencia(TipoAudienciaJpa j) {
        return TipoAudiencia.valueOf(j.name());
    }

    private static TipoAudienciaJpa toJpaAudiencia(TipoAudiencia d) {
        return TipoAudienciaJpa.valueOf(d.name());
    }

    private static EstadoEvento toDomainEstado(EstadoEventoJpa j) {
        return EstadoEvento.valueOf(j.name());
    }

    private static EstadoEventoJpa toJpaEstado(EstadoEvento d) {
        return EstadoEventoJpa.valueOf(d.name());
    }

    private static TipoEvento toDomainTipoEvento(TipoEventoJpa j) {
        return TipoEvento.valueOf(j.name());
    }

    private static TipoEventoJpa toJpaTipoEvento(TipoEvento d) {
        return TipoEventoJpa.valueOf(d.name());
    }

    private static FrecuenciaRecurrencia toDomainFrecuencia(FrecuenciaRecurrenciaJpa j) {
        return FrecuenciaRecurrencia.valueOf(j.name());
    }

    private static FrecuenciaRecurrenciaJpa toJpaFrecuencia(FrecuenciaRecurrencia d) {
        return FrecuenciaRecurrenciaJpa.valueOf(d.name());
    }

    private static TipoReglaRecordatorio toDomainTipoRegla(TipoReglaRecordatorioJpa j) {
        return TipoReglaRecordatorio.valueOf(j.name());
    }

    private static TipoReglaRecordatorioJpa toJpaTipoRegla(TipoReglaRecordatorio d) {
        return TipoReglaRecordatorioJpa.valueOf(d.name());
    }
}

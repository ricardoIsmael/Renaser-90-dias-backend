package com.renaser.os.leadership.application.services;

import com.renaser.os.leadership.application.ports.in.IndicadoresDeMentor;
import com.renaser.os.leadership.application.ports.out.LeerObservacionesPort;
import com.renaser.os.leadership.application.services.GrupoDelMentorEnLaSemana.Resultado;
import com.renaser.os.leadership.domain.model.atencion.AtencionDeTickets;
import com.renaser.os.leadership.domain.model.documento.DatosDelMentor;
import com.renaser.os.leadership.domain.model.documento.DatosDelMentor.Encabezado;
import com.renaser.os.leadership.domain.model.documento.DatosDelMentor.LoQueLeDijoElLider;
import com.renaser.os.leadership.domain.model.documento.DatosDelMentor.Pendientes;
import com.renaser.os.leadership.domain.model.documento.ReglasDelMentorEnLaSemana;
import com.renaser.os.leadership.domain.model.documento.SemanaDelDocumento;
import com.renaser.os.leadership.domain.model.observacion.ObservacionDeMentor;
import com.renaser.os.leadership.domain.model.periodo.MesDelReporte;
import com.renaser.os.mentoring.api.EvaluacionDeMentorFinder.EvaluacionDeMentor;
import com.renaser.os.mentoring.api.MedicionDeMentoresFinder.GrupoMedido;
import com.renaser.os.shared.domain.RechazoConCodigoException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.FichaDeMentorFinder.FichaDeMentor;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Reúne los datos del documento semanal de un mentor (spec 056, FR-036): su grupo en la semana ({@link GrupoDelMentorEnLaSemana}), sus pendientes,
 * lo que el líder le dijo esa semana y su propia semana si sigue su programa. <b>No autoriza</b>: quien lo llama ya exigió el liderazgo y que el
 * mentor sea de las formaciones de quien pide.
 *
 * <p>Nunca lee las notas privadas del mentor sobre un aprendiz (FR-037). Lo que el líder le dijo ({@code observaciones_mentor}) es otra cosa: es
 * de ÉL y es justamente lo que el documento cuenta. Una fuente que no responde sale «no disponible», nunca como cero (RL-21).
 */
@Component
class ArmadoDelDocumentoDelMentor {

    private static final int TOPE_DE_OBSERVACIONES = 50;

    private final IndicadoresDeMentores indicadores;
    private final GrupoDelMentorEnLaSemana grupo;
    private final LeerObservacionesPort observaciones;
    private final UserSummaryFinder usuarios;
    private final ProgramaDelMentor programa;
    private final FormacionDeLaPersona formacion;

    ArmadoDelDocumentoDelMentor(IndicadoresDeMentores indicadores, GrupoDelMentorEnLaSemana grupo, LeerObservacionesPort observaciones,
                                UserSummaryFinder usuarios, ProgramaDelMentor programa, FormacionDeLaPersona formacion) {
        this.indicadores = indicadores;
        this.grupo = grupo;
        this.observaciones = observaciones;
        this.usuarios = usuarios;
        this.programa = programa;
        this.formacion = formacion;
    }

    /**
     * @param semanaHasta el viernes que cierra la semana; null = la semana en curso
     * @throws RechazoConCodigoException {@code SEMANA_INVALIDA} o {@code FUERA_DEL_PROGRAMA}
     */
    DatosDelMentor armar(UserId actor, FichaDeMentor mentor, LocalDate semanaHasta, Instant ahora) {
        ZoneId zona = programa.zonaDe(mentor.id());
        LocalDate hoy = ahora.atZone(zona).toLocalDate();
        LocalDate viernes = SemanaDelDocumento.viernes(semanaHasta, hoy);
        LocalDate sabado = viernes.minusDays(6);
        IndicadoresDeMentores.Lectura lectura = indicadores.leer(List.of(mentor), mesDeLaSemana(viernes, hoy, ahora), formacion.alcanceDe(actor));
        IndicadoresDeMentor suyos = lectura.indicadores().getFirst();
        List<GrupoMedido> grupos = lectura.medicion() == null ? List.of()
                : lectura.medicion().grupos().stream().filter(g -> mentor.id().equals(g.mentorId())).toList();
        Resultado delGrupo = grupo.leer(grupos, viernes);
        List<LoQueLeDijoElLider> dichas = loQueLeDijo(mentor.id(), sabado, viernes, zona);
        Pendientes pendientes = pendientes(suyos, delGrupo);
        return new DatosDelMentor(new Encabezado(mentor.nombreCompleto(), formacion.nombreDe(mentor.id()), grupos.size(), sabado,
                viernes, zona.getId()), delGrupo.titular(), delGrupo.conteo(), delGrupo.aprendices(), pendientes, delGrupo.alertas(), dichas,
                ReglasDelMentorEnLaSemana.foco(delGrupo.enRojoPorEscribir().size(), pendientes.consultas() == null ? 0 : pendientes.consultas(),
                        delGrupo.alertas().size()), programa.suSemana(actor, mentor.id(), viernes, ahora));
    }

    /**
     * El mes de la evaluación: el del viernes de la semana o, si la semana corre, el de hoy. El reporte se lee en la zona del programa (Lima) y no acepta un mes
     * que ahí todavía no empezó, así que una persona en una zona adelantada nunca pide el mes que viene.
     */
    private static MesDelReporte mesDeLaSemana(LocalDate viernes, LocalDate hoy, Instant ahora) {
        YearMonth delPrograma = YearMonth.from(ahora.atZone(MesDelReporte.ZONA_DEL_PROGRAMA));
        YearMonth pedido = YearMonth.from(viernes.isAfter(hoy) ? hoy : viernes);
        return MesDelReporte.pedido((pedido.isAfter(delPrograma) ? delPrograma : pedido).toString(), MesDelReporte.ZONA_DEL_PROGRAMA, ahora);
    }

    private static Pendientes pendientes(IndicadoresDeMentor suyos, Resultado delGrupo) {
        AtencionDeTickets atencion = suyos.atencion().disponible() ? suyos.atencion().valor() : null;
        EvaluacionDeMentor evaluacion = suyos.evaluacion().disponible() ? suyos.evaluacion().valor() : null;
        return new Pendientes(atencion == null ? null : atencion.pendientes(), atencion == null ? null : atencion.diasDelMasAntiguo(),
                evaluacion == null ? null : evaluacion.estado(), evaluacion == null ? null : evaluacion.porcentaje(), delGrupo.enRojoPorEscribir());
    }

    /** Lo que el líder le dijo en esa semana, de la más vieja a la más nueva; el nombre de quien lo dijo, no su id. */
    private List<LoQueLeDijoElLider> loQueLeDijo(UserId mentor, LocalDate sabado, LocalDate viernes, ZoneId zona) {
        Instant desde = sabado.atStartOfDay(zona).toInstant();
        Instant hasta = viernes.plusDays(1).atStartOfDay(zona).toInstant();
        List<ObservacionDeMentor> deLaSemana = observaciones.deMentor(mentor, hasta, TOPE_DE_OBSERVACIONES).stream()
                .filter(observacion -> !observacion.creadoEn().isBefore(desde)).toList();
        Map<UserId, UserSummary> autores = usuarios.findByIds(deLaSemana.stream().map(ObservacionDeMentor::autorId).distinct().toList());
        return deLaSemana.stream().sorted(Comparator.comparing(ObservacionDeMentor::creadoEn))
                .map(observacion -> new LoQueLeDijoElLider(observacion.tipo().name(), observacion.creadoEn(),
                        autores.containsKey(observacion.autorId()) ? autores.get(observacion.autorId()).fullName() : null, observacion.texto()))
                .toList();
    }
}

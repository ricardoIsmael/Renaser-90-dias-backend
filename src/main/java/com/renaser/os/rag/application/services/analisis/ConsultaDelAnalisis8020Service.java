package com.renaser.os.rag.application.services.analisis;

import com.renaser.os.rag.api.Analisis8020Finder;
import com.renaser.os.rag.application.ports.in.analisis.ConsultarAnalisis8020UseCase;
import com.renaser.os.rag.application.ports.out.analisis.LoadAnalisisDelPeriodoPort;
import com.renaser.os.rag.domain.model.analisis.AnalisisDelPeriodo;
import com.renaser.os.rag.domain.model.analisis.EstadoDelAnalisis;
import com.renaser.os.rag.domain.model.analisis.ReglasDelAnalisis;
import com.renaser.os.rag.domain.model.analisis.TextoDelAnalisis;
import com.renaser.os.rag.domain.model.analisis.TipoDePeriodo;
import com.renaser.os.rocks.api.Pareto8020Finder.CifrasDel8020;
import com.renaser.os.rocks.api.PeriodosDelProgramaFinder;
import com.renaser.os.rocks.api.PeriodosDelProgramaFinder.PeriodoDelPrograma;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.ParticipacionPrograma;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import com.renaser.os.users.api.ProgramasActivadosFinder;
import com.renaser.os.users.api.ProgramasActivadosFinder.ProgramaActivado;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Lo que se lee del 80/20 de una persona en un periodo (spec 031, D-306). <b>Solo lee</b>: ninguna rama llama a la IA ni genera un
 * análisis (FR-006). Mientras el periodo está abierto muestra las cifras al día (FR-003) y nada más; cuando cerró, además el análisis si
 * ya está, o dice por qué todavía no: llega pronto, no alcanzó el mínimo o la IA no pudo.
 *
 * <p>Un análisis listo se muestra con la foto de cifras que se guardó con él, no con las de ahora: lo que se leyó hoy es lo que se lee mañana.
 * Y es de la formación de la persona hoy: lo que quedó de una formación anterior no aparece (nunca se mezclan datos de dos formaciones).
 * No autoriza: quien llama ya lo hizo ({@link Analisis8020Finder}).
 */
@Service
public class ConsultaDelAnalisis8020Service implements Analisis8020Finder, ConsultarAnalisis8020UseCase {

    private static final String SIN_DIA_UNO = "Todavía no eligió su Día 1";

    private final ParticipacionProgramaFinder participaciones;
    private final ProgramasActivadosFinder programas;
    private final PeriodosDelProgramaFinder periodos;
    private final LoadAnalisisDelPeriodoPort analisis;
    private final ReunirInsumosDelAnalisis insumos;
    private final ReglasDelAnalisis reglas;
    private final Clock clock;

    ConsultaDelAnalisis8020Service(ParticipacionProgramaFinder participaciones, ProgramasActivadosFinder programas,
                                   PeriodosDelProgramaFinder periodos, LoadAnalisisDelPeriodoPort analisis,
                                   ReunirInsumosDelAnalisis insumos, ReglasDelAnalisis reglas, Clock clock) {
        this.participaciones = participaciones;
        this.programas = programas;
        this.periodos = periodos;
        this.analisis = analisis;
        this.insumos = insumos;
        this.reglas = reglas;
        this.clock = clock;
    }

    @Override
    public VistaDelAnalisis deLaPersona(ConsultaDeAnalisis consulta) {
        return de(consulta.actorId(), Tipo.valueOf(consulta.tipo().name()), consulta.inicio());
    }

    @Override
    public VistaDelAnalisis de(UserId personaId, Tipo tipo, LocalDate inicio) {
        ParticipacionPrograma participacion = participaciones.deParticipante(personaId)
                .filter(p -> p.inscrito() && p.activado()).orElseThrow(() -> new NoSuchElementException(SIN_DIA_UNO));
        ProgramaActivado programa = programas.de(personaId).filter(p -> p.primeraFecha() != null)
                .orElseThrow(() -> new NoSuchElementException(SIN_DIA_UNO));
        LocalDate hoy = clock.now().atZone(programa.zona()).toLocalDate();
        if (hoy.isBefore(programa.primeraFecha())) {
            throw new NoSuchElementException("Su programa todavía no empieza");
        }
        PeriodoDelPrograma periodo = resolver(programa.primeraFecha(), programa.duracionDias(), tipo, inicio, hoy);
        Optional<AnalisisDelPeriodo> guardado = periodo.cerroA(hoy)
                ? analisis.deLaPersona(personaId, TipoDePeriodo.valueOf(tipo.name()), periodo.inicio())
                        .filter(fila -> fila.formacion() == participacion.formacion())
                : Optional.empty();
        return guardado.filter(fila -> fila.estado() == EstadoDelAnalisis.LISTO)
                .map(fila -> delAnalisisListo(fila, periodo, programa, hoy))
                .orElseGet(() -> conLasCifrasDeAhora(personaId, periodo, programa, hoy, guardado));
    }

    @Override
    public Optional<AnalisisLeido> ultimoListo(UserId personaId, Tipo tipo) {
        return participaciones.deParticipante(personaId).filter(p -> p.inscrito() && p.activado())
                .flatMap(p -> analisis.ultimoListo(personaId, p.formacion(), TipoDePeriodo.valueOf(tipo.name())))
                .map(fila -> new AnalisisLeido(tipo, fila.numero(), fila.inicio(), fila.fin(), aTextoVisible(fila.texto()),
                        fila.generadoEn()));
    }

    private PeriodoDelPrograma resolver(LocalDate primerDia, int duracionDias, Tipo tipo, LocalDate inicio, LocalDate hoy) {
        PeriodosDelProgramaFinder.Tipo delPrograma = PeriodosDelProgramaFinder.Tipo.valueOf(tipo.name());
        if (inicio == null) {
            return delPrograma == PeriodosDelProgramaFinder.Tipo.SEMANA ? periodos.semanaDe(primerDia, duracionDias, hoy) : periodos.mesDe(primerDia, duracionDias, hoy);
        }
        PeriodoDelPrograma pedido = periodos.queEmpiezaEn(primerDia, duracionDias, delPrograma, inicio)
                .orElseThrow(() -> new IllegalArgumentException("Ninguna " + tipo.name().toLowerCase() + " del programa empieza ese día"));
        if (pedido.inicio().isAfter(hoy)) {
            throw new IllegalArgumentException("Ese periodo todavía no llega");
        }
        return pedido;
    }

    /** El análisis listo, con las cifras que citaba cuando se escribió. */
    private VistaDelAnalisis delAnalisisListo(AnalisisDelPeriodo fila, PeriodoDelPrograma periodo, ProgramaActivado programa,
                                              LocalDate hoy) {
        SnapshotDelAnalisis foto = JsonDelAnalisis.leer(fila.cifrasEnJson(), SnapshotDelAnalisis.class);
        return new VistaDelAnalisis(Tipo.valueOf(periodo.tipo().name()), periodo.numero(), periodo.inicio(), periodo.fin(), true,
                vecino(periodos.anteriorA(programa.primeraFecha(), programa.duracionDias(), periodo)), siguiente(programa, periodo, hoy), foto.cifras(),
                foto.semanas(), "LISTO", List.of(), reglas.minimoDeAcciones(fila.tipo()), foto.codigoRenaser(),
                aTextoVisible(fila.texto()), fila.generadoEn());
    }

    /** Las cifras de ahora y el estado del análisis: abierto, pendiente, sin datos suficientes o sin análisis. */
    private VistaDelAnalisis conLasCifrasDeAhora(UserId personaId, PeriodoDelPrograma periodo, ProgramaActivado programa,
                                                 LocalDate hoy, Optional<AnalisisDelPeriodo> guardado) {
        TipoDePeriodo tipo = TipoDePeriodo.valueOf(periodo.tipo().name());
        CifrasDel8020 cifras = insumos.cifrasDe(personaId, periodo);
        List<String> falta = reglas.loQueFalta(tipo, cifras.planificadas(), cifras.diasConAcciones());
        List<PeriodoDelPrograma> empezadas = periodo.tipo() == PeriodosDelProgramaFinder.Tipo.MES
                ? periodos.semanasDelMes(programa.primeraFecha(), programa.duracionDias(), periodo).stream().filter(s -> !s.inicio().isAfter(hoy)).toList()
                : List.of();
        return new VistaDelAnalisis(Tipo.valueOf(tipo.name()), periodo.numero(), periodo.inicio(), periodo.fin(), periodo.cerroA(hoy),
                vecino(periodos.anteriorA(programa.primeraFecha(), programa.duracionDias(), periodo)), siguiente(programa, periodo, hoy), cifras,
                insumos.resumenesDe(personaId, empezadas), estadoSinTexto(periodo.cerroA(hoy), falta, guardado),
                periodo.cerroA(hoy) ? falta : List.of(), reglas.minimoDeAcciones(tipo), null, null, null);
    }

    private static String estadoSinTexto(boolean cerrado, List<String> falta, Optional<AnalisisDelPeriodo> guardado) {
        if (!cerrado) {
            return "EN_CURSO";
        }
        if (!falta.isEmpty() || guardado.filter(fila -> fila.estado() == EstadoDelAnalisis.SIN_DATOS).isPresent()) {
            return "SIN_DATOS";
        }
        return guardado.filter(fila -> fila.estado() == EstadoDelAnalisis.AGOTADO).isPresent() ? "SIN_ANALISIS" : "PENDIENTE";
    }

    private LocalDate siguiente(ProgramaActivado programa, PeriodoDelPrograma periodo, LocalDate hoy) {
        return vecino(periodos.siguienteA(programa.primeraFecha(), programa.duracionDias(), periodo, hoy));
    }

    private static LocalDate vecino(Optional<PeriodoDelPrograma> periodo) {
        return periodo.map(PeriodoDelPrograma::inicio).orElse(null);
    }

    private static TextoVisible aTextoVisible(TextoDelAnalisis texto) {
        return new TextoVisible(texto.loQueMasMovio(), texto.loQueSeTeFue(), texto.comoEstuviste(), texto.proximosPasos());
    }
}

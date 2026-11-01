package com.renaser.os.rag.application.services.analisis;

import com.renaser.os.rag.application.ports.in.analisis.GenerarAnalisis8020UseCase;
import com.renaser.os.rag.application.ports.out.analisis.GenerarAnalisis8020Port;
import com.renaser.os.rag.application.ports.out.analisis.LoadAnalisisDelPeriodoPort;
import com.renaser.os.rag.application.services.analisis.ArmadoDelPedido.PedidoArmado;
import com.renaser.os.rag.application.services.analisis.QuienPuedeTenerAnalisis.ParticipanteAnalizable;
import com.renaser.os.rag.domain.model.analisis.AnalisisDelPeriodo;
import com.renaser.os.rag.domain.model.analisis.AnalisisDelPeriodoId;
import com.renaser.os.rag.domain.model.analisis.ReglasDelAnalisis;
import com.renaser.os.rag.domain.model.analisis.TextoDelAnalisis;
import com.renaser.os.rag.domain.model.analisis.TipoDePeriodo;
import com.renaser.os.rag.domain.model.analisis.ValidadorDelTexto;
import com.renaser.os.rocks.api.PeriodosDelProgramaFinder;
import com.renaser.os.rocks.api.PeriodosDelProgramaFinder.PeriodoDelPrograma;
import com.renaser.os.rocks.api.PeriodosDelProgramaFinder.Tipo;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.ProveedorIaNoDisponibleException;
import com.renaser.os.shared.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Genera el análisis 80/20 de un periodo de una persona (spec 031, D-306). El orden es el que cuida el costo y la verdad:
 * <ol>
 *   <li>un periodo con resultado, o pendiente que aún no toca reintentar, se deja como está (a lo sumo <b>una</b> generación, FR-006);</li>
 *   <li>se juntan las cifras; si no alcanzan el mínimo se anota {@code SIN_DATOS} y <b>no se llama a la IA</b> (FR-015);</li>
 *   <li>se pasa por las puertas de la IA (proveedor, {@code ia.analisis}, tope diario, circuito): cerradas, queda pendiente sin contar
 *       intento (FR-016, FR-017, FR-019);</li>
 *   <li>se llama a la IA <b>fuera de toda transacción</b> (C-1);</li>
 *   <li>el texto se revisa: una cifra que no es la calculada, un diagnóstico o una comparación lo descartan (FR-009); recién entonces se
 *       guarda, con las cifras que cita, y se avisa.</li>
 * </ol>
 * Nada de lo que se lee, se manda o se recibe se escribe en el registro técnico (FR-024, SC-006): solo el resultado.
 */
@Service
public class GeneracionDeAnalisis8020Service implements GenerarAnalisis8020UseCase {

    private static final Logger log = LoggerFactory.getLogger(GeneracionDeAnalisis8020Service.class);

    private final LoadAnalisisDelPeriodoPort analisis;
    private final QuienPuedeTenerAnalisis quien;
    private final PeriodosDelProgramaFinder periodos;
    private final ReunirInsumosDelAnalisis insumos;
    private final PuertasDeLaIa puertas;
    private final GenerarAnalisis8020Port ia;
    private final RegistroDelAnalisis registro;
    private final ReglasDelAnalisis reglas;
    private final Clock clock;
    private final IdGenerator idGenerator;

    GeneracionDeAnalisis8020Service(LoadAnalisisDelPeriodoPort analisis, QuienPuedeTenerAnalisis quien,
                                    PeriodosDelProgramaFinder periodos, ReunirInsumosDelAnalisis insumos, PuertasDeLaIa puertas,
                                    GenerarAnalisis8020Port ia, RegistroDelAnalisis registro, ReglasDelAnalisis reglas, Clock clock,
                                    IdGenerator idGenerator) {
        this.analisis = analisis;
        this.quien = quien;
        this.periodos = periodos;
        this.insumos = insumos;
        this.puertas = puertas;
        this.ia = ia;
        this.registro = registro;
        this.reglas = reglas;
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    @Override
    public ResultadoDeLaGeneracion generar(UserId persona, TipoDePeriodo tipo, LocalDate inicio) {
        Optional<AnalisisDelPeriodo> existente = analisis.deLaPersona(persona, tipo, inicio);
        if (existente.isPresent() && !existente.get().tocaIntentarEn(clock.now(), reglas)) {
            return ResultadoDeLaGeneracion.YA_DECIDIDO;
        }
        Optional<ParticipanteAnalizable> participante = quien.de(persona);
        Optional<PeriodoDelPrograma> periodo = participante.flatMap(p -> periodoQueCerro(p, tipo, inicio));
        if (periodo.isEmpty()) {
            return ResultadoDeLaGeneracion.NO_APLICA;
        }
        InsumosDelAnalisis reunidos = insumos.reunir(persona, periodo.get(), participante.get().primerDia(), participante.get().duracionDias(),
                participante.get().zona(), reglas);
        if (!reglas.alcanza(tipo, reunidos.acciones().cifras().planificadas(), reunidos.acciones().cifras().diasConAcciones())) {
            return anotarSinDatos(participante.get(), periodo.get());
        }
        AnalisisDelPeriodo aIntentar = existente.orElseGet(() -> pendiente(participante.get(), periodo.get()));
        return puertas.estanAbiertas() ? intentar(aIntentar, reunidos) : ResultadoDeLaGeneracion.DIFERIDO;
    }

    private Optional<PeriodoDelPrograma> periodoQueCerro(ParticipanteAnalizable participante, TipoDePeriodo tipo, LocalDate inicio) {
        LocalDate hoy = clock.now().atZone(participante.zona()).toLocalDate();
        return periodos.queEmpiezaEn(participante.primerDia(), participante.duracionDias(), Tipo.valueOf(tipo.name()), inicio).filter(periodo -> periodo.cerroA(hoy));
    }

    private ResultadoDeLaGeneracion anotarSinDatos(ParticipanteAnalizable participante, PeriodoDelPrograma periodo) {
        registro.registrar(AnalisisDelPeriodo.sinDatosSuficientes(AnalisisDelPeriodoId.of(idGenerator.newId()), participante.id(),
                participante.formacion(), TipoDePeriodo.valueOf(periodo.tipo().name()), periodo.numero(), periodo.inicio(),
                periodo.fin(), clock));
        log.info("[rag.Analisis80_20] periodo sin datos suficientes; no se llama a la IA. tipo={} numero={}", periodo.tipo(), periodo.numero());
        return ResultadoDeLaGeneracion.SIN_DATOS;
    }

    private AnalisisDelPeriodo pendiente(ParticipanteAnalizable participante, PeriodoDelPrograma periodo) {
        return AnalisisDelPeriodo.pendiente(AnalisisDelPeriodoId.of(idGenerator.newId()), participante.id(), participante.formacion(),
                TipoDePeriodo.valueOf(periodo.tipo().name()), periodo.numero(), periodo.inicio(), periodo.fin(), clock);
    }

    /** La llamada a la IA y lo que sigue. Sin {@code @Transactional}: la IA no puede retener una conexión. */
    private ResultadoDeLaGeneracion intentar(AnalisisDelPeriodo pendiente, InsumosDelAnalisis reunidos) {
        PedidoArmado armado = ArmadoDelPedido.armar(pendiente.tipo(), reunidos);
        if (!puertas.pedirPaso()) {
            return ResultadoDeLaGeneracion.DIFERIDO;
        }
        TextoDelAnalisis texto;
        try {
            texto = ia.generar(armado.pedido());
        } catch (ProveedorIaNoDisponibleException caida) {
            puertas.anotarQueFallo();
            log.warn("[rag.Analisis80_20] la IA no respondió; se reintenta. tipo={} numero={}", pendiente.tipo(), pendiente.numero());
            return anotarFallo(pendiente);
        } catch (RuntimeException ilegible) {
            puertas.anotarQueRespondio();
            log.warn("[rag.Analisis80_20] la IA respondió algo que no es un análisis legible ({}); se reintenta. tipo={} numero={}",
                    ilegible.getClass().getSimpleName(), pendiente.tipo(), pendiente.numero());
            return anotarFallo(pendiente);
        }
        puertas.anotarQueRespondio();
        return guardarSiSePuedeMostrar(pendiente, texto, armado);
    }

    private ResultadoDeLaGeneracion guardarSiSePuedeMostrar(AnalisisDelPeriodo pendiente, TextoDelAnalisis texto, PedidoArmado armado) {
        List<String> incumplimientos = ValidadorDelTexto.incumplimientos(texto, armado.numerosPermitidos());
        if (!incumplimientos.isEmpty()) {
            log.warn("[rag.Analisis80_20] texto descartado ({}); se reintenta. tipo={} numero={}", String.join("; ", incumplimientos),
                    pendiente.tipo(), pendiente.numero());
            return anotarFallo(pendiente);
        }
        pendiente.completar(texto, JsonDelAnalisis.escribir(armado.foto()), clock);
        registro.registrar(pendiente);
        log.info("[rag.Analisis80_20] análisis listo. tipo={} numero={}", pendiente.tipo(), pendiente.numero());
        return ResultadoDeLaGeneracion.LISTO;
    }

    private ResultadoDeLaGeneracion anotarFallo(AnalisisDelPeriodo pendiente) {
        pendiente.fallar(reglas, clock);
        registro.registrar(pendiente);
        return ResultadoDeLaGeneracion.FALLO;
    }
}

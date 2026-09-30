package com.renaser.os.rag.application.services;

import com.renaser.os.rag.application.ports.in.herramienta.EjecutarHerramientaAgenteUseCase;
import com.renaser.os.rag.application.ports.out.habitos.ConsultarAgendaHabitosPort;
import com.renaser.os.rag.application.ports.out.habitos.ConsultarAgendaHabitosPort.HabitoDelDia;
import com.renaser.os.rag.application.ports.out.plan.GestionarPlanDeHabitosPort;
import com.renaser.os.rag.application.services.herramientas.LoQueLeFaltaHoy;
import com.renaser.os.rag.application.services.herramientas.CompletacionDeHabito;
import com.renaser.os.rag.application.services.herramientas.HerramientaAgente;
import com.renaser.os.rag.application.services.herramientas.PropuestaDeMarcarHabito;
import com.renaser.os.rag.domain.model.conversacion.AgenteConversacional;
import com.renaser.os.rag.domain.model.herramienta.CatalogoHerramientasAgente;
import com.renaser.os.rag.domain.model.herramienta.DefinicionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Ejecuta las herramientas del agente contra los puertos de negocio reales.
 *
 * <p><b>Sin ningun modelo detras, y probado igual.</b> Este servicio no depende de
 * {@code ChatIAPort}: se lo puede ejercitar entero con los puertos mockeados, que es como estan
 * probadas las tres herramientas hoy. Cuando exista un proveedor real, el adaptador lo llama y
 * este codigo no cambia.
 *
 * <p><b>Nada de {@code @Transactional} aca</b> (C-1, regla 01). Cada operacion de negocio abre y
 * cierra su propia transaccion corta puertas adentro; envolver la ejecucion de herramientas en
 * una transaccion propia la dejaria abierta mientras el bucle del proveedor va y vuelve, que es
 * exactamente como se agota el pool de Hikari para toda la API.
 *
 * <p><b>Todo fallo se traduce, ninguno se propaga.</b> Un modelo pide herramientas que no
 * existen, omite argumentos e inventa identificadores: es su comportamiento normal, no un caso
 * borde. Cada uno de esos vuelve como {@code Fallo} con un motivo que el asistente puede
 * repetirle a la persona. El detalle tecnico va al log, nunca al texto que ve el aprendiz.
 */
@Service
public class HerramientasAgenteService implements EjecutarHerramientaAgenteUseCase {

    private static final Logger log = LoggerFactory.getLogger(HerramientasAgenteService.class);

    private final ConsultarAgendaHabitosPort agendaHabitosPort;
    /** Las herramientas que viven en su propia clase (2026-09-23). Ver {@link HerramientaAgente}. */
    private final List<HerramientaAgente> adicionales;
    /**
     * Si {@code marcar_habito_completado} marca en el acto o deja una propuesta con botones
     * (fase 2, D-153, flag {@code renaser.ia.acompanante.confirmacion-con-botones}).
     */
    private final PropuestaDeMarcarHabito propuestaDeMarcar;

    /**
     * Para decir "vence en 45 min" o "ya vencio" en vez de un instante en UTC (bateria del
     * 2026-09-25): el modelo leia "vence=2026-09-25T14:10:00Z" como hora local, ofrecia como
     * "el mas proximo por vencer" uno que ya habia vencido y contaba los vencidos como pendientes.
     * (E-455: ahora dice cuanto falta para que se acaben sus puntos, o que ya no da puntos; un
     * habito no vence y se puede hacer igual.)
     */
    private final Clock clock;
    /**
     * Para listar los pausados junto a los de hoy (bateria del 2026-09-25, ronda 2): un pausado no
     * genera registro, y el modelo decia "no veo la ducha fria en tus habitos de hoy" y mandaba a
     * subir evidencia, en vez de decir que esta pausada.
     */
    private final GestionarPlanDeHabitosPort planPort;

    public HerramientasAgenteService(ConsultarAgendaHabitosPort agendaHabitosPort,
                                     List<HerramientaAgente> adicionales,
                                     PropuestaDeMarcarHabito propuestaDeMarcar, Clock clock,
                                     GestionarPlanDeHabitosPort planPort) {
        this.agendaHabitosPort = agendaHabitosPort;
        this.clock = clock;
        this.planPort = planPort;
        this.adicionales = List.copyOf(adicionales);
        this.propuestaDeMarcar = propuestaDeMarcar;
        requireNombresUnicos();
    }

    @Override
    public List<DefinicionHerramienta> disponibles(AgenteConversacional agente) {
        if (agente != AgenteConversacional.COMPANION) {
            return List.of();
        }
        return Stream.concat(delCatalogo().stream(), adicionales.stream().map(HerramientaAgente::definicion))
                .toList();
    }

    /** Con el flag prendido, {@code marcar_habito_completado} se describe como lo que hace: proponer. */
    private List<DefinicionHerramienta> delCatalogo() {
        return propuestaDeMarcar.activa() ? CatalogoHerramientasAgente.definicionesConConfirmacion()
                : CatalogoHerramientasAgente.definiciones();
    }

    @Override
    public ResultadoHerramienta ejecutar(UserId actorId, InvocacionHerramienta invocacion) {
        Optional<HerramientaAgente> adicional = adicionalLlamada(invocacion.nombre());
        Optional<DefinicionHerramienta> definicion = adicional.map(HerramientaAgente::definicion)
                .or(() -> CatalogoHerramientasAgente.porNombre(invocacion.nombre()));
        if (definicion.isEmpty()) {
            return ResultadoHerramienta.fallo("No existe una herramienta llamada " + invocacion.nombre() + ".");
        }
        List<String> faltantes = definicion.get().obligatoriosFaltantesEn(invocacion);
        if (!faltantes.isEmpty()) {
            return ResultadoHerramienta.fallo("Faltan datos para usar esa herramienta: " + String.join(", ",
                    faltantes) + ".");
        }
        return adicional.map(herramienta -> ejecutarAdicional(herramienta, actorId, invocacion))
                .orElseGet(() -> ejecutarConDatosValidos(actorId, invocacion));
    }

    private Optional<HerramientaAgente> adicionalLlamada(String nombre) {
        return adicionales.stream().filter(herramienta -> herramienta.definicion().nombre().equals(nombre))
                .findFirst();
    }

    /**
     * La red de seguridad del contrato: una herramienta adicional no deberia lanzar, pero si lo
     * hace, el modelo recibe un motivo legible y el detalle queda en el log.
     */
    private static ResultadoHerramienta ejecutarAdicional(HerramientaAgente herramienta, UserId actorId,
                                                          InvocacionHerramienta invocacion) {
        try {
            return herramienta.ejecutar(actorId, invocacion);
        } catch (RuntimeException falla) {
            log.warn("[rag] la herramienta {} fallo sin traducir el error", invocacion.nombre(), falla);
            return ResultadoHerramienta.fallo("No pude consultar eso en este momento.");
        }
    }

    /** Dos herramientas con el mismo nombre harian ambiguo lo que el modelo pide: se corta al arrancar. */
    private void requireNombresUnicos() {
        Set<String> vistos = new HashSet<>();
        Stream.concat(CatalogoHerramientasAgente.definiciones().stream(),
                        adicionales.stream().map(HerramientaAgente::definicion))
                .map(DefinicionHerramienta::nombre)
                .filter(nombre -> !vistos.add(nombre))
                .findFirst()
                .ifPresent(repetido -> {
                    throw new IllegalStateException("Dos herramientas del agente se llaman " + repetido);
                });
    }

    private ResultadoHerramienta ejecutarConDatosValidos(UserId actorId, InvocacionHerramienta invocacion) {
        return switch (invocacion.nombre()) {
            case CatalogoHerramientasAgente.CONSULTAR_HABITOS_DEL_DIA -> habitosDelDia(actorId);
            case CatalogoHerramientasAgente.CONSULTAR_PUNTOS_EN_JUEGO -> puntosEnJuego(actorId);
            case CatalogoHerramientasAgente.MARCAR_HABITO_COMPLETADO -> marcarCompletado(actorId,
                    invocacion.argumento(CatalogoHerramientasAgente.ARGUMENTO_REGISTRO_ID));
            default -> ResultadoHerramienta.fallo("Esa herramienta todavia no esta disponible.");
        };
    }

    /**
     * E-455: el texto lo arma {@link LoQueLeFaltaHoy}: cuantos le faltan y por dimension, en orden, y
     * sin decir que un habito "vencio" (lo que pasa es la hora de sus puntos). El total en juego sigue
     * en la MISMA respuesta (auditoria NFR 2026-09-06): sin el, el modelo encadenaba
     * consultar_puntos_en_juego para sumar lo que ya tenia adelante.
     */
    private ResultadoHerramienta habitosDelDia(UserId actorId) {
        List<HabitoDelDia> habitos = agendaHabitosPort.deHoyDe(actorId);
        if (habitos.isEmpty()) {
            return ResultadoHerramienta.exito(("Hoy no tiene ningun habito generado." + pausados(actorId)).trim());
        }
        return ResultadoHerramienta.exito((LoQueLeFaltaHoy.texto(habitos, clock.now()) + pausados(actorId)).trim());
    }

    /**
     * Los pausados no estan en la lista de hoy (no generan registro): se nombran aparte para que el
     * modelo diga "esta pausado" en vez de "no lo veo". Best-effort: sin el plan, la lista de hoy
     * sale igual.
     */
    private String pausados(UserId actorId) {
        try {
            List<String> titulos = planPort.planDe(actorId).habitos().stream()
                    .filter(GestionarPlanDeHabitosPort.HabitoDelPlan::pausadoHoy)
                    .map(GestionarPlanDeHabitosPort.HabitoDelPlan::titulo)
                    .toList();
            return titulos.isEmpty() ? "" : "\nPausados (no se le piden ningun dia hasta que los reactive): "
                    + String.join(", ", titulos) + ". Si pregunta por uno de estos, dile que esta pausado.";
        } catch (RuntimeException falla) {
            log.warn("[rag] no se pudieron leer los pausados del plan ({})", falla.getClass().getSimpleName());
            return "";
        }
    }

    private ResultadoHerramienta puntosEnJuego(UserId actorId) {
        int total = 0;
        int pendientes = 0;
        Instant ahora = clock.now();
        for (HabitoDelDia habito : agendaHabitosPort.deHoyDe(actorId)) {
            if (habito.sigueEnJuego() && !LoQueLeFaltaHoy.yaNoDaPuntos(habito, ahora)) {
                total += habito.puntosEnJuego();
                pendientes++;
            }
        }
        return ResultadoHerramienta.exito("Le quedan " + total + " puntos en juego hoy, repartidos en " + pendientes
                + " habito(s) que todavia puede entregar.");
    }

    /**
     * Con el flag apagado marca en el acto, exactamente como antes de la fase 2. Prendido, NO
     * marca: deja una propuesta y la persona confirma con un boton (D-153). En los dos casos un id
     * inventado se rechaza antes de tocar ningun puerto.
     */
    private ResultadoHerramienta marcarCompletado(UserId actorId, String registroId) {
        return CompletacionDeHabito.registroIdDe(registroId)
                .map(id -> propuestaDeMarcar.activa() ? propuestaDeMarcar.proponer(actorId, id)
                        : CompletacionDeHabito.completar(agendaHabitosPort, actorId, id))
                .orElseGet(CompletacionDeHabito::identificadorInvalido);
    }
}

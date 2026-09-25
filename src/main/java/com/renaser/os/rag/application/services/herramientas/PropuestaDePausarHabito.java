package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.in.propuesta.ProponerAccionUseCase;
import com.renaser.os.rag.application.ports.out.plan.GestionarPlanDeHabitosPort;
import com.renaser.os.rag.application.ports.out.plan.GestionarPlanDeHabitosPort.HabitoDelPlan;
import com.renaser.os.rag.application.ports.out.plan.GestionarPlanDeHabitosPort.PlanDelAprendiz;
import com.renaser.os.rag.domain.model.herramienta.DefinicionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ParametroHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.rag.domain.model.herramienta.TipoParametroHerramienta;
import com.renaser.os.shared.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * {@code proponer_pausar_habito} (R2, propuesta; fase 2, D-153): pausar un habito NO obligatorio
 * de su plan —hasta una fecha o sin fecha de fin— o reactivarlo. No escribe: deja una propuesta y
 * la persona confirma con el boton. La escritura es {@link PausarHabitoConfirmable}, que llama al
 * mismo caso de uso que {@code PATCH /api/v1/habit-unlocks/{habitId}}.
 *
 * <p><b>Valida antes de proponer con los datos de {@code habits}</b> para no ofrecer un boton que
 * va a fallar: el habito tiene que estar en su plan, no ser obligatorio, y el cambio tiene que
 * cambiar algo. "Hoy" y "pausado hoy" los resuelve {@code habits} en la zona del participante.
 *
 * <p>Solo existe con {@code renaser.ia.acompanante.confirmacion-con-botones} prendido: sin botones
 * en la app, una propuesta no tiene quien la confirme.
 */
@Component
@ConditionalOnProperty(name = "renaser.ia.acompanante.confirmacion-con-botones", havingValue = "true")
public class PropuestaDePausarHabito implements HerramientaAgente {

    public static final String NOMBRE = "proponer_pausar_habito";
    public static final String ARGUMENTO_HABITO_ID = "habito_id";
    public static final String ARGUMENTO_ACCION = "accion";
    public static final String ARGUMENTO_HASTA = "hasta";
    public static final String ACCION_PAUSAR = "pausar";
    public static final String ACCION_REACTIVAR = "reactivar";

    private static final Logger log = LoggerFactory.getLogger(PropuestaDePausarHabito.class);

    private static final DefinicionHerramienta DEFINICION = new DefinicionHerramienta(NOMBRE,
            "Propone pausar un habito de su plan (hasta una fecha o sin fecha de fin) o reactivar uno pausado. "
                    + "NO lo pausa: deja una propuesta y la persona tiene que tocar Confirmar en la app. Nunca "
                    + "digas que ya quedo pausado o reactivado. Los habitos obligatorios no se pueden pausar. "
                    + "Usala solo si la persona pidio pausar o reactivar; saca el habito_id de consultar_horarios.",
            List.of(ParametroHerramienta.obligatorio(ARGUMENTO_HABITO_ID, TipoParametroHerramienta.IDENTIFICADOR,
                            "El habito_id que devuelve consultar_horarios."),
                    ParametroHerramienta.obligatorio(ARGUMENTO_ACCION, TipoParametroHerramienta.TEXTO,
                            "'pausar' o 'reactivar'."),
                    new ParametroHerramienta(ARGUMENTO_HASTA, TipoParametroHerramienta.TEXTO,
                            "Solo al pausar: ultimo dia de la pausa (inclusive) en formato yyyy-MM-dd. Omitelo "
                                    + "si pidio pausarlo sin fecha de fin.", false)));

    private final GestionarPlanDeHabitosPort planPort;
    private final ProponerAccionUseCase proponerAccion;

    public PropuestaDePausarHabito(GestionarPlanDeHabitosPort planPort, ProponerAccionUseCase proponerAccion) {
        this.planPort = planPort;
        this.proponerAccion = proponerAccion;
    }

    @Override
    public DefinicionHerramienta definicion() {
        return DEFINICION;
    }

    @Override
    public ResultadoHerramienta ejecutar(UserId actorId, InvocacionHerramienta invocacion) {
        Optional<UUID> habitoId = CompletacionDeHabito.registroIdDe(invocacion.argumento(ARGUMENTO_HABITO_ID));
        if (habitoId.isEmpty()) {
            return ResultadoHerramienta.fallo("Ese habito_id no es valido. Consulta primero los horarios y usa el "
                    + "habito_id que devuelven.");
        }
        String accion = accionDe(invocacion.argumento(ARGUMENTO_ACCION));
        if (!ACCION_PAUSAR.equals(accion) && !ACCION_REACTIVAR.equals(accion)) {
            return ResultadoHerramienta.fallo("La accion tiene que ser 'pausar' o 'reactivar'.");
        }
        String hastaTexto = ACCION_PAUSAR.equals(accion) ? invocacion.argumento(ARGUMENTO_HASTA) : null;
        Optional<LocalDate> hasta = FechaDelPlan.leer(hastaTexto);
        if (!FechaDelPlan.ausente(hastaTexto) && hasta.isEmpty()) {
            return ResultadoHerramienta.fallo("La fecha 'hasta' tiene que venir como yyyy-MM-dd (por ejemplo "
                    + "2026-09-27).");
        }
        Pedido pedido = new Pedido(habitoId.get(), ACCION_PAUSAR.equals(accion), hasta.orElse(null));
        return LecturaDelPlan.conPlan(planPort, actorId, plan -> proponerSiCorresponde(actorId, plan, pedido));
    }

    private ResultadoHerramienta proponerSiCorresponde(UserId actorId, PlanDelAprendiz plan, Pedido pedido) {
        Optional<HabitoDelPlan> habito = plan.habitoDelPlan(pedido.habitoId());
        if (habito.isEmpty()) {
            return ResultadoHerramienta.fallo(porQueNoSePausa(plan, pedido.habitoId()));
        }
        Optional<String> impedimento = pedido.pausar() ? impedimentoParaPausar(habito.get(), plan, pedido)
                : impedimentoParaReactivar(habito.get());
        if (impedimento.isPresent()) {
            return ResultadoHerramienta.fallo(impedimento.get());
        }
        String resumen = resumenDe(habito.get(), pedido);
        try {
            proponerAccion.proponer(actorId, invocacionPara(pedido), resumen);
        } catch (RuntimeException falla) {
            log.warn("[rag] no se pudo guardar la propuesta de {}", NOMBRE, falla);
            return AvisoDePropuesta.noSePudoPreparar();
        }
        return AvisoDePropuesta.creada(resumen, null);
    }

    /**
     * Un habito que no esta entre los que se suman al plan: o es obligatorio del programa, o es de la
     * base de su dia. Antes los dos casos decian "no esta en su plan", y el modelo lo resumia en "no
     * es posible pausarlo", sin motivo ni alternativa (E-245).
     */
    private static String porQueNoSePausa(PlanDelAprendiz plan, UUID habitoId) {
        return plan.obligatorio(habitoId)
                .map(obligatorio -> LoQueSiSePuede.obligatorio(obligatorio.titulo()))
                .orElseGet(() -> "Ese habito no se pausa: la pausa es solo para los que se suman a su plan. "
                        + LoQueSiSePuede.CON_UNO_DE_LA_BASE + "\n" + pausables(plan));
    }

    private static Optional<String> impedimentoParaPausar(HabitoDelPlan habito, PlanDelAprendiz plan, Pedido pedido) {
        if (habito.obligatorio()) {
            return Optional.of(LoQueSiSePuede.obligatorio(habito.titulo()));
        }
        if (pedido.hasta() != null && pedido.hasta().isBefore(plan.hoy())) {
            return Optional.of("Esa fecha ya paso (hoy es " + FechaDelPlan.legible(plan.hoy()) + " para la "
                    + "persona): la pausa tiene que terminar hoy o despues.");
        }
        if (habito.pausadoHoy() && Objects.equals(habito.pausadoHasta(), pedido.hasta())) {
            return Optional.of("'" + habito.titulo() + "' ya esta pausado " + finDe(pedido.hasta()) + ".");
        }
        return Optional.empty();
    }

    private static Optional<String> impedimentoParaReactivar(HabitoDelPlan habito) {
        return habito.pausadoHoy() ? Optional.empty()
                : Optional.of("'" + habito.titulo() + "' no esta pausado: ya le toca normalmente.");
    }

    /** Lo que ve la persona junto a los botones: el cambio exacto, en sus fechas. */
    static String resumenDe(HabitoDelPlan habito, Pedido pedido) {
        if (!pedido.pausar()) {
            return "Reactivar '" + habito.titulo() + "' en tu plan";
        }
        if (habito.pausadoHoy()) {
            return "Cambiar la pausa de '" + habito.titulo() + "': ahora " + finDe(pedido.hasta());
        }
        return "Pausar '" + habito.titulo() + "' desde hoy " + finDe(pedido.hasta())
                + " (si hoy lo tenias pendiente, sale de tu dia)";
    }

    private static String finDe(LocalDate hasta) {
        return hasta == null ? "sin fecha de fin" : "hasta el " + FechaDelPlan.legible(hasta) + " inclusive";
    }

    private static String pausables(PlanDelAprendiz plan) {
        String lista = plan.habitos().stream().filter(habito -> !habito.obligatorio())
                .map(habito -> "habito_id=" + habito.habitoId() + " | " + habito.titulo()
                        + (habito.pausadoHoy() ? " | pausado=si" : ""))
                .collect(Collectors.joining("\n"));
        return lista.isEmpty() ? "No tiene habitos que se puedan pausar."
                : "Los que puede pausar o reactivar:\n" + lista;
    }

    private static String accionDe(String texto) {
        return texto == null ? "" : texto.trim().toLowerCase(Locale.ROOT);
    }

    /** Se guarda la invocacion normalizada —id limpio, accion en minusculas, fecha ISO—, no la del modelo. */
    private static InvocacionHerramienta invocacionPara(Pedido pedido) {
        Map<String, String> argumentos = new HashMap<>();
        argumentos.put(ARGUMENTO_HABITO_ID, pedido.habitoId().toString());
        argumentos.put(ARGUMENTO_ACCION, pedido.pausar() ? ACCION_PAUSAR : ACCION_REACTIVAR);
        if (pedido.hasta() != null) {
            argumentos.put(ARGUMENTO_HASTA, pedido.hasta().toString());
        }
        return new InvocacionHerramienta(NOMBRE, argumentos);
    }

    /** @param hasta {@code null} = sin fecha de fin (o reactivar) */
    record Pedido(UUID habitoId, boolean pausar, LocalDate hasta) {
    }
}

package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.in.propuesta.ProponerAccionUseCase;
import com.renaser.os.rag.application.ports.out.plan.GestionarPlanDeHabitosPort;
import com.renaser.os.rag.application.ports.out.plan.GestionarPlanDeHabitosPort.HabitoSemanal;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * {@code proponer_dia_de_habito_semanal} (R2, propuesta; fase 2, D-153): elegir que dia de ESTA
 * semana hace un habito de eleccion semanal. No escribe: deja una propuesta y la persona confirma
 * con el boton. La escritura es {@link DiaDeHabitoSemanalConfirmable}, que llama al mismo caso de
 * uso que {@code PUT /api/v1/weekly-habit-days/{habitId}}.
 *
 * <p><b>Valida antes de proponer con los datos de {@code habits}</b>: el habito tiene que ser de
 * eleccion semanal y la fecha uno de los {@code diasElegibles} que calcula {@code habits} en la
 * zona del participante (dias de esta semana que no pasaron; ninguno en el Dia 0).
 *
 * <p><b>Limitacion que el modelo tiene que conocer (D-H3, sigue abierto):</b> la generacion del
 * dia todavia no filtra por el dia elegido, asi que elegir el dia lo deja ANOTADO pero el habito
 * puede seguir apareciendo los otros dias. Por eso el resumen dice "anotar" y el aviso al modelo
 * le prohibe prometer lo contrario.
 */
@Component
@ConditionalOnProperty(name = "renaser.ia.acompanante.confirmacion-con-botones", havingValue = "true")
public class PropuestaDeDiaDeHabitoSemanal implements HerramientaAgente {

    public static final String NOMBRE = "proponer_dia_de_habito_semanal";
    public static final String ARGUMENTO_HABITO_ID = "habito_id";
    public static final String ARGUMENTO_FECHA = "fecha";

    static final String ADVERTENCIA_D_H3 = "Ojo: por ahora elegir el dia solo lo deja anotado; no le digas que "
            + "el habito desaparece de los otros dias de su agenda.";

    private static final Logger log = LoggerFactory.getLogger(PropuestaDeDiaDeHabitoSemanal.class);

    private static final DefinicionHerramienta DEFINICION = new DefinicionHerramienta(NOMBRE,
            "Propone el dia de ESTA semana en que la persona hara un habito de eleccion semanal. NO lo elige: "
                    + "deja una propuesta y la persona tiene que tocar Confirmar en la app. Nunca digas que ya "
                    + "quedo elegido. Solo sirven dias de esta semana que no hayan pasado; si el habito o el dia "
                    + "no sirven, la respuesta te dice cuales si.",
            List.of(ParametroHerramienta.obligatorio(ARGUMENTO_HABITO_ID, TipoParametroHerramienta.IDENTIFICADOR,
                            "El habito_id del habito semanal (de consultar_horarios o de la respuesta de esta "
                                    + "herramienta)."),
                    ParametroHerramienta.obligatorio(ARGUMENTO_FECHA, TipoParametroHerramienta.TEXTO,
                            "El dia elegido en formato yyyy-MM-dd.")));

    private final GestionarPlanDeHabitosPort planPort;
    private final ProponerAccionUseCase proponerAccion;

    public PropuestaDeDiaDeHabitoSemanal(GestionarPlanDeHabitosPort planPort, ProponerAccionUseCase proponerAccion) {
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
            return ResultadoHerramienta.fallo("Ese habito_id no es valido. Usa el habito_id que devuelve "
                    + "consultar_horarios.");
        }
        Optional<LocalDate> fecha = FechaDelPlan.leer(invocacion.argumento(ARGUMENTO_FECHA));
        if (fecha.isEmpty()) {
            return ResultadoHerramienta.fallo("La fecha tiene que venir como yyyy-MM-dd (por ejemplo 2026-09-24).");
        }
        return LecturaDelPlan.conPlan(planPort, actorId,
                plan -> proponerSiCorresponde(actorId, plan, habitoId.get(), fecha.get()));
    }

    private ResultadoHerramienta proponerSiCorresponde(UserId actorId, PlanDelAprendiz plan, UUID habitoId,
                                                       LocalDate fecha) {
        Optional<HabitoSemanal> habito = plan.habitoSemanal(habitoId);
        if (habito.isEmpty()) {
            return ResultadoHerramienta.fallo("Ese habito no es de eleccion semanal. " + semanales(plan));
        }
        Optional<String> impedimento = impedimento(habito.get(), fecha);
        if (impedimento.isPresent()) {
            return ResultadoHerramienta.fallo(impedimento.get());
        }
        String resumen = resumenDe(habito.get(), fecha);
        try {
            proponerAccion.proponer(actorId, invocacionPara(habitoId, fecha), resumen);
        } catch (RuntimeException falla) {
            log.warn("[rag] no se pudo guardar la propuesta de {}", NOMBRE, falla);
            return AvisoDePropuesta.noSePudoPreparar();
        }
        return AvisoDePropuesta.creada(resumen, ADVERTENCIA_D_H3);
    }

    private static Optional<String> impedimento(HabitoSemanal habito, LocalDate fecha) {
        if (habito.diasElegibles().isEmpty()) {
            return Optional.of("Todavia no puede elegir dia: eso se habilita desde el Dia 1 del programa.");
        }
        if (!habito.diasElegibles().contains(fecha)) {
            return Optional.of("Ese dia no se puede elegir: solo dias de esta semana que no hayan pasado ("
                    + legibles(habito.diasElegibles()) + ").");
        }
        if (fecha.equals(habito.diaElegido())) {
            return Optional.of("'" + habito.titulo() + "' ya tiene elegido el " + FechaDelPlan.legible(fecha) + ".");
        }
        return Optional.empty();
    }

    /** Lo que ve la persona junto a los botones. Dice "anotar" a proposito: ver D-H3 en el javadoc. */
    static String resumenDe(HabitoSemanal habito, LocalDate fecha) {
        String resumen = "Anotar el " + FechaDelPlan.legible(fecha) + " como tu dia de '" + habito.titulo()
                + "' esta semana";
        return habito.diaElegido() == null ? resumen
                : resumen + " (en lugar del " + FechaDelPlan.legible(habito.diaElegido()) + ")";
    }

    private static String semanales(PlanDelAprendiz plan) {
        if (plan.semanales().isEmpty()) {
            return "No tiene habitos de eleccion semanal.";
        }
        return "Los de eleccion semanal son:\n" + plan.semanales().stream()
                .map(habito -> "habito_id=" + habito.habitoId() + " | " + habito.titulo()
                        + (habito.diaElegido() == null ? "" : " | elegido=" + habito.diaElegido())
                        + " | dias_posibles=" + habito.diasElegibles())
                .collect(Collectors.joining("\n"));
    }

    private static String legibles(List<LocalDate> fechas) {
        return fechas.stream().map(fecha -> FechaDelPlan.legible(fecha) + " = " + fecha)
                .collect(Collectors.joining(", "));
    }

    /** Se guarda la invocacion normalizada —id limpio y fecha ISO—, no la que mando el modelo. */
    private static InvocacionHerramienta invocacionPara(UUID habitoId, LocalDate fecha) {
        return new InvocacionHerramienta(NOMBRE,
                Map.of(ARGUMENTO_HABITO_ID, habitoId.toString(), ARGUMENTO_FECHA, fecha.toString()));
    }
}

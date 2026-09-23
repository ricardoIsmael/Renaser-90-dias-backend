package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.in.propuesta.ProponerAccionUseCase;
import com.renaser.os.rag.application.ports.out.enfoque.EnfoqueDiarioDelAprendizPort;
import com.renaser.os.rag.application.ports.out.enfoque.EnfoqueDiarioDelAprendizPort.SantuarioDeHoy;
import com.renaser.os.rag.domain.model.herramienta.DefinicionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ParametroHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.rag.domain.model.herramienta.TipoParametroHerramienta;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * {@code proponer_iniciar_santuario} (R2, propuesta, 2026-09-23): iniciar la sesion de Santuario de
 * hoy. No escribe: deja una propuesta; la escritura es {@link IniciarSantuarioConfirmable}, que
 * llama al mismo caso de uso que la app ({@code IniciarSesionBloqueoUseCase}).
 *
 * <p><b>Solo iniciar.</b> Completar el Santuario o salir antes son flujos de la app (evidencia y
 * honestidad); esta herramienta no los ofrece y el modelo tampoco.
 *
 * <p>Valida antes de proponer con datos de {@code habits}: que hoy haya un Santuario, que siga
 * pendiente y sin sesion, y que ya sea su hora ({@code iniciableDesde}, el mismo disparo que usa el
 * caso de uso). Al confirmar, el caso de uso vuelve a decidir.
 */
@Component
@ConditionalOnProperty(name = "renaser.ia.acompanante.confirmacion-con-botones", havingValue = "true")
public class PropuestaDeIniciarSantuario implements HerramientaAgente {

    public static final String NOMBRE = "proponer_iniciar_santuario";
    public static final String ARGUMENTO_REGISTRO_ID = "registro_id";

    private static final Logger log = LoggerFactory.getLogger(PropuestaDeIniciarSantuario.class);

    private static final DefinicionHerramienta DEFINICION = new DefinicionHerramienta(NOMBRE,
            "Propone iniciar ahora su Santuario de hoy (tiempo de bloqueo). NO lo inicia: deja una propuesta y la "
                    + "persona tiene que tocar Confirmar en la app. Solo sirve para EMPEZAR: completarlo o salir se "
                    + "hace en la app, no lo ofrezcas. Usala solo si la persona pidio empezar su Santuario.",
            List.of(new ParametroHerramienta(ARGUMENTO_REGISTRO_ID, TipoParametroHerramienta.IDENTIFICADOR,
                    "Solo si tiene mas de un Santuario hoy: el registro_id que te devuelve esta herramienta al "
                            + "listarlos. Omitelo en otro caso.", false)));

    private final EnfoqueDiarioDelAprendizPort enfoquePort;
    private final ProponerAccionUseCase proponerAccion;
    private final Clock clock;

    public PropuestaDeIniciarSantuario(EnfoqueDiarioDelAprendizPort enfoquePort, ProponerAccionUseCase proponerAccion,
                                       Clock clock) {
        this.enfoquePort = enfoquePort;
        this.proponerAccion = proponerAccion;
        this.clock = clock;
    }

    @Override
    public DefinicionHerramienta definicion() {
        return DEFINICION;
    }

    @Override
    public ResultadoHerramienta ejecutar(UserId actorId, InvocacionHerramienta invocacion) {
        String pedido = invocacion.argumento(ARGUMENTO_REGISTRO_ID);
        Optional<UUID> registroId = CompletacionDeHabito.registroIdDe(pedido);
        if (!FechaDelPlan.ausente(pedido) && registroId.isEmpty()) {
            return ResultadoHerramienta.fallo("Ese registro_id no es valido.");
        }
        return LecturaDelEnfoque.con(() -> enfoquePort.santuariosDeHoy(actorId), "su Santuario de hoy",
                santuarios -> elegirYProponer(actorId, santuarios, registroId));
    }

    private ResultadoHerramienta elegirYProponer(UserId actorId, List<SantuarioDeHoy> santuarios,
                                                 Optional<UUID> registroId) {
        if (santuarios.isEmpty()) {
            return ResultadoHerramienta.fallo("Hoy no tiene un Santuario en su plan.");
        }
        Optional<SantuarioDeHoy> elegido = registroId.isPresent()
                ? santuarios.stream().filter(santuario -> santuario.registroId().equals(registroId.get())).findFirst()
                : unicoCandidato(santuarios);
        if (elegido.isEmpty()) {
            return ResultadoHerramienta.fallo((registroId.isPresent() ? "Ese registro_id no es un Santuario de hoy. "
                    : "Tiene mas de un Santuario hoy: preguntale cual y vuelve a llamar con su registro_id.\n")
                    + listado(santuarios));
        }
        return proponerSiCorresponde(actorId, elegido.get());
    }

    /** Con uno solo, ese; con varios, el unico que todavia se puede iniciar (si hay exactamente uno). */
    private static Optional<SantuarioDeHoy> unicoCandidato(List<SantuarioDeHoy> santuarios) {
        if (santuarios.size() == 1) {
            return Optional.of(santuarios.getFirst());
        }
        List<SantuarioDeHoy> iniciables = santuarios.stream().filter(SantuarioDeHoy::iniciable).toList();
        return iniciables.size() == 1 ? Optional.of(iniciables.getFirst()) : Optional.empty();
    }

    private ResultadoHerramienta proponerSiCorresponde(UserId actorId, SantuarioDeHoy santuario) {
        if (!santuario.iniciable()) {
            return ResultadoHerramienta.fallo("'" + santuario.titulo() + "' ya se inicio hoy o ya no esta pendiente: "
                    + "no se puede volver a iniciar.");
        }
        MomentoDelAprendiz momento = new MomentoDelAprendiz(clock.now(), santuario.zona());
        if (santuario.iniciableDesde() != null && momento.ahora().isBefore(santuario.iniciableDesde())) {
            return ResultadoHerramienta.fallo("Todavia no es la hora: '" + santuario.titulo() + "' se puede iniciar "
                    + "desde las " + momento.horaDe(santuario.iniciableDesde()) + " (faltan "
                    + momento.faltaPara(santuario.iniciableDesde()) + ").");
        }
        String resumen = "Iniciar tu Santuario '" + santuario.titulo() + "' ahora";
        try {
            proponerAccion.proponer(actorId, new InvocacionHerramienta(NOMBRE,
                    Map.of(ARGUMENTO_REGISTRO_ID, santuario.registroId().toString())), resumen);
        } catch (RuntimeException falla) {
            log.warn("[rag] no se pudo guardar la propuesta de {}", NOMBRE, falla);
            return AvisoDePropuesta.noSePudoPreparar();
        }
        return AvisoDePropuesta.creada(resumen, "Completarlo o salir antes se hace desde la app.");
    }

    private static String listado(List<SantuarioDeHoy> santuarios) {
        return santuarios.stream()
                .map(santuario -> "registro_id=" + santuario.registroId() + " | " + santuario.titulo()
                        + (santuario.iniciable() ? "" : " | ya iniciado o no pendiente"))
                .collect(Collectors.joining("\n"));
    }
}

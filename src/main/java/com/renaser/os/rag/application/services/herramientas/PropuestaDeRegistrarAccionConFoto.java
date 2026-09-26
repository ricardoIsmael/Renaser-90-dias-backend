package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.in.propuesta.ConsultarPropuestasDelTurnoUseCase.PedidoDeEvidencia;
import com.renaser.os.rag.application.ports.out.habitos.ConsultarAgendaHabitosPort;
import com.renaser.os.rag.application.ports.out.rocas.ConsultarRocasDelAprendizPort;
import com.renaser.os.rag.application.ports.out.rocas.ConsultarRocasDelAprendizPort.RocaDelDia;
import com.renaser.os.rag.domain.model.conversacion.DestinoDeEvidencia;
import com.renaser.os.rag.domain.model.herramienta.DefinicionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ParametroHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.rag.domain.model.herramienta.TipoParametroHerramienta;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code proponer_registrar_accion_con_foto} (D-178, decision del dueno del 2026-09-26): marcar como
 * hecha una accion del dia (roca diaria) desde el acompanante funciona EXACTAMENTE como un habito que
 * exige evidencia ({@link PropuestaDeRegistrarConFoto}): se deja la tarjeta de la camara, la persona
 * saca la foto y la app la sube como evidencia de la roca con {@code POST /rocks/{id}/evidence}, que
 * la completa y paga sus puntos de siempre. Aca solo se valida y se pide la tarjeta, con
 * {@link DestinoDeEvidencia#ROCA} para que la app vaya a los endpoints de rocas.
 *
 * <p><b>Valida con los datos de {@code rocks}</b> ({@code RocasDelAprendizFinder.deHoy}, que ya
 * resuelve "hoy" en la zona de la persona y el bloqueo Pareto): la roca tiene que ser una de SUS
 * acciones de hoy y no estar completada. Si esta bloqueada (no es la verde de su eje y la verde no
 * tiene evidencia), se rechaza diciendo cual va primero: la tarjeta terminaria en el
 * {@code 403 GREEN_NOT_EVIDENCED} de {@code CompletarRocaDiariaUseCase}, que sigue siendo quien
 * decide.
 *
 * <p>La tarjeta vence al terminar el dia local de la persona (regla 02), igual que la del habito.
 * Una accion nunca pregunta "¿Que sentiste?": eso es solo de los rituales (D-172).
 *
 * <p>Solo existe con {@code renaser.ia.acompanante.confirmacion-con-botones}, como la de habitos.
 */
@Component
@ConditionalOnProperty(name = "renaser.ia.acompanante.confirmacion-con-botones", havingValue = "true")
public class PropuestaDeRegistrarAccionConFoto implements HerramientaAgente {

    public static final String NOMBRE = "proponer_registrar_accion_con_foto";
    public static final String ARGUMENTO_ROCA_ID = "roca_id";

    private static final Logger log = LoggerFactory.getLogger(PropuestaDeRegistrarAccionConFoto.class);

    private static final DefinicionHerramienta DEFINICION = new DefinicionHerramienta(NOMBRE,
            "Para registrar como hecha una accion (roca) de HOY: le deja a la persona en la app un boton que "
                    + "abre la camara; saca la foto y la app la registra y suma sus puntos. NO la registra por "
                    + "si sola. Usala directo cuando diga que hizo o quiere marcar una accion de hoy, sin "
                    + "preguntar antes.",
            List.of(ParametroHerramienta.obligatorio(ARGUMENTO_ROCA_ID, TipoParametroHerramienta.IDENTIFICADOR,
                    "El roca_id de la accion, tal cual lo devolvio consultar_rocas con alcance hoy. No lo "
                            + "inventes.")));

    private final ConsultarRocasDelAprendizPort rocasPort;
    private final ConsultarAgendaHabitosPort agendaPort;
    private final PedidosDeEvidenciaDelTurno pedidos;
    private final Clock clock;

    public PropuestaDeRegistrarAccionConFoto(ConsultarRocasDelAprendizPort rocasPort,
                                             ConsultarAgendaHabitosPort agendaPort,
                                             PedidosDeEvidenciaDelTurno pedidos, Clock clock) {
        this.rocasPort = rocasPort;
        this.agendaPort = agendaPort;
        this.pedidos = pedidos;
        this.clock = clock;
    }

    @Override
    public DefinicionHerramienta definicion() {
        return DEFINICION;
    }

    @Override
    public ResultadoHerramienta ejecutar(UserId actorId, InvocacionHerramienta invocacion) {
        return rocaIdDe(invocacion.argumento(ARGUMENTO_ROCA_ID))
                .map(rocaId -> pedirTraduciendoFallos(actorId, rocaId))
                .orElseGet(() -> ResultadoHerramienta.fallo("Ese roca_id no es valido. Usa el que devuelve "
                        + "consultar_rocas con alcance hoy."));
    }

    private static Optional<UUID> rocaIdDe(String valor) {
        try {
            return valor == null ? Optional.empty() : Optional.of(UUID.fromString(valor.trim()));
        } catch (IllegalArgumentException invalido) {
            return Optional.empty();
        }
    }

    private ResultadoHerramienta pedirTraduciendoFallos(UserId actorId, UUID rocaId) {
        try {
            return pedirSiCorresponde(actorId, rocaId);
        } catch (NoSuchElementException sinPrograma) {
            return ResultadoHerramienta.fallo("No encontre un programa activo para esta cuenta.");
        } catch (NotAuthorizedException sinAcceso) {
            return ResultadoHerramienta.fallo("No puedo registrar acciones: la cuenta esta suspendida o todavia "
                    + "no tiene el programa de rocas activo.");
        } catch (RuntimeException falla) {
            log.warn("[rag] la herramienta {} no pudo leer las rocas de hoy", NOMBRE, falla);
            return ResultadoHerramienta.fallo("No pude revisar sus acciones de hoy en este momento.");
        }
    }

    private ResultadoHerramienta pedirSiCorresponde(UserId actorId, UUID rocaId) {
        List<RocaDelDia> deHoy = rocasPort.deHoy(actorId).rocas();
        Optional<RocaDelDia> roca = deHoy.stream().filter(r -> rocaId.equals(r.id())).findFirst();
        if (roca.isEmpty()) {
            return ResultadoHerramienta.fallo("Esa accion no esta entre las de hoy de la persona. Consulta "
                    + "consultar_rocas con alcance hoy y usa el roca_id que devuelve.");
        }
        return motivoParaNoPedir(roca.get(), deHoy).orElseGet(() -> pedir(actorId, roca.get()));
    }

    /** Vacio si se puede pedir la foto; si no, el motivo para el modelo. */
    static Optional<ResultadoHerramienta> motivoParaNoPedir(RocaDelDia roca, List<RocaDelDia> deHoy) {
        if (roca.completada()) {
            return Optional.of(ResultadoHerramienta.fallo("'" + roca.titulo() + "' ya esta registrada hoy: no "
                    + "hace falta otra foto."));
        }
        if (roca.bloqueadaPorPareto()) {
            return Optional.of(ResultadoHerramienta.fallo(bloqueadaPorPareto(roca, deHoy)));
        }
        return Optional.empty();
    }

    /** Ley IV: en cada eje, la verde va primero. Se nombra cual, para que la persona sepa que hacer. */
    private static String bloqueadaPorPareto(RocaDelDia roca, List<RocaDelDia> deHoy) {
        String motivo = "'" + roca.titulo() + "' todavia no se puede registrar: en cada eje primero va la accion "
                + "verde, y la de " + roca.eje() + " todavia no tiene su evidencia. ";
        return deHoy.stream()
                .filter(r -> r.eje().equals(roca.eje()) && "VERDE".equals(r.color()) && !r.completada())
                .findFirst()
                .map(verde -> motivo + "Primero tiene que registrar '" + verde.titulo() + "' (roca_id="
                        + verde.id() + "). Diselo en una frase y ofrecele la foto de esa.")
                .orElse(motivo + "Diselo en una frase: primero tiene que completar la verde de ese eje.");
    }

    private ResultadoHerramienta pedir(UserId actorId, RocaDelDia roca) {
        Instant ahora = clock.now();
        pedidos.pedir(actorId, new PedidoDeEvidencia(roca.id(), roca.titulo(), ahora,
                PropuestaDeRegistrarConFoto.finDelDiaLocal(ahora, agendaPort.zonaDe(actorId)), false,
                DestinoDeEvidencia.ROCA));
        return ResultadoHerramienta.exito("Boton de la camara listo para la accion '" + roca.titulo() + "'. "
                + "TODAVIA NO esta registrada: se registra, y suma sus puntos, cuando la persona saque la foto, "
                + "en la app. Dilo en una sola frase corta, por ejemplo \"Te deje abajo el boton para sacarle "
                + "foto a " + roca.titulo() + "\". No preguntes si quiere, no digas que ya quedo y no la "
                + "mandes a Training.");
    }
}

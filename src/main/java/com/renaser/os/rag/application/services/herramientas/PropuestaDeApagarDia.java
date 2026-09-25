package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.in.propuesta.ProponerAccionUseCase;
import com.renaser.os.rag.application.ports.out.horarios.ConsultarHorariosPort;
import com.renaser.os.rag.application.ports.out.horarios.ConsultarHorariosPort.HorarioDeHabito;
import com.renaser.os.rag.application.ports.out.horarios.ConsultarHorariosPort.HorariosDelDia;
import com.renaser.os.rag.domain.model.herramienta.DefinicionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ParametroHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.rag.domain.model.herramienta.TipoParametroHerramienta;
import com.renaser.os.shared.domain.UserId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@code proponer_apagar_dia} (R2, fase 4, 2026-09-23): PROPONE apagar un habito un dia concreto
 * ("el jueves no medito"), o volver a encenderlo. La escritura la hace {@link ApagarDiaConfirmable}
 * cuando la persona toca "Confirmar" (D-153); en {@code habits} es {@code CambiarEstadoHabitoEnFechaUseCase},
 * el del {@code PATCH …/days/{date}}.
 *
 * <p>Antes de proponer mira {@code consultar_horarios}: el dia no puede haber pasado (hoy SI se
 * puede apagar, a diferencia de cambiar la hora) y un obligatorio (V18) no se apaga. No consume la
 * cuota de cambios. Solo se ofrece con {@code confirmacion-con-botones=true}.
 */
@Component
@ConditionalOnProperty(name = "renaser.ia.acompanante.confirmacion-con-botones", havingValue = "true")
public class PropuestaDeApagarDia implements HerramientaAgente {

    public static final String NOMBRE = "proponer_apagar_dia";
    static final String APAGAR = "apagar";
    static final String ENCENDER = "encender";

    private static final DefinicionHerramienta DEFINICION = new DefinicionHerramienta(NOMBRE,
            "PROPONE apagar un habito del aprendiz UN dia concreto (ese dia no se le pide), o volver a encenderlo. "
                    + "NO cambia nada: deja una propuesta que la persona confirma con un boton. Llama SIEMPRE antes "
                    + "a consultar_horarios para tener el habito_id y ver si es obligatorio (los obligatorios no se "
                    + "apagan). Los dias pasados no se tocan. No gasta cambios de horario. Nunca digas que ya quedo "
                    + "apagado.",
            List.of(ParametroHerramienta.obligatorio(ArgumentosDeHorario.HABITO_ID,
                            TipoParametroHerramienta.IDENTIFICADOR, "El habito_id que devolvio consultar_horarios."),
                    ParametroHerramienta.obligatorio(ArgumentosDeHorario.FECHA, TipoParametroHerramienta.TEXTO,
                            "El dia, yyyy-MM-dd. Hoy o un dia futuro; no calcules tu la fecha de hoy, tomala de "
                                    + "consultar_horarios."),
                    new ParametroHerramienta(ArgumentosDeHorario.ACCION, TipoParametroHerramienta.TEXTO,
                            "'apagar' (por defecto) o 'encender' para volver a activarlo ese dia.", false)));

    private final ConsultarHorariosPort horariosPort;
    private final ProponerAccionUseCase proponerAccion;

    public PropuestaDeApagarDia(ConsultarHorariosPort horariosPort, ProponerAccionUseCase proponerAccion) {
        this.horariosPort = horariosPort;
        this.proponerAccion = proponerAccion;
    }

    @Override
    public DefinicionHerramienta definicion() {
        return DEFINICION;
    }

    @Override
    public ResultadoHerramienta ejecutar(UserId actorId, InvocacionHerramienta invocacion) {
        try {
            DiaPedido leido = DiaPedido.de(invocacion);
            HorariosDelDia hoy = HorariosParaProponer.de(horariosPort, actorId, null);
            DiaPedido pedido = leido.dentroDelPrograma(hoy);
            requireNoEsObligatorio(pedido, hoy);
            if (pedido.fecha().isBefore(hoy.fecha())) {
                throw new PropuestaImposibleException("Ese dia ya paso: solo se puede apagar hoy o un dia futuro. "
                        + "Hoy es " + ArgumentosDeHorario.texto(hoy.fecha()) + ".");
            }
            HorariosDelDia dia = pedido.fecha().equals(hoy.fecha()) ? hoy
                    : HorariosParaProponer.de(horariosPort, actorId, pedido.fecha());
            HorarioDeHabito habito = HorariosParaProponer.habito(dia, pedido.habitoId());
            requirePosible(pedido, habito);
            String resumen = resumenDe(pedido, habito, pedido.fecha().equals(hoy.fecha()));
            return PropuestaPendiente.registrar(proponerAccion, actorId, pedido.invocacion(), resumen);
        } catch (PropuestaImposibleException imposible) {
            return ResultadoHerramienta.fallo(imposible.getMessage());
        }
    }

    /**
     * Un obligatorio no se apaga ningun dia: se dice antes de mirar la fecha. Si no, una fecha mal
     * armada respondia "queda fuera del programa" y el modelo daba ese motivo, que no era el real
     * (bateria del 2026-09-25, ronda 2).
     */
    private static void requireNoEsObligatorio(DiaPedido pedido, HorariosDelDia hoy) {
        if (!pedido.apagar()) {
            return;
        }
        hoy.habitos().stream()
                .filter(habito -> habito.obligatorio() && pedido.habitoId().equals(habito.habitoId()))
                .findFirst()
                .ifPresent(habito -> {
                    throw new PropuestaImposibleException(LoQueSiSePuede.obligatorio(habito.titulo()));
                });
    }

    private static void requirePosible(DiaPedido pedido, HorarioDeHabito habito) {
        if (pedido.apagar() && habito.obligatorio()) {
            throw new PropuestaImposibleException(LoQueSiSePuede.obligatorio(habito.titulo()));
        }
        if (habito.pausado()) {
            throw new PropuestaImposibleException(LoQueSiSePuede.pausado(habito.titulo()));
        }
        if (pedido.apagar() && habito.apagado()) {
            throw new PropuestaImposibleException("'" + habito.titulo() + "' ya esta apagado ese dia.");
        }
        if (!pedido.apagar() && !habito.apagado()) {
            throw new PropuestaImposibleException("'" + habito.titulo() + "' no esta apagado ese dia: no hay nada "
                    + "que encender.");
        }
    }

    /** Lo que ve la persona junto a los botones. */
    static String resumenDe(DiaPedido pedido, HorarioDeHabito habito, boolean esHoy) {
        String dia = (esHoy ? "hoy, " : "el ") + ArgumentosDeHorario.texto(pedido.fecha());
        if (pedido.apagar()) {
            return "Apagar '" + habito.titulo() + "' solo " + dia + ": ese dia no se le va a pedir. No gasta cambios "
                    + "de horario.";
        }
        return "Volver a activar '" + habito.titulo() + "' " + dia + ", con su horario de ese dia.";
    }

    /** Los argumentos ya leidos, y su forma canonica: la que se guarda y se ejecuta al confirmar. */
    record DiaPedido(UUID habitoId, LocalDate fecha, boolean apagar) {

        static DiaPedido de(InvocacionHerramienta invocacion) {
            UUID habitoId = ArgumentosDeHorario.habitoId(invocacion.argumento(ArgumentosDeHorario.HABITO_ID));
            LocalDate fecha = ArgumentosDeHorario.fecha(invocacion.argumento(ArgumentosDeHorario.FECHA));
            return new DiaPedido(habitoId, fecha, esApagar(invocacion.argumento(ArgumentosDeHorario.ACCION)));
        }

        private static boolean esApagar(String accion) {
            if (!ArgumentosDeHorario.presente(accion) || APAGAR.equalsIgnoreCase(accion.trim())) {
                return true;
            }
            if (ENCENDER.equalsIgnoreCase(accion.trim())) {
                return false;
            }
            throw new PropuestaImposibleException("La accion tiene que ser 'apagar' o 'encender'.");
        }

        /** E-276: con el año corregido si el modelo lo armo con uno viejo. */
        DiaPedido dentroDelPrograma(HorariosDelDia hoy) {
            return new DiaPedido(habitoId, HorariosParaProponer.dentroDelPrograma(fecha, hoy), apagar);
        }

        InvocacionHerramienta invocacion() {
            return new InvocacionHerramienta(NOMBRE, Map.of(ArgumentosDeHorario.HABITO_ID, habitoId.toString(),
                    ArgumentosDeHorario.FECHA, fecha.toString(),
                    ArgumentosDeHorario.ACCION, apagar ? APAGAR : ENCENDER));
        }
    }
}

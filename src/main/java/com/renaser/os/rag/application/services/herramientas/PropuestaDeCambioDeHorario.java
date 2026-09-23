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
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@code proponer_cambio_de_horario} (R2, fase 4, 2026-09-23): PROPONE cambiar la hora de un
 * habito, en general o para un solo dia futuro. La escritura la hace
 * {@link CambioDeHorarioConfirmable} cuando la persona toca "Confirmar" (D-153).
 *
 * <p>Antes de proponer valida con {@code consultar_horarios} lo que ya se sabe que {@code habits}
 * rechazaria: fecha que no es futura, habito que no es de la persona, y <b>cupo semanal agotado</b>
 * ({@code CuotaEdicionHorario}, §5.0: el acompanante no tiene cuota propia ni la saltea). El resumen
 * dice el cambio exacto, desde cuando rige y cuantos cambios le quedarian.
 *
 * <p>Solo se ofrece con {@code renaser.ia.acompanante.confirmacion-con-botones=true}: sin botones
 * en la app, la persona no tendria como confirmar.
 */
@Component
@ConditionalOnProperty(name = "renaser.ia.acompanante.confirmacion-con-botones", havingValue = "true")
public class PropuestaDeCambioDeHorario implements HerramientaAgente {

    public static final String NOMBRE = "proponer_cambio_de_horario";

    private static final DefinicionHerramienta DEFINICION = new DefinicionHerramienta(NOMBRE,
            "PROPONE cambiar la hora de inicio (y opcionalmente la hora limite) de un habito del aprendiz: como "
                    + "horario general desde manana, o solo para una fecha futura. NO cambia nada: deja una "
                    + "propuesta que la persona confirma con un boton. Llama SIEMPRE antes a consultar_horarios "
                    + "para tener el habito_id y el cupo de cambios. Nunca digas que el cambio ya esta hecho. El dia "
                    + "de hoy no se puede reacomodar.",
            List.of(ParametroHerramienta.obligatorio(ArgumentosDeHorario.HABITO_ID,
                            TipoParametroHerramienta.IDENTIFICADOR, "El habito_id que devolvio consultar_horarios."),
                    ParametroHerramienta.obligatorio(ArgumentosDeHorario.HORA_INICIO, TipoParametroHerramienta.TEXTO,
                            "Nueva hora de inicio, HH:mm en 24 horas (por ejemplo 06:30)."),
                    new ParametroHerramienta(ArgumentosDeHorario.HORA_LIMITE, TipoParametroHerramienta.TEXTO,
                            "Nueva hora limite, HH:mm, posterior a la de inicio. Omitela si la persona no la pidio.",
                            false),
                    new ParametroHerramienta(ArgumentosDeHorario.FECHA, TipoParametroHerramienta.TEXTO,
                            "Solo si el cambio es para UN dia: fecha futura yyyy-MM-dd. Omitela para cambiar el "
                                    + "horario general desde manana.", false)));

    private final ConsultarHorariosPort horariosPort;
    private final ProponerAccionUseCase proponerAccion;

    public PropuestaDeCambioDeHorario(ConsultarHorariosPort horariosPort, ProponerAccionUseCase proponerAccion) {
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
            CambioPedido cambio = CambioPedido.de(invocacion);
            HorariosDelDia hoy = HorariosParaProponer.de(horariosPort, actorId, null);
            LocalDate rigeDesde = rigeDesde(cambio, hoy.fecha());
            HorariosDelDia diaQueCambia = HorariosParaProponer.de(horariosPort, actorId, rigeDesde);
            HorarioDeHabito actual = HorariosParaProponer.habito(diaQueCambia, cambio.habitoId());
            HorariosParaProponer.requireCupo(diaQueCambia.cuota());
            String resumen = resumenDe(cambio, actual, rigeDesde) + " "
                    + HorariosParaProponer.gastoDeCupo(diaQueCambia.cuota());
            return PropuestaPendiente.registrar(proponerAccion, actorId, cambio.invocacion(), resumen);
        } catch (PropuestaImposibleException imposible) {
            return ResultadoHerramienta.fallo(imposible.getMessage());
        }
    }

    /**
     * Un cambio general rige desde manana (D-91: el dia en curso no se reacomoda); uno puntual, desde
     * su fecha, que tiene que ser futura. "Hoy" es el que dio {@code habits} en la zona del
     * participante. Al confirmar, {@code habits} lo vuelve a decidir con su propio reloj.
     */
    private static LocalDate rigeDesde(CambioPedido cambio, LocalDate hoy) {
        if (cambio.fecha() == null) {
            return hoy.plusDays(1);
        }
        if (!cambio.fecha().isAfter(hoy)) {
            throw new PropuestaImposibleException("Solo se pueden reacomodar dias futuros: el dia de hoy y los "
                    + "anteriores no se cambian. Hoy es " + ArgumentosDeHorario.texto(hoy) + ".");
        }
        return cambio.fecha();
    }

    /** Lo que ve la persona junto a los botones: el cambio exacto y desde cuando. */
    static String resumenDe(CambioPedido cambio, HorarioDeHabito actual, LocalDate rigeDesde) {
        String deA = "de " + HorariosParaProponer.franja(actual.horaDisparo(), actual.horaLimite()) + " a "
                + HorariosParaProponer.franjaPedida(cambio.horaInicio(), cambio.horaLimite());
        if (cambio.fecha() == null) {
            return "Cambiar '" + actual.titulo() + "' " + deA + " como horario general, desde el "
                    + ArgumentosDeHorario.texto(rigeDesde) + " (hoy sigue igual).";
        }
        return "Cambiar '" + actual.titulo() + "' solo el " + ArgumentosDeHorario.texto(rigeDesde) + ", " + deA
                + " (los demas dias no cambian).";
    }

    /** Los argumentos ya leidos, y su forma canonica: la que se guarda y se ejecuta al confirmar. */
    record CambioPedido(UUID habitoId, LocalTime horaInicio, LocalTime horaLimite, LocalDate fecha) {

        static CambioPedido de(InvocacionHerramienta invocacion) {
            UUID habitoId = ArgumentosDeHorario.habitoId(invocacion.argumento(ArgumentosDeHorario.HABITO_ID));
            LocalTime inicio = ArgumentosDeHorario.hora(invocacion.argumento(ArgumentosDeHorario.HORA_INICIO),
                    ArgumentosDeHorario.HORA_INICIO);
            String limite = invocacion.argumento(ArgumentosDeHorario.HORA_LIMITE);
            String fecha = invocacion.argumento(ArgumentosDeHorario.FECHA);
            CambioPedido cambio = new CambioPedido(habitoId, inicio,
                    ArgumentosDeHorario.presente(limite) ? ArgumentosDeHorario.hora(limite,
                            ArgumentosDeHorario.HORA_LIMITE) : null,
                    ArgumentosDeHorario.presente(fecha) ? ArgumentosDeHorario.fecha(fecha) : null);
            cambio.requireLimitePosterior();
            return cambio;
        }

        /**
         * {@code habits} no rechaza un limite anterior al inicio: lo acomoda solo a fin del dia (D-122).
         * Proponerlo mostraria en el boton una hora que no es la que se guarda, asi que se pide bien.
         */
        private void requireLimitePosterior() {
            if (horaLimite != null && !horaLimite.isAfter(horaInicio)) {
                throw new PropuestaImposibleException("La hora_limite tiene que ser posterior a la hora_inicio. "
                        + "Preguntale a la persona hasta que hora quiere tener el habito.");
            }
        }

        InvocacionHerramienta invocacion() {
            Map<String, String> argumentos = new HashMap<>();
            argumentos.put(ArgumentosDeHorario.HABITO_ID, habitoId.toString());
            argumentos.put(ArgumentosDeHorario.HORA_INICIO, ArgumentosDeHorario.texto(horaInicio));
            if (horaLimite != null) {
                argumentos.put(ArgumentosDeHorario.HORA_LIMITE, ArgumentosDeHorario.texto(horaLimite));
            }
            if (fecha != null) {
                argumentos.put(ArgumentosDeHorario.FECHA, fecha.toString());
            }
            return new InvocacionHerramienta(NOMBRE, argumentos);
        }
    }
}

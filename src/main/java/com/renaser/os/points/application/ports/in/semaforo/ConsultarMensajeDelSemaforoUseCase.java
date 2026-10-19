package com.renaser.os.points.application.ports.in.semaforo;

import com.renaser.os.points.application.ports.in.semaforo.ConsultarMiSemaforoUseCase.ResumenParaHoy;
import com.renaser.os.points.domain.model.semaforo.CumplimientoDelDia;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.ParticipacionPrograma;

import java.util.Objects;
import java.util.Optional;

/**
 * El mensaje del día que acompaña al semáforo de la tarjeta de Hoy (D-277, spec 006): elige uno del catálogo la
 * primera vez que la persona lo pide cada día (en su zona), lo mantiene durante el día y lo escribe con los datos de
 * ese momento.
 *
 * <p>No relee el semáforo, la participación ni lo de hoy: quien llama (el agregado de {@code /home}) ya los leyó y los
 * pasa, así el mensaje no suma consultas por lo que ya se sabe (SC-004).
 */
public interface ConsultarMensajeDelSemaforoUseCase {

    /** Vacío si no hay ningún mensaje posible para la situación de la persona. */
    Optional<MensajeDelDia> deHoy(ConsultaDelMensaje consulta);

    /**
     * @param participacion la de la persona, tal como la lee {@code /home}
     * @param semaforo      el resumen de la tarjeta (ventana vigente); el mensaje nunca contradice su color
     * @param deHoy         lo que la persona tenía por hacer hoy y lo que ya hizo, con las listas que {@code /home}
     *                      ya leyó: de ahí sale el {@code {faltan}}, sin una consulta más. Vacío cuando quien llama no
     *                      lo conoce o no lo necesita (la tarjeta del chat, D-281, que no dice {@code {faltan}}): sin
     *                      esto no hay «cuánto falta»
     */
    record ConsultaDelMensaje(ParticipacionPrograma participacion, ResumenParaHoy semaforo,
                              Optional<CumplimientoDelDia> deHoy) {

        public ConsultaDelMensaje {
            Objects.requireNonNull(participacion, "participacion es obligatoria");
            Objects.requireNonNull(semaforo, "semaforo es obligatorio");
            Objects.requireNonNull(deHoy, "deHoy es obligatorio");
        }

        /** Con lo de hoy ya leído, como lo arma {@code /home}. */
        public ConsultaDelMensaje(ParticipacionPrograma participacion, ResumenParaHoy semaforo,
                                  CumplimientoDelDia deHoy) {
            this(participacion, semaforo, Optional.of(Objects.requireNonNull(deHoy, "deHoy es obligatorio")));
        }

        /** Sin lo de hoy: el mensaje no puede decir cuánto falta para subir de color. */
        public static ConsultaDelMensaje sinLoDeHoy(ParticipacionPrograma participacion, ResumenParaHoy semaforo) {
            return new ConsultaDelMensaje(participacion, semaforo, Optional.empty());
        }

        public UserId persona() {
            return participacion.participanteId();
        }
    }

    /**
     * @param id      estable durante el día mientras no cambie el grupo
     * @param texto   ya escrito: sin llaves, en tuteo, hasta 300 caracteres
     * @param sticker nombre del sticker del búho que lo acompaña
     */
    record MensajeDelDia(String id, String texto, String sticker) {
    }
}

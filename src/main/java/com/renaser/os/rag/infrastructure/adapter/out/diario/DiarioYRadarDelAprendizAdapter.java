package com.renaser.os.rag.infrastructure.adapter.out.diario;

import com.renaser.os.habits.api.DiarioYRadarPort;
import com.renaser.os.rag.application.ports.out.diario.DiarioYRadarDelAprendizPort;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Implementa {@link DiarioYRadarDelAprendizPort} delegando en el contrato publico de
 * {@code habits} (D-41). Es una traduccion y nada mas: que es "hoy", la hora local, el "uno por
 * hora" y todas las guardas los resuelve {@code habits}. Mismo patron que
 * {@code GestionarPlanDeHabitosAdapter}. No loguea nada: el contenido es personal.
 */
@Component
class DiarioYRadarDelAprendizAdapter implements DiarioYRadarDelAprendizPort {

    private final DiarioYRadarPort diarioYRadar;

    DiarioYRadarDelAprendizAdapter(DiarioYRadarPort diarioYRadar) {
        this.diarioYRadar = diarioYRadar;
    }

    @Override
    public BitacoraDeHoy bitacoraDeHoy(UserId participanteId) {
        return aBitacora(diarioYRadar.bitacoraDeHoy(participanteId));
    }

    @Override
    public BitacoraDeHoy escribirBitacoraDeHoy(UserId actorId, String texto) {
        return aBitacora(diarioYRadar.escribirBitacoraDeHoy(actorId, texto));
    }

    @Override
    public Optional<CheckInRadar> ultimoCheckInRadar(UserId participanteId) {
        return diarioYRadar.ultimoCheckInRadar(participanteId)
                .map(ultimo -> new CheckInRadar(ultimo.registradoEn(), ultimo.deEstaHora(),
                        aRespuestas(ultimo.respuestas())));
    }

    @Override
    public CheckInRadarRegistrado registrarCheckInRadar(UserId actorId, RespuestasRadar respuestas) {
        DiarioYRadarPort.CheckInRadarRegistrado registrado = diarioYRadar.registrarCheckInRadar(actorId,
                new DiarioYRadarPort.RespuestasRadar(respuestas.queHago(), respuestas.quePienso(),
                        respuestas.queSiento(), respuestas.nivelEnergia(), respuestas.queEvito()));
        return new CheckInRadarRegistrado(registrado.registradoEn(), registrado.yaExistia());
    }

    private static BitacoraDeHoy aBitacora(DiarioYRadarPort.BitacoraDeHoy bitacora) {
        return new BitacoraDeHoy(bitacora.fecha(), bitacora.existe(), bitacora.texto(), bitacora.tieneAudio());
    }

    private static RespuestasRadar aRespuestas(DiarioYRadarPort.RespuestasRadar respuestas) {
        return new RespuestasRadar(respuestas.queHago(), respuestas.quePienso(), respuestas.queSiento(),
                respuestas.nivelEnergia(), respuestas.queEvito());
    }
}

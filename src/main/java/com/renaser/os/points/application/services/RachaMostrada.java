package com.renaser.os.points.application.services;

import com.renaser.os.points.api.DiasConHabitoCumplidoFinder;
import com.renaser.os.points.domain.model.puntaje.Racha;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.ParticipacionPrograma;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDate;
import java.util.NoSuchElementException;

/**
 * La racha que se MUESTRA: dias seguidos con al menos un habito cumplido. Unico lugar donde se
 * arma la ventana y se le pide la racha al dominio; lo usan {@code HomeAgregadoService}
 * ({@code GET /home}) y {@code ResumenPuntajeService} ({@code points.api.ResumenPuntajeFinder}),
 * para que la regla no quede escrita dos veces (2026-09-23).
 *
 * <p><b>Se deriva, no se lee de la fila de puntaje.</b> {@code puntaje.rachaActual()} vale 0
 * para todo el mundo porque quien la avanza ({@code RegistrarCoherenciaDiariaUseCase}) no tiene
 * un solo llamador en el backend — verificado el 2026-09-14. Mostrar ese 0 era decirle a un
 * aprendiz con 30 dias seguidos que no tiene racha.
 *
 * <p><b>Lo que NO hace, a proposito:</b> no toca
 * {@code PuntajeParticipante#actualizarRachaTrasDia}, que es lo que gobierna el bono de puntos.
 * Si esto escribiera la racha, empezarian a otorgarse bonos que hoy no se otorgan, como efecto
 * colateral de un cambio de pantalla. Aca se responde <i>que se muestra</i>; el premio sigue
 * siendo una decision aparte.
 *
 * <p>La ventana arranca en la fecha de inicio del programa: una racha no puede empezar antes de
 * que el programa empiece. Sin inscripcion se miran los ultimos 90 dias, que es el largo del
 * programa entero.
 *
 * <p>Misma politica de falla parcial que el resto de los widgets de Inicio: si el finder se cae,
 * la racha sale en cero en vez de devolver un 500.
 *
 * <p><b>Un programa que todavia no empezo no tiene racha.</b> A quien se inscribe hoy le queda
 * la {@code fechaInicio} en manana, y pedirle al finder "desde manana hasta hoy" es un rango al
 * reves: lanza {@code IllegalArgumentException}, que no es una falla del widget sino un error
 * de programacion, y por eso no se atrapa abajo — tumbaba el {@code /home} entero de cada
 * cuenta nueva durante su primer dia (2026-09-16, en produccion). Se corta antes de preguntar.
 */
final class RachaMostrada {

    private static final Logger log = LoggerFactory.getLogger(RachaMostrada.class);

    private final DiasConHabitoCumplidoFinder diasConHabitoCumplidoFinder;

    RachaMostrada(DiasConHabitoCumplidoFinder diasConHabitoCumplidoFinder) {
        this.diasConHabitoCumplidoFinder = diasConHabitoCumplidoFinder;
    }

    /** "Hoy" es la fecha de {@code ahora} en la zona del participante, nunca la del servidor (regla 02 §1). */
    Racha de(UserId participanteId, ParticipacionPrograma participacion, Instant ahora) {
        try {
            LocalDate hoy = LocalDate.ofInstant(ahora, participacion.zona());
            LocalDate desde = participacion.fechaInicio() != null ? participacion.fechaInicio() : hoy.minusDays(90);
            if (desde.isAfter(hoy)) {
                return Racha.NINGUNA;
            }
            return Racha.derivarDe(diasConHabitoCumplidoFinder.entre(participanteId, desde, hoy), hoy);
        } catch (NoSuchElementException | NotAuthorizedException e) {
            log.warn("[points.RachaMostrada] la racha no aplica para este participante, degradada a cero. causa={}",
                    e.getClass().getSimpleName());
            return Racha.NINGUNA;
        }
    }
}

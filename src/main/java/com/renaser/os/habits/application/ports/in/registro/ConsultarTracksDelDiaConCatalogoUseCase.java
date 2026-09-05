package com.renaser.os.habits.application.ports.in.registro;

import com.renaser.os.habits.domain.model.habito.TipoHabito;
import com.renaser.os.habits.domain.model.registro.PuntosEnJuego;
import com.renaser.os.habits.domain.model.registro.RegistroHabito;
import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Hueco #10: la pantalla de hoy necesita, por cada registro, el catalogo resuelto — titulo,
 * tipo, guia y horario — no solo el registro crudo. Se resuelve con UNA proyeccion de
 * lectura (batch de habitos/horarios/preferencias/guias), nunca N+1 sobre la lista de
 * registros del dia.
 */
public interface ConsultarTracksDelDiaConCatalogoUseCase {

    /** Autoservicio: actorId debe ser el propio participanteId (igual que {@code ConsultarTracksDelDiaUseCase}). */
    List<TrackDelDiaConCatalogo> consultar(UserId actorId, UserId participanteId, LocalDate fecha);

    /**
     * Los tracks de HOY del propio aprendiz, con "hoy" resuelto en SU zona horaria.
     *
     * <p><b>Existe para sacarle esa decision al controller</b> (corregido 2026-09-05, E-105).
     * {@code HabitTrackController.hoy} pasaba {@code LocalDate.now()} — la fecha del SERVIDOR.
     * Con el proceso en UTC y el padron en {@code America/Lima} (UTC-5), a partir de las 19:00
     * hora de Lima esa fecha ya es la de MANANA: la consulta buscaba los registros de un dia que
     * todavia no existe y la pantalla de habitos quedaba vacia todas las noches, justo en la
     * franja de mayor uso. Es el mismo bug de zona que la regla 02 documenta a partir de E-91, y
     * la razon por la que "que dia es hoy para esta persona" no puede vivir en un adaptador de
     * transporte: es una decision de dominio.
     */
    List<TrackDelDiaConCatalogo> consultarHoyDe(UserId participanteId);

    /**
     * {@code horaDisparo}/{@code horaLimite}: horario RESUELTO (preferencia del participante
     * si existe, si no el del catalogo vigente para {@code diaPrograma}/{@code tipoDia} del
     * registro) — {@code null} si el habito no tiene horario configurado en ninguno de los
     * dos. {@code guia}: la vigente para {@code diaPrograma}, o {@code null} si no hay
     * ninguna todavia.
     *
     * <p>{@code puntosEnJuego} (2026-09-05, pedido del dueno: "al mencionar un habito, decir
     * cuantos puntos se ganan o se pierden"): cuanto paga completarlo AHORA, cual es el techo
     * de la escala, y hasta que instante se puede entregar. Lo calcula el servidor y no el
     * movil porque la escala es una regla de negocio (D-97): si el cliente la reimplementa, el
     * dia que cambie el aprendiz ve un numero y cobra otro. Es {@code null} cuando el registro
     * ya esta en un estado terminal — lo que esta hecho, vencido o fallido no tiene nada en
     * juego.
     *
     * <p>NO trae {@code claveSistema}, a proposito: el movil ya la recibe por
     * {@code MiHabitoResponse.systemKey} de {@code GET /api/v1/habits} y une catalogo y track
     * por {@code habitoId}. Repetirla aca seria un segundo lugar por donde el mismo dato puede
     * quedar desincronizado.
     */
    record TrackDelDiaConCatalogo(RegistroHabito registro, String tituloHabito, TipoHabito tipoHabito,
                                   GuiaResumen guia, LocalTime horaDisparo, LocalTime horaLimite,
                                   PuntosEnJuego puntosEnJuego) {
    }

    record GuiaResumen(String mantraTitulo, String mantraIntro, String queHacer, String comoHacerlo) {
    }
}

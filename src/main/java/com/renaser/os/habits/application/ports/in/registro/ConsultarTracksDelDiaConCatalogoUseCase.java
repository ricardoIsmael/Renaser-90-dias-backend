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
     * <p>{@code tieneEvidencia} (2026-09-05, D-113): si ese registro ya tiene al menos una
     * evidencia subida, en cualquier estado de validacion. Lo resuelve el servidor por la misma
     * razon que {@code puntosEnJuego} — porque el cliente no puede hacerlo bien. El movil venia
     * cruzando {@code GET /api/v1/evidence} contra estos ids, y ese listado es UNA pagina de 20
     * filas sin filtro de fecha que mezcla los tres destinos: apenas el aprendiz supera esas 20
     * filas, la evidencia de un habito de hoy queda fuera de la pagina y la pantalla le ofrece
     * "SUBIR" algo que ya subio. Ver {@code evidence.api.RegistrosConEvidenciaFinder}.
     *
     * <p>{@code exigeEvidencia} (2026-09-14): si el CATALOGO pide evidencia para este habito. No
     * confundir con {@code tieneEvidencia}, que dice si YA subio una — son la pregunta y la
     * respuesta, y hacen falta las dos para poder decir "este pide foto y todavia no la subiste".
     *
     * <p>Nace porque el agente conversacional no lo sabia: marcaba un habito como hecho sin poder
     * mencionar que ademas hay que subir evidencia, y la persona se enteraba dias despues por un
     * aviso al mentor de evidencia vencida que a ella nadie le habia pedido. Completar NO la
     * exige —ni desde la app ni desde el agente, ver {@code RegistroService.completar}, que no
     * mira la exigencia en ninguna de sus cuatro guardas—, asi que esto es informacion, no un
     * candado nuevo. Cambiar eso seria otra decision y no esta tomada.
     *
     * <p>{@code claveSistema} (2026-09-26, D-171): la clave funcional del habito de catalogo
     * ({@code DAILY_CLASS}, {@code AUDIO_THERAPY_WEEKLY}...), {@code null} en los personales. La
     * necesitan los llamadores de adentro del servidor —el acompanante tiene que distinguir la
     * Clase diaria, que se cierra con su resumen, y ubicar la Audioterapia de hoy—, que no tienen
     * el catalogo a mano. <b>No viaja al movil:</b> {@code RegistroHabitoConCatalogoResponse} la
     * sigue sin mapear, porque la app ya la recibe por {@code MiHabitoResponse.systemKey} de
     * {@code GET /api/v1/habits} y une catalogo y track por {@code habitoId}.
     *
     * <p>Corregido 2026-09-26. Aca decia "NO trae {@code claveSistema}, a proposito", con el
     * argumento de la app (que sigue valiendo para la respuesta HTTP y por eso no se toco).
     */
    record TrackDelDiaConCatalogo(RegistroHabito registro, String tituloHabito, TipoHabito tipoHabito,
                                   GuiaResumen guia, LocalTime horaDisparo, LocalTime horaLimite,
                                   PuntosEnJuego puntosEnJuego, boolean tieneEvidencia,
                                   boolean exigeEvidencia, String claveSistema) {

        /** Sin clave de sistema: un habito personal, o un llamador que no la necesita. */
        public TrackDelDiaConCatalogo(RegistroHabito registro, String tituloHabito, TipoHabito tipoHabito,
                                      GuiaResumen guia, LocalTime horaDisparo, LocalTime horaLimite,
                                      PuntosEnJuego puntosEnJuego, boolean tieneEvidencia, boolean exigeEvidencia) {
            this(registro, tituloHabito, tipoHabito, guia, horaDisparo, horaLimite, puntosEnJuego, tieneEvidencia,
                    exigeEvidencia, null);
        }
    }

    record GuiaResumen(String mantraTitulo, String mantraIntro, String queHacer, String comoHacerlo) {
    }
}

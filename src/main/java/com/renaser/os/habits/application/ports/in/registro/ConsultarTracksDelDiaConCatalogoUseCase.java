package com.renaser.os.habits.application.ports.in.registro;

import com.renaser.os.habits.domain.model.habito.TipoHabito;
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
     * {@code horaDisparo}/{@code horaLimite}: horario RESUELTO (preferencia del participante
     * si existe, si no el del catalogo vigente para {@code diaPrograma}/{@code tipoDia} del
     * registro) — {@code null} si el habito no tiene horario configurado en ninguno de los
     * dos. {@code guia}: la vigente para {@code diaPrograma}, o {@code null} si no hay
     * ninguna todavia.
     *
     * <p>NO trae {@code claveSistema}, a proposito: el movil ya la recibe por
     * {@code MiHabitoResponse.systemKey} de {@code GET /api/v1/habits} y une catalogo y track
     * por {@code habitoId}. Repetirla aca seria un segundo lugar por donde el mismo dato puede
     * quedar desincronizado.
     */
    record TrackDelDiaConCatalogo(RegistroHabito registro, String tituloHabito, TipoHabito tipoHabito,
                                   GuiaResumen guia, LocalTime horaDisparo, LocalTime horaLimite) {
    }

    record GuiaResumen(String mantraTitulo, String mantraIntro, String queHacer, String comoHacerlo) {
    }
}

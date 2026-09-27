package com.renaser.os.rocks.application.ports.out.participante;

import com.renaser.os.rocks.domain.model.rocasemanal.SemanaPrograma;
import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

/**
 * Copia PROPIA de `rocks` del patrón documentado en
 * `docs/MODULO_PHASECONTRACTS.md` §2: `users.api.UserSummary` filtra
 * `UserRole`/`UserStatus`, tipos internos de `users` fuera de su
 * `@NamedInterface("api")`. En vez de importar eso, `rocks` lee
 * `participantes_programa`+`usuarios` con su propia query nativa y su propio
 * enum local `RolParticipante`.
 *
 * <p>Más rica que la de `phasecontracts` (que solo necesitaba día de programa
 * + rol + suspendido): las ventanas de planificación (§ver
 * `VentanaPlanificacionSemanal`/`VentanaPlanificacionDiaria`) necesitan la
 * ZONA HORARIA real del participante, y el cálculo de número de semana
 * necesita `fechaInicio` — ambas columnas ya existen en
 * `participantes_programa` (`timezone`, `fecha_inicio`).
 *
 * <p><b>Nota 2026-09-27 (D-203):</b> el javadoc de arriba describe la versión original. Hoy el
 * adaptador no manda SQL propio: delega en {@code users.api.ParticipacionProgramaFinder} (D-41) y,
 * para quien llegó al día 90, en {@code users.api.ProgramasActivadosFinder}. Y no viaja la
 * {@code fecha_inicio} cruda sino el Día 1 elegido: sin activar, aquella es provisional (D-201).
 */
public interface ConsultarProgresoParticipanteRocksPort {

    Optional<ProgresoParticipanteRocks> deParticipante(UserId participanteId);


    /**
     * `participantes_programa.programa_activado_en IS NOT NULL` — el reloj de los 90 dias
     * arrancó. NO significa "esta inscrito": un TRAINEE recien aprobado tiene fila y este campo
     * en `null` hasta que completa primer login + Ficha + Terminos.
     *
     * <p>Existe para E-169: el staff que activa su seguimiento personal
     * (`POST /api/v1/mentor/activate-tracking`) queda con este campo puesto, y es lo unico que
     * distingue a un mentor que SI cursa el programa de uno que no.
     *
     * @param diaPrograma            el día de HOY, ya derivado con {@code dias_ajuste_programa} por
     *                               {@code users.api}, acotado a 0..90
     * @param diaUnoElegido          el Día 1 que la persona eligió ({@code users.api.ParticipacionPrograma
     *                               .diaUnoElegido()}), o {@code null} si todavía no activó su programa: antes
     *                               de la activación {@code fecha_inicio} es una fecha PROVISIONAL del alta y
     *                               no es un Día 1 (D-201, E-336)
     * @param ultimaFechaDelPrograma la fecha del día 90 con el ajuste, tal como la calcula {@code users}
     *                               ({@code ProgramasActivadosFinder}); {@code null} cuando no se pidió,
     *                               porque con el día sin acotar no hace falta (ver
     *                               {@link #primerDiaEfectivo})
     */
    record ProgresoParticipanteRocks(int diaPrograma, LocalDate diaUnoElegido, ZoneId zona, RolParticipante rol,
                                      boolean suspendido, boolean programaActivado, LocalDate ultimaFechaDelPrograma) {

        /** Sin la fecha del día 90: la usan los dobles de prueba y todo participante con el día sin acotar. */
        public ProgresoParticipanteRocks(int diaPrograma, LocalDate diaUnoElegido, ZoneId zona, RolParticipante rol,
                                         boolean suspendido, boolean programaActivado) {
            this(diaPrograma, diaUnoElegido, zona, rol, suspendido, programaActivado, null);
        }

        /**
         * Las semanas de programa de esta persona (D-203): lunes a domingo desde la semana de su primer
         * día efectivo. {@code hoy} es la fecha en SU zona, no la del servidor. Vacío mientras no eligió su
         * Día 1: todavía no hay fechas del programa, y quien lee decide qué hacer sin ellas (la semana es la
         * 1, sin rango).
         */
        public Optional<SemanaPrograma> semanas(LocalDate hoy) {
            return primerDiaEfectivo(hoy).map(SemanaPrograma::desde);
        }

        /**
         * El día 1 del programa con el ajuste de día ya aplicado: {@code fecha_inicio + dias_ajuste}.
         * {@code rocks} no ve el ajuste; lo reconstruye con lo que da {@code users.api}.
         *
         * <ol>
         *   <li><b>Sin Día 1 elegido</b>: vacío. La fecha provisional del alta no se usa de ancla (D-201):
         *       daría una semana 1 con fechas que no existen.</li>
         *   <li><b>El reloj no arrancó</b> (el Día 1 todavía no llegó): el Día 1. No puede haber ajuste
         *       antes del día 1 (D-195) y el día que viene es el guardado, que no sirve de ancla.</li>
         *   <li><b>Día 90 o después</b>: la fecha exacta del día 90 menos 89, si el adaptador la trajo.
         *       Desde el 90 el día llega acotado y la cuenta de abajo daría un ancla que se corre un día
         *       por cada día que pasa (el límite conocido de D-192 para los graduados, E-339).</li>
         *   <li><b>En curso</b>: {@code hoy − (día − 1)}, exacto mientras el día no esté acotado.</li>
         * </ol>
         */
        public Optional<LocalDate> primerDiaEfectivo(LocalDate hoy) {
            if (diaUnoElegido == null) {
                return Optional.empty();
            }
            if (hoy.isBefore(diaUnoElegido)) {
                return Optional.of(diaUnoElegido);
            }
            if (ultimaFechaDelPrograma != null) {
                return Optional.of(ultimaFechaDelPrograma.minusDays(SemanaPrograma.ULTIMO_DIA - 1L));
            }
            return Optional.of(hoy.minusDays(diaPrograma - 1L));
        }
    }

    /** Espejo LOCAL (a este modulo) del enum Postgres `rol_usuario`. */
    enum RolParticipante {
        ALCHEMIST,
        ADMIN,
        MENTOR_LEAD,
        MENTOR,
        TRAINEE
    }
}

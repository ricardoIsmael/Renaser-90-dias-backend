package com.renaser.os.users.api;

import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Programas de 90 días ya activados, con las fechas locales en que corren. Lo necesita el
 * semáforo del aprendiz ({@code points}, D-168) para saber qué días medir de cada persona sin leer
 * {@code participantes_programa} de frente (regla 01, D-41).
 *
 * <p><b>Por qué una interfaz aparte y no un método más en {@link ParticipacionProgramaFinder}:</b>
 * aquella tiene implementaciones de prueba en cinco módulos; sumarle un método las rompería todas
 * por una pregunta que ninguna de ellas hace.
 *
 * <p>Las fechas salen del agregado ({@code ParticipacionPrograma}), que es el único que sabe
 * cómo el ajuste de día del administrador corre el calendario. Nadie las recalcula afuera: la
 * columna generada que duplicaba esta cuenta se desincronizó en cuanto apareció el ajuste (V22).
 */
public interface ProgramasActivadosFinder {

    /**
     * Página de programas activados, ordenada por participante. Incluye cualquier rol y
     * cualquier estado de cuenta: filtrar suspendidos o roles es decisión de quien consume.
     *
     * @param offset múltiplo de {@code limite} (se avanza página a página)
     */
    List<ProgramaActivado> pagina(int offset, int limite);

    /** Vacío si la persona no existe o todavía no activó su programa. */
    Optional<ProgramaActivado> de(UserId participanteId);

    /**
     * Los programas activados de varias personas en UNA consulta (la tabla de un grupo, el resumen
     * por grupos). Sin clave = esa persona no existe o no activó su programa.
     */
    Map<UserId, ProgramaActivado> deVarios(Collection<UserId> participantes);

    /**
     * @param zona         zona horaria del participante: sus días son los de esta zona
     * @param primeraFecha primera fecha local con día de programa ≥ 1
     * @param ultimaFecha  fecha local del día 90 (la graduación esperada menos un día)
     */
    record ProgramaActivado(UserId participanteId, ZoneId zona, LocalDate primeraFecha, LocalDate ultimaFecha) {
    }
}

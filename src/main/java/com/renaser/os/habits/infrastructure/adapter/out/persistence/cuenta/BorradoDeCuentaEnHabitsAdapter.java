package com.renaser.os.habits.infrastructure.adapter.out.persistence.cuenta;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.BorradoDeDatosDeCuenta;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lo que {@code habits} borra de una persona cuando su cuenta se borra para siempre (D-243): su track de
 * hábitos (registros, sesiones del Santuario, rachas sin celular y sus revisiones semanales), su bitácora
 * nocturna, el Espíritu, el Radar, todo lo de sus horarios (desbloqueos, días, horarios por fecha, cambios
 * pendientes, preferencias, historial, renombres) y sus hábitos PERSONALES con sus horarios, guías y
 * adjuntos. Los hábitos del sistema son catálogo y no se tocan.
 *
 * <p>{@code revisiones_semanales_sin_celular} (V1) no tenía dueño en Java; es del Día sin celular, que es
 * de este módulo.
 *
 * <p><b>Archivos.</b> Los audios de su bitácora, las evidencias de salida del Santuario y los adjuntos de
 * las guías de sus hábitos personales.
 *
 * <p><b>Que otro participante nombre la misma clave en SU bitácora o SU sesión NO la retiene</b>, a
 * propósito y como hacía {@code users} hasta D-243: esas dos columnas las llena el cliente sin validación
 * ({@code BitacoraNocturnaService.escribir}, {@code SantuarioService.romper}), y honrarlas dejaría que
 * cualquiera impida que se borre el archivo de otra persona escribiendo una cadena en su propio diario. El
 * reverso —que la persona haya escrito en su diario la clave de un tercero— no es asunto de este módulo:
 * {@code users} solo borra del bucket las claves cuya forma es de la cuenta ({@code ClavesDeCuenta}).
 */
@Component
@Order(140)
class BorradoDeCuentaEnHabitsAdapter implements BorradoDeDatosDeCuenta {

    private static final String SUS_REGISTROS =
            "(SELECT id FROM renaser.registros_habito WHERE participante_id = :id)";
    private static final String SUS_HABITOS_PERSONALES =
            "(SELECT id FROM renaser.habitos WHERE participante_id = :id)";
    private static final String GUIAS_DE_SUS_HABITOS =
            "(SELECT id FROM renaser.guias_habito WHERE habito_id IN " + SUS_HABITOS_PERSONALES + ")";

    private static final String SQL_ARCHIVOS = """
            SELECT audio_ruta FROM renaser.entradas_diario WHERE participante_id = :id AND audio_ruta IS NOT NULL
            UNION
            SELECT evidencia_salida_ruta FROM renaser.sesiones_bloqueo
             WHERE registro_habito_id IN %s AND evidencia_salida_ruta IS NOT NULL
            UNION
            SELECT ruta_storage FROM renaser.adjuntos_guia WHERE guia_id IN %s AND ruta_storage IS NOT NULL
            """.formatted(SUS_REGISTROS, GUIAS_DE_SUS_HABITOS);

    /** Solo el catálogo de guías retiene: ver el javadoc de la clase sobre la bitácora y el Santuario. */
    private static final String SQL_EN_USO = """
            SELECT ruta_storage FROM renaser.adjuntos_guia
             WHERE ruta_storage IN (:claves) AND guia_id NOT IN %s
            """.formatted(GUIAS_DE_SUS_HABITOS);

    /** Hijos antes que padres: lo que cuelga de sus registros y de sus hábitos personales, primero. */
    private static final List<String> BORRADOS = List.of(
            "DELETE FROM renaser.sesiones_bloqueo WHERE registro_habito_id IN " + SUS_REGISTROS,
            "DELETE FROM renaser.rachas_sin_celular WHERE participante_id = :id OR registro_habito_id IN "
                    + SUS_REGISTROS,
            "DELETE FROM renaser.revisiones_semanales_sin_celular WHERE participante_id = :id",
            "DELETE FROM renaser.registros_habito WHERE participante_id = :id",
            "DELETE FROM renaser.entradas_diario WHERE participante_id = :id",
            "DELETE FROM renaser.registros_espiritu WHERE participante_id = :id",
            "DELETE FROM renaser.registros_radar WHERE participante_id = :id",
            "DELETE FROM renaser.desbloqueos_habito WHERE participante_id = :id",
            "DELETE FROM renaser.dias_semanales_habito WHERE participante_id = :id",
            "DELETE FROM renaser.horario_semanal_habito WHERE participante_id = :id",
            "DELETE FROM renaser.horarios_habito_por_fecha WHERE participante_id = :id",
            "DELETE FROM renaser.cambios_horario_pendientes WHERE participante_id = :id",
            "DELETE FROM renaser.preferencias_horario WHERE participante_id = :id",
            "DELETE FROM renaser.historial_cambios_horario WHERE participante_id = :id",
            "DELETE FROM renaser.renombres_habito WHERE participante_id = :id",
            "DELETE FROM renaser.adjuntos_guia WHERE guia_id IN " + GUIAS_DE_SUS_HABITOS,
            "DELETE FROM renaser.guias_habito WHERE habito_id IN " + SUS_HABITOS_PERSONALES,
            "DELETE FROM renaser.horarios_habito WHERE habito_id IN " + SUS_HABITOS_PERSONALES,
            "DELETE FROM renaser.habitos WHERE participante_id = :id");

    private final NamedParameterJdbcTemplate jdbc;

    BorradoDeCuentaEnHabitsAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Set<String> archivosDe(UserId cuenta) {
        return new HashSet<>(jdbc.queryForList(SQL_ARCHIVOS, Map.of("id", cuenta.value()), String.class));
    }

    @Override
    public Set<String> archivosEnUsoTrasBorrar(UserId cuenta, Set<String> claves) {
        if (claves == null || claves.isEmpty()) {
            return Set.of();
        }
        var parametros = new MapSqlParameterSource("id", cuenta.value()).addValue("claves", claves);
        return new HashSet<>(jdbc.queryForList(SQL_EN_USO, parametros, String.class));
    }

    @Override
    public void borrarDatosDe(UserId cuenta) {
        BORRADOS.forEach(sql -> jdbc.update(sql, Map.of("id", cuenta.value())));
    }
}

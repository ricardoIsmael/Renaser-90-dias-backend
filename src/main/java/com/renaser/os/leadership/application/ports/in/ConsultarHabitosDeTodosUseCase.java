package com.renaser.os.leadership.application.ports.in;

import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * «Hábitos de todos» (spec 029): el líder de mentores ve, hábito por hábito, quién cumplió y quién no entre todos los alumnos de SUS formaciones, un día a la
 * vez. Solo lectura. <b>No registra consulta</b> (FR-015): abrir una lista no es abrir la ficha de nadie; la ficha y la evidencia, que se abren desde aquí,
 * dejan la suya como siempre. Administración y Alquimista pueden usar la misma lectura (ven las tres formaciones).
 */
public interface ConsultarHabitosDeTodosUseCase {

    /** El valor de {@code grupo} que pide a los alumnos sin ningún grupo. */
    String SIN_GRUPO = "none";

    HabitosDeTodos resumen(ConsultaHabitosDeTodos consulta);

    PersonasDeUnHabito personas(ConsultaPersonasDeUnHabito consulta);

    /**
     * @param fecha     null = hoy; hasta 7 días atrás
     * @param grupo     null = todos; {@link #SIN_GRUPO}; o el id de un grupo
     * @param formacion null = todas las del alcance de quien pide
     * @param categoria la clave de una categoría (la dimensión); null = todas
     */
    record ConsultaHabitosDeTodos(UserId actorId, LocalDate fecha, String grupo, Formacion formacion, String categoria) {
        public ConsultaHabitosDeTodos {
            Objects.requireNonNull(actorId, "actorId es obligatorio");
        }
    }

    record ConsultaPersonasDeUnHabito(UserId actorId, UUID habitoId, LocalDate fecha, String grupo, Formacion formacion,
                                      String busqueda, int pagina, int tamano) {
        public ConsultaPersonasDeUnHabito {
            Objects.requireNonNull(actorId, "actorId es obligatorio");
            Objects.requireNonNull(habitoId, "habitoId es obligatorio");
        }
    }

    /** @param alumnos todos los que se miran con esos filtros; {@code conHabitos} los que ese día tienen algún hábito exigible */
    record HabitosDeTodos(LocalDate fecha, boolean esHoy, LocalDate masAntigua, LocalDate masReciente, Totales totales,
                          List<HabitoDelDia> habitos) {
    }

    record Totales(int alumnos, int conHabitos, int esperados, int cumplieron, int pendientes, int sinCumplir) {
    }

    record HabitoDelDia(UUID habitoId, String titulo, String categoriaClave, String categoriaCodigo, String formacion, int esperados,
                        int cumplieron, int pendientes, int sinCumplir) {
    }

    record PersonasDeUnHabito(LocalDate fecha, boolean esHoy, HabitoDelDia habito, List<FilaDePersona> personas, int pagina,
                              int tamano, long total) {
    }

    /**
     * @param estado         CUMPLIDO, PENDIENTE, SIN_CUMPLIR o SIN_DATOS
     * @param motivo         solo en SIN_DATOS: NO_EXIGIBLE, NO_LE_TOCABA o ANTES_DEL_DIA_1; nunca un incumplimiento
     * @param horaPlanificada la que le toca a ESA persona ese día (su preferencia sobre el catálogo); null si no tiene hora fija
     * @param cumplioA       la hora local en que cumplió (HH:mm, en la zona de la persona); null si no cumplió
     * @param evidenciaId    la evidencia entregada (para abrir la hoja de siempre); null si no hay
     */
    record FilaDePersona(UUID personaId, String nombre, String foto, UUID grupoId, String grupo, String estado, String motivo,
                         LocalTime horaPlanificada, String cumplioA, UUID evidenciaId) {
    }
}

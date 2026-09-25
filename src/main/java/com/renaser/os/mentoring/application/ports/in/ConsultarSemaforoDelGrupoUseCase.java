package com.renaser.os.mentoring.application.ports.in;

import com.renaser.os.mentoring.domain.model.semaforo.ConteoPorColor;
import com.renaser.os.mentoring.domain.model.semaforo.MedicionDelAprendiz;
import com.renaser.os.mentoring.domain.model.semaforo.PeriodoDelSemaforo;
import com.renaser.os.points.api.SemanaDelSemaforo;
import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * La tabla del semáforo de un grupo, vista por quien lo acompaña VIGENTEMENTE (D-168,
 * docs/arquitectura/SEMAFORO_DEL_APRENDIZ.md §4.3). Un exmentor con el token todavía válido no la
 * ve. La misma tabla, para administración, entra por {@link ConsultarSemaforoDelGrupoAdministrativoUseCase}.
 */
public interface ConsultarSemaforoDelGrupoUseCase {

    TablaDelSemaforo tablaDe(ConsultaTablaDelGrupo consulta);

    /**
     * @param semanaHasta null = la ventana vigente (los últimos 7 días cerrados de cada aprendiz); si
     *                    viene, la semana sábado→viernes que termina ese día, que tiene que ser viernes
     */
    record ConsultaTablaDelGrupo(UserId actorId, UUID grupoId, LocalDate semanaHasta) {

        public ConsultaTablaDelGrupo {
            Objects.requireNonNull(actorId, "actorId es obligatorio");
            Objects.requireNonNull(grupoId, "grupoId es obligatorio");
            if (semanaHasta != null) {
                SemanaDelSemaforo.exigirCierreValido(semanaHasta);
            }
        }
    }

    /**
     * @param resumen    cuántos de {@code aprendices} hay en cada color
     * @param aprendices rojo, amarillo, sin datos y verde; dentro de cada color, por nombre. Solo
     *                   aprendices vigentes del grupo, nunca su mentor aunque además curse.
     */
    record TablaDelSemaforo(UUID grupoId, String grupoNombre, PeriodoDelSemaforo periodo, ConteoPorColor resumen,
                            List<FilaDelSemaforo> aprendices) {
    }

    /**
     * @param nombre    null si {@code users} no lo conoce; va al final de su color
     * @param medicion  {@link MedicionDelAprendiz#SIN_MEDICION} si el semáforo no lo mide
     */
    record FilaDelSemaforo(UUID aprendizId, String nombre, String avatarUrl, MedicionDelAprendiz medicion) {
    }
}

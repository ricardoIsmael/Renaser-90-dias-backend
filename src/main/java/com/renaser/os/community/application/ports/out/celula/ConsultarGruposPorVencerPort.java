package com.renaser.os.community.application.ports.out.celula;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Los grupos cuyo periodo termina dentro de la ventana de aviso.
 *
 * <p>Proyecta columnas y no el agregado {@code Celula}: la pregunta necesita el nombre y la fecha
 * de cierre, nada mas. Cargar la celula entera —con su mentor, su cohorte y su cupo— para
 * componer un texto de aviso seria traer bytes para tirarlos, y este barrido recorre todo el
 * padron.
 */
public interface ConsultarGruposPorVencerPort {

    /**
     * Grupos con {@code periodo_fin} entre {@code hoy} y {@code hoy + dias}, ambos inclusive.
     *
     * <p>El filtro por fecha va en la CONSULTA. Traer todas las celulas y descartarlas en Java
     * funcionaria hoy con cinco grupos y dejaria de funcionar sin avisar el dia que sean mil.
     *
     * <p>Las celulas SIN periodo quedan fuera: no vencen, asi que no hay nada de que avisar.
     */
    List<GrupoQueVence> conCierreEntre(LocalDate desde, LocalDate hasta);

    /**
     * Las DOS fechas, no solo el cierre. Quien llama tiene que reconstruir el
     * {@code PeriodoGrupo} entero para preguntarle a las reglas, y un periodo armado con
     * {@code inicio == fin} responde que el grupo es FUTURO mientras siga vivo -- con lo que no se
     * avisaria nunca. Devolver media fecha invita justo a ese error.
     */
    record GrupoQueVence(UUID celulaId, String nombre, LocalDate inicioDelPeriodo, LocalDate finDelPeriodo) {
    }
}

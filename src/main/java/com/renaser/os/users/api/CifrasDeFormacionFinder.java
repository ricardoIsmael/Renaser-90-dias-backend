package com.renaser.os.users.api;

import com.renaser.os.shared.domain.Formacion;

/**
 * Las cifras de personas de cada formación para el centro «Formaciones» de Administración (spec 022, FR-025, FR-026). Cada cifra se cuenta con el
 * mismo criterio que la lista a la que lleva ({@code GET /admin/trainees?formation=…}): cuentas reales (sin las de prueba de «ver como»), de cualquier
 * estado, para que la cifra y la lista coincidan siempre.
 */
public interface CifrasDeFormacionFinder {

    CifrasDePersonas de(Formacion formacion);

    /**
     * @param aprendices           todos los aprendices de la formación = {@code porEmpezar + enCurso + terminaron}
     * @param porEmpezar           todavía no eligieron su Día 1
     * @param enCurso              ya empezaron y no terminaron
     * @param terminaron           el programa llegó a su último día
     * @param aprendicesSinGrupo   aprendices de la formación que no están en ningún grupo (la cola «a quién hay que ubicar»)
     * @param personal             integrantes del personal que la tienen asignada
     */
    record CifrasDePersonas(int aprendices, int porEmpezar, int enCurso, int terminaron, int aprendicesSinGrupo, int personal) {
    }

    /** Integrantes del personal activo sin ninguna formación asignada: no ven nada hasta que Administración les asigne una. */
    int personalSinFormacion();
}

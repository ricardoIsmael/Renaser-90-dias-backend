package com.renaser.os.leadership.application.ports.in.formacion;

import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;

import java.util.List;

/**
 * El centro «Formaciones» de Administración (spec 022, FR-025, FR-026): las tres formaciones con su información principal. Solo Administración y
 * Alquimista. Cada cifra se cuenta con el mismo criterio que la lista a la que lleva, ya filtrada por la formación.
 */
public interface ConsultarCentroDeFormacionesUseCase {

    /** Las tres, en su orden, con sus cifras clave. */
    List<TarjetaDeFormacion> tarjetas(UserId actor);

    /** El detalle de una: resumen, personas, grupos y lo que hay por atender. */
    DetalleDeFormacion detalle(UserId actor, Formacion formacion);

    record Resumen(Formacion formacion, String nombre, String descripcion, boolean activa, List<Integer> duracionesDias,
                   List<String> etiquetasDeDuracion, int version) {
    }

    record Personas(int aprendices, int porEmpezar, int enCurso, int terminaron, int personal) {
    }

    record Grupos(int total, int conMentor, int sinMentor, int recepcion) {
    }

    record PorAtender(int gruposSinMentor, int aprendicesSinGrupo, int personalSinFormacion) {

        public int total() {
            return gruposSinMentor + aprendicesSinGrupo + personalSinFormacion;
        }
    }

    record TarjetaDeFormacion(Resumen resumen, int aprendicesActivos, int personal, int grupos, int porAtender) {
    }

    record DetalleDeFormacion(Resumen resumen, Personas personas, Grupos grupos, PorAtender porAtender) {
    }
}

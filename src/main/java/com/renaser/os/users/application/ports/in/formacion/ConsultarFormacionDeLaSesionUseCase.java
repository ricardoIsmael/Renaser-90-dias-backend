package com.renaser.os.users.application.ports.in.formacion;

import com.renaser.os.shared.domain.UserId;

import java.util.List;

/**
 * La formación de la persona que inició sesión (spec 022, FR-012): a cuál pertenece, entre cuáles puede elegir y cómo se parte su programa en
 * fases. La app la lee en {@code /auth/me}, en el login y en {@code GET /me/formacion}.
 */
public interface ConsultarFormacionDeLaSesionUseCase {

    FormacionDeLaSesion consultar(UserId persona);

    record OpcionDeFormacion(String codigo, String nombre) {
    }

    /** Una fase del programa de ESTA persona, estirada a su duración (Q2-A). */
    record FaseDelPrograma(int numero, int primerDia, int ultimoDia) {
    }

    /**
     * @param activa        la formación en la que trabaja ahora; {@code null} para Administración, Alquimista y Soporte TI (ven todas) y para el
     *                      personal sin ninguna asignada
     * @param disponibles   entre las que puede elegir (el personal con varias), o las tres si las ve todas
     * @param puedeElegir   solo el personal con más de una formación
     * @param veTodas       Administración, Alquimista y Soporte TI
     * @param sinFormacion  personal al que Administración todavía no le asignó ninguna
     * @param delPrograma   el código de la formación de SU programa propio (el de su participación), o {@code null} si no lo tiene
     * @param duracionDias  la duración de su programa (90 si no lo tiene)
     * @param fases         las cuatro fases según {@code duracionDias}
     */
    record FormacionDeLaSesion(OpcionDeFormacion activa, List<OpcionDeFormacion> disponibles, boolean puedeElegir, boolean veTodas,
                               boolean sinFormacion, String delPrograma, int duracionDias, List<FaseDelPrograma> fases) {
    }
}

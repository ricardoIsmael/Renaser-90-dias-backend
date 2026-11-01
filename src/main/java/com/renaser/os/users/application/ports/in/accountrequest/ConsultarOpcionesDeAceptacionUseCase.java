package com.renaser.os.users.application.ports.in.accountrequest;

import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.FormacionConfigurada;

import java.util.List;

/**
 * Lo que la hoja «Aceptar» ofrece (spec 025): las formaciones que reciben personas nuevas con sus duraciones, y si «Con diagnóstico» se puede
 * ofrecer. Lo decide el servidor: la app no recalcula nada. Solo quien decide solicitudes.
 */
public interface ConsultarOpcionesDeAceptacionUseCase {

    OpcionesDeAceptacion consultar(UserId actor);

    /**
     * @param formaciones           solo las activas, en su orden
     * @param diagnosticoDisponible hay un diagnóstico publicado que asignar (spec 026); sin la ficha con diagnóstico instalada es {@code false}
     * @param sinTerminos           las formaciones (de las activas) que todavía no tienen términos y condiciones publicados: no reciben personas nuevas (spec 027)
     */
    record OpcionesDeAceptacion(List<FormacionConfigurada> formaciones, boolean diagnosticoDisponible, List<Formacion> sinTerminos) {
    }
}

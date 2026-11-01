package com.renaser.os.users.infrastructure.adapter.in.rest.accountrequest;

import com.renaser.os.users.api.FormacionConfigurada;
import com.renaser.os.users.application.ports.in.accountrequest.ConsultarOpcionesDeAceptacionUseCase.OpcionesDeAceptacion;

import java.util.List;

/**
 * Lo que ofrece la hoja «Aceptar» (spec 025): las formaciones que reciben personas nuevas, con sus duraciones y cómo se dicen, y si «Con diagnóstico» se
 * puede ofrecer. Los nombres del servidor mandan: la app no los escribe a mano.
 */
public record OpcionesDeAceptacionResponse(List<Formation> formations, boolean diagnosisAvailable) {

    /** @param durationsDays las opciones en días; {@code durationLabels}, cómo se dicen («3 meses», «1 año», «90 días») */
    /** @param termsPublished spec 027: {@code false} = la formación todavía no publicó sus términos y no recibe personas nuevas (la hoja la muestra apagada) */
    public record Formation(String code, String name, String description, List<Integer> durationsDays, List<String> durationLabels,
                            int defaultDurationDays, boolean termsPublished) {

        static Formation from(FormacionConfigurada formacion, boolean terminosPublicados) {
            return new Formation(formacion.formacion().codigo(), formacion.nombre(), formacion.descripcion(), formacion.duracionesDias(),
                    formacion.etiquetasDeDuracion(), formacion.duracionPorDefecto(), terminosPublicados);
        }
    }

    public static OpcionesDeAceptacionResponse from(OpcionesDeAceptacion opciones) {
        return new OpcionesDeAceptacionResponse(opciones.formaciones().stream()
                .map(f -> Formation.from(f, !opciones.sinTerminos().contains(f.formacion()))).toList(), opciones.diagnosticoDisponible());
    }
}

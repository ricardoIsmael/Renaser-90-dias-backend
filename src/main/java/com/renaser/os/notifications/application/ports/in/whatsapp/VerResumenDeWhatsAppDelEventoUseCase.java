package com.renaser.os.notifications.application.ports.in.whatsapp;

import com.renaser.os.shared.domain.UserId;

import java.util.UUID;

/**
 * El resumen de conteos de los WhatsApp de un evento para Administracion y Alquimista (spec 041, FR-029): cuantos salieron, llegaron, se leyeron, fallaron y
 * no tenian numero. Sin nombres ni textos.
 */
public interface VerResumenDeWhatsAppDelEventoUseCase {

    /**
     * @throws com.renaser.os.shared.domain.NotAuthorizedException si quien pregunta no es Administracion ni Alquimista (mentor, guia, lider y aprendiz)
     */
    ResumenDeWhatsApp resumen(UserId actor, UUID eventoId);

    /**
     * @param enviados    los que salieron hacia WhatsApp (aceptados, enviados, entregados y leidos)
     * @param entregados  los que llegaron al telefono (entregados y leidos)
     * @param leidos      los que se leyeron
     * @param fallaron    los que el proveedor no pudo entregar
     * @param sinNumero   las personas a quienes les tocaba y no tienen un WhatsApp en la ficha
     * @param noSeSupo    los que se mandaron y no se supo como terminaron (un corte)
     * @param omitidos    los que no se mandaron por otro motivo (llegaron tarde, el evento ya empezo, tope mensual)
     */
    record ResumenDeWhatsApp(int enviados, int entregados, int leidos, int fallaron, int sinNumero, int noSeSupo, int omitidos) {
    }
}

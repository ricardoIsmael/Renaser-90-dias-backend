package com.renaser.os.notifications.infrastructure.adapter.in.rest.whatsapp;

import com.renaser.os.notifications.application.ports.in.whatsapp.VerResumenDeWhatsAppDelEventoUseCase;
import com.renaser.os.notifications.application.ports.in.whatsapp.VerResumenDeWhatsAppDelEventoUseCase.ResumenDeWhatsApp;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.Funcionalidad;
import com.renaser.os.shared.web.security.RequiresPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * El resumen de los WhatsApp de un evento (spec 041, FR-029): {@code GET /api/v1/calendar/events/{id}/whatsapp}. Vive en {@code notifications}, dueño de la tabla,
 * bajo el mismo prefijo de los eventos. Solo conteos. Con {@code eventos.whatsapp} apagada responde 404 {@code FUNCIONALIDAD_NO_DISPONIBLE} (la funcionalidad se
 * esconde, no avisa mantenimiento).
 */
@RestController
@RequestMapping("/api/v1/calendar/events")
@Funcionalidad("eventos.whatsapp")
class ResumenDeWhatsAppController {

    private final VerResumenDeWhatsAppDelEventoUseCase resumen;

    ResumenDeWhatsAppController(VerResumenDeWhatsAppDelEventoUseCase resumen) {
        this.resumen = resumen;
    }

    @RequiresPermission(value = Permission.MANAGE_CALENDAR, scope = "solo ADMIN/ALCHEMIST (D-186): el servicio rechaza a MENTOR, MENTOR_LEAD, TRAINEE_GUIDE y TRAINEE")
    @GetMapping("/{id}/whatsapp")
    ResumenWire resumen(@ActorAutenticado UserId actor, @PathVariable UUID id) {
        return ResumenWire.de(resumen.resumen(actor, id));
    }

    /** Lo que sale por el cable: solo conteos. */
    record ResumenWire(int enviados, int entregados, int leidos, int fallaron, int sinNumero, int noSeSupo, int omitidos) {
        static ResumenWire de(ResumenDeWhatsApp r) {
            return new ResumenWire(r.enviados(), r.entregados(), r.leidos(), r.fallaron(), r.sinNumero(), r.noSeSupo(), r.omitidos());
        }
    }
}

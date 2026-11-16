package com.renaser.os.notifications.infrastructure.adapter.in.rest.whatsapp;

import com.renaser.os.notifications.application.ports.in.whatsapp.VerAvisoDeSaldoDeWhatsAppUseCase;
import com.renaser.os.notifications.domain.model.whatsapp.AvisoDeSaldoDeWhatsApp;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.RequiresPermission;
import com.renaser.os.shared.web.security.SinInterruptor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * El estado de los envíos por WhatsApp para el panel de Administración (spec 054): {@code GET /api/v1/admin/whatsapp/estado}. Solo lectura, solo ADMIN y
 * ALCHEMIST, sin números ni nombres.
 */
@RestController
@RequestMapping("/api/v1/admin/whatsapp")
@SinInterruptor(motivo = "Es un aviso de Administración sobre la cuenta de YCloud: si se apagan los recordatorios por WhatsApp, el aviso de saldo no debe esconderse con ellos")
class EstadoDeWhatsAppController {

    private final VerAvisoDeSaldoDeWhatsAppUseCase aviso;

    EstadoDeWhatsAppController(VerAvisoDeSaldoDeWhatsAppUseCase aviso) {
        this.aviso = aviso;
    }

    @RequiresPermission(value = Permission.MANAGE_CALENDAR, scope = "solo ADMIN/ALCHEMIST (spec 054): el servicio rechaza a MENTOR, MENTOR_LEAD, TRAINEE_GUIDE y TRAINEE")
    @GetMapping("/estado")
    EstadoWire estado(@ActorAutenticado UserId actor) {
        return EstadoWire.de(aviso.ver(actor));
    }

    /** Lo que sale por el cable: si avisar, cuántos fallaron por saldo y desde cuándo (ISO-8601 en UTC). */
    record EstadoWire(boolean sinSaldo, int fallaron, Instant desde) {
        static EstadoWire de(AvisoDeSaldoDeWhatsApp a) {
            return new EstadoWire(a.avisar(), a.fallaron(), a.desde());
        }
    }
}

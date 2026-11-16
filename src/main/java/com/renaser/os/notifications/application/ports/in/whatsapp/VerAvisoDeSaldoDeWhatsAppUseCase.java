package com.renaser.os.notifications.application.ports.in.whatsapp;

import com.renaser.os.notifications.domain.model.whatsapp.AvisoDeSaldoDeWhatsApp;
import com.renaser.os.shared.domain.UserId;

/** El aviso de «WhatsApp sin saldo» del panel de Administración (spec 054). */
public interface VerAvisoDeSaldoDeWhatsAppUseCase {

    /**
     * @throws com.renaser.os.shared.domain.NotAuthorizedException si quien pregunta no es Administración ni Alquimista, o su cuenta no está activa
     */
    AvisoDeSaldoDeWhatsApp ver(UserId actor);
}

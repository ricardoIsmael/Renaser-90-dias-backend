package com.renaser.os.users.application.ports.out.whatsapp;

import com.renaser.os.shared.domain.UserId;

/** ¿Hay una solicitud de acceso PENDIENTE de otra persona con este WhatsApp? (spec 051: el número es único). */
public interface NumeroEnSolicitudPendientePort {

    /**
     * @param numeroInternacional {@code +51987654321}; se compara sin espacios, paréntesis ni guiones contra {@code solicitudes_cuenta.telefono}
     * @param excepto             la cuenta de la propia solicitud, o {@code null} si todavía no existe
     */
    boolean hayPendienteDeOtra(String numeroInternacional, UserId excepto);
}

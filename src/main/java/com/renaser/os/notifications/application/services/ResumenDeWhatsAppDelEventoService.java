package com.renaser.os.notifications.application.services;

import com.renaser.os.notifications.application.ports.in.whatsapp.VerResumenDeWhatsAppDelEventoUseCase;
import com.renaser.os.notifications.application.ports.out.whatsapp.EntregasDeWhatsAppPort;
import com.renaser.os.notifications.application.ports.out.whatsapp.EntregasDeWhatsAppPort.ConteoDeEntregas;
import com.renaser.os.notifications.domain.model.whatsapp.EstadoDeEntrega;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

/**
 * El resumen de conteos de los WhatsApp de un evento (spec 041, FR-029). Solo Administracion y Alquimista (los que gestionan eventos, D-186); mentor, guia,
 * lider y aprendiz reciben 403. Son conteos: ni nombres, ni textos, ni numeros.
 */
@Service
public class ResumenDeWhatsAppDelEventoService implements VerResumenDeWhatsAppDelEventoUseCase {

    private static final Set<UserRole> QUIEN_LO_VE = Set.of(UserRole.ADMIN, UserRole.ALCHEMIST);

    private final UserSummaryFinder usuarios;
    private final EntregasDeWhatsAppPort entregas;

    ResumenDeWhatsAppDelEventoService(UserSummaryFinder usuarios, EntregasDeWhatsAppPort entregas) {
        this.usuarios = usuarios;
        this.entregas = entregas;
    }

    @Override
    public ResumenDeWhatsApp resumen(UserId actor, UUID eventoId) {
        UserSummary quien = usuarios.findById(actor).orElseThrow(() -> new NoSuchElementException("Usuario no encontrado: " + actor));
        if (!quien.status().allowsAccess() || !QUIEN_LO_VE.contains(quien.role())) {
            throw new NotAuthorizedException("Solo Administración y Alquimista ven el resumen de WhatsApp de un evento");
        }
        ConteoDeEntregas conteo = entregas.conteoDelEvento(eventoId);
        Map<EstadoDeEntrega, Integer> porEstado = conteo.porEstado();
        int leidos = cuantos(porEstado, EstadoDeEntrega.LEIDO);
        int entregados = cuantos(porEstado, EstadoDeEntrega.ENTREGADO) + leidos;
        int enviados = cuantos(porEstado, EstadoDeEntrega.ACEPTADO) + cuantos(porEstado, EstadoDeEntrega.ENVIADO) + entregados;
        int otrosOmitidos = cuantos(porEstado, EstadoDeEntrega.OMITIDO) - conteo.omitidasSinNumero();
        return new ResumenDeWhatsApp(enviados, entregados, leidos, cuantos(porEstado, EstadoDeEntrega.FALLIDO), conteo.personasSinNumero(),
                cuantos(porEstado, EstadoDeEntrega.NO_SE_SUPO), Math.max(otrosOmitidos, 0));
    }

    private static int cuantos(Map<EstadoDeEntrega, Integer> porEstado, EstadoDeEntrega estado) {
        return porEstado.getOrDefault(estado, 0);
    }
}

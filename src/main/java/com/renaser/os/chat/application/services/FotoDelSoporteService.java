package com.renaser.os.chat.application.services;

import com.renaser.os.chat.application.ports.in.conversacion.AutorizarAccesoAConversacionUseCase;
import com.renaser.os.chat.application.ports.in.conversacion.VerFotoDelSoporteUseCase;
import com.renaser.os.chat.application.ports.out.bienvenida.TarjetaConNombrePort;
import com.renaser.os.chat.application.ports.out.bienvenida.TarjetaConNombrePort.TarjetaConNombre;
import com.renaser.os.chat.application.ports.out.conversacion.LoadConversacionPort;
import com.renaser.os.chat.domain.model.conversacion.Conversacion;
import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.chat.domain.model.conversacion.PrimerNombre;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;

/**
 * La foto del chat de soporte: la tarjeta con el primer nombre del aprendiz dueño (D-205).
 *
 * <p><b>El orden importa.</b> Primero que la conversación exista (404), después que quien pide pueda
 * verla (403) y recién entonces si es un soporte (404): así, a quien no participa no se le dice qué
 * tipo de conversación es. Es el mismo orden que el resto del módulo (existe, participa).
 *
 * <p>Sin {@code @Transactional}: dibujar lleva decenas de milisegundos y no debe retener una conexión
 * de la base. Las lecturas van cada una en la transacción de su repositorio.
 */
@Service
public class FotoDelSoporteService implements VerFotoDelSoporteUseCase {

    private final LoadConversacionPort loadConversacionPort;
    private final AutorizarAccesoAConversacionUseCase autorizarAcceso;
    private final UserSummaryFinder userSummaryFinder;
    private final TarjetaConNombrePort tarjetaPort;

    public FotoDelSoporteService(LoadConversacionPort loadConversacionPort,
                                 AutorizarAccesoAConversacionUseCase autorizarAcceso,
                                 UserSummaryFinder userSummaryFinder, TarjetaConNombrePort tarjetaPort) {
        this.loadConversacionPort = loadConversacionPort;
        this.autorizarAcceso = autorizarAcceso;
        this.userSummaryFinder = userSummaryFinder;
        this.tarjetaPort = tarjetaPort;
    }

    @Override
    public FotoDelSoporte foto(UserId actorId, ConversacionId conversacionId) {
        requireActivo(actorId);
        Conversacion conversacion = loadConversacionPort.porId(conversacionId)
                .orElseThrow(() -> new NoSuchElementException("Conversacion no encontrada: " + conversacionId));
        if (!autorizarAcceso.puedeVer(conversacionId, actorId)) {
            throw new NotAuthorizedException("No eres participante de esta conversación");
        }
        UserId aprendiz = conversacion.aprendizDelSoporte().orElseThrow(() -> new NoSuchElementException(
                "Solo el chat de soporte tiene foto propia; los grupos usan la tarjeta sin nombre"));
        TarjetaConNombre tarjeta = tarjetaPort.tarjetaDe(primerNombreDe(aprendiz));
        return new FotoDelSoporte(tarjeta.jpeg(), tarjeta.huella());
    }

    /** Sin cuenta (se dio de baja y el chat quedó), la tarjeta va sin nombre: es la del programa. */
    private String primerNombreDe(UserId aprendiz) {
        return userSummaryFinder.findById(aprendiz).map(u -> PrimerNombre.de(u.fullName())).orElse("");
    }

    private void requireActivo(UserId actorId) {
        boolean activo = userSummaryFinder.findById(actorId).map(u -> u.status() == UserStatus.ACTIVE).orElse(false);
        if (!activo) {
            throw new NotAuthorizedException("La cuenta esta suspendida");
        }
    }
}

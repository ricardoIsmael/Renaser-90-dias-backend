package com.renaser.os.rag.application.services;

import com.renaser.os.rag.application.ports.in.voz.SintetizarVozUseCase;
import com.renaser.os.rag.application.ports.out.ia.SintetizarVozPort;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Voz del orbe. <b>Sin {@code @Transactional}</b> a proposito (regla 01, C-1): la sintesis es una
 * llamada de red al servicio de voz, y no hay nada que escribir en la base.
 *
 * <p>La guarda de cuenta activa es la misma de {@code ConversacionRenasiaService}: el filtro de
 * permisos ya la aplica en HTTP, pero el caso de uso no confia en que lo llame siempre un
 * controller.
 */
@Service
public class SintetizarVozService implements SintetizarVozUseCase {

    private final UserSummaryFinder userSummaryFinder;
    private final SintetizarVozPort sintetizarVozPort;

    public SintetizarVozService(UserSummaryFinder userSummaryFinder, SintetizarVozPort sintetizarVozPort) {
        this.userSummaryFinder = userSummaryFinder;
        this.sintetizarVozPort = sintetizarVozPort;
    }

    @Override
    public Optional<byte[]> sintetizar(UserId actorId, String texto) {
        requireActivo(actorId);
        return sintetizarVozPort.sintetizar(textoValido(texto));
    }

    private static String textoValido(String texto) {
        String recortado = texto == null ? "" : texto.strip();
        if (recortado.isEmpty()) {
            throw new IllegalArgumentException("El texto a leer en voz alta no puede estar vacio");
        }
        if (recortado.length() > LARGO_MAXIMO_TEXTO) {
            throw new IllegalArgumentException(
                    "El texto a leer en voz alta no puede superar " + LARGO_MAXIMO_TEXTO + " caracteres");
        }
        return recortado;
    }

    private void requireActivo(UserId actorId) {
        UserSummary usuario = userSummaryFinder.findById(actorId)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado: " + actorId));
        if (usuario.status() != UserStatus.ACTIVE) {
            throw new NotAuthorizedException("La cuenta esta suspendida");
        }
    }
}

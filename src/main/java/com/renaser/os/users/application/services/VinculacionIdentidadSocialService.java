package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.IdentidadYaVinculadaException;
import com.renaser.os.users.application.ports.in.autenticacion.VincularIdentidadSocialUseCase;
import com.renaser.os.users.application.ports.out.autenticacion.CanjeCodigoCommand;
import com.renaser.os.users.application.ports.out.autenticacion.IdentidadVerificada;
import com.renaser.os.users.application.ports.out.autenticacion.LoadIdentidadExternaPort;
import com.renaser.os.users.application.ports.out.autenticacion.SaveIdentidadExternaPort;
import com.renaser.os.users.application.ports.out.autenticacion.VerificadorIdentidadProveedor;
import com.renaser.os.users.domain.model.identidadexterna.IdentidadExterna;
import com.renaser.os.users.domain.model.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Vinculacion explicita de una identidad social a una cuenta ya autenticada
 * (docs/MODULO_AUTH.md §6.9). Clase propia y no un metodo mas de
 * {@link AutenticacionSocialService}: son dos casos de uso distintos (CLAUDE.MD §5.4.8). Aquel
 * <b>establece</b> identidad para alguien que todavia no la tiene; este <b>agrega una forma de
 * entrar</b> a alguien que ya probo quien es. Comparten el canje del {@code code} y la exigencia
 * de correo verificado — reusados via {@link RegistroVerificadoresIdentidad} y
 * {@link IdentidadVerificada#exigirEmailVerificado}, no copiados.
 */
@Service
public class VinculacionIdentidadSocialService implements VincularIdentidadSocialUseCase {

    private static final Logger log = LoggerFactory.getLogger(VinculacionIdentidadSocialService.class);

    private final RegistroVerificadoresIdentidad verificadores;
    private final LoadIdentidadExternaPort loadIdentidadExternaPort;
    private final SaveIdentidadExternaPort saveIdentidadExternaPort;
    private final RequireActiveUserGuard requireActiveUserGuard;
    private final Clock clock;

    public VinculacionIdentidadSocialService(List<VerificadorIdentidadProveedor> verificadores,
                                              LoadIdentidadExternaPort loadIdentidadExternaPort,
                                              SaveIdentidadExternaPort saveIdentidadExternaPort,
                                              RequireActiveUserGuard requireActiveUserGuard, Clock clock) {
        this.verificadores = new RegistroVerificadoresIdentidad(verificadores);
        this.loadIdentidadExternaPort = loadIdentidadExternaPort;
        this.saveIdentidadExternaPort = saveIdentidadExternaPort;
        this.requireActiveUserGuard = requireActiveUserGuard;
        this.clock = clock;
    }

    /**
     * El orden de los pasos no es casual:
     *
     * <ol>
     *   <li><b>El actor primero.</b> Cargarlo y rechazarlo si esta suspendido antes de tocar al
     *       proveedor evita quemar un {@code code} de OAuth — que es de un solo uso — en una
     *       peticion que igual iba a terminar en 403. Es tambien la capa 3 de la defensa en
     *       profundidad (CLAUDE.MD §5.3.4/D-11): una cuenta suspendida no vincula nada.</li>
     *   <li><b>La identidad del proveedor despues</b>, con la misma exigencia de correo
     *       verificado que el login social.</li>
     *   <li><b>El chequeo de dueño al final</b>, resuelto SIEMPRE por {@code (proveedor, sujeto)}
     *       y nunca por correo (§6.4).</li>
     * </ol>
     */
    @Override
    @Transactional
    public void vincular(VincularIdentidadSocialCommand command) {
        User actor = requireActiveUserGuard.of(command.actorId());
        VerificadorIdentidadProveedor verificador = verificadores.para(command.proveedor());
        IdentidadVerificada identidad = verificador.verificar(
                new CanjeCodigoCommand(command.code(), command.codeVerifier(), command.redirectUri()));
        identidad.exigirEmailVerificado(command.proveedor());

        Optional<IdentidadExterna> vinculoExistente = loadIdentidadExternaPort.porProveedorYSujeto(
                command.proveedor(), identidad.sujeto());
        if (vinculoExistente.isPresent()) {
            rechazarSiEsDeOtroUsuario(vinculoExistente.get(), actor, command);
            return;
        }
        // emailProveedor es informativo (§2.2): sirve para mostrar "vinculada a juan@..." en la
        // pantalla de cuenta. NO se compara con el correo del usuario a proposito — vincular un
        // Google personal a una cuenta con correo de trabajo es legitimo, y quien garantiza que
        // una misma cuenta del proveedor no sirva a dos usuarios es la UNIQUE (proveedor,
        // sujeto_proveedor), no una comparacion de correos.
        saveIdentidadExternaPort.guardar(IdentidadExterna.vincular(command.proveedor(), identidad.sujeto(),
                actor.id(), identidad.email(), clock));
        log.info("Identidad social vinculada: proveedor={} usuarioId={}", command.proveedor(), actor.id());
    }

    /**
     * Idempotencia de un lado, frontera de seguridad del otro, y la diferencia entre las dos es
     * una sola comparacion: si el vinculo ya es de este mismo actor, volver a tocar el boton no
     * es un error (no se reescribe nada, se conserva la {@code vinculadaEn} original); si es de
     * otro, es el vector de apropiacion inverso al de §6.4 y se corta con 409.
     */
    private void rechazarSiEsDeOtroUsuario(IdentidadExterna vinculo, User actor,
                                            VincularIdentidadSocialCommand command) {
        if (!vinculo.usuarioId().equals(actor.id())) {
            log.warn("Vinculacion rechazada: la identidad de {} ya pertenece a otro usuario. actorId={}",
                    command.proveedor(), actor.id());
            throw new IdentidadYaVinculadaException(command.proveedor().name());
        }
        log.info("Vinculacion idempotente: la identidad de {} ya estaba vinculada a usuarioId={}",
                command.proveedor(), actor.id());
    }
}

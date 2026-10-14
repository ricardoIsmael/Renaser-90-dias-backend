package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.CuentaDePrueba;
import com.renaser.os.users.api.CuentasDePruebaUseCase;
import com.renaser.os.users.api.RolPersonalizadoAsignado;
import com.renaser.os.users.api.RolPersonalizadoCambiadoEvent;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.application.ports.out.mentorprofile.LoadMentorProfilePort;
import com.renaser.os.users.application.ports.out.mentorprofile.SaveMentorProfilePort;
import com.renaser.os.users.application.ports.out.participante.LoadParticipacionProgramaPort;
import com.renaser.os.users.application.ports.out.participante.SaveParticipacionProgramaPort;
import com.renaser.os.users.application.ports.out.rolpersonalizado.CargarRolesPersonalizadosPort;
import com.renaser.os.users.application.ports.out.user.BloquearCuentasDePruebaPort;
import com.renaser.os.users.application.ports.out.user.LoadUserPort;
import com.renaser.os.users.application.ports.out.user.SaveUserPort;
import com.renaser.os.users.domain.model.mentorprofile.MentorProfile;
import com.renaser.os.users.domain.model.participante.ParticipacionPrograma;
import com.renaser.os.users.domain.model.rolpersonalizado.RolPersonalizado;
import com.renaser.os.users.domain.model.user.CorreoDeCuentaDePrueba;
import com.renaser.os.users.domain.model.user.Email;
import com.renaser.os.users.domain.model.user.User;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Las cuentas de prueba de «ver como rol» (ver {@link CuentasDePruebaUseCase}; spec 002, fase 2, US5; D-270). Cada
 * pedido, en este orden: quien pide es Soporte TI activo (403), el rol pedido es válido (400 / 404), el candado entre
 * instancias, y recién ahí la cuenta (creada o reparada), su perfil de mentor si acompaña grupos y su programa a mitad
 * del camino si es aprendiz.
 *
 * <p>No publica {@code UsuarioRegistradoEvent} ni {@code RolDeUsuarioCambiadoEvent} ni
 * {@code EstadoDeCuentaCambiadoEvent}: sus oyentes la sumarían al chat de la comunidad, a chats de soporte con el staff
 * real o al grupo de bienvenida. Solo avisa {@link RolPersonalizadoCambiadoEvent} cuando cambia su rol personalizado,
 * para que la regla de disponibilidad de la plataforma la restrinja enseguida.
 */
@Service
class CuentasDePruebaService implements CuentasDePruebaUseCase {

    private final LoadUserPort loadUserPort;
    private final SaveUserPort saveUserPort;
    private final CargarRolesPersonalizadosPort rolesPersonalizados;
    private final LoadParticipacionProgramaPort loadParticipacion;
    private final SaveParticipacionProgramaPort saveParticipacion;
    private final LoadMentorProfilePort loadMentorProfile;
    private final SaveMentorProfilePort saveMentorProfile;
    private final BloquearCuentasDePruebaPort candado;
    private final IdGenerator idGenerator;
    private final Clock clock;
    private final ApplicationEventPublisher eventos;

    @SuppressWarnings("java:S107") // colaboradores, no parámetros de negocio
    CuentasDePruebaService(LoadUserPort loadUserPort, SaveUserPort saveUserPort,
                           CargarRolesPersonalizadosPort rolesPersonalizados,
                           LoadParticipacionProgramaPort loadParticipacion,
                           SaveParticipacionProgramaPort saveParticipacion, LoadMentorProfilePort loadMentorProfile,
                           SaveMentorProfilePort saveMentorProfile, BloquearCuentasDePruebaPort candado,
                           IdGenerator idGenerator, Clock clock, ApplicationEventPublisher eventos) {
        this.loadUserPort = loadUserPort;
        this.saveUserPort = saveUserPort;
        this.rolesPersonalizados = rolesPersonalizados;
        this.loadParticipacion = loadParticipacion;
        this.saveParticipacion = saveParticipacion;
        this.loadMentorProfile = loadMentorProfile;
        this.saveMentorProfile = saveMentorProfile;
        this.candado = candado;
        this.idGenerator = idGenerator;
        this.clock = clock;
        this.eventos = eventos;
    }

    @Override
    @Transactional
    public CuentaDePrueba deRol(UserId soporteTi, UserRole rol) {
        exigirSoporteTi(soporteTi);
        if (rol == null || rol == UserRole.IT_SUPPORT) {
            throw new IllegalArgumentException("Elige uno de los seis roles: Soporte TI no se mira con «ver como»");
        }
        candado.bloquear();
        User cuenta = preparar(CorreoDeCuentaDePrueba.deRol(rol), CorreoDeCuentaDePrueba.nombre(rol.nombreVisible()),
                rol, null);
        return new CuentaDePrueba(cuenta.id(), cuenta.fullName(), cuenta.role(), null);
    }

    @Override
    @Transactional
    public CuentaDePrueba deRolPersonalizado(UserId soporteTi, UUID rolPersonalizadoId) {
        exigirSoporteTi(soporteTi);
        RolPersonalizado rol = rolesPersonalizados.porId(Objects.requireNonNull(rolPersonalizadoId,
                        "rolPersonalizadoId es obligatorio"))
                .orElseThrow(() -> new NoSuchElementException("El rol personalizado no existe"));
        if (!rol.activo()) {
            throw new IllegalArgumentException("«" + rol.nombre() + "» está desactivado: no se puede ver la app como ese rol");
        }
        candado.bloquear();
        Email correo = CorreoDeCuentaDePrueba.deRolPersonalizado(rol.id());
        UUID asignadoAntes = loadUserPort.byEmail(correo).map(User::rolPersonalizadoId).orElse(null);
        User cuenta = preparar(correo, CorreoDeCuentaDePrueba.nombre(rol.nombre()), rol.rolBase(), rol);
        if (!rol.id().equals(asignadoAntes)) {
            eventos.publishEvent(new RolPersonalizadoCambiadoEvent(rol.id(), clock.now()));
        }
        // El nombre sale del rol recién leído y no de la cuenta guardada: el de la cuenta lo calcula la base al leerla.
        return new CuentaDePrueba(cuenta.id(), cuenta.fullName(), cuenta.role(),
                new RolPersonalizadoAsignado(rol.id(), rol.nombre()));
    }

    /** Soporte TI activo, como la consola que la pide (la plataforma ya lo comprobó; acá se repite, defensa en capas). */
    private void exigirSoporteTi(UserId soporteTi) {
        boolean esSoporteTiActivo = soporteTi != null && loadUserPort.byId(soporteTi)
                .filter(usuario -> usuario.esSoporteTi() && usuario.hasAccess()).isPresent();
        if (!esSoporteTiActivo) {
            throw new NotAuthorizedException("Solo Soporte TI ve la app como otro rol");
        }
    }

    private User preparar(Email correo, String nombre, UserRole rol, RolPersonalizado rolPersonalizado) {
        Optional<User> existente = loadUserPort.byEmail(correo);
        User cuenta;
        if (existente.isEmpty()) {
            cuenta = User.cuentaDePrueba(UserId.of(idGenerator.newId()), correo, nombre, rol);
            cuenta.prepararComoCuentaDePrueba(nombre, rol, rolPersonalizado);
            cuenta = saveUserPort.registrarNueva(cuenta);
        } else {
            cuenta = existente.get();
            if (!cuenta.esCuentaDePrueba()) {
                throw new IllegalStateException("El correo de la cuenta de prueba ya lo usa otra cuenta");
            }
            if (cuenta.prepararComoCuentaDePrueba(nombre, rol, rolPersonalizado)) {
                cuenta = saveUserPort.save(cuenta);
            }
        }
        asegurarPerfilDeMentor(cuenta);
        asegurarProgramaSiEsAprendiz(cuenta);
        return cuenta;
    }

    /** Un mentor o una guía necesitan su perfil de mentor para liderar o acompañar un grupo (FK de {@code celulas}). */
    private void asegurarPerfilDeMentor(User cuenta) {
        if (cuenta.role().actuaComoMentor() && loadMentorProfile.byUserId(cuenta.id()).isEmpty()) {
            saveMentorProfile.save(MentorProfile.create(cuenta.id(), clock));
        }
    }

    /** El aprendiz de prueba vive a mitad del programa; si el reloj lo sacó del tramo 30–60, vuelve al día 45. */
    private void asegurarProgramaSiEsAprendiz(User cuenta) {
        if (cuenta.role() != UserRole.TRAINEE) {
            return;
        }
        Optional<ParticipacionPrograma> actual = loadParticipacion.byParticipanteId(cuenta.id());
        if (actual.isEmpty() || !actual.get().sigueAMitadDelPrograma(clock)) {
            saveParticipacion.save(ParticipacionPrograma.aMitadDelProgramaParaCuentaDePrueba(cuenta.id(),
                    actual.orElse(null), clock));
        }
    }
}

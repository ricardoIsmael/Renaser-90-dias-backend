package com.renaser.os.plataforma.infrastructure.adapter.out.sesion;

import com.renaser.os.plataforma.application.ports.out.SesionesDeVerComoPort;
import com.renaser.os.plataforma.domain.model.vercomo.RolElegido;
import com.renaser.os.plataforma.domain.model.vercomo.SesionDeVerComo;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.SesionDeSoloLectura;
import com.renaser.os.users.api.UserRole;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Las sesiones de «ver como» en Spring Session (Redis), las mismas que abre el login (D-270). Se crean directo en el
 * repositorio, no con la {@code HttpSession} del pedido: así la sesión de Soporte TI con la que se pidió queda intacta
 * (el filtro de Spring Session no reescribe su {@code X-Auth-Token}) y la nueva tiene su propio id.
 *
 * <p>Qué guarda: el {@code SecurityContext} de la cuenta de prueba (lo que lee todo el servidor para saber quién pide) y
 * la marca de {@link SesionDeSoloLectura} (quién la abrió, hasta cuándo, qué rol). Solo {@link String} y {@link Long}:
 * el serializador JSON de las sesiones ({@code RedisSessionConfig}) no acepta otros tipos.
 *
 * <p><b>El índice por persona es el de Soporte TI</b>, no el de la cuenta de prueba
 * ({@link FindByIndexNameSessionRepository#PRINCIPAL_NAME_INDEX_NAME}): cerrar todas las sesiones de quien la abrió
 * —al suspenderlo o al cambiar su contraseña— también la cierra. La inactividad máxima es la misma media hora del
 * vencimiento, así Redis la borra sola aunque nadie vuelva a usarla.
 */
@Component
class SesionesDeVerComoRedisAdapter implements SesionesDeVerComoPort {

    private final FindByIndexNameSessionRepository<? extends Session> repositorio;
    private final Clock clock;

    SesionesDeVerComoRedisAdapter(FindByIndexNameSessionRepository<? extends Session> repositorio, Clock clock) {
        this.repositorio = repositorio;
        this.clock = clock;
    }

    @Override
    public String abrir(SesionDeVerComo sesion) {
        return crear(repositorio, sesion);
    }

    @Override
    public Optional<SesionDeVerComo> leer(String idSesion) {
        Session guardada = repositorio.findById(idSesion);
        if (guardada == null || guardada.isExpired() || !(guardada.getAttribute(SesionDeSoloLectura.ABIERTA_POR)
                instanceof String abiertaPor)) {
            return Optional.empty();
        }
        Object contexto = guardada.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        if (!(contexto instanceof SecurityContext securityContext) || securityContext.getAuthentication() == null) {
            return Optional.empty();
        }
        Object venceEn = guardada.getAttribute(SesionDeSoloLectura.VENCE_EN);
        return Optional.of(new SesionDeVerComo(UserId.of(securityContext.getAuthentication().getName()),
                UserId.of(abiertaPor), rolDe(guardada),
                venceEn instanceof Number milisegundos ? Instant.ofEpochMilli(milisegundos.longValue()) : clock.now()));
    }

    @Override
    public void cerrar(String idSesion) {
        repositorio.deleteById(idSesion);
    }

    private <S extends Session> String crear(FindByIndexNameSessionRepository<S> repositorioTipado,
                                             SesionDeVerComo sesion) {
        S nueva = repositorioTipado.createSession();
        SecurityContext contexto = SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(new UsernamePasswordAuthenticationToken(sesion.cuenta().value().toString(), null,
                List.of()));
        nueva.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, contexto);
        nueva.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME,
                sesion.abiertaPor().value().toString());
        nueva.setAttribute(SesionDeSoloLectura.ABIERTA_POR, sesion.abiertaPor().value().toString());
        nueva.setAttribute(SesionDeSoloLectura.VENCE_EN, sesion.venceEn().toEpochMilli());
        marcarRol(nueva, sesion.rol());
        nueva.setMaxInactiveInterval(Duration.between(clock.now(), sesion.venceEn()).plusMinutes(1));
        repositorioTipado.save(nueva);
        return nueva.getId();
    }

    private static void marcarRol(Session sesion, RolElegido rol) {
        switch (rol) {
            case RolElegido.Base base -> sesion.setAttribute(SesionDeSoloLectura.ROL, base.rol().name());
            case RolElegido.Personalizado personalizado -> {
                sesion.setAttribute(SesionDeSoloLectura.ROL_PERSONALIZADO_ID, personalizado.id().toString());
                sesion.setAttribute(SesionDeSoloLectura.ROL_PERSONALIZADO_NOMBRE, personalizado.nombre());
            }
        }
    }

    private static RolElegido rolDe(Session sesion) {
        if (sesion.getAttribute(SesionDeSoloLectura.ROL) instanceof String rol) {
            return new RolElegido.Base(UserRole.valueOf(rol));
        }
        Object id = sesion.getAttribute(SesionDeSoloLectura.ROL_PERSONALIZADO_ID);
        Object nombre = sesion.getAttribute(SesionDeSoloLectura.ROL_PERSONALIZADO_NOMBRE);
        return new RolElegido.Personalizado(UUID.fromString(String.valueOf(id)), String.valueOf(nombre));
    }
}

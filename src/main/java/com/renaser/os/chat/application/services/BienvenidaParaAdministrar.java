package com.renaser.os.chat.application.services;

import com.renaser.os.chat.application.ports.in.bienvenida.BienvenidaEditable;
import com.renaser.os.chat.application.ports.in.bienvenida.BienvenidaEditable.PortadaEditable;
import com.renaser.os.chat.application.ports.in.bienvenida.BienvenidaEditable.TextoEditable;
import com.renaser.os.chat.application.ports.in.bienvenida.BienvenidaEditable.UltimoCambio;
import com.renaser.os.chat.application.ports.out.bienvenida.CambiosDeBienvenidaPort;
import com.renaser.os.chat.application.ports.out.bienvenida.DibujarBienvenidaPort;
import com.renaser.os.chat.application.ports.out.bienvenida.TextosOriginalesDeBienvenidaPort;
import com.renaser.os.chat.domain.model.bienvenida.CambioDeBienvenida;
import com.renaser.os.chat.domain.model.bienvenida.EstadoDePieza;
import com.renaser.os.chat.domain.model.bienvenida.PiezaDeBienvenida;
import com.renaser.os.chat.domain.model.bienvenida.TextoDeBienvenida;
import com.renaser.os.shared.application.ports.out.AlmacenamientoPort;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Lo que comparten los casos de uso de Administración sobre la bienvenida (D-210): quién puede cambiarla
 * y cómo se ve para cambiarla. Es la de UNA formación (spec 035): sus mensajes y su portada; lo vigente de cada pieza lo resuelve
 * {@link EstadoDePieza}.
 *
 * <p><b>Quién:</b> ADMIN y ALCHEMIST con la cuenta activa, y nadie más: el mismo criterio que D-186 para
 * el calendario ({@code UserRole.canManageRoles()} es exactamente esos dos) y el mismo orden que
 * {@code ConversacionService.requireActivoAdmin} (primero la cuenta, después el rol). Lo exige el servicio
 * y no solo {@code @RequiresPermission(MANAGE_WELCOME)}: para MENTOR, ADMIN y ALCHEMIST el interceptor
 * todavía deja pasar todo (A-1), así que el 403 de un MENTOR y el de un ADMIN suspendido salen de acá.
 */
@Component
class BienvenidaParaAdministrar {

    private final TextosOriginalesDeBienvenidaPort originales;
    private final CambiosDeBienvenidaPort cambios;
    private final UserSummaryFinder usuarios;
    private final AlmacenamientoPort almacenamiento;
    private final boolean activa;

    BienvenidaParaAdministrar(TextosOriginalesDeBienvenidaPort originales, CambiosDeBienvenidaPort cambios,
                              UserSummaryFinder usuarios, AlmacenamientoPort almacenamiento,
                              @Value("${renaser.chat.bienvenida.activa:false}") boolean activa) {
        this.originales = originales;
        this.cambios = cambios;
        this.usuarios = usuarios;
        this.almacenamiento = almacenamiento;
        this.activa = activa;
    }

    /** @throws NotAuthorizedException si la cuenta no está activa o no es ADMIN/ALCHEMIST (403) */
    void exigirQuePuedaCambiarla(UserId actorId) {
        UserSummary actor = usuarios.findById(actorId)
                .orElseThrow(() -> new NotAuthorizedException("Cuenta inexistente o suspendida"));
        if (actor.status() != UserStatus.ACTIVE) {
            throw new NotAuthorizedException("La cuenta esta suspendida");
        }
        if (!actor.role().canManageRoles()) {
            throw new NotAuthorizedException("Solo Administración y Alquimista pueden cambiar la bienvenida");
        }
    }

    /**
     * Cómo está hoy una pieza en {@code formacion}. Un texto: su último cambio o el original de ESA formación. La portada: la suya o, si no tiene, la vigente
     * de la Escuela.
     */
    EstadoDePieza estado(Formacion formacion, PiezaDeBienvenida pieza) {
        if (pieza.esTexto()) {
            return new EstadoDePieza(pieza, original(formacion, pieza), cambios.ultimo(formacion, pieza).orElse(null));
        }
        CambioDeBienvenida delaEscuela = cambios.ultimo(Formacion.POR_DEFECTO, pieza).orElse(null);
        CambioDeBienvenida propio = formacion == Formacion.POR_DEFECTO ? delaEscuela : cambios.ultimo(formacion, pieza).orElse(null);
        return EstadoDePieza.deImagen(formacion, pieza, DibujarBienvenidaPort.PORTADA_ORIGINAL, delaEscuela, propio);
    }

    /** Una lectura de la bitácora y una de las cuentas de quienes cambiaron algo (anti-N+1). */
    BienvenidaEditable leer(Formacion formacion) {
        Map<PiezaDeBienvenida, CambioDeBienvenida> ultimos = cambios.ultimos(formacion);
        Map<UserId, UserSummary> autores = usuarios.findByIds(ultimos.values().stream()
                .map(CambioDeBienvenida::cambiadoPor).filter(Objects::nonNull).distinct().toList());
        List<TextoEditable> textos = PiezaDeBienvenida.textos().stream()
                .map(pieza -> texto(new EstadoDePieza(pieza, original(formacion, pieza), ultimos.get(pieza)), autores))
                .toList();
        EstadoDePieza portada = estado(formacion, PiezaDeBienvenida.PORTADA);
        boolean heredada = formacion != Formacion.POR_DEFECTO && !portada.cambiada();
        return new BienvenidaEditable(formacion, activa, TextoDeBienvenida.LARGO_MAXIMO, textos, new PortadaEditable(
                portada.cambiada(), heredada, almacenamiento.guardaObjetos(), ultimoCambio(portada, autores)));
    }

    private String original(Formacion formacion, PiezaDeBienvenida pieza) {
        return originales.original(formacion, pieza);
    }

    private static TextoEditable texto(EstadoDePieza estado, Map<UserId, UserSummary> autores) {
        return new TextoEditable(estado.pieza(), estado.vigente(), estado.original(), estado.cambiada(),
                estado.pieza().marcadores(), ultimoCambio(estado, autores));
    }

    private static UltimoCambio ultimoCambio(EstadoDePieza estado, Map<UserId, UserSummary> autores) {
        return estado.ultimo().map(cambio -> new UltimoCambio(
                        cambio.quien().map(autores::get).map(UserSummary::fullName).orElse(null),
                        cambio.cambiadoEn(), cambio.esVueltaAlOriginal()))
                .orElse(null);
    }
}

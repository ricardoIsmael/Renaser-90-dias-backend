package com.renaser.os.leadership.application.services;

import com.renaser.os.chat.api.MensajeDirectoPort;
import com.renaser.os.chat.api.MensajeDirectoPort.MensajeDirecto;
import com.renaser.os.leadership.application.ports.out.GuardarObservacionPort;
import com.renaser.os.leadership.application.ports.out.LeerObservacionesPort;
import com.renaser.os.leadership.domain.model.observacion.ObservacionDeMentor;
import com.renaser.os.leadership.domain.model.observacion.ObservacionDeMentor.Contenido;
import com.renaser.os.leadership.domain.model.observacion.ObservacionDeMentor.Envio;
import com.renaser.os.leadership.domain.model.observacion.ObservacionDeMentor.SobrePersona;
import com.renaser.os.leadership.domain.model.observacion.TipoObservacion;
import com.renaser.os.shared.domain.AlcanceDeFormacion;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.AlcanceDeFormacionFinder;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Lo que tienen en común «Avisar a su mentor» y «Recordar al mentor» (spec 056, FR-019 y FR-029): el líder le escribe a un mentor SOBRE una persona del
 * grupo, y el mentor se entera por su chat y por el teléfono, una sola vez por {@code claveOperacion}.
 *
 * <p>Se guarda como una observación SUGERENCIA sobre esa persona (spec 005, R-7): así la idempotencia no pide tabla nueva —repetir la clave encuentra lo
 * ya hecho, y dos pedidos a la vez chocan contra el índice único de {@code observaciones_mentor}, que deshace el mensaje del que pierde— y lo que el líder
 * le dijo queda en «Lo que le dijiste». Si el chat no puede entregar el mensaje, el error sube con su motivo y no queda nada guardado, para que el líder
 * sepa que el mentor no se enteró (igual que en una observación: SC-005).
 *
 * <p>También decide a quién se le avisa: la persona tiene que ser de una formación del líder (403 {@code FUERA_DE_SU_ALCANCE}, no 404: existe, solo que no
 * le toca).
 */
@Component
class EntregaDeAvisosAlMentor {

    private final AccesoDeLiderazgo acceso;
    private final AlcanceDeFormacionFinder formaciones;
    private final LeerObservacionesPort leer;
    private final GuardarObservacionPort guardar;
    private final MensajeDirectoPort chat;
    private final AvisosDeObservaciones avisos;
    private final IdGenerator ids;
    private final Clock clock;

    EntregaDeAvisosAlMentor(AccesoDeLiderazgo acceso, AlcanceDeFormacionFinder formaciones, LeerObservacionesPort leer, GuardarObservacionPort guardar,
                            MensajeDirectoPort chat, AvisosDeObservaciones avisos, IdGenerator ids, Clock clock) {
        this.acceso = acceso;
        this.formaciones = formaciones;
        this.leer = leer;
        this.guardar = guardar;
        this.chat = chat;
        this.avisos = avisos;
        this.ids = ids;
        this.clock = clock;
    }

    /**
     * La persona tiene que ser de alguna de las formaciones del líder; Administración y Alquimista ven todas. Una persona que no existe no tiene formación:
     * se deja pasar y quien la mira la responde como 404.
     */
    void exigirQueSeaDeSusFormaciones(UserId actor, UserId persona) {
        AlcanceDeFormacion alcance = acceso.alcanceDe(actor);
        Set<Formacion> suyas = formaciones.formacionesDe(persona);
        if (!suyas.isEmpty() && suyas.stream().noneMatch(alcance::incluye)) {
            throw new NotAuthorizedException("Esa persona es de otra formación: tú ves solo las de tus formaciones.", NotAuthorizedException.FUERA_DE_SU_ALCANCE);
        }
    }

    /** Lo ya entregado con esa clave, si lo hay. La misma clave para OTRA persona es un error del cliente, no un reintento. */
    Optional<ObservacionDeMentor> yaEntregada(UserId autor, UserId persona, String claveOperacion) {
        Optional<ObservacionDeMentor> existente = leer.porAutorYClave(autor, claveOperacion);
        existente.ifPresent(o -> {
            if (!Objects.equals(o.personaId(), persona)) {
                throw new IllegalArgumentException("Esa clave de operación ya se usó para otro aviso");
            }
        });
        return existente;
    }

    /**
     * Escribe {@code textoParaElChat} al mentor, guarda la observación con {@code textoDeLaObservacion} (hasta 1000 caracteres) y publica el aviso del
     * teléfono. Todo en la transacción de quien llama.
     */
    void entregar(Entrega entrega) {
        MensajeDirecto entregado = chat.enviar(entrega.autor(), entrega.mentor(), entrega.textoParaElChat());
        ObservacionDeMentor guardada = guardar.insertar(ObservacionDeMentor.registrar(ids.newId(), new Contenido(entrega.mentor(), entrega.autor(),
                TipoObservacion.SUGERENCIA, entrega.textoDeLaObservacion(), new Envio(true, entregado.mensajeId()), entrega.claveOperacion(),
                new SobrePersona(entrega.persona(), null)), clock));
        avisos.alMentor(guardada, entregado.conversacionId());
    }

    record Entrega(UserId autor, UserId mentor, UserId persona, String textoDeLaObservacion, String textoParaElChat, String claveOperacion) {
    }
}

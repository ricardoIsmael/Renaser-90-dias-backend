package com.renaser.os.chat.application.services;

import com.renaser.os.chat.application.ports.in.mensaje.EnviarMensajeDelProgramaUseCase;
import com.renaser.os.chat.application.ports.in.mensaje.EnviarMensajeDelProgramaUseCase.AvisoDeLaPieza;
import com.renaser.os.chat.application.ports.in.mensaje.EnviarMensajeDelProgramaUseCase.EntregaDelPrograma;
import com.renaser.os.chat.application.ports.in.mensaje.EnviarMensajeDelProgramaUseCase.PiezaDelPrograma;
import com.renaser.os.chat.application.ports.in.ranking.PublicarPodioDeLaSemanaUseCase;
import com.renaser.os.chat.application.ports.in.ranking.VerPodioDeLaSemanaUseCase;
import com.renaser.os.chat.application.ports.out.conversacion.LoadConversacionPort;
import com.renaser.os.chat.application.ports.out.mensaje.LoadMensajePort;
import com.renaser.os.chat.application.ports.out.ranking.DibujarPodioPort;
import com.renaser.os.chat.domain.model.conversacion.Conversacion;
import com.renaser.os.chat.domain.model.mensaje.ContenidoDelPrograma;
import com.renaser.os.chat.domain.model.ranking.PodioDeLaSemana;
import com.renaser.os.chat.domain.model.ranking.SemanaDelRanking;
import com.renaser.os.chat.domain.model.ranking.TextoDelPodio;
import com.renaser.os.points.api.RankingGeneralFinder;
import com.renaser.os.shared.application.ports.out.AlmacenamientoPort;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

/**
 * El podio semanal del ranking general en el grupo «Formación Renaser Global» (D-262).
 *
 * <p><b>Qué sale:</b> la imagen del podio (sin aviso) y, después, el texto (aviso a todos los del grupo), los dos
 * firmados por el programa y sin persona detrás ({@link EntregaDelPrograma#sinPersona}, V95): nombran a varios
 * aprendices y no pueden caer con la cuenta de uno.
 *
 * <p><b>Si el texto de la semana ya salió, está hecha</b> y no se mira nada más: ni el ranking, ni la imagen, ni
 * el almacenamiento (el mismo criterio que {@code TarjetaEnSoporte}). Así el reintento de cada hora del lunes no
 * hace trabajo de más.
 *
 * <p><b>Sin almacenamiento de verdad sale solo el texto</b> (G-5, como la tarjeta del semáforo): con {@code noop}
 * la imagen apuntaría a un archivo inexistente. Si S3 FALLA, en cambio, no sale nada: la imagen es el podio, y
 * la corrida de la hora siguiente lo reintenta entero.
 *
 * <p>Sin {@code @Transactional}: dibujar y subir no retienen una conexión; los mensajes van en la transacción
 * corta de {@code enviarUnaVez}.
 */
@Service
public class PodioDeLaSemanaService implements PublicarPodioDeLaSemanaUseCase, VerPodioDeLaSemanaUseCase {

    private static final Logger log = LoggerFactory.getLogger(PodioDeLaSemanaService.class);

    private final RankingGeneralFinder ranking;
    private final DibujarPodioPort dibujo;
    private final AlmacenamientoPort almacenamiento;
    private final LoadConversacionPort conversaciones;
    private final LoadMensajePort mensajes;
    private final EnviarMensajeDelProgramaUseCase delPrograma;
    private final UserSummaryFinder usuarios;
    private final Clock clock;
    private final boolean activo;

    PodioDeLaSemanaService(RankingGeneralFinder ranking, DibujarPodioPort dibujo, AlmacenamientoPort almacenamiento,
                           LoadConversacionPort conversaciones, LoadMensajePort mensajes,
                           EnviarMensajeDelProgramaUseCase delPrograma, UserSummaryFinder usuarios, Clock clock,
                           @Value("${renaser.chat.ranking-semanal.activo:false}") boolean activo) {
        this.ranking = ranking;
        this.dibujo = dibujo;
        this.almacenamiento = almacenamiento;
        this.conversaciones = conversaciones;
        this.mensajes = mensajes;
        this.delPrograma = delPrograma;
        this.usuarios = usuarios;
        this.clock = clock;
        this.activo = activo;
    }

    @Override
    public ResultadoDelPodio publicarLaSemanaCerrada() {
        SemanaDelRanking semana = SemanaDelRanking.ultimaCerradaAl(clock.now());
        return activo ? publicarEnCadaFormacion(semana) : resultado(semana, Estado.APAGADO, 0);
    }

    @Override
    public ResultadoDelPodio publicarAhora(UserId actorId) {
        exigirAdministracion(actorId);
        return publicarEnCadaFormacion(SemanaDelRanking.ultimaCerradaAl(clock.now()));
    }

    /**
     * Spec 024 (FR-040): UN podio por formación, cada uno con SUS aprendices y en SU comunidad general. Una formación que falla no detiene a las
     * otras (el barrido reintenta cada hora del lunes y los ids hacen que repetir no duplique), pero el primer fallo se vuelve a lanzar al final para que
     * quien llama —el barrido— lo vea y lo registre como siempre. El resultado junta los tres.
     */
    private ResultadoDelPodio publicarEnCadaFormacion(SemanaDelRanking semana) {
        Estado mejor = Estado.APAGADO;
        int piezas = 0;
        RuntimeException primerFallo = null;
        for (Formacion formacion : Formacion.values()) {
            try {
                ResultadoDelPodio una = publicar(semana, formacion);
                piezas += una.piezas();
                mejor = mejor.ordinal() <= una.estado().ordinal() ? mejor : una.estado();
            } catch (RuntimeException e) {
                log.error("[chat.podio] el podio de {} del {} no se pudo publicar: {}", formacion, semana.lunes(), e.getMessage(), e);
                if (primerFallo == null) {
                    primerFallo = e;
                }
            }
        }
        if (primerFallo != null) {
            throw primerFallo;
        }
        return resultado(semana, mejor, piezas);
    }

    @Override
    public VistaPreviaDelPodio vistaPrevia(UserId actorId, Formacion formacion) {
        exigirAdministracion(actorId);
        SemanaDelRanking semana = SemanaDelRanking.ultimaCerradaAl(clock.now());
        PodioDeLaSemana podio = podioDe(semana, formacion);
        boolean vacio = podio.estaVacio();
        return new VistaPreviaDelPodio(semana.lunes(), semana.domingo(), podio.todos(),
                vacio ? null : TextoDelPodio.para(podio, semana), vacio ? null : dibujo.dibujar(podio, semana),
                DibujarPodioPort.TIPO_CONTENIDO, TextoDelPodio.plantillaDe(semana).name(),
                mensajes.porId(semana.idDelTexto(formacion)).isPresent());
    }

    private ResultadoDelPodio publicar(SemanaDelRanking semana, Formacion formacion) {
        Optional<Conversacion> grupoGeneral = conversaciones.global(formacion);
        if (grupoGeneral.isEmpty()) {
            log.warn("[chat.podio] no existe la comunidad general de {}: su podio del {} no se publica", formacion, semana.lunes());
            return resultado(semana, Estado.SIN_GRUPO_GENERAL, 0);
        }
        if (mensajes.porId(semana.idDelTexto(formacion)).isPresent()) {
            return resultado(semana, Estado.YA_ESTABA, 0);
        }
        PodioDeLaSemana podio = podioDe(semana, formacion);
        if (podio.estaVacio()) {
            log.info("[chat.podio] nadie de {} cerró la semana del {} con puntaje: no se publica", formacion, semana.lunes());
            return resultado(semana, Estado.SIN_PUNTAJES, 0);
        }
        int piezas = delPrograma.enviarUnaVez(EntregaDelPrograma.sinPersona(grupoGeneral.get().id(),
                piezas(podio, semana, formacion)));
        log.info("[chat.podio] podio de {} del {} al {} publicado ({} piezas, {} en el podio)", formacion, semana.lunes(),
                semana.domingo(), piezas, podio.todos().size());
        return resultado(semana, piezas > 0 ? Estado.PUBLICADO : Estado.YA_ESTABA, piezas);
    }

    /** El ranking cerrado el domingo de los aprendices de {@code formacion}: sus 7 días de hábitos son justo esa semana. */
    private PodioDeLaSemana podioDe(SemanaDelRanking semana, Formacion formacion) {
        return PodioDeLaSemana.de(ranking.alCorte(semana.domingo(), formacion).stream()
                .map(p -> new PodioDeLaSemana.Aprendiz(p.nombreCompleto(), p.puntaje()))
                .toList());
    }

    private List<PiezaDelPrograma> piezas(PodioDeLaSemana podio, SemanaDelRanking semana, Formacion formacion) {
        List<PiezaDelPrograma> piezas = new ArrayList<>(2);
        if (almacenamiento.guardaObjetos()) {
            piezas.add(new PiezaDelPrograma(semana.idDeLaImagen(formacion), imagenSubida(podio, semana, formacion),
                    AvisoDeLaPieza.SIN_AVISO));
        }
        piezas.add(new PiezaDelPrograma(semana.idDelTexto(formacion), ContenidoDelPrograma.texto(TextoDelPodio.para(podio, semana)),
                AvisoDeLaPieza.A_TODOS));
        return piezas;
    }

    private ContenidoDelPrograma imagenSubida(PodioDeLaSemana podio, SemanaDelRanking semana, Formacion formacion) {
        byte[] imagen = dibujo.dibujar(podio, semana);
        almacenamiento.subir(semana.rutaDeLaImagen(formacion), imagen, DibujarPodioPort.TIPO_CONTENIDO);
        return ContenidoDelPrograma.imagen(semana.rutaDeLaImagen(formacion), DibujarPodioPort.TIPO_CONTENIDO, imagen.length);
    }

    /** ADMIN y ALCHEMIST con la cuenta activa (el criterio de D-210): primero la cuenta, después el rol. */
    private void exigirAdministracion(UserId actorId) {
        UserSummary actor = usuarios.findById(actorId)
                .orElseThrow(() -> new NotAuthorizedException("Cuenta inexistente o suspendida"));
        if (actor.status() != UserStatus.ACTIVE) {
            throw new NotAuthorizedException("La cuenta esta suspendida");
        }
        if (!actor.role().canManageRoles()) {
            throw new NotAuthorizedException("Solo Administración y Alquimista pueden publicar el podio de la semana");
        }
    }

    private static ResultadoDelPodio resultado(SemanaDelRanking semana, Estado estado, int piezas) {
        return new ResultadoDelPodio(semana.lunes(), semana.domingo(), estado, piezas);
    }
}

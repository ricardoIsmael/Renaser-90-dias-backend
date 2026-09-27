package com.renaser.os.chat.application.services;

import com.renaser.os.chat.application.ports.in.conversacion.AutorizarAccesoAConversacionUseCase;
import com.renaser.os.chat.application.ports.in.conversacion.VerFotosDelChatUseCase;
import com.renaser.os.chat.application.ports.out.bienvenida.TarjetaConNombrePort;
import com.renaser.os.chat.application.ports.out.bienvenida.TarjetaConNombrePort.TarjetaConNombre;
import com.renaser.os.chat.application.ports.out.conversacion.LoadConversacionPort;
import com.renaser.os.chat.domain.model.conversacion.Conversacion;
import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.chat.domain.model.conversacion.FotoDeIntegrantes;
import com.renaser.os.chat.domain.model.conversacion.PrimerNombre;
import com.renaser.os.chat.domain.model.conversacion.TipoConversacion;
import com.renaser.os.community.api.FotoPropiaDelGrupoFinder;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Las fotos del chat que sirve el backend: la tarjeta con nombre del soporte (D-205), la de cada
 * integrante de un grupo o de un soporte (D-206) y la foto propia de un grupo (D-212), que guarda
 * {@code community} y se lee de su contrato público.
 *
 * <p><b>El orden importa.</b> Cuenta activa (403), la conversación existe (404), quien pide puede verla
 * (403) y recién entonces si es del tipo que corresponde y, para un integrante, si lo es (404): así, a
 * quien no participa no se le dice qué tipo de conversación es ni quién está adentro.
 *
 * <p><b>Integrante = quien puede verla</b>, con la misma regla de todo el módulo
 * ({@link AutorizarAccesoAConversacionUseCase}): en un grupo, la pertenencia vigente (mentor y aprendices
 * de hoy); en un soporte, la aprendiz y el staff que participa. No se escribe otra regla.
 *
 * <p><b>El modo</b> ({@code renaser.chat.foto-de-integrantes}, {@code CHAT_FOTO_DE_INTEGRANTES} en
 * Parameter Store; D-206): {@code TARJETA} (el default, decisión del dueño) muestra a todos con su
 * tarjeta; {@code FOTO_SUBIDA}, solo a los que no subieron foto. Se lee al arrancar, como el interruptor
 * de las bienvenidas: se cambia en Parameter Store y se reinicia el contenedor. Un valor que no es un modo
 * no tumba el arranque: queda {@code TARJETA} y se avisa en el log.
 *
 * <p>Sin {@code @Transactional}: dibujar lleva decenas de milisegundos y no debe retener una conexión
 * de la base. Las lecturas van cada una en la transacción de su repositorio.
 * <blockquote><b>Corregido 2026-09-27 (D-206).</b> Se llamaba {@code FotoDelSoporteService} y solo
 * servía la foto del soporte.</blockquote>
 */
@Service
public class FotosDelChatService implements VerFotosDelChatUseCase {

    private static final Logger log = LoggerFactory.getLogger(FotosDelChatService.class);

    /** Los que tienen integrantes con tarjeta. La comunidad usa el fénix; un 1 a 1, a la persona. */
    private static final Set<TipoConversacion> CON_TARJETAS_DE_INTEGRANTES =
            EnumSet.of(TipoConversacion.CELULA, TipoConversacion.SOPORTE);

    private final LoadConversacionPort loadConversacionPort;
    private final AutorizarAccesoAConversacionUseCase autorizarAcceso;
    private final UserSummaryFinder userSummaryFinder;
    private final TarjetaConNombrePort tarjetaPort;
    private final FotoPropiaDelGrupoFinder fotosDeGrupos;
    private final FotoDeIntegrantes modo;

    public FotosDelChatService(LoadConversacionPort loadConversacionPort,
                               AutorizarAccesoAConversacionUseCase autorizarAcceso,
                               UserSummaryFinder userSummaryFinder, TarjetaConNombrePort tarjetaPort,
                               FotoPropiaDelGrupoFinder fotosDeGrupos,
                               @Value("${renaser.chat.foto-de-integrantes:TARJETA}") String modo) {
        this.loadConversacionPort = loadConversacionPort;
        this.autorizarAcceso = autorizarAcceso;
        this.userSummaryFinder = userSummaryFinder;
        this.tarjetaPort = tarjetaPort;
        this.fotosDeGrupos = fotosDeGrupos;
        this.modo = modoDe(modo);
        log.info("[chat.fotos] foto de los integrantes en grupos y soporte: {} (CHAT_FOTO_DE_INTEGRANTES)", this.modo);
    }

    /**
     * El soporte, la tarjeta de su aprendiz; un grupo, su foto propia si la tiene (D-212). Un grupo que usa
     * la de Renaser da 404, como antes, y la app muestra la tarjeta que trae.
     */
    @Override
    public FotoDelChat fotoDeLaConversacion(UserId actorId, ConversacionId conversacionId) {
        Conversacion conversacion = laQuePuedeVer(actorId, conversacionId);
        return switch (conversacion.tipo()) {
            case SOPORTE -> tarjetaDe(conversacion.aprendizDelSoporte().orElseThrow(() -> new NoSuchElementException(
                    "El soporte no tiene aprendiz reconocible: " + conversacionId)));
            case CELULA -> fotoPropiaDe(conversacion);
            default -> throw new NoSuchElementException("La comunidad y los 1 a 1 no tienen foto propia");
        };
    }

    private FotoDelChat fotoPropiaDe(Conversacion grupo) {
        return fotosDeGrupos.fotoDe(grupo.celulaId())
                .map(foto -> new FotoDelChat(foto.jpeg(), huellaDe(foto.jpeg())))
                .orElseThrow(() -> new NoSuchElementException("El grupo usa la foto de Renaser"));
    }

    @Override
    public FotoDelChat fotoDeIntegrante(UserId actorId, ConversacionId conversacionId, UserId integranteId) {
        Conversacion conversacion = laQuePuedeVer(actorId, conversacionId);
        if (!CON_TARJETAS_DE_INTEGRANTES.contains(conversacion.tipo())
                || !autorizarAcceso.puedeVer(conversacionId, integranteId)) {
            throw new NoSuchElementException("No es integrante de un grupo o soporte que puedas ver");
        }
        return tarjetaDe(integranteId);
    }

    @Override
    public Optional<TarjetasDelGrupo> tarjetasDelGrupo(UUID grupoId, Collection<UserId> integrantes) {
        return loadConversacionPort.porCelulaId(grupoId)
                .map(chat -> new TarjetasDelGrupo(chat.id(), quienesLlevanTarjeta(integrantes)));
    }

    /** Con {@code TARJETA}, todos y sin consultar a nadie; con {@code FOTO_SUBIDA}, una consulta en lote. */
    private List<UserId> quienesLlevanTarjeta(Collection<UserId> integrantes) {
        if (modo == FotoDeIntegrantes.TARJETA) {
            return List.copyOf(integrantes);
        }
        Map<UserId, UserSummary> cuentas = userSummaryFinder.findByIds(integrantes);
        return integrantes.stream().filter(id -> modo.llevaTarjeta(subioFoto(cuentas.get(id)))).toList();
    }

    private static boolean subioFoto(UserSummary cuenta) {
        return cuenta != null && cuenta.avatarUrl() != null && !cuenta.avatarUrl().isBlank();
    }

    /** Cuenta activa, conversación existente y que quien pide pueda verla, en ese orden. */
    private Conversacion laQuePuedeVer(UserId actorId, ConversacionId conversacionId) {
        requireActivo(actorId);
        Conversacion conversacion = loadConversacionPort.porId(conversacionId)
                .orElseThrow(() -> new NoSuchElementException("Conversacion no encontrada: " + conversacionId));
        if (!autorizarAcceso.puedeVer(conversacionId, actorId)) {
            throw new NotAuthorizedException("No eres participante de esta conversación");
        }
        return conversacion;
    }

    /** Sin cuenta (se dio de baja y la conversación quedó), la tarjeta va sin nombre: es la del programa. */
    private FotoDelChat tarjetaDe(UserId persona) {
        String nombre = userSummaryFinder.findById(persona).map(u -> PrimerNombre.de(u.fullName())).orElse("");
        TarjetaConNombre tarjeta = tarjetaPort.tarjetaDe(nombre);
        return new FotoDelChat(tarjeta.jpeg(), tarjeta.huella());
    }

    private void requireActivo(UserId actorId) {
        boolean activo = userSummaryFinder.findById(actorId).map(u -> u.status() == UserStatus.ACTIVE).orElse(false);
        if (!activo) {
            throw new NotAuthorizedException("La cuenta esta suspendida");
        }
    }

    /** Mismo criterio que las tarjetas: SHA-256 del contenido, 32 caracteres. Cambia si y solo si cambia la foto. */
    private static String huellaDe(byte[] jpeg) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(jpeg)).substring(0, 32);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("La JVM no trae SHA-256", e);
        }
    }

    private static FotoDeIntegrantes modoDe(String valor) {
        return FotoDeIntegrantes.de(valor).orElseGet(() -> {
            log.warn("[chat.fotos] CHAT_FOTO_DE_INTEGRANTES='{}' no es TARJETA ni FOTO_SUBIDA: queda TARJETA", valor);
            return FotoDeIntegrantes.TARJETA;
        });
    }
}

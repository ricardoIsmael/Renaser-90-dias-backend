package com.renaser.os.chat.application.services;

import com.renaser.os.chat.application.ports.in.caja.CartaDeCajaUseCase;
import com.renaser.os.chat.application.ports.out.bienvenida.CambiosDeBienvenidaPort;
import com.renaser.os.chat.application.ports.out.caja.DibujarCartaDeCajaPort;
import com.renaser.os.chat.domain.model.bienvenida.CambioDeBienvenida;
import com.renaser.os.chat.domain.model.bienvenida.EstadoDePieza;
import com.renaser.os.chat.domain.model.bienvenida.FondoDeCarta;
import com.renaser.os.chat.domain.model.bienvenida.PiezaDeBienvenida;
import com.renaser.os.chat.domain.model.bienvenida.PortadaDeBienvenida;
import com.renaser.os.shared.application.ports.out.AlmacenamientoPort;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * La carta con el nombre de la Caja Renaser (D-219). El fondo va en la bitácora de la bienvenida
 * ({@code cambios_bienvenida}, pieza {@code CARTA_CAJA}): lo vigente es su último cambio, y volver al
 * original es otro cambio. Se sube en dos pasos como la portada (D-210), y se revisa antes de usarlo; abrir la
 * imagen va sin transacción, y lo único que escribe es el INSERT de la bitácora.
 *
 * <p><b>Quién:</b> solo ADMIN con la cuenta activa (spec §3, «Solo el Admin»), el mismo guard que la caja en
 * {@code onboarding}. El nombre es el de la cuenta del aprendiz, completo y tal como está escrito.
 */
@Service
public class CartaDeCajaService implements CartaDeCajaUseCase {

    private static final Duration VALIDEZ_URL_SUBIDA = Duration.ofMinutes(10);

    private final UserSummaryFinder usuarios;
    private final CambiosDeBienvenidaPort cambios;
    private final DibujarCartaDeCajaPort dibujante;
    private final AlmacenamientoPort almacenamiento;
    private final IdGenerator idGenerator;
    private final Clock clock;

    CartaDeCajaService(UserSummaryFinder usuarios, CambiosDeBienvenidaPort cambios, DibujarCartaDeCajaPort dibujante,
                       AlmacenamientoPort almacenamiento, IdGenerator idGenerator, Clock clock) {
        this.usuarios = usuarios;
        this.cambios = cambios;
        this.dibujante = dibujante;
        this.almacenamiento = almacenamiento;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    @Override
    public byte[] carta(UserId actorId, UserId aprendizId) {
        exigirAdmin(actorId);
        UserSummary aprendiz = usuarios.findById(aprendizId).filter(u -> u.role() == UserRole.TRAINEE)
                .orElseThrow(() -> new NoSuchElementException("No hay un aprendiz con ese id"));
        return dibujante.dibujar(aprendiz.fullName(), estado().cambiada() ? estado().vigente() : null);
    }

    @Override
    public FondoDeLaCarta fondo(UserId actorId) {
        exigirAdmin(actorId);
        return leer();
    }

    @Override
    public UrlDeSubida solicitarSubidaDeFondo(UserId actorId, String tipoContenido) {
        exigirAdmin(actorId);
        PortadaDeBienvenida.exigirTipoDeContenido(tipoContenido);
        String ruta = FondoDeCarta.PREFIJO_RUTA + idGenerator.newId();
        return new UrlDeSubida(almacenamiento.firmarSubida(ruta, tipoContenido.strip(), VALIDEZ_URL_SUBIDA), ruta);
    }

    @Override
    public FondoDeLaCarta confirmarFondo(UserId actorId, String ruta) {
        exigirAdmin(actorId);
        String propia = FondoDeCarta.exigirRutaPropia(ruta);
        if (!propia.equals(estado().vigente())) {
            if (!almacenamiento.guardaObjetos()) {
                throw new IllegalStateException("Este servidor no tiene dónde guardar imágenes, así que el fondo "
                        + "no se puede cambiar desde acá.");
            }
            dibujante.revisar(propia);
            cambios.registrar(CambioDeBienvenida.fondoDeCarta(propia, actorId, clock.now()));
        }
        return leer();
    }

    @Override
    public FondoDeLaCarta volverAlFondoOriginal(UserId actorId) {
        exigirAdmin(actorId);
        if (estado().cambiada()) {
            cambios.registrar(CambioDeBienvenida.volverAlOriginal(PiezaDeBienvenida.CARTA_CAJA, actorId, clock.now()));
        }
        return leer();
    }

    private EstadoDePieza estado() {
        return new EstadoDePieza(PiezaDeBienvenida.CARTA_CAJA, "original",
                cambios.ultimo(PiezaDeBienvenida.CARTA_CAJA).orElse(null));
    }

    private FondoDeLaCarta leer() {
        Optional<CambioDeBienvenida> ultimo = cambios.ultimo(PiezaDeBienvenida.CARTA_CAJA);
        String por = ultimo.flatMap(CambioDeBienvenida::quien).flatMap(usuarios::findById)
                .map(UserSummary::fullName).orElse(null);
        return new FondoDeLaCarta(estado().cambiada(), almacenamiento.guardaObjetos(), por,
                ultimo.map(CambioDeBienvenida::cambiadoEn).orElse(null));
    }

    /** Primero la cuenta, después el rol (el mismo criterio que {@code GuardiaDeCaja} en onboarding). */
    private void exigirAdmin(UserId actorId) {
        UserSummary actor = usuarios.findById(actorId)
                .orElseThrow(() -> new NotAuthorizedException("Cuenta inexistente o suspendida"));
        if (!actor.status().allowsAccess()) {
            throw new NotAuthorizedException("La cuenta esta suspendida");
        }
        if (actor.role() != UserRole.ADMIN) {
            throw new NotAuthorizedException("Solo el Admin lleva la Caja Renaser");
        }
    }
}

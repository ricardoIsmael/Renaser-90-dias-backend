package com.renaser.os.onboarding.application.services.caja;

import com.renaser.os.onboarding.application.ports.in.caja.DetalleDeCaja;
import com.renaser.os.onboarding.application.ports.in.caja.FotosDeCajaUseCase;
import com.renaser.os.onboarding.application.ports.out.caja.PasosDeCajaPort;
import com.renaser.os.onboarding.application.ports.out.media.SaveMediaPort;
import com.renaser.os.onboarding.domain.model.caja.CajaRenaser;
import com.renaser.os.onboarding.domain.model.caja.FotoDeCaja;
import com.renaser.os.onboarding.domain.model.caja.PasoDeCaja;
import com.renaser.os.onboarding.domain.model.media.ClaseMedia;
import com.renaser.os.onboarding.domain.model.media.MediaOnboarding;
import com.renaser.os.shared.application.ports.out.AlmacenamientoPort;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * La foto de la caja armada y el comprobante (D-219), en dos pasos como la portada de la bienvenida (D-210):
 * el Admin pide una URL prefirmada, el teléfono sube directo al almacenamiento, y confirma la ruta.
 *
 * <p>La ruta la arma el servidor bajo la caja del APRENDIZ ({@code onboarding/<aprendizId>/caja/}): la media
 * queda a nombre de él en {@code medias_onboarding}, y confirmar solo acepta rutas de ese prefijo.
 *
 * <p><b>Confirmar comprueba que la foto exista</b> (la baja con tope de peso): sin eso, un paso apuntaría a un
 * objeto que no está, y la foto del chat saldría rota. Por eso, con el almacenamiento de marcador (local),
 * confirmar responde 409 como la portada. Bajarla va FUERA de la transacción; lo único que escribe es el
 * INSERT de la media y el paso, juntos.
 */
@Service
public class FotosDeCajaService implements FotosDeCajaUseCase {

    private static final Duration VALIDEZ_URL_SUBIDA = Duration.ofMinutes(10);

    private final GuardiaDeCaja guardia;
    private final CajasDelPadron padron;
    private final LecturaDeCaja lectura;
    private final PasosDeCajaPort pasos;
    private final SaveMediaPort medias;
    private final AlmacenamientoPort almacenamiento;
    private final IdGenerator idGenerator;
    private final Clock clock;
    private final TransactionTemplate transaccion;

    FotosDeCajaService(GuardiaDeCaja guardia, CajasDelPadron padron, LecturaDeCaja lectura, PasosDeCajaPort pasos,
                       SaveMediaPort medias, AlmacenamientoPort almacenamiento, IdGenerator idGenerator, Clock clock,
                       PlatformTransactionManager transactionManager) {
        this.guardia = guardia;
        this.padron = padron;
        this.lectura = lectura;
        this.pasos = pasos;
        this.medias = medias;
        this.almacenamiento = almacenamiento;
        this.idGenerator = idGenerator;
        this.clock = clock;
        this.transaccion = new TransactionTemplate(transactionManager);
    }

    @Override
    public UrlDeSubida solicitarSubida(UserId actorId, UserId aprendizId, FotoDeCaja foto, String tipoContenido) {
        guardia.exigirAdmin(actorId);
        foto.accion().exigirDesde(padron.deAprendiz(aprendizId).caja().estado());
        String tipo = FotoDeCaja.exigirTipoDeContenido(tipoContenido);
        String ruta = FotoDeCaja.rutaNueva(aprendizId, idGenerator.newId());
        return new UrlDeSubida(almacenamiento.firmarSubida(ruta, tipo, VALIDEZ_URL_SUBIDA), ruta);
    }

    @Override
    public DetalleDeCaja confirmar(UserId actorId, UserId aprendizId, FotoDeCaja foto, String ruta) {
        guardia.exigirAdmin(actorId);
        CajaRenaser caja = padron.deAprendiz(aprendizId).caja();
        foto.accion().exigirDesde(caja.estado());
        String propia = FotoDeCaja.exigirRutaDe(aprendizId, ruta);
        boolean yaEsLaVigente = caja.ultimo(foto.paso()).flatMap(p -> p.dato(PasoDeCaja.RUTA))
                .filter(propia::equals).isPresent();
        if (!yaEsLaVigente) {
            long peso = pesoDeLaSubida(propia);
            transaccion.executeWithoutResult(status -> guardar(caja, actorId, foto, propia, peso));
        }
        return lectura.detalle(padron.deAprendiz(aprendizId));
    }

    private long pesoDeLaSubida(String ruta) {
        if (!almacenamiento.guardaObjetos()) {
            throw new IllegalStateException("Este servidor no tiene dónde guardar fotos, así que no se pueden "
                    + "confirmar desde acá.");
        }
        byte[] subida = almacenamiento.leer(ruta, FotoDeCaja.PESO_MAXIMO_EN_BYTES)
                .orElseThrow(() -> new NoSuchElementException("No encontramos la foto subida: vuelve a elegirla."));
        FotoDeCaja.exigirImagen(subida);
        return subida.length;
    }

    private void guardar(CajaRenaser caja, UserId actorId, FotoDeCaja foto, String ruta, long peso) {
        MediaOnboarding media = medias.guardar(MediaOnboarding.registrar(caja.aprendizId(), CajaRenaser.FLUJO,
                foto.clave(), ClaseMedia.FOTO, MediaOnboarding.BUCKET_DEFAULT, ruta, null, peso, null, null, clock));
        pasos.reemplazar(foto.accion().paso(caja, actorId,
                Map.of(PasoDeCaja.MEDIA_ID, String.valueOf(media.id()), PasoDeCaja.RUTA, ruta)));
    }
}

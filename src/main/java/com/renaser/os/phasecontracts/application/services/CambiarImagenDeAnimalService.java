package com.renaser.os.phasecontracts.application.services;

import com.renaser.os.phasecontracts.application.ports.in.animal.AnimalDeFaseVista;
import com.renaser.os.phasecontracts.application.ports.in.animal.CambiarImagenDeAnimalUseCase;
import com.renaser.os.phasecontracts.application.ports.out.animal.AnimalesDeFasePort;
import com.renaser.os.phasecontracts.application.ports.out.animal.ImagenDeAnimalPort;
import com.renaser.os.phasecontracts.domain.model.animal.AnimalDeFase;
import com.renaser.os.phasecontracts.domain.model.animal.ImagenDeAnimal;
import com.renaser.os.phasecontracts.domain.model.contrato.FasePrograma;
import com.renaser.os.shared.application.ports.out.AlmacenamientoPort;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * Cambiar la imagen del animal de una fase (D-258). La imagen la sube el teléfono con URL prefirmada, en el
 * mismo almacenamiento que las evidencias ({@link AlmacenamientoPort}); al confirmar se abre y se revisa
 * (formato, peso, medidas) SIN transacción, y solo entonces se guarda la ruta. Sin almacenamiento de
 * verdad (local, pruebas) confirmar responde 409. No se borran las imágenes reemplazadas: pesan poco.
 */
@Service
class CambiarImagenDeAnimalService implements CambiarImagenDeAnimalUseCase {

    private static final Duration VALIDEZ_URL_SUBIDA = Duration.ofMinutes(10);

    private final GuardiaDeAnimalesDeFase guardia;
    private final AnimalesDeFasePort animales;
    private final ImagenDeAnimalPort imagenes;
    private final AnimalesDeFaseParaMostrar paraMostrar;
    private final AlmacenamientoPort almacenamiento;
    private final IdGenerator idGenerator;
    private final Clock clock;

    CambiarImagenDeAnimalService(GuardiaDeAnimalesDeFase guardia, AnimalesDeFasePort animales,
                                 ImagenDeAnimalPort imagenes, AnimalesDeFaseParaMostrar paraMostrar,
                                 AlmacenamientoPort almacenamiento, IdGenerator idGenerator, Clock clock) {
        this.guardia = guardia;
        this.animales = animales;
        this.imagenes = imagenes;
        this.paraMostrar = paraMostrar;
        this.almacenamiento = almacenamiento;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    @Override
    public UrlDeSubida solicitarSubida(UserId actorId, int fase, String tipoContenido) {
        guardia.exigirQuePuedaCambiarlos(actorId);
        FasePrograma.porNumero(fase);
        ImagenDeAnimal.exigirTipoDeContenido(tipoContenido);
        String ruta = ImagenDeAnimal.PREFIJO_RUTA + fase + "/" + idGenerator.newId();
        return new UrlDeSubida(almacenamiento.firmarSubida(ruta, tipoContenido.strip(), VALIDEZ_URL_SUBIDA), ruta);
    }

    @Override
    public List<AnimalDeFaseVista> confirmar(UserId actorId, int fase, String ruta) {
        guardia.exigirQuePuedaCambiarlos(actorId);
        AnimalDeFase animal = animales.porFase(FasePrograma.porNumero(fase));
        String propia = ImagenDeAnimal.exigirRutaPropia(ruta);
        revisar(propia);
        animal.usarImagen(propia, actorId, clock.now());
        animales.guardar(animal);
        return paraMostrar.todos();
    }

    private void revisar(String ruta) {
        if (!almacenamiento.guardaObjetos()) {
            throw new IllegalStateException("Este servidor no tiene dónde guardar imágenes, así que la imagen "
                    + "no se puede cambiar desde acá.");
        }
        imagenes.revisar(ruta);
    }
}

package com.renaser.os.phasecontracts.domain.model.animal;

import com.renaser.os.phasecontracts.domain.model.contrato.FasePrograma;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;

/**
 * El animal que representa a una fase del programa (D-258): una imagen y un nombre, los dos opcionales.
 * Sin imagen o sin nombre, la app usa los que trae incluidos.
 */
public final class AnimalDeFase {

    private final FasePrograma fase;
    private String nombre;
    private String rutaImagen;
    private UserId actualizadoPor;
    private Instant actualizadoEn;

    private AnimalDeFase(FasePrograma fase, String nombre, String rutaImagen, UserId actualizadoPor,
                         Instant actualizadoEn) {
        this.fase = fase;
        this.nombre = nombre;
        this.rutaImagen = rutaImagen;
        this.actualizadoPor = actualizadoPor;
        this.actualizadoEn = actualizadoEn;
    }

    public static AnimalDeFase sinPersonalizar(FasePrograma fase) {
        return new AnimalDeFase(fase, null, null, null, null);
    }

    public static AnimalDeFase rehidratar(FasePrograma fase, String nombre, String rutaImagen, UserId actualizadoPor,
                                          Instant actualizadoEn) {
        return new AnimalDeFase(fase, nombre, rutaImagen, actualizadoPor, actualizadoEn);
    }

    public void usarImagen(String ruta, UserId actor, Instant ahora) {
        this.rutaImagen = ImagenDeAnimal.exigirRutaPropia(ruta);
        registrar(actor, ahora);
    }

    public void restaurarImagen(UserId actor, Instant ahora) {
        this.rutaImagen = null;
        registrar(actor, ahora);
    }

    /** Un nombre vacío vuelve al que trae la app. */
    public void nombrar(String texto, UserId actor, Instant ahora) {
        this.nombre = NombreDeAnimal.normalizar(texto);
        registrar(actor, ahora);
    }

    public boolean tieneImagenPropia() {
        return rutaImagen != null;
    }

    private void registrar(UserId actor, Instant ahora) {
        this.actualizadoPor = actor;
        this.actualizadoEn = ahora;
    }

    public FasePrograma fase() { return fase; }
    public String nombre() { return nombre; }
    public String rutaImagen() { return rutaImagen; }
    public UserId actualizadoPor() { return actualizadoPor; }
    public Instant actualizadoEn() { return actualizadoEn; }
}

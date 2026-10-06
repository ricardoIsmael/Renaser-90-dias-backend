package com.renaser.os.phasecontracts.application.ports.out.animal;

/** Las imágenes que sube Administración para los animales: abrirlas y revisarlas antes de usarlas. */
public interface ImagenDeAnimalPort {

    /**
     * Trae la imagen subida en {@code ruta} y revisa formato, peso y medidas. Baja el objeto: no se llama
     * dentro de una transacción.
     *
     * @throws IllegalArgumentException si la imagen no sirve, con el motivo en palabras simples
     * @throws java.util.NoSuchElementException si no hay nada subido en esa ruta
     */
    void revisar(String ruta);
}

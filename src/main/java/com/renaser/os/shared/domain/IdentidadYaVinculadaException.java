package com.renaser.os.shared.domain;

/**
 * Esa cuenta del proveedor ({@code (proveedor, sujeto)}) ya esta vinculada a OTRO usuario de
 * Renaser (docs/MODULO_AUTH.md §6.9). Es el vector de apropiacion en el sentido inverso al que
 * cubre §6.4: si vincular fuera libre, alguien que consigue un {@code code} de la cuenta social
 * de otra persona podria colgarla de su propio usuario y entrar como ella para siempre. La
 * {@code UNIQUE (proveedor, sujeto_proveedor)} de la base ya lo impide a nivel de dato; esta
 * excepcion es para decirlo antes y con un mensaje entendible.
 *
 * <p>El mensaje es deliberadamente generico: <b>nunca</b> dice a que cuenta esta vinculada ni
 * con que correo — eso convertiria el endpoint en un oraculo para descubrir de quien es una
 * identidad social.
 */
public class IdentidadYaVinculadaException extends RuntimeException {

    public IdentidadYaVinculadaException(String proveedor) {
        super("Esa cuenta de " + proveedor + " ya esta vinculada a otro usuario");
    }
}

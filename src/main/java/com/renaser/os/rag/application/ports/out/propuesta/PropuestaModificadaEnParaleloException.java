package com.renaser.os.rag.application.ports.out.propuesta;

/**
 * Otra escritura cambio la propuesta entre que se leyo y se quiso guardar: el caso tipico es el
 * doble toque sobre "Confirmar". Parte del contrato de {@link SavePropuestaAccionPort}, para que el
 * caso de uso no tenga que conocer la excepcion de Spring/JPA que la origina.
 */
public class PropuestaModificadaEnParaleloException extends RuntimeException {

    public PropuestaModificadaEnParaleloException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}

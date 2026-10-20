package com.renaser.os.points.application.ports.out.semaforo.mensaje;

/** El azar que elige el mensaje del día. Es un puerto para que las pruebas lo fijen. */
public interface AzarPort {

    /** Un entero uniforme en {@code [0, n)}. */
    int indice(int n);
}

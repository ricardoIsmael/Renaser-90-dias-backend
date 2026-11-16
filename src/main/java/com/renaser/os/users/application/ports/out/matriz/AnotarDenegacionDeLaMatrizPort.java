package com.renaser.os.users.application.ports.out.matriz;

/**
 * Deja, de forma durable, cuántas veces la matriz de MENTOR y TRAINEE_GUIDE negó (o habría negado) cada endpoint ese día (spec 050,
 * tramo H, FR-041): el registro de Docker solo vive desde el último despliegue y la sombra dura semanas. Sin la identidad de la
 * cuenta. Es un registro auxiliar: si falla, el pedido sigue.
 */
public interface AnotarDenegacionDeLaMatrizPort {

    void anotar(DenegacionDeLaMatriz denegacion);
}

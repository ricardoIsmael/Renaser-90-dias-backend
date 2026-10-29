package com.renaser.os;

import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.AbstractTestExecutionListener;

/**
 * Deja el {@link RelojDePrueba} en hora real al terminar cada prueba, para que ninguna prueba herede
 * el reloj movido de la anterior en un contexto compartido. Se registra solo, para toda prueba de
 * Spring, en {@code META-INF/spring.factories} de las pruebas.
 *
 * <p>Corre después del {@code @AfterEach} de la propia prueba (las limpiezas que consultan la hora
 * la siguen viendo fija) y no fuerza a cargar un contexto que la prueba no usó.
 */
public class RestablecerRelojDePrueba extends AbstractTestExecutionListener {

    /** Alto a propósito: en la fase «después» los oyentes corren de mayor a menor orden. */
    private static final int ORDEN = 6000;

    @Override
    public int getOrder() {
        return ORDEN;
    }

    @Override
    public void afterTestMethod(TestContext contexto) {
        if (!contexto.hasApplicationContext()) {
            return;
        }
        contexto.getApplicationContext().getBeanProvider(RelojDePrueba.class)
                .ifAvailable(RelojDePrueba::restablecer);
    }
}

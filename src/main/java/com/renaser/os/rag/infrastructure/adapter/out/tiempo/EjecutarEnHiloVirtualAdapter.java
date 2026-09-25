package com.renaser.os.rag.infrastructure.adapter.out.tiempo;

import com.renaser.os.rag.application.ports.out.memoria.EjecutarEnSegundoPlanoPort;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Un hilo virtual por tarea (D-167): la compactacion de la memoria espera al modelo varios segundos
 * sin ocupar un hilo del sistema ni la conexion del pedido que ya se respondio. Al apagar el backend
 * no se aceptan tareas nuevas; lo que estaba a mitad se pierde y se rehace en otro turno.
 */
@Component
class EjecutarEnHiloVirtualAdapter implements EjecutarEnSegundoPlanoPort, DisposableBean {

    private final ExecutorService hilos = Executors.newVirtualThreadPerTaskExecutor();

    @Override
    public void ejecutar(Runnable tarea) {
        hilos.execute(tarea);
    }

    @Override
    public void destroy() {
        hilos.shutdown();
    }
}

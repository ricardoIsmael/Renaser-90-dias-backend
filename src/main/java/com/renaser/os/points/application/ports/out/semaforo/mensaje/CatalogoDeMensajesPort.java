package com.renaser.os.points.application.ports.out.semaforo.mensaje;

import com.renaser.os.points.domain.model.semaforo.mensaje.GrupoDelMensaje;
import com.renaser.os.points.domain.model.semaforo.mensaje.MensajeDelSemaforo;

import java.util.List;
import java.util.Optional;

/**
 * El catálogo de mensajes del semáforo ({@code mensajes_semaforo}, D-277). Es corto (~115 filas) y solo cambia con una
 * migración o una corrección a mano, así que quien lo implementa puede tenerlo en memoria: un cambio puede tardar en
 * verse lo que dure esa copia ({@link #recargar()} la descarta).
 */
public interface CatalogoDeMensajesPort {

    /** Los mensajes activos de un grupo, ordenados por id: el azar elige por posición y tiene que significar lo mismo siempre. */
    List<MensajeDelSemaforo> activosDe(GrupoDelMensaje grupo);

    /** Un mensaje por su id, activo o no: el que se eligió hoy se sigue mostrando aunque lo desactiven después. */
    Optional<MensajeDelSemaforo> porId(String id);

    /**
     * Descarta la copia en memoria: la próxima consulta vuelve a leer el catálogo. Para quien corrige filas a mano y
     * no quiere esperar a que la copia venza, y para las pruebas que cambian el catálogo.
     */
    void recargar();
}

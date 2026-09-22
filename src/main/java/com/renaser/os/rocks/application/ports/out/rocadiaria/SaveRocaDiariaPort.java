package com.renaser.os.rocks.application.ports.out.rocadiaria;

import com.renaser.os.rocks.domain.model.rocadiaria.RocaDiaria;
import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.util.List;

public interface SaveRocaDiariaPort {

    RocaDiaria save(RocaDiaria rocaDiaria);

    List<RocaDiaria> saveAll(List<RocaDiaria> rocasDiarias);

    /**
     * Borra el plan de ese dia. <b>Existe solo para reemplazarlo por uno nuevo</b>, y por eso vive
     * en el puerto de escritura y no en uno propio: no hay ningun caso de uso de "borrar el dia".
     *
     * <p><b>Quien llama tiene que haber verificado que la fecha todavia no llego.</b> Un dia ya
     * vivido puede tener evidencia subida y puntos otorgados, y borrarlo seria borrar el trabajo de
     * una persona — {@code RocaDiariaService} solo lo invoca para fechas posteriores a hoy.
     */
    void borrarDeParticipanteYFecha(UserId participanteId, LocalDate fecha);
}

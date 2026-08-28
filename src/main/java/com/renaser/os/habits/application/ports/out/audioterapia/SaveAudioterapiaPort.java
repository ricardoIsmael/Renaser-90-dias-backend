package com.renaser.os.habits.application.ports.out.audioterapia;

import com.renaser.os.habits.application.ports.out.audioterapia.AudioterapiaCatalogPort.Audioterapia;

import java.util.NoSuchElementException;

public interface SaveAudioterapiaPort {

    /** @throws NoSuchElementException si no existe ninguna audioterapia con esa semana */
    Audioterapia actualizarDuracion(int semana, int duracionDias);
}

package com.renaser.os.rag.application.ports.in.analisis;

/**
 * El barrido del análisis 80/20 (spec 031): genera el de cada semana y cada mes que, en la zona de cada persona, ya cerró. Se invoca cada
 * hora y es seguro repetirlo: decide por persona y periodo. No se expone por REST; solo lo invoca su scheduler.
 */
public interface GenerarAnalisisPendientesUseCase {

    void generarLosQueTocan();
}

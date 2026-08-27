package com.renaser.os.habits.application.ports.in.guiaadmin;

import com.renaser.os.habits.domain.model.guia.AdjuntoGuia;
import com.renaser.os.habits.domain.model.guia.GuiaHabito;

import java.util.List;

/**
 * Proyeccion de lectura para el panel admin: una guia con sus adjuntos ya resueltos
 * (el frontend los espera anidados dentro del GET de guias — `HabitGuide.attachments`,
 * ver `habitsAdmin.ts`). Ensamblada en lote por {@code GuiaHabitoAdminService}, nunca N+1.
 */
public record GuiaConAdjuntos(GuiaHabito guia, List<AdjuntoGuia> adjuntos) {
}

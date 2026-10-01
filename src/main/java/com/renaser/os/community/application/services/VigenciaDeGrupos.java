package com.renaser.os.community.application.services;

import com.renaser.os.community.application.ports.out.acompanamiento.LoadPoliticaMentoriaPort;
import com.renaser.os.community.domain.model.acompanamiento.PoliticaMentoria;
import com.renaser.os.community.domain.model.celula.Celula;
import com.renaser.os.community.domain.model.cohorte.CohorteId;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * La UNICA respuesta a «¿este grupo está en curso hoy?» (D-240).
 *
 * <p>En curso = el día de hoy, en la zona de la política de SU cohorte, cae dentro del periodo del
 * grupo ({@link Celula#vigenteEn}); un grupo sin periodo siempre lo está. Ni cerrado ni programado.
 *
 * <p><b>Por qué existe.</b> Hasta el 2026-10-01 había dos reglas: el acceso (chat, padrón, semana del
 * aprendiz) exigía «en curso» con la zona de la cohorte, y las listas (`/me/cell`, `/me/cells`, el
 * contexto del mentor) ocultaban solo los cerrados, con la zona fija de Lima. Un grupo programado se
 * listaba y al abrirlo daba 403 (E-477), y el traslado automático elegía grupos cerrados o todavía
 * no empezados (E-476). Lo que se lista, lo que se puede abrir y adonde se traslada a alguien sale
 * ahora de acá.
 *
 * <p>Clase simple y no bean: la construye cada servicio con el puerto de políticas que ya tenía, así
 * que ningún constructor tuvo que cambiar para sumarla.
 */
final class VigenciaDeGrupos {

    private final LoadPoliticaMentoriaPort politicas;

    VigenciaDeGrupos(LoadPoliticaMentoriaPort politicas) {
        this.politicas = politicas;
    }

    /** Si el grupo está corriendo en ese instante, mirado en el día de su cohorte. */
    boolean enCurso(Celula celula, Instant instante) {
        return celula.vigenteEn(hoyDe(celula, instante));
    }

    /** El día de ese instante para el grupo: en la zona de su cohorte, no la del servidor (E-91). */
    LocalDate hoyDe(Celula celula, Instant instante) {
        return instante.atZone(zonaDe(celula.cohorteId())).toLocalDate();
    }

    ZoneId zonaDe(CohorteId cohorteId) {
        return ZoneId.of(politicas.porCohorte(cohorteId)
                .orElseGet(() -> PoliticaMentoria.porDefecto(cohorteId))
                .zonaHoraria());
    }
}

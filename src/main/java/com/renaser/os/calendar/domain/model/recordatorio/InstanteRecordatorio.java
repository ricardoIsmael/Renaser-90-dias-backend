package com.renaser.os.calendar.domain.model.recordatorio;

import com.renaser.os.calendar.domain.model.evento.ReglaRecordatorio;

import java.time.Instant;

/** Un instante de disparo ya resuelto — reminderInstantsFor() del repo viejo. */
public record InstanteRecordatorio(Instant enviarEn, Instant inicioOcurrencia, ReglaRecordatorio regla) {
}

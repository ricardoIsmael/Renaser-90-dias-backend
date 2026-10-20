package com.renaser.os.mentoring.api;

import java.time.LocalDate;
import java.util.List;

/** Un día de la semana de seguimiento, siempre presente aunque no tenga obligaciones. */
public record DiaDelAlumno(LocalDate fecha, Integer diaDePrograma, List<ObligacionDelDia> obligaciones) {
}

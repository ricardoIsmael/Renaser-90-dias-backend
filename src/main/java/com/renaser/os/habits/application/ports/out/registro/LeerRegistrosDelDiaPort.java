package com.renaser.os.habits.application.ports.out.registro;

import com.renaser.os.habits.api.CumplimientoDelDiaFinder.RegistroDelDia;
import com.renaser.os.shared.domain.AlcanceDeFormacion;
import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/** Los registros de hábitos del catálogo de varias personas en un día, en una sola consulta (spec 029). */
public interface LeerRegistrosDelDiaPort {

    List<RegistroDelDia> deVariasPersonas(Collection<UserId> personas, LocalDate fecha, AlcanceDeFormacion alcance);
}

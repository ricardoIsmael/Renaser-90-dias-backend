package com.renaser.os.habits.application.ports.out.preferencia;

import com.renaser.os.habits.domain.model.preferencia.PreferenciaHorario;

public interface SavePreferenciaHorarioPort {

    PreferenciaHorario save(PreferenciaHorario preferencia);
}

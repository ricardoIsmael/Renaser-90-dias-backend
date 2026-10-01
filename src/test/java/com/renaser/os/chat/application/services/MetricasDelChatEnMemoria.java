package com.renaser.os.chat.application.services;

import com.renaser.os.chat.application.ports.out.metricas.RegistrarMetricaDelChatPort;
import com.renaser.os.chat.domain.model.mensaje.TipoMensaje;

import java.util.ArrayList;
import java.util.List;

/** Las metricas del chat que registro el servicio, para mirarlas en las pruebas (D-237). */
public class MetricasDelChatEnMemoria implements RegistrarMetricaDelChatPort {

    public final List<TipoMensaje> subidasSolicitadas = new ArrayList<>();
    public final List<TipoMensaje> mediaEnviada = new ArrayList<>();

    @Override
    public void subidaDeMediaSolicitada(TipoMensaje tipo) {
        subidasSolicitadas.add(tipo);
    }

    @Override
    public void mediaEnviada(TipoMensaje tipo) {
        mediaEnviada.add(tipo);
    }
}

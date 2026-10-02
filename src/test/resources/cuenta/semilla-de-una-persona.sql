-- Una persona con al menos una fila en cada tabla de los módulos que implementan BorradoDeDatosDeCuenta
-- (D-243). {{P}} es su UUID. Los ids auxiliares se derivan con md5('{{P}}-algo')::uuid para que dos
-- personas sembradas en la misma base no choquen. La usa BorradoDeCuentaEnLosModulosIT.

INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
VALUES ('{{P}}', '{{P}}@renaser.test', 'Persona {{P}}', 'APRENDIZ', 'ACTIVO');
INSERT INTO renaser.participantes_programa (usuario_id, dia_programa, timezone, fecha_inicio)
VALUES ('{{P}}', 10, 'America/Lima', CURRENT_DATE - 9);
INSERT INTO renaser.perfiles_mentor (usuario_id) VALUES ('{{P}}');

-- community
INSERT INTO renaser.cohortes (id, nombre, fecha_inicio) VALUES (md5('{{P}}-coh')::uuid, 'C {{P}}', CURRENT_DATE - 9);
INSERT INTO renaser.celulas (id, nombre, cohorte_id, mentor_id) VALUES (md5('{{P}}-cel')::uuid, 'G {{P}}', md5('{{P}}-coh')::uuid, '{{P}}');
INSERT INTO renaser.asignaciones_celula (id, celula_id, usuario_id, funcion, inicio, motivo, actor_id, clave_operacion)
VALUES (md5('{{P}}-asig')::uuid, md5('{{P}}-cel')::uuid, '{{P}}', 'APRENDIZ', now() - interval '9 days', 'ADMINISTRATIVO', '{{P}}', '{{P}}-asig');
INSERT INTO renaser.publicaciones_muro (id, autor_id, texto, categoria_clave) VALUES (md5('{{P}}-pub')::uuid, '{{P}}', 'Hola', 'AGRADECIMIENTO');
INSERT INTO renaser.medias_publicacion (id, publicacion_id, bucket, ruta_storage, mime)
VALUES (md5('{{P}}-media')::uuid, md5('{{P}}-pub')::uuid, 'renaser', 'muro/fotos/{{P}}/foto.jpg', 'image/jpeg');
INSERT INTO renaser.comentarios_muro (id, publicacion_id, autor_id, texto) VALUES (md5('{{P}}-com')::uuid, md5('{{P}}-pub')::uuid, '{{P}}', 'Bien');
INSERT INTO renaser.reacciones_muro (publicacion_id, usuario_id, tipo) VALUES (md5('{{P}}-pub')::uuid, '{{P}}', 'ME_GUSTA');
INSERT INTO renaser.testimonios (id, usuario_id, publicacion_muro_id, nombre, foto_evento_ruta, texto, estrellas)
VALUES (md5('{{P}}-tes')::uuid, '{{P}}', md5('{{P}}-pub')::uuid, 'Persona', 'muro/fotos/{{P}}/foto.jpg', 'Me cambió', 5);
INSERT INTO renaser.anomalias_acompanamiento (origen, clase, celula_id, usuario_id, detalle)
VALUES ('PRUEBA', 'PRUEBA', md5('{{P}}-cel')::uuid, '{{P}}', 'x');

-- chat
INSERT INTO renaser.conversaciones (id, tipo, clave_directa) VALUES (md5('{{P}}-sop')::uuid, 'SOPORTE', 'soporte:{{P}}');
INSERT INTO renaser.participantes_conversacion (conversacion_id, usuario_id) VALUES (md5('{{P}}-sop')::uuid, '{{P}}');
INSERT INTO renaser.mensajes (id, conversacion_id, emisor_id, tipo, texto, media_bucket, media_ruta)
VALUES (md5('{{P}}-msg')::uuid, md5('{{P}}-sop')::uuid, '{{P}}', 'IMAGEN', 'hola', 'renaser', 'chat/{{P}}/foto.jpg');
INSERT INTO renaser.mensajes_bienvenida (usuario_destinatario_id, mensaje_id) VALUES ('{{P}}', md5('{{P}}-msg')::uuid);
INSERT INTO renaser.cambios_bienvenida (pieza, texto, cambiado_por) VALUES ('GRUPO', 'Bienvenida', '{{P}}');

-- calendar
INSERT INTO renaser.eventos (id, titulo, inicia_en, timezone, tipo_ubicacion, tipo_audiencia, tipo_evento, creado_por)
VALUES (md5('{{P}}-ev')::uuid, 'Evento', now() + interval '1 day', 'America/Lima', 'ZOOM', 'TODOS', 'ESPONTANEO', '{{P}}');
INSERT INTO renaser.confirmaciones_evento (evento_id, inicio_ocurrencia, usuario_id, estado)
VALUES (md5('{{P}}-ev')::uuid, now() + interval '1 day', '{{P}}', 'ASISTE');
INSERT INTO renaser.recordatorios_evento (evento_id, inicio_ocurrencia, usuario_id, enviar_en)
VALUES (md5('{{P}}-ev')::uuid, now() + interval '1 day', '{{P}}', now());

-- notifications
INSERT INTO renaser.notificaciones (usuario_id, tipo, titulo, cuerpo) VALUES ('{{P}}', 'ANUNCIO_SISTEMA', 'T', 'C');
INSERT INTO renaser.preferencias_notificacion (usuario_id, tipo) VALUES ('{{P}}', 'ANUNCIO_SISTEMA');
INSERT INTO renaser.tokens_push (usuario_id, token) VALUES ('{{P}}', 'token-{{P}}');

-- support
INSERT INTO renaser.tickets_soporte (usuario_id, categoria, asunto, mensaje, adjunto_bucket, adjunto_ruta)
VALUES ('{{P}}', 'TECNICO', 'A', 'M', 'renaser', 'soporte/{{P}}/adjunto.png');
INSERT INTO renaser.tickets_mentor (participante_id, descripcion_bloqueo, soluciones_intentadas, impacto_meta_smart, estado, respuesta_mentor, respondido_por)
VALUES ('{{P}}', 'b', 's', 'i', 'RESPONDIDO', 'r', '{{P}}');

-- leadership
INSERT INTO renaser.observaciones_mentor (id, mentor_id, autor_id, tipo, texto, clave_operacion, creado_en)
VALUES (md5('{{P}}-obs')::uuid, '{{P}}', '{{P}}', 'SUGERENCIA', 'texto', '{{P}}-obs', now());

-- academy
INSERT INTO renaser.asignaciones_curso (curso_id, usuario_id, asignada_por)
VALUES ((SELECT min(id) FROM renaser.cursos), '{{P}}', '{{P}}');
INSERT INTO renaser.progreso_lecciones (usuario_id, leccion_id) VALUES ('{{P}}', (SELECT min(id) FROM renaser.lecciones));
INSERT INTO renaser.grupos (nombre) VALUES ('grupo {{P}}');
INSERT INTO renaser.miembros_grupo (grupo_id, usuario_id) VALUES ((SELECT max(id) FROM renaser.grupos WHERE nombre = 'grupo {{P}}'), '{{P}}');
INSERT INTO renaser.recomendaciones_academia (participante_id, fecha, leccion_id, motivo)
VALUES ('{{P}}', CURRENT_DATE, (SELECT min(id) FROM renaser.lecciones), 'PRUEBA');

-- rag
INSERT INTO renaser.conversaciones_renasia (usuario_id) VALUES ('{{P}}');
INSERT INTO renaser.mensajes_renasia (id, usuario_id, rol, contenido) VALUES (md5('{{P}}-mr')::uuid, '{{P}}', 'USUARIO', 'hola');
INSERT INTO renaser.fuentes_mensaje_renasia (mensaje_id, leccion_id) VALUES (md5('{{P}}-mr')::uuid, (SELECT min(id) FROM renaser.lecciones));
INSERT INTO renaser.memorias_renasia (participante_id, resumen, compactado_hasta, actualizado_en) VALUES ('{{P}}', 'r', now(), now());
INSERT INTO renaser.recuerdos_renasia (id, participante_id, categoria, texto, creado_en) VALUES (md5('{{P}}-rec-ia')::uuid, '{{P}}', 'CONTEXTO_DE_VIDA', 't', now());
INSERT INTO renaser.agenda_ocupada (id, participante_id, dia_semana, desde_minuto, hasta_minuto) VALUES (md5('{{P}}-ag')::uuid, '{{P}}', 1, 60, 120);
INSERT INTO renaser.informes_espejo_sombra (id, participante_id, semana_inicio, cantidad_entradas, patron_dominante, pct_pasado, pct_presente, pct_futuro, insight)
VALUES (md5('{{P}}-inf')::uuid, '{{P}}', CURRENT_DATE, 3, 'PASADO', 50, 30, 20, 'x');
INSERT INTO renaser.preguntas_confrontacion (informe_id, orden, pregunta) VALUES (md5('{{P}}-inf')::uuid, 1, '¿?');
INSERT INTO renaser.propuestas_acompanante (id, participante_id, herramienta, argumentos, argumentos_hash, resumen, estado, creada_en, vence_en)
VALUES (md5('{{P}}-prop')::uuid, '{{P}}', 'h', '{}'::jsonb, 'hash-{{P}}', 'r', 'PENDIENTE', now(), now() + interval '1 hour');

-- onboarding
INSERT INTO renaser.medias_onboarding (usuario_id, clase, bucket, ruta_storage)
VALUES ('{{P}}', 'FOTO', 'renaser', 'onboarding/{{P}}/caja/foto.jpg');
INSERT INTO renaser.respuestas_onboarding (usuario_id, pregunta_id, valor_texto, media_id)
VALUES ('{{P}}', (SELECT min(id) FROM renaser.preguntas_onboarding), 'x',
        (SELECT id FROM renaser.medias_onboarding WHERE usuario_id = '{{P}}'));
INSERT INTO renaser.grabaciones_v90 (usuario_id, fase, eje, indice, media_id)
VALUES ('{{P}}', 'INICIO', 'CUERPO', 1, (SELECT id FROM renaser.medias_onboarding WHERE usuario_id = '{{P}}'));
INSERT INTO renaser.estado_onboarding (usuario_id) VALUES ('{{P}}');
INSERT INTO renaser.etapas_onboarding_completadas (usuario_id, flujo, marcada_por) VALUES ('{{P}}', 'caja:1:ENVIADA', '{{P}}');
INSERT INTO renaser.acciones_mapa (id, usuario_id, accion_id, area, texto, frecuencia_semanal)
VALUES (md5('{{P}}-am')::uuid, '{{P}}', 'a1', 'salud', 'caminar diez minutos', 3);
INSERT INTO renaser.dias_accion_mapa (accion_mapa_id, dia_semana) VALUES (md5('{{P}}-am')::uuid, 1);
INSERT INTO renaser.protocolos_reemplazo_mapa (id, usuario_id, protocolo_id, patron, disparador, conducta_actual, respuesta_alternativa)
VALUES (md5('{{P}}-pr')::uuid, '{{P}}', 'p1', 'pa', 'di', 'co', 're');

-- phasecontracts
INSERT INTO renaser.contratos_fase (participante_id, fase, bucket, ruta_firma)
VALUES ('{{P}}', 'FASE_1_RENACER', 'renaser', 'firmas/{{P}}/fase1.png');

-- habits (y lo que de ellos cuelga en evidence y rocks)
INSERT INTO renaser.habitos (id, ambito, participante_id, titulo, categoria_clave)
VALUES (md5('{{P}}-hab')::uuid, 'PERSONAL', '{{P}}', 'Mi hábito', 'CUERPO');
INSERT INTO renaser.horarios_habito (id, habito_id, dia_inicio) VALUES (md5('{{P}}-hh')::uuid, md5('{{P}}-hab')::uuid, 1);
INSERT INTO renaser.guias_habito (id, habito_id) VALUES (md5('{{P}}-guia')::uuid, md5('{{P}}-hab')::uuid);
INSERT INTO renaser.adjuntos_guia (id, guia_id, seccion, tipo_medio, ruta_storage)
VALUES (md5('{{P}}-adj')::uuid, md5('{{P}}-guia')::uuid, 'QUE_HACER', 'IMAGEN', 'guias/{{P}}/adjunto.pdf');
INSERT INTO renaser.entradas_diario (id, participante_id, fecha, tipo, audio_bucket, audio_ruta)
VALUES (md5('{{P}}-ed')::uuid, '{{P}}', CURRENT_DATE, 'BITACORA_NOCTURNA', 'renaser', 'bitacora/{{P}}/audio.m4a');
INSERT INTO renaser.registros_habito (id, participante_id, habito_id, fecha_ejecucion, dia_programa, tipo_dia, entrada_diario_id)
VALUES (md5('{{P}}-rh')::uuid, '{{P}}', md5('{{P}}-hab')::uuid, CURRENT_DATE, 10, 'DISCIPLINA', md5('{{P}}-ed')::uuid);
INSERT INTO renaser.sesiones_bloqueo (registro_habito_id, iniciada_en, duracion_minima_min, evidencia_salida_bucket, evidencia_salida_ruta)
VALUES (md5('{{P}}-rh')::uuid, now(), 30, 'renaser', 'santuario/{{P}}/salida.jpg');
INSERT INTO renaser.rachas_sin_celular (id, registro_habito_id, participante_id, iniciada_en, horas_objetivo)
VALUES (md5('{{P}}-racha')::uuid, md5('{{P}}-rh')::uuid, '{{P}}', now(), 24);
INSERT INTO renaser.revisiones_semanales_sin_celular (participante_id, semana_inicio, rachas_completas, rachas_requeridas, puntos_penalizacion)
VALUES ('{{P}}', CURRENT_DATE, 1, 1, 0);
INSERT INTO renaser.registros_espiritu (id, participante_id, dia, desbloqueado_en, fecha_limite)
VALUES (md5('{{P}}-re')::uuid, '{{P}}', (SELECT min(dia) FROM renaser.audios_espiritu), now(), CURRENT_DATE + 1);
INSERT INTO renaser.registros_radar (id, participante_id, que_hago, que_pienso, que_siento, nivel_energia, que_evito)
VALUES (md5('{{P}}-rr')::uuid, '{{P}}', 'a', 'b', 'c', 5, 'd');
INSERT INTO renaser.desbloqueos_habito (participante_id, habito_id, dia_desbloqueo) VALUES ('{{P}}', md5('{{P}}-hab')::uuid, 1);
INSERT INTO renaser.dias_semanales_habito (participante_id, habito_id, fecha_ejecucion, semana_inicio) VALUES ('{{P}}', md5('{{P}}-hab')::uuid, CURRENT_DATE, CURRENT_DATE);
INSERT INTO renaser.horario_semanal_habito (participante_id, habito_id, dia_semana, activo) VALUES ('{{P}}', md5('{{P}}-hab')::uuid, 1, false);
INSERT INTO renaser.horarios_habito_por_fecha (participante_id, habito_id, fecha, recordatorio_activo, creado_en, actualizado_en, activo) VALUES ('{{P}}', md5('{{P}}-hab')::uuid, CURRENT_DATE, false, now(), now(), false);
INSERT INTO renaser.preferencias_horario (participante_id, habito_id) VALUES ('{{P}}', md5('{{P}}-hab')::uuid);
INSERT INTO renaser.cambios_horario_pendientes (participante_id, habito_id, fecha_efectiva) VALUES ('{{P}}', md5('{{P}}-hab')::uuid, CURRENT_DATE + 1);
INSERT INTO renaser.historial_cambios_horario (participante_id, habito_id, cambiado_el) VALUES ('{{P}}', md5('{{P}}-hab')::uuid, CURRENT_DATE);
INSERT INTO renaser.renombres_habito (participante_id, habito_id, titulo_personal, motivo) VALUES ('{{P}}', md5('{{P}}-hab')::uuid, 'Otro nombre', 'porque sí');

-- rocks
INSERT INTO renaser.rocas_maestras (id, participante_id, eje, objetivo) VALUES (md5('{{P}}-rm')::uuid, '{{P}}', 'CUERPO', 'o');
INSERT INTO renaser.rocas_semanales (id, roca_maestra_id, numero_semana, titulo) VALUES (md5('{{P}}-rs')::uuid, md5('{{P}}-rm')::uuid, 1, 't');
INSERT INTO renaser.rocas_mensuales (id, roca_maestra_id, numero_mes, titulo) VALUES (md5('{{P}}-rme')::uuid, md5('{{P}}-rm')::uuid, 1, 't');
INSERT INTO renaser.rocas_diarias (id, participante_id, fecha, posicion, titulo, color, puntaje_impacto, eje, roca_semanal_id)
VALUES (md5('{{P}}-rd')::uuid, '{{P}}', CURRENT_DATE, 1, 't', 'VERDE', 5, 'CUERPO', md5('{{P}}-rs')::uuid);
INSERT INTO renaser.acciones_diarias (roca_diaria_id, orden, descripcion) VALUES (md5('{{P}}-rd')::uuid, 1, 'a');
INSERT INTO renaser.eventos_verdugo (id, participante_id, roca_diaria_id, disparado_en)
VALUES (md5('{{P}}-ver')::uuid, '{{P}}', md5('{{P}}-rd')::uuid, now());

-- evidence
INSERT INTO renaser.evidencias (id, participante_id, registro_habito_id, tipo, bucket, ruta_storage)
VALUES (md5('{{P}}-evi')::uuid, '{{P}}', md5('{{P}}-rh')::uuid, 'FOTO', 'renaser', 'evidencia-habitos/{{P}}/foto.jpg');
INSERT INTO renaser.evidencias (id, participante_id, roca_diaria_id, tipo, bucket, ruta_storage)
VALUES (md5('{{P}}-evi2')::uuid, '{{P}}', md5('{{P}}-rd')::uuid, 'FOTO', 'renaser', 'rocas/{{P}}/foto.jpg');
INSERT INTO renaser.evidencias (id, participante_id, registro_espiritu_id, tipo, contenido_texto)
VALUES (md5('{{P}}-evi3')::uuid, '{{P}}', md5('{{P}}-re')::uuid, 'TEXTO', 'mi espíritu');

-- points
INSERT INTO renaser.ranking_aprendices (fecha, tipo, participante_id, posicion, puntaje) VALUES (CURRENT_DATE, 'GENERAL', '{{P}}', 1, 50);
INSERT INTO renaser.puntajes_participante (participante_id) VALUES ('{{P}}');
INSERT INTO renaser.ajustes_puntos_liga (participante_id, motivo, delta, delta_aplicado, saldo_posterior)
VALUES ('{{P}}', 'AJUSTE_MANUAL', 5, 5, 5);
INSERT INTO renaser.historial_coherencia (participante_id, fecha, valor) VALUES ('{{P}}', CURRENT_DATE, 50);
INSERT INTO renaser.semaforo_dias (participante_id, fecha, habitos_programados, habitos_cumplidos, objetivos_programados, objetivos_cumplidos, calculado_en)
VALUES ('{{P}}', CURRENT_DATE, 1, 1, 1, 1, now());
INSERT INTO renaser.semaforo_pausas (id, usuario_id, desde, creada_en, motivo) VALUES (md5('{{P}}-sp')::uuid, '{{P}}', CURRENT_DATE, now(), 'CUENTA_SUSPENDIDA');
INSERT INTO renaser.semaforo_semanas (participante_id, semana_hasta, semana_desde, porcentaje, dias_con_datos, dias_medidos, version_formula, cerrada_en)
VALUES ('{{P}}', date_trunc('week', CURRENT_DATE)::date + 4, date_trunc('week', CURRENT_DATE)::date - 2, 50, 7, 7, 'v1', now());

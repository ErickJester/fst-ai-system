# 11 · Selección del trabajo terminal (pruebas de backend con PostgreSQL)

**Qué es:** una selección de pruebas marcadas con la etiqueta `@Tag("trabajo-terminal")`. No se borró ninguna prueba: el resto de la suite sigue igual. El trabajo terminal documenta solo las seleccionadas.

**Qué se incluye:** pruebas del backend con PostgreSQL real, más 49 pruebas nuevas que se etiquetaron a nivel de clase. La regla de «solo PostgreSQL» tiene una excepción en un grupo: el de la regla del último Administrador, donde por instrucción del orquestador se incluyen 2 pruebas que corren sin base (`ReglaAdministradoresTest`).

**Cómo se corre:**
- `./mvnw -o test -Dgroups=trabajo-terminal`: solo las seleccionadas. Sin base de datos, 75 se omiten y 2 corren.
- `./mvnw -o test`: sin cambio; corre todo como antes.

**Cómo se contó:** con la corrida `-Dgroups=trabajo-terminal`, en los reportes de surefire: **77 seleccionadas** (49 de clase + 28 de método), 77 pruebas en el reporte, 0 fallos, 0 errores.

| Tipo de etiqueta | Pruebas |
|---|---|
| Por clase: `CrearEliminarSubirTest` | 16 |
| Por clase: `PlazoDeRecuperacionTest` | 4 |
| Por clase: `RestriccionesDeLaBaseTest` | 29 |
| Por método, con PostgreSQL | 26 |
| Por método, sin base | 2 |
| **Total** | **77** |

---

## Por área

### Cuentas y sesión

| Caso comprobado | Pruebas |
|---|---|
| Regla del último Administrador: quitar al único Administrador activo no se puede, y con otro activo sí (sin base) | 2 |
| Regla del último Administrador con PostgreSQL: el último Administrador activo no se desactiva ni se le quita el rol (409) | 1 |
| Autoprotección: el Administrador no puede darse una temporal a sí mismo (409) | 1 |
| Un investigador recibe 403 en todo `/admin` | 1 |
| Con temporal, cambiar la contraseña exige aceptar el aviso y los términos; sin aceptación no cambia nada | 1 |
| Con temporal, solo sirven el estado público de salud y el cierre de sesión | 1 |
| 409 por identificador o correo repetidos | 1 |
| Editar una cuenta cambia datos y rol, y permite desactivarla | 1 |
| El enlace de recuperación sirve una sola vez | 1 |
| Un enlace vencido no sirve, ni uno que se usó por otro camino | 1 |
| *Subtotal de cuentas y sesión* | **11** |

### Recuperación de contraseña (plazo del enlace)

| Caso comprobado | Pruebas |
|---|---|
| El enlace dura exactamente lo que marca `app.reset.minutos` (60 min) | 1 |
| El enlace sirve dentro de su plazo y solo una vez | 1 |
| Un enlace emitido hace 59 min todavía sirve (firmado con la fecha pasada, no con el reloj) | 1 |
| Un enlace emitido hace 62 min ya no sirve y la contraseña no cambia (igual, firmado) | 1 |
| *Subtotal de recuperación* | **4** |

Nota: en el área de cuentas, la regla del último Administrador y la autoprotección suman 4 de las 8 pruebas del grupo (ver nota al pie).

### Experimentos y carga de video

| Caso comprobado | Pruebas |
|---|---|
| La lista se filtra por estado | 1 |
| El aviso de vencimiento a 7 días aparece en el resumen | 1 |
| Crear, eliminar y subir video (16 pruebas nuevas): ver la tabla de clase | 16 |
| *Subtotal de experimentos* | **18** |

### Pausa

| Caso comprobado | Pruebas |
|---|---|
| Con pausa, el estado trae el cilindro, el segundo y el plazo que queda | 1 |
| *Subtotal de pausa* | **1** |

### Resultados y reportes

| Caso comprobado | Pruebas |
|---|---|
| Los bloques de cada espécimen tienen la forma de la guía | 1 |
| Comparación entre grupos, en orden, con faltantes y estadísticos | 1 |
| El CSV del grupo trae columnas, filas y estadísticos | 1 |
| El XLSX trae los mismos datos con números de verdad | 1 |
| El Excel del experimento tiene la hoja de resultados y la de segundo a segundo | 1 |
| Los totales traen las cinco claves, con «Sin ver», y suman 100 % | 1 |
| *Subtotal de resultados* | **6** |

### Revisión segundo a segundo

| Caso comprobado | Pruebas |
|---|---|
| Permiso temporal de video: lo piden quien subió el video y el Administrador; otra cuenta da 403 | 1 |
| Una URL con la sesión (JWT) en lugar del token de video se rechaza | 1 |
| Corregir segundos: solo se guardan los que llegan y el resto queda igual | 1 |
| Corregir una conducta activa sube el nivel a agrupado; quitarla lo baja a preciso | 1 |
| Reiniciar: el análisis queda en cola sin resultados, reportes ni archivos viejos | 1 |
| *Subtotal de revisión* | **5** |

### Notificaciones y concurrencia

| Caso comprobado | Pruebas |
|---|---|
| El contador de no leídas cuenta todas las notificaciones de la cuenta | 1 |
| Dos reinicios a la vez: solo pasa uno | 1 |
| El candado de la cuenta deja pasar la notificación del worker mientras el `FOR UPDATE` falla por tiempo de espera | 1 |
| *Subtotal de notificaciones y concurrencia* | **3** |

### Restricciones de la base (integridad)

| Caso comprobado | Pruebas |
|---|---|
| Restricciones de la base de datos: 29 casos (ver la lista de clase) | 29 |
| *Subtotal de restricciones* | **29** |

---

## Total

| Área | Pruebas |
|---|---|
| Cuentas y sesión (incluye 2 pruebas sin base de la regla del último Administrador) | 11 |
| Recuperación de contraseña (plazo) | 4 |
| Experimentos y carga de video (incluye las 16 de crear, eliminar y subir) | 18 |
| Pausa | 1 |
| Resultados y reportes | 6 |
| Revisión segundo a segundo | 5 |
| Notificaciones y concurrencia | 3 |
| Restricciones de la base | 29 |
| **Total** | **77** |

El total de la tabla coincide con el conteo de `-Dgroups=trabajo-terminal` (77). La lista de abajo tiene las mismas 77.

---

## Cada prueba seleccionada

### Por método (28)

| Archivo | Prueba | RF |
|---|---|---|
| `ReglaAdministradoresTest.java` | `quitar_al_unico_administrador_activo_deja_el_sistema_sin_administrador` (sin base) | RF-05 |
| `ReglaAdministradoresTest.java` | `con_otro_administrador_activo_si_se_puede` (sin base) | RF-05 |
| `AdministracionYRecuperacionTest.java` | `r16_no_se_desactiva_ni_se_le_quita_el_rol_al_ultimo_administrador` | RF-05 |
| `AdministracionYRecuperacionTest.java` | `el_administrador_no_puede_darse_una_temporal_a_si_mismo` | RF-05 (autoprotección) |
| `AdministracionYRecuperacionTest.java` | `un_investigador_recibe_403_en_todo_admin` | RF-04, RF-07 |
| `AdministracionYRecuperacionTest.java` | `con_contrasena_temporal_el_cambio_exige_aceptar_los_terminos_y_no_toca_nada_sin_ello` | RF-05, RNF-05 (y RN-18) |
| `AdministracionYRecuperacionTest.java` | `con_la_temporal_el_health_publico_sigue_dando_200_pero_lo_demas_no` | RNF-05 |
| `AdministracionYRecuperacionTest.java` | `identificador_o_correo_repetidos_responden_409` | RF-05 |
| `AdministracionYRecuperacionTest.java` | `patch_cambia_datos_y_rol_desactiva_y_no_hay_delete` | RF-05, RF-06 |
| `AdministracionYRecuperacionTest.java` | `el_enlace_cambia_la_contrasena_una_sola_vez` | RF-03 |
| `AdministracionYRecuperacionTest.java` | `el_enlace_deja_de_servir_si_la_contrasena_cambia_por_otro_camino_o_si_vencio_o_no_es_de_restablecer` | RF-03 |
| `AdministracionYRecuperacionTest.java` | `el_candado_de_la_cuenta_deja_pasar_las_notificaciones_del_worker_y_for_update_no` | sin RF directo (concurrencia) |
| `ListaDeExperimentosTest.java` | `cada_estado_filtra_los_experimentos_que_tienen_ese_estado` | RF-23 |
| `ListaDeExperimentosTest.java` | `el_resumen_trae_los_avisos_de_vencimiento` | RF-24 |
| `EstadoConPausaTest.java` | `con_pausa_json_devuelve_cilindro_desde_segundo_y_el_plazo_que_queda` | RF-15 |
| `BloquesDeResultadosTest.java` | `devuelve_los_bloques_de_cada_especimen_con_la_forma_de_la_guia` | RF-18 |
| `GraficasEndpointTest.java` | `da_los_grupos_en_orden_con_sus_faltantes_y_sus_estadisticos` | RF-21, RF-31 |
| `ReportesCsvXlsxTest.java` | `el_csv_del_grupo_trae_las_columnas_las_filas_y_los_estadisticos` | RF-22, RF-31 |
| `ReportesCsvXlsxTest.java` | `el_xlsx_trae_lo_mismo_con_numeros_de_verdad` | RF-22 |
| `SegundosYExcelTest.java` | `el_excel_del_experimento_tiene_las_dos_hojas` | RF-22 |
| `SinVerYNivelTest.java` | `totales_y_minutos_traen_las_cinco_claves_y_suman_el_cien_por_ciento` | RF-19, RF-20 |
| `RevisionYVideoTest.java` | `el_token_de_video_lo_piden_quien_subio_el_video_y_un_administrador_y_otra_cuenta_recibe_403` | RF-32 |
| `RevisionYVideoTest.java` | `el_jwt_de_sesion_en_la_url_se_rechaza` | RF-32, RNF-04 |
| `RevisionYVideoTest.java` | `solo_se_guardan_los_segundos_que_vienen_y_el_resto_queda_como_estaba` | RF-32 |
| `SinVerYNivelTest.java` | `una_correccion_a_conducta_activa_sube_el_nivel_a_agrupado_y_quitarla_lo_baja_a_preciso` | RF-32 |
| `ReinicioDeAnalisisTest.java` | `el_creador_reinicia_y_el_analisis_queda_en_cola_sin_resultados_ni_reportes_ni_archivos_viejos` | RF-32 |
| `ReinicioDeAnalisisTest.java` | `dos_reinicios_a_la_vez_solo_pasa_uno` | RF-32 (concurrencia) |
| `ArreglosE50Test.java` | `el_contador_de_no_leidas_cuenta_todas_las_de_la_cuenta` | RF-33 |

Nota al pie (grupo de la regla del último Administrador y autoprotección): **Selección: de las 8 pruebas del grupo, el trabajo terminal documenta 4** (2 de `ReglaAdministradoresTest`, sin base; 2 de `AdministracionYRecuperacionTest`, con PostgreSQL).

### Por clase (49)

**`CrearEliminarSubirTest.java`** (16; todas con PostgreSQL)
- `crear_un_experimento_lo_guarda_y_aparece_en_la_lista` — RF-11
- `un_nombre_repetido_responde_409_con_su_mensaje` — RF-11
- `un_nombre_vacio_o_una_fecha_invalida_responden_400` — RF-11
- `crear_sin_sesion_responde_401` — RNF-04
- `el_creador_puede_eliminar_su_experimento` — RF-27
- `un_administrador_puede_eliminar_el_experimento_de_otro` — RF-27, RF-30
- `otro_investigador_recibe_403_y_no_se_borra_nada` — RF-27
- `con_un_analisis_procesando_responde_409_y_no_borra_nada` — RF-27
- `con_un_analisis_en_pausa_responde_409_y_no_borra_nada` — RF-27
- `al_eliminar_desaparecen_sus_grupos_tandas_y_notificaciones_y_los_de_otro_experimento_quedan` — RF-27
- `otro_investigador_recibe_403_con_el_mensaje_de_creador` — RF-08 y RF-09 (subida)
- `un_archivo_que_no_es_mp4_ni_mov_se_rechaza_con_422` — RF-08
- `un_video_vertical_responde_422_y_no_deja_archivos_ni_filas` — RF-10
- `un_video_horizontal_del_dia_2_crea_la_tanda_el_video_y_el_analisis_en_cola` — RF-09, RF-12
- `un_video_horizontal_del_dia_1_se_guarda_sin_analisis` — RF-09
- `un_segundo_video_del_mismo_dia_en_la_misma_tanda_responde_409` — RF-09

**`PlazoDeRecuperacionTest.java`** (4; todas con PostgreSQL)
- `el_enlace_dura_exactamente_lo_que_marca_app_reset_minutos` — RF-03
- `el_enlace_sirve_dentro_de_su_plazo_y_solo_una_vez` — RF-03
- `un_enlace_emitido_hace_59_minutos_todavia_sirve` — RF-03 (el enlace vencido se firma con la fecha pasada, no con el reloj)
- `un_enlace_emitido_hace_62_minutos_ya_no_sirve_y_la_contrasena_no_cambia` — RF-03 (igual que el anterior)

**`RestriccionesDeLaBaseTest.java`** (29; todas con PostgreSQL; integridad de datos, sin RF directo)
- `control_la_cadena_completa_y_valida_se_inserta_sin_error`
- `control_tandas_de_dos_y_cuatro_cilindros_son_validas`
- `control_segundos_1_y_300_son_validos`
- `control_video_con_duracion_positiva_es_valido`
- `control_analisis_con_error_y_diagnostico_es_valido`
- `tanda_con_uno_cilindro_se_rechaza`
- `tanda_con_cinco_cilindros_se_rechaza`
- `tanda_con_ordinal_cero_se_rechaza`
- `especimen_con_numero_de_cilindro_cero_se_rechaza`
- `especimen_con_numero_de_cilindro_cinco_se_rechaza`
- `segundo_cero_se_rechaza`
- `segundo_301_se_rechaza`
- `video_con_duracion_cero_se_rechaza`
- `video_con_duracion_negativa_se_rechaza`
- `analisis_con_diagnostico_y_estado_distinto_de_error_se_rechaza`
- `grupo_con_tipo_fuera_del_dominio_se_rechaza`
- `analisis_con_estado_fuera_del_dominio_se_rechaza`
- `segundo_con_conducta_fuera_del_dominio_se_rechaza`
- `correo_de_usuario_repetido_se_rechaza`
- `ordinal_repetido_dentro_del_mismo_grupo_se_rechaza`
- `sesion_repetida_en_la_misma_tanda_se_rechaza`
- `numero_de_cilindro_repetido_en_la_misma_tanda_se_rechaza`
- `nombre_de_experimento_repetido_se_rechaza`
- `segundo_analisis_del_mismo_video_se_rechaza`
- `no_se_borra_un_usuario_que_tiene_experimentos`
- `no_se_borra_un_experimento_que_tiene_grupos`
- `no_se_borra_un_grupo_que_tiene_tandas`
- `no_se_borra_una_tanda_que_tiene_videos`
- `no_se_borra_un_video_que_tiene_analisis`

---

## Reglas que no pude cubrir con una prueba buena de PostgreSQL

- **Pausa, ajuste del recuadro y continuar** (RF-15): solo hay una prueba seleccionada del estado con pausa. El ajuste del recuadro y «continuar» tienen pruebas, pero no contra PostgreSQL con su flujo completo; no las forcé.
- **Video que `ffprobe` no puede leer** (RF-10): no hay prueba con PostgreSQL que lo verifique.
- **Subida con tipo de grupo o sesión inválidos** (RF-09): solo está la de `.avi` y la del video vertical.
- **Conservar resultados indefinidamente** (RF-26) y **borrado automático a los 30 días** (RF-25): no tienen prueba con PostgreSQL.
- **Clasificación de conducta** (RF-13, RF-14, RF-17): no hay prueba de backend; el pipeline está en el worker.

---

## Verificación del conteo

- `-Dgroups=trabajo-terminal`: 77 pruebas en el reporte; 75 omitidas (sin `FST_TEST_DB_URL`) y 2 corren (las de `ReglaAdministradoresTest`).
- Por clase: 16 + 4 + 29 = 49.
- Por método: 28 (26 con PostgreSQL y 2 sin base).
- 49 + 28 = 77.

# FST Rat Tracker — Manual de Usuario

---

## PARTE 1: MANUAL TÉCNICO (DevOps/SysAdmin)

### Requisitos previos

**Sistema:**
- Linux, macOS o Windows con WSL2
- Docker + Docker Compose instalados

**Software:**
- Git
- 10+ GB de espacio libre en disco (para videos + BD)

---

### Paso 1: Clonar el repositorio

```bash
git clone https://github.com/ErickJester/fst-ai-system.git
cd fst-ai-system
```

---

### Paso 2: Crear archivo `.env`

```bash
cp .env.example .env
```

Contenido de `.env`:
```
UPLOAD_MAX_MB=2048
```

Ajusta `UPLOAD_MAX_MB` según el tamaño máximo de videos que quieras permitir (en MB).

---

### Paso 3: Levantar los servicios con Docker Compose

**Primera vez (construcción de imágenes):**

```bash
docker-compose up --build
```

**Ejecuciones posteriores:**

```bash
docker-compose up -d
```

(El `-d` ejecuta en background)

**Servicios levantados:**
- **API**: `http://localhost:8000`
- **Frontend**: `http://localhost:5173`
- **PostgreSQL**: `localhost:5432` (usuario: `fst`, password: `fst`, BD: `fst`)
- **Worker** (análisis en background): sin interfaz directa

---

### Paso 4: Verificar que todo está corriendo

```bash
docker-compose ps
```

Debe mostrar 4 contenedores en estado `Up`:
- `db`
- `api`
- `worker`
- `frontend`

---

### Paso 5: Ver logs en tiempo real

```bash
# Todos los logs
docker-compose logs -f

# Solo API
docker-compose logs -f api

# Solo worker
docker-compose logs -f worker
```

---

### Paso 6: Detener los servicios

```bash
docker-compose down
```

Para detener E ELIMINAR volúmenes (bases de datos):
```bash
docker-compose down -v
```

⚠️ **Advertencia**: esto borra todos los datos.

---

### Instalación del modelo custom (opcional)

Si tienes un modelo YOLO custom entrenado (`weights/rat.pt`):

1. Coloca el archivo en `./weights/rat.pt`
2. Reinicia los contenedores:
   ```bash
   docker-compose restart api worker
   ```

Si NO existe `weights/rat.pt`, el sistema usa `yolov8n.pt` (modelo genérico de COCO).

---

### Troubleshooting técnico

**Problema: Puerto 8000/5173 ya en uso**
```bash
# Cambia los puertos en docker-compose.yml o detén el servicio que ocupa el puerto
lsof -i :8000
```

**Problema: BD no se conecta**
```bash
# Verifica que postgres inició correctamente
docker-compose logs db

# Reinicia solo la BD
docker-compose restart db
```

**Problema: Worker no procesa videos**
```bash
# Revisa logs del worker
docker-compose logs worker

# Verifica que tiene permisos en /data
docker-compose exec worker ls -la /data
```

**Problema: Falta conexión entre servicios**
```bash
# Verifica la red de Docker Compose
docker network ls
docker network inspect fst-ai-system_default
```

---

### Backup y restauración

**Backup de la base de datos:**
```bash
docker-compose exec db pg_dump -U fst -d fst > backup.sql
```

**Restaurar desde backup:**
```bash
docker-compose exec -T db psql -U fst -d fst < backup.sql
```

---

### Configuración avanzada

#### Variables de entorno (en docker-compose.yml)

| Variable | Descripción | Valor por defecto |
|----------|-------------|-------------------|
| `DATABASE_URL` | URL de conexión PostgreSQL | `postgresql+psycopg2://fst:fst@db:5432/fst` |
| `FLASK_ENV` | Modo Flask | `development` |
| `UPLOAD_MAX_MB` | Tamaño máximo de video | `2048` |
| `VITE_API_BASE` | URL base de la API (frontend) | `http://localhost:8000` |

---

---

## PARTE 2: MANUAL DE USUARIO (Investigador)

### Inicio de sesión

1. Abre el navegador en `http://localhost:5173`
2. Usa las credenciales:
   - **Email**: `investigador@example.com`
   - **Contraseña**: `changeme`

⚠️ El sistema pedirá cambiar la contraseña en el primer acceso.

---

### Flujo principal: Subir y analizar un video

#### **Paso 1: Crear un experimento**

1. En la página principal, haz clic en **"+ Nuevo Experimento"**
2. Completa el formulario:
   - **Nombre**: p.ej. "Experimento Depresión DAY1"
   - **Fecha**: fecha del experimento
   - **Tratamiento**: p.ej. "Control" o "Fármaco X"
   - **Especie**: "Rata Wistar" o similar
   - **Disposición**: `auto` (detecta automáticamente) o `1x3`, `1x4`, `2x2` (cilindros)
   - **Notas**: observaciones relevantes

3. Haz clic en **"Crear"**

---

#### **Paso 2: Subir video del primer día (DAY1)**

1. En tu experimento, haz clic en **"+ Subir video"**
2. Selecciona el día: **DAY1**
3. Carga el archivo `.mp4` (máx. 2 GB por defecto)
4. El sistema **inicia automáticamente** el análisis

**Estado del análisis:**
- En la columna "Estado", ves:
  - 🔵 **QUEUED**: esperando en cola
  - 🟠 **RUNNING**: analizando (barra de progreso con %)
  - 🟢 **DONE**: análisis completado
  - 🔴 **FAILED**: error (revisa con el técnico)

---

#### **Paso 3: Consultar resultados de DAY1**

Una vez que el análisis esté **DONE**:

1. En tu experimento, haz clic en **"Ver resultados"**
2. Verás:
   - **Tiempos totales** por rata:
     - Nado (segundos)
     - Inmovilidad (segundos)
     - Escape (segundos)
   - **Desglose por minuto**: tablas detalladas de cada minuto

3. Exporta a CSV si lo necesitas (botón **"Descargar CSV"**)

---

#### **Paso 4: Repetir para DAY2**

1. En el mismo experimento, sube el video de **DAY2**
2. Espera a que termine el análisis
3. Ahora puedes hacer **comparación DAY1 vs DAY2**

---

### Comparación DAY1 vs DAY2

Una vez que ambos videos estén analizados:

1. En tu experimento, haz clic en **"Comparación"**
2. Verás un lado a lado:
   - Tiempos totales por día
   - Porcentajes de cada conducta
   - Cambios relativo (↑ más nado en DAY2, ↓ menos inmovilidad, etc.)

---

### Consultar notificaciones

El sistema envía avisos sobre:
- ✅ Análisis completado
- ❌ Análisis fallido
- ⏰ Video próximo a ser eliminado (30 días después del análisis)

Abre el ícono 🔔 en la esquina superior.

---

### Gestionar experimentos

#### Listar todos tus experimentos

1. En el menú lateral, haz clic en **"Experimentos"**
2. Ves todos tus proyectos

#### Buscar/Filtrar

- **Por fecha**: usa el selector de rango
- **Por tratamiento**: filtro en el lado izquierdo
- **Por estado**: "en progreso", "completo", "error"

#### Eliminar un experimento

⚠️ **Irreversible**. Se elimina el experimento y todos sus videos/análisis.

1. Abre el experimento
2. Haz clic en **"Opciones"** → **"Eliminar"**
3. Confirma

---

### Exportar resultados

#### Descargar CSV

1. En tu experimento → **"Resultados"** → **"Descargar CSV"**
2. Abre en Excel o Python (`pandas`)

#### Generar reporte PDF

1. En tu experimento → **"Reportes"** → **"Generar PDF"**
2. Espera a que se genere (puede tardar 1-2 min)
3. Se descarga automáticamente

---

### Parámetros de análisis y qué significan

El sistema usa estos umbrales para clasificar conducta:

| Parámetro | Significado | Valor por defecto |
|-----------|-------------|-------------------|
| `immobile_thr` | Movimiento mínimo para dejar de ser inmóvil (px) | 6.5 |
| `disp_thr` | Desplazamiento máximo del centro (px/frame) | 8.0 |
| `pos_std_thr` | Dispersión espacial máxima (px) | 20.0 |
| `climb_aspect_thr` | Ratio altura/ancho para detectar escape | 1.6 |

**Conducta clasificada como:**
- **Escape**: aspect_ratio > 1.6 Y movimiento ≥ 6.5 px (postura vertical + movimiento)
- **Inmovilidad**: movimiento < 6.5 Y desplazamiento < 8.0 Y dispersión < 20.0
- **Nado**: todo lo demás

---

### Preguntas frecuentes

**P: ¿Cuánto tarda el análisis de un video?**
R: Típicamente 2-10 minutos según la duración y resolución. Videos de 6 minutos ~5 min de análisis.

**P: ¿Puedo subir un video mientras se procesa otro?**
R: Sí, se encolan. El worker procesa uno por uno en orden.

**P: ¿Se eliminan los videos después de cierto tiempo?**
R: Sí, 30 días después de que termina el análisis. Los resultados se conservan indefinidamente.

**P: ¿Qué formato de video necesito?**
R: Obligatorio `.mp4`. Recomendado: H.264/H.265, 30 FPS, resolución ≥ 640x480.

**P: ¿Puedo ver el video anotado?**
R: Sí, cuando termina el análisis se genera `*_tracked.mp4` en la carpeta de datos.

**P: ¿Puedo editar los umbrales de clasificación?**
R: Actualmente están fijos. Contacta con el administrador si necesitas ajustarlos.

**P: ¿Dónde está el archivo de log detallado?**
R: En `/data/` del contenedor, o pídele al técnico que revise `docker-compose logs worker`.

---

### Flujo típico de investigación

```
1. Creas un experimento
   ↓
2. Subes video DAY1
   ↓
3. Esperas análisis (~5-10 min)
   ↓
4. Revisan resultados
   ↓
5. Suben video DAY2
   ↓
6. Esperas análisis
   ↓
7. Hacen comparación DAY1 vs DAY2
   ↓
8. Descargan CSV / generan PDF
   ↓
9. Exportan para análisis estadístico (R, Python, etc.)
```

---

### Soporte

**Problema técnico (error en análisis, BD caída):**
→ Contactar al técnico. Proporciona:
- ID del experimento
- Nombre del video
- Captura de pantalla del error

**Pregunta sobre resultados:**
→ Revisa la documentación técnica en `CLAUDE.md` o consulta con el responsable del proyecto.

---

## Resumen rápido

### Para técnico:
```bash
git clone ...
cd fst-ai-system
cp .env.example .env
docker-compose up --build    # Primera vez
docker-compose up -d         # Después
```

### Para investigador:
1. Abre `http://localhost:5173`
2. Crea experimento
3. Sube video (auto-inicia análisis)
4. Espera a DONE
5. Consulta resultados o comparación
6. Descarga CSV/PDF

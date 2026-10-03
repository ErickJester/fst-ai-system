import enum
from datetime import datetime, timedelta
from sqlalchemy import (
    String, Integer, DateTime, Enum, ForeignKey, Text, Float,
    Boolean, UniqueConstraint, Index, CheckConstraint,
)
from sqlalchemy.orm import Mapped, mapped_column, relationship
from .db import Base


# ── Enums ────────────────────────────────────────────────────────────

class Role(enum.Enum):
    INVESTIGADOR = "INVESTIGADOR"
    ADMIN = "ADMIN"


class Day(enum.Enum):
    DAY1 = "DAY1"
    DAY2 = "DAY2"


class JobStatus(enum.Enum):
    QUEUED = "QUEUED"
    RUNNING = "RUNNING"
    DONE = "DONE"
    FAILED = "FAILED"


class PipelineStage(enum.Enum):
    PREPROCESSING = "PREPROCESSING"
    ROI_DETECTION = "ROI_DETECTION"
    TRACKING = "TRACKING"
    BEHAVIOR = "BEHAVIOR"   # solo si hay plugin de conducta (pipeline/behavior_plugin.py)
    DONE = "DONE"


class ReportFormat(enum.Enum):
    PDF = "PDF"
    CSV = "CSV"


class NotificationType(enum.Enum):
    ANALYSIS_DONE = "ANALYSIS_DONE"
    ANALYSIS_FAILED = "ANALYSIS_FAILED"
    VIDEO_EXPIRING = "VIDEO_EXPIRING"
    VIDEO_DELETED = "VIDEO_DELETED"


# ── Users (RF-01..RF-07, RNF-04, RNF-05) ────────────────────────────

class User(Base):
    """Credenciales, rol y estado de un usuario del sistema."""
    __tablename__ = "users"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    email: Mapped[str] = mapped_column(String(255), unique=True, nullable=False, index=True)
    password_hash: Mapped[str] = mapped_column(String(255), nullable=False)
    name: Mapped[str] = mapped_column(String(120), nullable=False)
    role: Mapped[Role] = mapped_column(Enum(Role), nullable=False, default=Role.INVESTIGADOR)
    is_active: Mapped[bool] = mapped_column(Boolean, default=True, nullable=False)
    must_change_password: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow, nullable=False)
    last_login: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)

    experiments: Mapped[list["Experiment"]] = relationship(back_populates="user", cascade="all, delete-orphan")
    notifications: Mapped[list["Notification"]] = relationship(back_populates="user", cascade="all, delete-orphan")


# ── Experiments (antes "sessions") ───────────────────────────────────

class Experiment(Base):
    """Agrupa los videos y resultados de un experimento FST.
    layout es la fuente de verdad para la disposición espacial del pipeline
    ('auto', '1x3', '1x4', '2x2'). analysis_configs NO duplica este campo;
    el job hereda el layout de su experimento vía video → experiment.
    """
    __tablename__ = "experiments"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"), nullable=False, index=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow, nullable=False)
    name: Mapped[str] = mapped_column(String(120), nullable=False)
    experiment_date: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    treatment: Mapped[str | None] = mapped_column(String(255), nullable=True)
    species: Mapped[str | None] = mapped_column(String(120), nullable=True)
    layout: Mapped[str] = mapped_column(String(20), nullable=False, default="auto")
    notes: Mapped[str | None] = mapped_column(Text, nullable=True)

    user: Mapped["User"] = relationship(back_populates="experiments")
    videos: Mapped[list["Video"]] = relationship(back_populates="experiment", cascade="all, delete-orphan")
    subjects: Mapped[list["Subject"]] = relationship(back_populates="experiment", cascade="all, delete-orphan")
    reports: Mapped[list["Report"]] = relationship(back_populates="experiment", cascade="all, delete-orphan")


# ── Subjects ──────────────────────────────────────────────────────────

class Subject(Base):
    """Identidad persistente de un animal dentro de un experimento.
    rat_idx es la ÚNICA fuente de verdad para la posición del animal;
    ni animals ni rois duplican este campo — ambos referencian subject_id.
    """
    __tablename__ = "subjects"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    experiment_id: Mapped[int] = mapped_column(ForeignKey("experiments.id"), nullable=False, index=True)
    rat_idx: Mapped[int] = mapped_column(Integer, nullable=False)
    label: Mapped[str | None] = mapped_column(String(50), nullable=True)

    experiment: Mapped["Experiment"] = relationship(back_populates="subjects")
    animals: Mapped[list["Animal"]] = relationship(back_populates="subject")
    rois: Mapped[list["ROI"]] = relationship(back_populates="subject")

    __table_args__ = (
        UniqueConstraint("experiment_id", "rat_idx", name="uq_subject_position"),
    )


# ── Videos ───────────────────────────────────────────────────────────

class Video(Base):
    """Archivo de video subido. Se elimina del disco 30 días después
    de que el análisis termina (RN-05, RF-24, RNF-08).
    duration_s se establece por el worker al completar el análisis.
    """
    __tablename__ = "videos"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    experiment_id: Mapped[int] = mapped_column(ForeignKey("experiments.id"), nullable=False, index=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow, nullable=False)
    day: Mapped[Day] = mapped_column(Enum(Day), nullable=False)
    filename: Mapped[str] = mapped_column(String(255), nullable=False)
    path: Mapped[str] = mapped_column(String(512), nullable=False)
    duration_s: Mapped[float | None] = mapped_column(Float, nullable=True)
    deletion_date: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)

    experiment: Mapped["Experiment"] = relationship(back_populates="videos")
    jobs: Mapped[list["Job"]] = relationship(back_populates="video", cascade="all, delete-orphan")
    rois: Mapped[list["ROI"]] = relationship(back_populates="video", cascade="all, delete-orphan")

    __table_args__ = (
        UniqueConstraint("experiment_id", "day", name="uq_experiment_day"),
        CheckConstraint(
            "deletion_date IS NULL OR deletion_date > created_at",
            name="ck_deletion_after_creation",
        ),
    )


# ── ROIs ──────────────────────────────────────────────────────────────

class ROI(Base):
    """Región de interés (cilindro) detectada en un video.
    Referencia subject_id en lugar de rat_idx crudo para mantener
    la posición sincronizada con la identidad del animal vía FK.
    """
    __tablename__ = "rois"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    video_id: Mapped[int] = mapped_column(ForeignKey("videos.id"), nullable=False, index=True)
    subject_id: Mapped[int] = mapped_column(ForeignKey("subjects.id"), nullable=False, index=True)
    x: Mapped[int] = mapped_column(Integer, nullable=False)
    y: Mapped[int] = mapped_column(Integer, nullable=False)
    w: Mapped[int] = mapped_column(Integer, nullable=False)
    h: Mapped[int] = mapped_column(Integer, nullable=False)

    video: Mapped["Video"] = relationship(back_populates="rois")
    subject: Mapped["Subject"] = relationship(back_populates="rois")

    __table_args__ = (
        UniqueConstraint("video_id", "subject_id", name="uq_roi_position"),
    )


# ── Analysis configs ─────────────────────────────────────────────────

class AnalysisConfig(Base):
    """Parámetros exactos usados en una ejecución del pipeline.
    layout NO se duplica aquí — la autoridad es experiments.layout
    (el job hereda vía video → experiment).
    model_hash (SHA-256) permite reproducibilidad exacta de los pesos.
    """
    __tablename__ = "analysis_configs"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow, nullable=False)
    model_name: Mapped[str] = mapped_column(String(120), nullable=False)
    model_hash: Mapped[str] = mapped_column(String(64), nullable=False)
    pipeline_version: Mapped[str] = mapped_column(String(30), nullable=False)
    conf_threshold: Mapped[float] = mapped_column(Float, nullable=False)
    tracker_yaml: Mapped[str] = mapped_column(String(60), nullable=False, default="bytetrack.yaml")
    skip_frames: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    max_freeze_frames: Mapped[int] = mapped_column(Integer, nullable=False, default=20)
    stabilize: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False)
    yolo_available: Mapped[bool | None] = mapped_column(Boolean, nullable=True)
    # Plugin de conducta usado (NULL = sin clasificación)
    behavior_plugin: Mapped[str | None] = mapped_column(String(120), nullable=True)
    behavior_plugin_version: Mapped[str | None] = mapped_column(String(60), nullable=True)

    jobs: Mapped[list["Job"]] = relationship(back_populates="config")


# ── Jobs ─────────────────────────────────────────────────────────────

class Job(Base):
    """Tarea de análisis encolada por el worker.
    Incluye stage y progress_pct para la barra de progreso (RF-15).
    config_id enlaza a los parámetros exactos usados para reproducibilidad.
    """
    __tablename__ = "jobs"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    video_id: Mapped[int] = mapped_column(ForeignKey("videos.id"), nullable=False, index=True)
    config_id: Mapped[int | None] = mapped_column(ForeignKey("analysis_configs.id"), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow, nullable=False)
    started_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    finished_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    status: Mapped[JobStatus] = mapped_column(Enum(JobStatus), default=JobStatus.QUEUED, nullable=False, index=True)
    stage: Mapped[PipelineStage | None] = mapped_column(Enum(PipelineStage), nullable=True)
    progress_pct: Mapped[int] = mapped_column(Integer, default=0, nullable=False)
    error: Mapped[str | None] = mapped_column(Text, nullable=True)
    output_video_path: Mapped[str | None] = mapped_column(String(512), nullable=True)
    output_json_path: Mapped[str | None] = mapped_column(String(512), nullable=True)

    video: Mapped["Video"] = relationship(back_populates="jobs")
    config: Mapped["AnalysisConfig | None"] = relationship(back_populates="jobs")
    animals: Mapped[list["Animal"]] = relationship(back_populates="job", cascade="all, delete-orphan")


# ── Animals ──────────────────────────────────────────────────────────

class Animal(Base):
    """Un animal en una ejecución de análisis específica.
    subject_id es la ÚNICA referencia a la identidad del animal;
    rat_idx se eliminó para evitar duplicidad conceptual con Subject.
    Para obtener la posición: animal.subject.rat_idx.
    """
    __tablename__ = "animals"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    job_id: Mapped[int] = mapped_column(ForeignKey("jobs.id"), nullable=False, index=True)
    subject_id: Mapped[int] = mapped_column(ForeignKey("subjects.id"), nullable=False, index=True)

    job: Mapped["Job"] = relationship(back_populates="animals")
    subject: Mapped["Subject"] = relationship(back_populates="animals")
    tracking: Mapped["TrackingResult | None"] = relationship(
        back_populates="animal", cascade="all, delete-orphan", uselist=False,
    )
    behavior_segments: Mapped[list["BehaviorSegment"]] = relationship(
        back_populates="animal", cascade="all, delete-orphan",
        order_by="BehaviorSegment.start_s",
    )

    __table_args__ = (
        UniqueConstraint("job_id", "subject_id", name="uq_job_subject"),
    )


# ── Tracking results ─────────────────────────────────────────────────

class TrackingResult(Base):
    """Calidad del tracking de un animal en una ejecución (1:1 con animals).
    Conteo de frames procesados según la fuente de la bbox:
      detected = yolo + track + classic (detección real)
      freeze / lost = bbox congelada por política de continuidad
      none = sin bbox
    detected + freeze + lost + none = frames_total.
    """
    __tablename__ = "tracking_results"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    animal_id: Mapped[int] = mapped_column(ForeignKey("animals.id"), nullable=False, unique=True)
    frames_total: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    frames_yolo: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    frames_track: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    frames_classic: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    frames_freeze: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    frames_lost: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    frames_none: Mapped[int] = mapped_column(Integer, nullable=False, default=0)

    animal: Mapped["Animal"] = relationship(back_populates="tracking")

    __table_args__ = (
        CheckConstraint(
            "frames_yolo + frames_track + frames_classic + frames_freeze"
            " + frames_lost + frames_none = frames_total",
            name="ck_tracking_frames_sum",
        ),
    )


# ── Behavior segments (plugin externo) ───────────────────────────────

class BehaviorSegment(Base):
    """Intervalo [start_s, end_s) con una conducta, producido por el plugin
    de conducta (pipeline/behavior_plugin.py). label es texto libre definido
    por el plugin: el sistema no fija las clases. Los totales por conducta y
    por minuto se derivan en consulta, no se almacenan.
    """
    __tablename__ = "behavior_segments"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    animal_id: Mapped[int] = mapped_column(ForeignKey("animals.id"), nullable=False, index=True)
    start_s: Mapped[float] = mapped_column(Float, nullable=False)
    end_s: Mapped[float] = mapped_column(Float, nullable=False)
    label: Mapped[str] = mapped_column(String(60), nullable=False)
    confidence: Mapped[float | None] = mapped_column(Float, nullable=True)

    animal: Mapped["Animal"] = relationship(back_populates="behavior_segments")

    __table_args__ = (
        CheckConstraint("start_s >= 0 AND end_s > start_s", name="ck_segment_interval"),
        CheckConstraint(
            "confidence IS NULL OR (confidence >= 0 AND confidence <= 1)",
            name="ck_segment_confidence",
        ),
    )


# ── Reports (RF-21, RN-06) ───────────────────────────────────────────

class Report(Base):
    """Archivo PDF o CSV generado para un experimento.
    Se conserva indefinidamente aunque el video original se haya borrado.
    """
    __tablename__ = "reports"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    experiment_id: Mapped[int] = mapped_column(ForeignKey("experiments.id"), nullable=False, index=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow, nullable=False)
    format: Mapped[ReportFormat] = mapped_column(Enum(ReportFormat), nullable=False)
    path: Mapped[str] = mapped_column(String(512), nullable=False)

    experiment: Mapped["Experiment"] = relationship(back_populates="reports")


# ── Notifications (RF-23, RF-16) ─────────────────────────────────────

class Notification(Base):
    """Aviso al investigador: análisis completado, error, video próximo
    a eliminarse, etc. Todos los tipos de notificación actuales requieren
    un experimento asociado, por lo que experiment_id es NOT NULL.
    """
    __tablename__ = "notifications"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"), nullable=False, index=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow, nullable=False)
    type: Mapped[NotificationType] = mapped_column(Enum(NotificationType), nullable=False)
    message: Mapped[str] = mapped_column(Text, nullable=False)
    is_read: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False)
    experiment_id: Mapped[int] = mapped_column(ForeignKey("experiments.id"), nullable=False)

    user: Mapped["User"] = relationship(back_populates="notifications")

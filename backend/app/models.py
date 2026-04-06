import enum
from datetime import datetime, timedelta
from sqlalchemy import (
    String, Integer, DateTime, Enum, ForeignKey, Text, Float,
    Boolean, JSON, UniqueConstraint, Index,
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
    CLASSIFICATION = "CLASSIFICATION"
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
    Cada experimento pertenece a un investigador (user_id FK).
    """
    __tablename__ = "experiments"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"), nullable=False, index=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow, nullable=False)
    name: Mapped[str] = mapped_column(String(120), nullable=False)
    experiment_date: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    treatment: Mapped[str | None] = mapped_column(String(255), nullable=True)
    species: Mapped[str | None] = mapped_column(String(120), nullable=True)
    num_animals: Mapped[int] = mapped_column(Integer, default=4, nullable=False)
    notes: Mapped[str | None] = mapped_column(Text, nullable=True)

    user: Mapped["User"] = relationship(back_populates="experiments")
    videos: Mapped[list["Video"]] = relationship(back_populates="experiment", cascade="all, delete-orphan")
    reports: Mapped[list["Report"]] = relationship(back_populates="experiment", cascade="all, delete-orphan")


# ── Videos ───────────────────────────────────────────────────────────

class Video(Base):
    """Archivo de video subido. Se elimina del disco 30 días después
    de que el análisis termina (RN-05, RF-24, RNF-08).
    """
    __tablename__ = "videos"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    experiment_id: Mapped[int] = mapped_column(ForeignKey("experiments.id"), nullable=False, index=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow, nullable=False)
    day: Mapped[Day] = mapped_column(Enum(Day), nullable=False)
    filename: Mapped[str] = mapped_column(String(255), nullable=False)
    path: Mapped[str] = mapped_column(String(512), nullable=False)
    roi_json: Mapped[str | None] = mapped_column(Text, nullable=True)
    deletion_date: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)

    experiment: Mapped["Experiment"] = relationship(back_populates="videos")
    jobs: Mapped[list["Job"]] = relationship(back_populates="video", cascade="all, delete-orphan")

    __table_args__ = (
        UniqueConstraint("experiment_id", "day", name="uq_experiment_day"),
    )


# ── Jobs ─────────────────────────────────────────────────────────────

class Job(Base):
    """Tarea de análisis encolada por el worker.
    Incluye stage y progress_pct para la barra de progreso (RF-15).
    """
    __tablename__ = "jobs"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    video_id: Mapped[int] = mapped_column(ForeignKey("videos.id"), nullable=False, index=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow, nullable=False)
    started_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    finished_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    status: Mapped[JobStatus] = mapped_column(Enum(JobStatus), default=JobStatus.QUEUED, nullable=False, index=True)
    stage: Mapped[PipelineStage | None] = mapped_column(Enum(PipelineStage), nullable=True)
    progress_pct: Mapped[int] = mapped_column(Integer, default=0, nullable=False)
    error: Mapped[str | None] = mapped_column(Text, nullable=True)

    video: Mapped["Video"] = relationship(back_populates="jobs")
    animals: Mapped[list["Animal"]] = relationship(back_populates="job", cascade="all, delete-orphan")


# ── Animals ──────────────────────────────────────────────────────────

class Animal(Base):
    """Un animal detectado en un video procesado.
    rat_idx es la posición en el encuadre (0..3), asignada por ROI.
    """
    __tablename__ = "animals"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    job_id: Mapped[int] = mapped_column(ForeignKey("jobs.id"), nullable=False, index=True)
    rat_idx: Mapped[int] = mapped_column(Integer, nullable=False)
    roi_x: Mapped[int | None] = mapped_column(Integer, nullable=True)
    roi_y: Mapped[int | None] = mapped_column(Integer, nullable=True)
    roi_w: Mapped[int | None] = mapped_column(Integer, nullable=True)
    roi_h: Mapped[int | None] = mapped_column(Integer, nullable=True)

    job: Mapped["Job"] = relationship(back_populates="animals")
    results: Mapped[list["BehaviorResult"]] = relationship(back_populates="animal", cascade="all, delete-orphan")

    __table_args__ = (
        UniqueConstraint("job_id", "rat_idx", name="uq_job_animal"),
    )


# ── Behavior results (antes "result_summary") ────────────────────────

class BehaviorResult(Base):
    """Tiempo en segundos de cada conducta para un animal en una sesión.
    per_minute_json guarda el desglose por minuto (RF-19) como lista de
    dicts: [{"minute": 1, "swim_s": ..., "immobile_s": ..., "escape_s": ...}, ...]
    """
    __tablename__ = "behavior_results"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    animal_id: Mapped[int] = mapped_column(ForeignKey("animals.id"), nullable=False, index=True)
    swim_s: Mapped[float] = mapped_column(Float, default=0.0, nullable=False)
    immobile_s: Mapped[float] = mapped_column(Float, default=0.0, nullable=False)
    escape_s: Mapped[float] = mapped_column(Float, default=0.0, nullable=False)
    per_minute_json: Mapped[dict | None] = mapped_column(JSON, nullable=True)

    animal: Mapped["Animal"] = relationship(back_populates="results")


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
    a eliminarse, etc.
    """
    __tablename__ = "notifications"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"), nullable=False, index=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow, nullable=False)
    type: Mapped[NotificationType] = mapped_column(Enum(NotificationType), nullable=False)
    message: Mapped[str] = mapped_column(Text, nullable=False)
    is_read: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False)
    experiment_id: Mapped[int | None] = mapped_column(ForeignKey("experiments.id"), nullable=True)

    user: Mapped["User"] = relationship(back_populates="notifications")

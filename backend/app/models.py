import enum
from datetime import datetime
from sqlalchemy import String, Integer, DateTime, Enum, ForeignKey, Text, Float, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column, relationship
from .db import Base

class Day(enum.Enum):
    DAY1 = "DAY1"
    DAY2 = "DAY2"

class JobStatus(enum.Enum):
    QUEUED = "QUEUED"
    RUNNING = "RUNNING"
    DONE = "DONE"
    FAILED = "FAILED"

class Session(Base):
    __tablename__ = "sessions"
    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow, nullable=False)
    name: Mapped[str] = mapped_column(String(120), nullable=False)
    notes: Mapped[str | None] = mapped_column(Text, nullable=True)
    videos: Mapped[list["Video"]] = relationship(back_populates="session", cascade="all, delete-orphan")

class Video(Base):
    __tablename__ = "videos"
    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    session_id: Mapped[int] = mapped_column(ForeignKey("sessions.id"), nullable=False, index=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow, nullable=False)
    day: Mapped[Day] = mapped_column(Enum(Day), nullable=False)
    filename: Mapped[str] = mapped_column(String(255), nullable=False)
    path: Mapped[str] = mapped_column(String(512), nullable=False)
    roi_json: Mapped[str | None] = mapped_column(Text, nullable=True)

    session: Mapped["Session"] = relationship(back_populates="videos")
    jobs: Mapped[list["Job"]] = relationship(back_populates="video", cascade="all, delete-orphan")

class Job(Base):
    __tablename__ = "jobs"
    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    video_id: Mapped[int] = mapped_column(ForeignKey("videos.id"), nullable=False, index=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow, nullable=False)
    started_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    finished_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    status: Mapped[JobStatus] = mapped_column(Enum(JobStatus), default=JobStatus.QUEUED, nullable=False, index=True)
    error: Mapped[str | None] = mapped_column(Text, nullable=True)

    video: Mapped["Video"] = relationship(back_populates="jobs")
    summaries: Mapped[list["ResultSummary"]] = relationship(back_populates="job", cascade="all, delete-orphan")

class ResultSummary(Base):
    __tablename__ = "result_summary"
    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    job_id: Mapped[int] = mapped_column(ForeignKey("jobs.id"), nullable=False)
    rat_idx: Mapped[int] = mapped_column(Integer, nullable=False)  # 0..3
    swim_s: Mapped[float] = mapped_column(Float, default=0.0, nullable=False)
    immobile_s: Mapped[float] = mapped_column(Float, default=0.0, nullable=False)
    escape_s: Mapped[float] = mapped_column(Float, default=0.0, nullable=False)

    job: Mapped["Job"] = relationship(back_populates="summaries")

    __table_args__ = (UniqueConstraint("job_id", "rat_idx", name="uq_job_rat"),)

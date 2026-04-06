import hashlib
import time
from datetime import datetime, timedelta
from pathlib import Path
from sqlalchemy import text, select
from app.db import SessionLocal
from app.models import (
    JobStatus, Job, Video, Animal, BehaviorResult,
    PipelineStage, Notification, NotificationType,
    Subject, ROI, AnalysisConfig, BehaviorPerMinute,
)
from pipeline.run_analysis import run_analysis, VERSION
from pipeline.tracker import DEFAULT_MODEL, DEFAULT_CONF

POLL_SECONDS = 2
VIDEO_RETENTION_DAYS = 30


def _model_hash(model_path: str) -> str:
    """SHA-256 del archivo de pesos para reproducibilidad exacta."""
    h = hashlib.sha256()
    try:
        with open(model_path, "rb") as f:
            for chunk in iter(lambda: f.read(8192), b""):
                h.update(chunk)
        return h.hexdigest()
    except FileNotFoundError:
        return "file-not-found"


def claim_next_job_id(db):
    row = db.execute(text("""
        SELECT id FROM jobs
        WHERE status = 'QUEUED'
        ORDER BY id ASC
        FOR UPDATE SKIP LOCKED
        LIMIT 1
    """)).fetchone()
    return int(row[0]) if row else None


def main():
    while True:
        job_id = None
        try:
            # Claim job in txn
            with SessionLocal() as db:
                db.begin()
                job_id = claim_next_job_id(db)
                if not job_id:
                    db.commit()
                    time.sleep(POLL_SECONDS)
                    continue

                job = db.get(Job, job_id)
                job.status = JobStatus.RUNNING
                job.started_at = datetime.utcnow()
                job.stage = PipelineStage.PREPROCESSING
                job.progress_pct = 0
                db.add(job)
                db.commit()

            # Process outside txn
            with SessionLocal() as db:
                job = db.get(Job, job_id)
                video = db.get(Video, job.video_id)
                experiment = video.experiment
                layout = experiment.layout

                vid = Path(video.path)
                tracked_video = str(vid.parent / f"{vid.stem}_tracked.mp4")
                tracked_json = str(vid.parent / f"{vid.stem}_tracking.json")

                # Registrar config antes de correr para reproducibilidad
                config = AnalysisConfig(
                    model_name=DEFAULT_MODEL,
                    model_hash=_model_hash(DEFAULT_MODEL),
                    pipeline_version=VERSION,
                    conf_threshold=DEFAULT_CONF,
                    skip_seconds=0.0,
                    immobile_thr=6.5,
                    disp_thr=8.0,
                    pos_std_thr=20.0,
                    climb_aspect_thr=1.6,
                )
                db.add(config)
                db.flush()
                job.config_id = config.id
                db.add(job)
                db.flush()

                summaries = run_analysis(
                    video.path,
                    layout=layout,
                    output_video=tracked_video,
                    output_json=tracked_json,
                )

                # Limpiar animales previos de este job (re-run)
                db.query(Animal).filter(Animal.job_id == job_id).delete()
                db.flush()

                for s in summaries:
                    # Upsert Subject — identidad persistente del animal
                    subject = db.execute(
                        select(Subject).where(
                            Subject.experiment_id == experiment.id,
                            Subject.rat_idx == s.rat_idx,
                        )
                    ).scalars().first()
                    if not subject:
                        subject = Subject(experiment_id=experiment.id, rat_idx=s.rat_idx)
                        db.add(subject)
                        db.flush()

                    # Upsert ROI — referencia subject_id, no rat_idx crudo
                    roi_rec = db.execute(
                        select(ROI).where(
                            ROI.video_id == video.id,
                            ROI.subject_id == subject.id,
                        )
                    ).scalars().first()
                    roi_coords = s.roi
                    if roi_rec:
                        roi_rec.x, roi_rec.y, roi_rec.w, roi_rec.h = roi_coords
                    else:
                        roi_rec = ROI(
                            video_id=video.id,
                            subject_id=subject.id,
                            x=roi_coords[0],
                            y=roi_coords[1],
                            w=roi_coords[2],
                            h=roi_coords[3],
                        )
                        db.add(roi_rec)
                    db.flush()

                    # Animal sin rat_idx — identidad vía subject_id
                    animal = Animal(
                        job_id=job_id,
                        subject_id=subject.id,
                    )
                    db.add(animal)
                    db.flush()

                    total_analyzed = s.swim_s + s.immobile_s + s.escape_s
                    result = BehaviorResult(
                        animal_id=animal.id,
                        swim_s=s.swim_s,
                        immobile_s=s.immobile_s,
                        escape_s=s.escape_s,
                        total_analyzed_s=total_analyzed,
                    )
                    db.add(result)

                    for pm in s.per_minute:
                        db.add(BehaviorPerMinute(
                            animal_id=animal.id,
                            minute=pm["minute"],
                            swim_s=pm["swim_s"],
                            immobile_s=pm["immobile_s"],
                            escape_s=pm["escape_s"],
                        ))

                job.status = JobStatus.DONE
                job.stage = PipelineStage.DONE
                job.progress_pct = 100
                job.finished_at = datetime.utcnow()
                job.error = None

                # RF-24/RN-05: programar borrado del video a 30 días
                video.deletion_date = datetime.utcnow() + timedelta(days=VIDEO_RETENTION_DAYS)

                # Duración del video derivada del total de ventanas analizadas
                if summaries and summaries[0].per_minute:
                    last_minute = summaries[0].per_minute[-1]
                    video.duration_s = last_minute["minute"] * 60.0

                db.add(job)
                db.commit()

                # Crear notificación de análisis completado
                try:
                    notification = Notification(
                        user_id=experiment.user_id,
                        type=NotificationType.ANALYSIS_DONE,
                        message=f"Análisis completado para '{experiment.name}' - {video.day.value}",
                        experiment_id=experiment.id,
                    )
                    db.add(notification)
                    db.commit()
                except Exception:
                    pass

        except Exception as e:
            try:
                if job_id:
                    with SessionLocal() as db:
                        job = db.get(Job, job_id)
                        if job:
                            job.status = JobStatus.FAILED
                            job.finished_at = datetime.utcnow()
                            job.error = str(e)
                            db.add(job)
                            db.commit()

                            # Notificación de fallo
                            try:
                                video = db.get(Video, job.video_id)
                                if video and video.experiment:
                                    notification = Notification(
                                        user_id=video.experiment.user_id,
                                        type=NotificationType.ANALYSIS_FAILED,
                                        message=f"Error en análisis de '{video.experiment.name}': {str(e)[:200]}",
                                        experiment_id=video.experiment.id,
                                    )
                                    db.add(notification)
                                    db.commit()
                            except Exception:
                                pass
            except Exception:
                pass
            time.sleep(POLL_SECONDS)


if __name__ == "__main__":
    main()

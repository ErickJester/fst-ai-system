import time
from datetime import datetime, timedelta
from sqlalchemy import text, select
from app.db import SessionLocal
from app.models import (
    JobStatus, Job, Video, Animal, BehaviorResult,
    PipelineStage, Notification, NotificationType,
)
from pipeline.run_analysis import run_analysis

POLL_SECONDS = 2
VIDEO_RETENTION_DAYS = 30


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

                from pathlib import Path
                vid = Path(video.path)
                tracked_video = str(vid.parent / f"{vid.stem}_tracked.mp4")
                tracked_json = str(vid.parent / f"{vid.stem}_tracking.json")

                summaries = run_analysis(
                    video.path,
                    output_video=tracked_video,
                    output_json=tracked_json,
                )

                # Limpiar animales previos de este job (re-run)
                db.query(Animal).filter(Animal.job_id == job_id).delete()
                db.flush()

                for s in summaries:
                    animal = Animal(
                        job_id=job_id,
                        rat_idx=s.rat_idx,
                    )
                    db.add(animal)
                    db.flush()

                    result = BehaviorResult(
                        animal_id=animal.id,
                        swim_s=s.swim_s,
                        immobile_s=s.immobile_s,
                        escape_s=s.escape_s,
                        per_minute_json=getattr(s, 'per_minute', None),
                    )
                    db.add(result)

                job.status = JobStatus.DONE
                job.stage = PipelineStage.DONE
                job.progress_pct = 100
                job.finished_at = datetime.utcnow()
                job.error = None

                # RF-24/RN-05: programar borrado del video a 30 días
                video.deletion_date = datetime.utcnow() + timedelta(days=VIDEO_RETENTION_DAYS)

                db.add(job)
                db.commit()

                # Crear notificación de análisis completado
                try:
                    experiment = video.experiment
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

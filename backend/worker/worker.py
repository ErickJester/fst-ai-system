import hashlib
import time
from datetime import datetime, timedelta
from pathlib import Path
from sqlalchemy import text, select
from app.db import SessionLocal
from app.models import (
    JobStatus, Job, Video, Animal, TrackingResult,
    PipelineStage, Notification, NotificationType,
    Subject, ROI, AnalysisConfig, BehaviorSegment,
)
from pipeline.tracker import (
    track_video, VERSION, DEFAULT_MODEL, DEFAULT_CONF, MAX_FREEZE,
)
from pipeline.behavior_plugin import load_classifier, validate_segments

TRACKER_YAML = "bytetrack.yaml"

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
    # Plugin de conducta opcional (FST_BEHAVIOR_PLUGIN). Si está configurado
    # pero no carga, el worker no arranca: mejor fallar que analizar sin él.
    classifier = load_classifier()
    print(
        f"[worker] plugin de conducta: {classifier.name} {classifier.version}"
        if classifier else "[worker] sin plugin de conducta: solo tracking",
        flush=True,
    )

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
                    tracker_yaml=TRACKER_YAML,
                    skip_frames=0,
                    max_freeze_frames=MAX_FREEZE,
                    stabilize=False,
                    behavior_plugin=classifier.name if classifier else None,
                    behavior_plugin_version=classifier.version if classifier else None,
                )
                db.add(config)
                db.flush()
                job.config_id = config.id
                job.stage = PipelineStage.TRACKING
                db.add(job)
                db.commit()

                result = track_video(
                    video.path,
                    output_video=tracked_video,
                    output_json=tracked_json,
                    layout=layout,
                    show_progress=False,
                    model_path=DEFAULT_MODEL,
                    conf=DEFAULT_CONF,
                    tracker_yaml=TRACKER_YAML,
                    max_freeze_frames=MAX_FREEZE,
                )
                config.yolo_available = result["yolo_available"]
                job.output_video_path = tracked_video
                job.output_json_path = tracked_json

                # Limpiar animales previos de este job (re-run)
                db.query(Animal).filter(Animal.job_id == job_id).delete()
                db.flush()

                stats = result["stats"]
                animal_by_rat = {}
                for roi in result["rois"]:
                    rat_idx = roi["rat_idx"]

                    # Upsert Subject — identidad persistente del animal
                    subject = db.execute(
                        select(Subject).where(
                            Subject.experiment_id == experiment.id,
                            Subject.rat_idx == rat_idx,
                        )
                    ).scalars().first()
                    if not subject:
                        subject = Subject(experiment_id=experiment.id, rat_idx=rat_idx)
                        db.add(subject)
                        db.flush()

                    # Upsert ROI — referencia subject_id, no rat_idx crudo
                    roi_rec = db.execute(
                        select(ROI).where(
                            ROI.video_id == video.id,
                            ROI.subject_id == subject.id,
                        )
                    ).scalars().first()
                    if roi_rec:
                        roi_rec.x, roi_rec.y, roi_rec.w, roi_rec.h = roi["x"], roi["y"], roi["w"], roi["h"]
                    else:
                        roi_rec = ROI(
                            video_id=video.id,
                            subject_id=subject.id,
                            x=roi["x"],
                            y=roi["y"],
                            w=roi["w"],
                            h=roi["h"],
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

                    db.add(TrackingResult(
                        animal_id=animal.id,
                        frames_total=stats["total"],
                        frames_yolo=stats["yolo"][rat_idx],
                        frames_track=stats["track"][rat_idx],
                        frames_classic=stats["classic"][rat_idx],
                        frames_freeze=stats["freeze"][rat_idx],
                        frames_lost=stats["lost"][rat_idx],
                        frames_none=stats["none"][rat_idx],
                    ))
                    animal_by_rat[rat_idx] = animal

                # Plugin de conducta (hueco para el clasificador externo)
                if classifier is not None:
                    job.stage = PipelineStage.BEHAVIOR
                    db.commit()
                    segments = classifier.classify(video.path, result)
                    validate_segments(segments, n_rats=len(animal_by_rat))
                    for seg in segments:
                        db.add(BehaviorSegment(
                            animal_id=animal_by_rat[seg.rat_idx].id,
                            start_s=seg.start_s,
                            end_s=seg.end_s,
                            label=seg.label,
                            confidence=seg.confidence,
                        ))

                job.status = JobStatus.DONE
                job.stage = PipelineStage.DONE
                job.progress_pct = 100
                job.finished_at = datetime.utcnow()
                job.error = None

                # RF-24/RN-05: programar borrado del video a 30 días
                video.deletion_date = datetime.utcnow() + timedelta(days=VIDEO_RETENTION_DAYS)

                # Duración: frames procesados / fps (track_video procesa todos los frames por defecto)
                if result["fps"] > 0:
                    video.duration_s = result["total_frames_processed"] / result["fps"]

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

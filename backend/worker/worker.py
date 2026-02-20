import time
from datetime import datetime
from sqlalchemy import text, select
from app.db import SessionLocal
from app.models import JobStatus, Job, Video, ResultSummary
from pipeline.run_analysis import run_analysis

POLL_SECONDS = 2

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
                db.add(job)
                db.commit()

            # Process outside txn
            with SessionLocal() as db:
                job = db.get(Job, job_id)
                video = db.get(Video, job.video_id)

                summaries = run_analysis(video.path)

                db.query(ResultSummary).filter(ResultSummary.job_id == job_id).delete()
                for s in summaries:
                    db.add(ResultSummary(
                        job_id=job_id,
                        rat_idx=s.rat_idx,
                        swim_s=s.swim_s,
                        immobile_s=s.immobile_s,
                        escape_s=s.escape_s
                    ))

                job.status = JobStatus.DONE
                job.finished_at = datetime.utcnow()
                job.error = None
                db.add(job)
                db.commit()

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
            except Exception:
                pass
            time.sleep(POLL_SECONDS)

if __name__ == "__main__":
    main()

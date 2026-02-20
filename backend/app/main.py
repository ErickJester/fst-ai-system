from flask import Flask, request, jsonify
from flask_cors import CORS
from sqlalchemy import select
from .schema import init_db
from .db import SessionLocal
from .models import Session as Sess, Video, Day, Job, JobStatus
from .storage import ensure_dirs, video_path

def create_app() -> Flask:
    app = Flask(__name__)
    CORS(app)
    ensure_dirs()
    init_db()

    @app.get("/health")
    def health():
        return {"ok": True}

    @app.post("/api/sessions")
    def create_session():
        data = request.get_json(force=True)
        name = (data.get("name") or "").strip()
        if not name:
            return jsonify({"error": "name required"}), 400
        notes = data.get("notes")
        with SessionLocal() as db:
            s = Sess(name=name, notes=notes)
            db.add(s)
            db.commit()
            db.refresh(s)
            return {"id": s.id, "name": s.name, "created_at": s.created_at.isoformat()}

    @app.get("/api/sessions")
    def list_sessions():
        with SessionLocal() as db:
            rows = db.execute(select(Sess).order_by(Sess.id.desc()).limit(100)).scalars().all()
            return [{"id": s.id, "name": s.name, "created_at": s.created_at.isoformat()} for s in rows]

    @app.post("/api/videos/upload")
    def upload_video():
        if "file" not in request.files:
            return jsonify({"error": "file required"}), 400
        f = request.files["file"]
        session_id = int(request.form.get("session_id", "0"))
        day = (request.form.get("day", "") or "").strip().upper()
        if session_id <= 0:
            return jsonify({"error": "session_id required"}), 400
        if day not in ("DAY1", "DAY2"):
            return jsonify({"error": "day must be DAY1 or DAY2"}), 400

        with SessionLocal() as db:
            s = db.get(Sess, session_id)
            if not s:
                return jsonify({"error": "session not found"}), 404

            v = Video(session_id=session_id, day=Day(day), filename=f.filename or "video.mp4", path="")
            db.add(v)
            db.commit()
            db.refresh(v)

            path = video_path(session_id, v.id, v.filename)
            f.save(path)
            v.path = path
            db.add(v)
            db.commit()

            return {"video_id": v.id, "session_id": session_id, "day": v.day.value, "filename": v.filename}

    @app.get("/api/sessions/<int:session_id>/videos")
    def list_videos(session_id: int):
        with SessionLocal() as db:
            rows = db.execute(select(Video).where(Video.session_id == session_id).order_by(Video.id.desc())).scalars().all()
            return [{"id": v.id, "day": v.day.value, "filename": v.filename} for v in rows]

    @app.post("/api/jobs")
    def create_job():
        data = request.get_json(force=True)
        video_id = int(data.get("video_id", 0))
        if video_id <= 0:
            return jsonify({"error": "video_id required"}), 400

        with SessionLocal() as db:
            v = db.get(Video, video_id)
            if not v:
                return jsonify({"error": "video not found"}), 404

            q = select(Job).where(Job.video_id == video_id, Job.status.in_([JobStatus.QUEUED, JobStatus.RUNNING])).order_by(Job.id.desc()).limit(1)
            existing = db.execute(q).scalars().first()
            if existing:
                return {"job_id": existing.id, "status": existing.status.value, "video_id": video_id}

            j = Job(video_id=video_id, status=JobStatus.QUEUED)
            db.add(j)
            db.commit()
            db.refresh(j)
            return {"job_id": j.id, "status": j.status.value, "video_id": video_id}

    @app.get("/api/jobs/<int:job_id>")
    def get_job(job_id: int):
        with SessionLocal() as db:
            j = db.get(Job, job_id)
            if not j:
                return jsonify({"error": "job not found"}), 404
            return {
                "job_id": j.id,
                "video_id": j.video_id,
                "status": j.status.value,
                "created_at": j.created_at.isoformat(),
                "started_at": j.started_at.isoformat() if j.started_at else None,
                "finished_at": j.finished_at.isoformat() if j.finished_at else None,
                "error": j.error,
            }

    @app.get("/api/jobs/<int:job_id>/summary")
    def get_summary(job_id: int):
        from .models import ResultSummary
        with SessionLocal() as db:
            rows = db.execute(select(ResultSummary).where(ResultSummary.job_id == job_id).order_by(ResultSummary.rat_idx.asc())).scalars().all()
            return [{"rat_idx": r.rat_idx, "swim_s": r.swim_s, "immobile_s": r.immobile_s, "escape_s": r.escape_s} for r in rows]

    return app

app = create_app()

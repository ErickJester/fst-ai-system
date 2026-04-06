from flask import Flask, request, jsonify
from flask_cors import CORS
from sqlalchemy import select
from .schema import init_db
from .db import SessionLocal
from .models import (
    User, Role, Experiment, Video, Day, Job, JobStatus,
    Animal, BehaviorResult, BehaviorPerMinute, Report, ReportFormat,
    Notification, NotificationType, PipelineStage,
)
from .storage import ensure_dirs, video_path


def create_app() -> Flask:
    app = Flask(__name__)
    CORS(app)
    ensure_dirs()
    init_db()

    # ── Health ────────────────────────────────────────────────────

    @app.get("/health")
    def health():
        return {"ok": True}

    # ── Auth (RF-01, RF-02) ──────────────────────────────────────
    # TODO: implementar JWT real con Flask-JWT-Extended
    # Por ahora endpoints placeholder que el frontend puede consumir

    @app.post("/auth/login")
    def login():
        data = request.get_json(force=True)
        email = (data.get("email") or "").strip().lower()
        password = data.get("password") or ""
        if not email or not password:
            return jsonify({"error": "Credenciales inválidas"}), 401

        with SessionLocal() as db:
            user = db.execute(
                select(User).where(User.email == email)
            ).scalars().first()
            if not user or not user.is_active:
                return jsonify({"error": "Credenciales inválidas"}), 401

            # TODO: verificar con bcrypt
            # if not bcrypt.checkpw(password.encode(), user.password_hash.encode()):
            #     return jsonify({"error": "Credenciales inválidas"}), 401

            from datetime import datetime
            user.last_login = datetime.utcnow()
            db.commit()

            return {
                "token": f"placeholder-jwt-{user.id}",
                "user": {
                    "id": user.id,
                    "email": user.email,
                    "name": user.name,
                    "role": user.role.value,
                    "must_change_password": user.must_change_password,
                },
            }

    @app.post("/auth/logout")
    def logout():
        # TODO: invalidar token JWT en blacklist
        return {"ok": True}

    # ── Experiments (antes sessions) ─────────────────────────────

    @app.post("/experiments")
    def create_experiment():
        data = request.get_json(force=True)
        name = (data.get("name") or "").strip()
        if not name:
            return jsonify({"error": "name required"}), 400

        # TODO: obtener user_id del JWT; por ahora se pasa en body
        user_id = int(data.get("user_id", 1))

        with SessionLocal() as db:
            user = db.get(User, user_id)
            if not user:
                return jsonify({"error": "user not found"}), 404

            from datetime import datetime
            exp_date = None
            if data.get("experiment_date"):
                try:
                    exp_date = datetime.fromisoformat(data["experiment_date"])
                except (ValueError, TypeError):
                    pass

            exp = Experiment(
                user_id=user_id,
                name=name,
                experiment_date=exp_date,
                treatment=data.get("treatment"),
                species=data.get("species"),
                layout=data.get("layout", "auto"),
                notes=data.get("notes"),
            )
            db.add(exp)
            db.commit()
            db.refresh(exp)
            return {
                "id": exp.id,
                "name": exp.name,
                "created_at": exp.created_at.isoformat(),
                "treatment": exp.treatment,
                "layout": exp.layout,
            }

    @app.get("/experiments")
    def list_experiments():
        # TODO: filtrar por user_id del JWT (RN-08)
        user_id = request.args.get("user_id", type=int)
        with SessionLocal() as db:
            q = select(Experiment).order_by(Experiment.id.desc()).limit(100)
            if user_id:
                q = q.where(Experiment.user_id == user_id)
            rows = db.execute(q).scalars().all()
            result = []
            for e in rows:
                result.append({
                    "id": e.id,
                    "name": e.name,
                    "created_at": e.created_at.isoformat(),
                    "treatment": e.treatment,
                    "species": e.species,
                    "layout": e.layout,
                    "experiment_date": e.experiment_date.isoformat() if e.experiment_date else None,
                    "user_id": e.user_id,
                })
            return result

    @app.get("/experiments/<int:exp_id>")
    def get_experiment(exp_id: int):
        with SessionLocal() as db:
            exp = db.get(Experiment, exp_id)
            if not exp:
                return jsonify({"error": "experiment not found"}), 404
            videos = []
            for v in exp.videos:
                latest_job = db.execute(
                    select(Job).where(Job.video_id == v.id).order_by(Job.id.desc()).limit(1)
                ).scalars().first()
                videos.append({
                    "id": v.id,
                    "day": v.day.value,
                    "filename": v.filename,
                    "deletion_date": v.deletion_date.isoformat() if v.deletion_date else None,
                    "job": {
                        "id": latest_job.id,
                        "status": latest_job.status.value,
                        "stage": latest_job.stage.value if latest_job.stage else None,
                        "progress_pct": latest_job.progress_pct,
                        "error": latest_job.error,
                    } if latest_job else None,
                })
            return {
                "id": exp.id,
                "name": exp.name,
                "created_at": exp.created_at.isoformat(),
                "experiment_date": exp.experiment_date.isoformat() if exp.experiment_date else None,
                "treatment": exp.treatment,
                "species": exp.species,
                "layout": exp.layout,
                "notes": exp.notes,
                "user_id": exp.user_id,
                "videos": videos,
            }

    @app.delete("/experiments/<int:exp_id>")
    def delete_experiment(exp_id: int):
        """RF-26: eliminar experimento permanentemente."""
        with SessionLocal() as db:
            exp = db.get(Experiment, exp_id)
            if not exp:
                return jsonify({"error": "experiment not found"}), 404
            # TODO: verificar que el user_id del JWT coincida (o sea admin)
            db.delete(exp)
            db.commit()
            return {"ok": True, "deleted_id": exp_id}

    # ── Videos ───────────────────────────────────────────────────

    @app.post("/experiments/<int:exp_id>/videos")
    def upload_video(exp_id: int):
        if "file" not in request.files:
            return jsonify({"error": "file required"}), 400
        f = request.files["file"]
        day = (request.form.get("day", "") or "").strip().upper()
        if day not in ("DAY1", "DAY2"):
            return jsonify({"error": "day must be DAY1 or DAY2"}), 400

        # RF-08: solo .mp4
        fname = f.filename or "video.mp4"
        if not fname.lower().endswith(".mp4"):
            return jsonify({"error": "Solo se aceptan archivos .mp4"}), 400

        with SessionLocal() as db:
            exp = db.get(Experiment, exp_id)
            if not exp:
                return jsonify({"error": "experiment not found"}), 404

            # RN-03: máximo un video por día por experimento
            existing = db.execute(
                select(Video).where(Video.experiment_id == exp_id, Video.day == Day(day))
            ).scalars().first()
            if existing:
                return jsonify({"error": f"Ya existe un video para {day} en este experimento"}), 409

            v = Video(experiment_id=exp_id, day=Day(day), filename=fname, path="")
            db.add(v)
            db.commit()
            db.refresh(v)

            path = video_path(exp_id, v.id, v.filename)
            f.save(path)
            v.path = path
            db.commit()

            # RF-12: encolar análisis automáticamente
            j = Job(video_id=v.id, status=JobStatus.QUEUED)
            db.add(j)
            db.commit()
            db.refresh(j)

            return {
                "video_id": v.id,
                "experiment_id": exp_id,
                "day": v.day.value,
                "filename": v.filename,
                "job_id": j.id,
            }

    @app.get("/experiments/<int:exp_id>/videos")
    def list_videos(exp_id: int):
        with SessionLocal() as db:
            rows = db.execute(
                select(Video).where(Video.experiment_id == exp_id).order_by(Video.id)
            ).scalars().all()
            return [{
                "id": v.id,
                "day": v.day.value,
                "filename": v.filename,
                "deletion_date": v.deletion_date.isoformat() if v.deletion_date else None,
            } for v in rows]

    # ── Jobs / Status ────────────────────────────────────────────

    @app.get("/experiments/<int:exp_id>/status")
    def experiment_status(exp_id: int):
        """RF-15: progreso del análisis con etapa y porcentaje."""
        with SessionLocal() as db:
            exp = db.get(Experiment, exp_id)
            if not exp:
                return jsonify({"error": "experiment not found"}), 404
            jobs_info = []
            for v in exp.videos:
                latest = db.execute(
                    select(Job).where(Job.video_id == v.id).order_by(Job.id.desc()).limit(1)
                ).scalars().first()
                if latest:
                    jobs_info.append({
                        "video_id": v.id,
                        "day": v.day.value,
                        "job_id": latest.id,
                        "status": latest.status.value,
                        "stage": latest.stage.value if latest.stage else None,
                        "progress_pct": latest.progress_pct,
                        "error": latest.error,
                        "started_at": latest.started_at.isoformat() if latest.started_at else None,
                        "finished_at": latest.finished_at.isoformat() if latest.finished_at else None,
                    })
            return {"experiment_id": exp_id, "jobs": jobs_info}

    # ── Results (RF-18) ──────────────────────────────────────────

    @app.get("/experiments/<int:exp_id>/results")
    def experiment_results(exp_id: int):
        """Tiempos totales por animal y por sesión (RF-18)."""
        with SessionLocal() as db:
            exp = db.get(Experiment, exp_id)
            if not exp:
                return jsonify({"error": "experiment not found"}), 404

            results_by_day = {}
            for v in exp.videos:
                latest_job = db.execute(
                    select(Job).where(Job.video_id == v.id, Job.status == JobStatus.DONE)
                    .order_by(Job.id.desc()).limit(1)
                ).scalars().first()
                if not latest_job:
                    continue
                animals = db.execute(
                    select(Animal).where(Animal.job_id == latest_job.id).order_by(Animal.rat_idx)
                ).scalars().all()
                day_results = []
                for a in animals:
                    for br in a.results:
                        day_results.append({
                            "rat_idx": a.rat_idx,
                            "swim_s": br.swim_s,
                            "immobile_s": br.immobile_s,
                            "escape_s": br.escape_s,
                        })
                results_by_day[v.day.value] = day_results
            return {"experiment_id": exp_id, "results": results_by_day}

    # ── Results per minute (RF-19) ───────────────────────────────

    @app.get("/experiments/<int:exp_id>/results/by-minute")
    def experiment_results_by_minute(exp_id: int):
        """Desglose por minuto para cada animal (RF-19)."""
        with SessionLocal() as db:
            exp = db.get(Experiment, exp_id)
            if not exp:
                return jsonify({"error": "experiment not found"}), 404

            per_minute = {}
            for v in exp.videos:
                latest_job = db.execute(
                    select(Job).where(Job.video_id == v.id, Job.status == JobStatus.DONE)
                    .order_by(Job.id.desc()).limit(1)
                ).scalars().first()
                if not latest_job:
                    continue
                animals = db.execute(
                    select(Animal).where(Animal.job_id == latest_job.id).order_by(Animal.rat_idx)
                ).scalars().all()
                day_data = []
                for a in animals:
                    minutes = db.execute(
                        select(BehaviorPerMinute)
                        .where(BehaviorPerMinute.animal_id == a.id)
                        .order_by(BehaviorPerMinute.minute)
                    ).scalars().all()
                    day_data.append({
                        "rat_idx": a.rat_idx,
                        "per_minute": [
                            {
                                "minute": m.minute,
                                "swim_s": m.swim_s,
                                "immobile_s": m.immobile_s,
                                "escape_s": m.escape_s,
                            }
                            for m in minutes
                        ],
                    })
                per_minute[v.day.value] = day_data
            return {"experiment_id": exp_id, "per_minute": per_minute}

    # ── Comparison Day1 vs Day2 (RF-20) ──────────────────────────

    @app.get("/experiments/<int:exp_id>/comparison")
    def experiment_comparison(exp_id: int):
        """Comparación Día 1 vs Día 2 (RF-20, RN-12)."""
        with SessionLocal() as db:
            exp = db.get(Experiment, exp_id)
            if not exp:
                return jsonify({"error": "experiment not found"}), 404

            days_data = {}
            for v in exp.videos:
                latest_job = db.execute(
                    select(Job).where(Job.video_id == v.id, Job.status == JobStatus.DONE)
                    .order_by(Job.id.desc()).limit(1)
                ).scalars().first()
                if not latest_job:
                    continue
                animals = db.execute(
                    select(Animal).where(Animal.job_id == latest_job.id).order_by(Animal.rat_idx)
                ).scalars().all()
                day_results = []
                for a in animals:
                    for br in a.results:
                        total = br.swim_s + br.immobile_s + br.escape_s
                        day_results.append({
                            "rat_idx": a.rat_idx,
                            "swim_s": br.swim_s,
                            "immobile_s": br.immobile_s,
                            "escape_s": br.escape_s,
                            "swim_pct": round(br.swim_s / total * 100, 1) if total > 0 else 0,
                            "immobile_pct": round(br.immobile_s / total * 100, 1) if total > 0 else 0,
                            "escape_pct": round(br.escape_s / total * 100, 1) if total > 0 else 0,
                        })
                days_data[v.day.value] = day_results

            if "DAY1" not in days_data or "DAY2" not in days_data:
                return jsonify({"error": "Se requieren ambos días procesados para la comparación (RN-12)"}), 400

            return {"experiment_id": exp_id, "comparison": days_data}

    # ── Reports (RF-21) ──────────────────────────────────────────

    @app.get("/experiments/<int:exp_id>/reports/pdf")
    def download_pdf(exp_id: int):
        # TODO: generar PDF con resultados
        return jsonify({"error": "Generación de PDF pendiente de implementar"}), 501

    @app.get("/experiments/<int:exp_id>/reports/csv")
    def download_csv(exp_id: int):
        # TODO: generar CSV con resultados
        return jsonify({"error": "Generación de CSV pendiente de implementar"}), 501

    # ── Admin endpoints (RF-05, RF-27, RF-28, RF-29) ─────────────

    @app.get("/admin/users")
    def admin_list_users():
        # TODO: verificar rol ADMIN del JWT
        role_filter = request.args.get("role")
        status_filter = request.args.get("status")
        with SessionLocal() as db:
            q = select(User).order_by(User.id)
            if role_filter:
                q = q.where(User.role == Role(role_filter.upper()))
            if status_filter == "active":
                q = q.where(User.is_active == True)
            elif status_filter == "inactive":
                q = q.where(User.is_active == False)
            users = db.execute(q).scalars().all()
            return [{
                "id": u.id,
                "email": u.email,
                "name": u.name,
                "role": u.role.value,
                "is_active": u.is_active,
                "last_login": u.last_login.isoformat() if u.last_login else None,
                "created_at": u.created_at.isoformat(),
            } for u in users]

    @app.post("/admin/users")
    def admin_create_user():
        # TODO: verificar rol ADMIN del JWT
        data = request.get_json(force=True)
        email = (data.get("email") or "").strip().lower()
        name = (data.get("name") or "").strip()
        if not email or not name:
            return jsonify({"error": "email and name required"}), 400

        # RNF-05: hash con bcrypt (placeholder hasta agregar dependencia)
        temp_password = data.get("password", "changeme")
        # TODO: password_hash = bcrypt.hashpw(temp_password.encode(), bcrypt.gensalt()).decode()
        password_hash = f"placeholder-hash-{temp_password}"

        with SessionLocal() as db:
            existing = db.execute(select(User).where(User.email == email)).scalars().first()
            if existing:
                return jsonify({"error": "email already registered"}), 409
            u = User(
                email=email,
                password_hash=password_hash,
                name=name,
                role=Role(data.get("role", "INVESTIGADOR").upper()),
                must_change_password=True,
            )
            db.add(u)
            db.commit()
            db.refresh(u)
            return {"id": u.id, "email": u.email, "name": u.name, "role": u.role.value}

    @app.patch("/admin/users/<int:user_id>")
    def admin_update_user(user_id: int):
        # TODO: verificar rol ADMIN del JWT
        data = request.get_json(force=True)
        with SessionLocal() as db:
            u = db.get(User, user_id)
            if not u:
                return jsonify({"error": "user not found"}), 404
            if "name" in data:
                u.name = data["name"]
            if "is_active" in data:
                u.is_active = bool(data["is_active"])
            if "role" in data:
                u.role = Role(data["role"].upper())
            db.commit()
            return {"id": u.id, "email": u.email, "name": u.name, "role": u.role.value, "is_active": u.is_active}

    @app.get("/admin/system")
    def admin_system_metrics():
        """RF-28: métricas del sistema (disco, cola)."""
        import shutil
        from .config import DATA_DIR
        disk = shutil.disk_usage(DATA_DIR)
        with SessionLocal() as db:
            queued = db.execute(
                select(Job).where(Job.status == JobStatus.QUEUED)
            ).scalars().all()
            running = db.execute(
                select(Job).where(Job.status == JobStatus.RUNNING)
            ).scalars().all()
        return {
            "disk_total_gb": round(disk.total / (1024**3), 2),
            "disk_used_gb": round(disk.used / (1024**3), 2),
            "disk_free_gb": round(disk.free / (1024**3), 2),
            "disk_usage_pct": round(disk.used / disk.total * 100, 1),
            "jobs_queued": len(queued),
            "jobs_running": len(running),
        }

    # ── Backward compat: legacy /api/* routes ────────────────────
    # Mantener temporalmente para que el frontend actual no se rompa

    @app.post("/api/sessions")
    def legacy_create_session():
        data = request.get_json(force=True)
        name = (data.get("name") or "").strip()
        if not name:
            return jsonify({"error": "name required"}), 400
        with SessionLocal() as db:
            # Crear con user_id=1 por defecto (legacy)
            exp = Experiment(user_id=1, name=name, notes=data.get("notes"))
            db.add(exp)
            db.commit()
            db.refresh(exp)
            return {"id": exp.id, "name": exp.name, "created_at": exp.created_at.isoformat()}

    @app.get("/api/sessions")
    def legacy_list_sessions():
        with SessionLocal() as db:
            rows = db.execute(select(Experiment).order_by(Experiment.id.desc()).limit(100)).scalars().all()
            return [{"id": e.id, "name": e.name, "created_at": e.created_at.isoformat()} for e in rows]

    @app.post("/api/videos/upload")
    def legacy_upload_video():
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
            exp = db.get(Experiment, session_id)
            if not exp:
                return jsonify({"error": "session not found"}), 404
            v = Video(experiment_id=session_id, day=Day(day), filename=f.filename or "video.mp4", path="")
            db.add(v)
            db.commit()
            db.refresh(v)
            path = video_path(session_id, v.id, v.filename)
            f.save(path)
            v.path = path
            db.commit()
            return {"video_id": v.id, "session_id": session_id, "day": v.day.value, "filename": v.filename}

    @app.get("/api/sessions/<int:session_id>/videos")
    def legacy_list_videos(session_id: int):
        with SessionLocal() as db:
            rows = db.execute(
                select(Video).where(Video.experiment_id == session_id).order_by(Video.id.desc())
            ).scalars().all()
            return [{"id": v.id, "day": v.day.value, "filename": v.filename} for v in rows]

    @app.post("/api/jobs")
    def legacy_create_job():
        data = request.get_json(force=True)
        video_id = int(data.get("video_id", 0))
        if video_id <= 0:
            return jsonify({"error": "video_id required"}), 400
        with SessionLocal() as db:
            v = db.get(Video, video_id)
            if not v:
                return jsonify({"error": "video not found"}), 404
            existing = db.execute(
                select(Job).where(Job.video_id == video_id, Job.status.in_([JobStatus.QUEUED, JobStatus.RUNNING]))
                .order_by(Job.id.desc()).limit(1)
            ).scalars().first()
            if existing:
                return {"job_id": existing.id, "status": existing.status.value, "video_id": video_id}
            j = Job(video_id=video_id, status=JobStatus.QUEUED)
            db.add(j)
            db.commit()
            db.refresh(j)
            return {"job_id": j.id, "status": j.status.value, "video_id": video_id}

    @app.get("/api/jobs/<int:job_id>")
    def legacy_get_job(job_id: int):
        with SessionLocal() as db:
            j = db.get(Job, job_id)
            if not j:
                return jsonify({"error": "job not found"}), 404
            return {
                "job_id": j.id,
                "video_id": j.video_id,
                "status": j.status.value,
                "stage": j.stage.value if j.stage else None,
                "progress_pct": j.progress_pct,
                "created_at": j.created_at.isoformat(),
                "started_at": j.started_at.isoformat() if j.started_at else None,
                "finished_at": j.finished_at.isoformat() if j.finished_at else None,
                "error": j.error,
            }

    @app.get("/api/jobs/<int:job_id>/summary")
    def legacy_get_summary(job_id: int):
        with SessionLocal() as db:
            animals = db.execute(
                select(Animal).where(Animal.job_id == job_id).order_by(Animal.rat_idx)
            ).scalars().all()
            result = []
            for a in animals:
                for br in a.results:
                    result.append({
                        "rat_idx": a.rat_idx,
                        "swim_s": br.swim_s,
                        "immobile_s": br.immobile_s,
                        "escape_s": br.escape_s,
                    })
            return result

    return app


app = create_app()

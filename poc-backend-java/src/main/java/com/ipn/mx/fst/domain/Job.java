package com.ipn.mx.fst.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Equivale a app/models.py :: Job (tabla "jobs").
 *  Ojo: progress_pct es NOT NULL en la tabla y su valor por omisión vive en el
 *  modelo de SQLAlchemy, no en PostgreSQL. Si no se mapea, el INSERT falla.
 *  ddl-auto=validate NO avisa de esto: solo revisa las columnas que sí mapeas. */
@Entity
@Table(name = "jobs")
public class Job {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@Column(name = "video_id", nullable = false)
	private Integer videoId;

	@Column(name = "config_id")
	private Integer configId;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@Column(name = "started_at")
	private LocalDateTime startedAt;

	@Column(name = "finished_at")
	private LocalDateTime finishedAt;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.NAMED_ENUM)
	@Column(nullable = false, columnDefinition = "jobstatus")
	private JobStatus status;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.NAMED_ENUM)
	@Column(columnDefinition = "pipelinestage")
	private PipelineStage stage;

	@Column(name = "progress_pct", nullable = false)
	private Integer progressPct;

	@Column(columnDefinition = "text")
	private String error;

	@Column(name = "output_video_path")
	private String outputVideoPath;

	@PrePersist
	void alCrear() {
		if (createdAt == null) createdAt = Reloj.ahora();
		if (status == null) status = JobStatus.QUEUED;
		if (progressPct == null) progressPct = 0;
	}

	public Integer getId() { return id; }
	public Integer getVideoId() { return videoId; }
	public void setVideoId(Integer v) { this.videoId = v; }
	public JobStatus getStatus() { return status; }
	public void setStatus(JobStatus s) { this.status = s; }
	public Integer getProgressPct() { return progressPct; }
	public void setProgressPct(Integer p) { this.progressPct = p; }
}

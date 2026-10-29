package com.ipn.mx.fst.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Equivale a app/models.py :: Video (tabla "videos"). */
@Entity
@Table(name = "videos")
public class Video {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@Column(name = "experiment_id", nullable = false)
	private Integer experimentId;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.NAMED_ENUM)
	@Column(nullable = false, columnDefinition = "day")
	private Day day;

	@Column(nullable = false, length = 255)
	private String filename;

	@Column(nullable = false, length = 512)
	private String path;

	@Column(name = "duration_s")
	private Double durationS;

	@Column(name = "deletion_date")
	private LocalDateTime deletionDate;

	@PrePersist
	void alCrear() {
		if (createdAt == null) createdAt = Reloj.ahora();
	}

	public Integer getId() { return id; }
	public Integer getExperimentId() { return experimentId; }
	public void setExperimentId(Integer e) { this.experimentId = e; }
	public LocalDateTime getCreatedAt() { return createdAt; }
	public Day getDay() { return day; }
	public void setDay(Day d) { this.day = d; }
	public String getFilename() { return filename; }
	public void setFilename(String f) { this.filename = f; }
	public String getPath() { return path; }
	public void setPath(String p) { this.path = p; }
	public Double getDurationS() { return durationS; }
	public LocalDateTime getDeletionDate() { return deletionDate; }
}

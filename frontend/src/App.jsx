import React, { useEffect, useMemo, useState } from 'react'
import axios from 'axios'

const API_BASE = import.meta.env.VITE_API_BASE || 'http://localhost:8000'

export default function App() {
  const api = useMemo(() => axios.create({ baseURL: API_BASE }), [])
  const [sessions, setSessions] = useState([])
  const [sessionName, setSessionName] = useState('Sesion 1')
  const [selectedSession, setSelectedSession] = useState('')
  const [videos, setVideos] = useState([])
  const [file, setFile] = useState(null)
  const [day, setDay] = useState('DAY1')
  const [jobId, setJobId] = useState(null)
  const [job, setJob] = useState(null)
  const [summary, setSummary] = useState([])

  async function refreshSessions() {
    const res = await api.get('/api/sessions')
    setSessions(res.data)
  }

  async function refreshVideos(sessionId) {
    if (!sessionId) return setVideos([])
    const res = await api.get(`/api/sessions/${sessionId}/videos`)
    setVideos(res.data)
  }

  useEffect(() => { refreshSessions() }, [])

  async function createSession() {
    const res = await api.post('/api/sessions', { name: sessionName })
    setSelectedSession(String(res.data.id))
    await refreshSessions()
    await refreshVideos(res.data.id)
  }

  async function uploadVideo() {
    if (!selectedSession) return alert('Selecciona/crea una sesión')
    if (!file) return alert('Selecciona un archivo')
    const form = new FormData()
    form.append('file', file)
    form.append('session_id', selectedSession)
    form.append('day', day)
    await api.post('/api/videos/upload', form, { headers: { 'Content-Type': 'multipart/form-data' } })
    await refreshVideos(selectedSession)
    setFile(null)
  }

  async function analyze(videoId) {
    const res = await api.post('/api/jobs', { video_id: videoId })
    setJobId(res.data.job_id)
    setJob(null)
    setSummary([])
  }

  useEffect(() => {
    if (!jobId) return
    const t = setInterval(async () => {
      const res = await api.get(`/api/jobs/${jobId}`)
      setJob(res.data)
      if (res.data.status === 'DONE') {
        const sum = await api.get(`/api/jobs/${jobId}/summary`)
        setSummary(sum.data)
        clearInterval(t)
      }
      if (res.data.status === 'FAILED') clearInterval(t)
    }, 1500)
    return () => clearInterval(t)
  }, [jobId])

  return (
    <div style={{ fontFamily: 'system-ui', padding: 16, maxWidth: 980, margin: '0 auto' }}>
      <h2>FST System (MVP)</h2>

      <div style={{ display: 'flex', gap: 12, alignItems: 'center', flexWrap: 'wrap' }}>
        <input value={sessionName} onChange={e => setSessionName(e.target.value)} />
        <button onClick={createSession}>Crear sesión</button>

        <select value={selectedSession} onChange={async e => { 
          setSelectedSession(e.target.value)
          await refreshVideos(e.target.value)
        }}>
          <option value="">-- seleccionar sesión --</option>
          {sessions.map(s => <option key={s.id} value={String(s.id)}>{s.id}: {s.name}</option>)}
        </select>
      </div>

      <hr />

      <div style={{ display: 'flex', gap: 12, alignItems: 'center', flexWrap: 'wrap' }}>
        <input type="file" onChange={e => setFile(e.target.files?.[0] || null)} />
        <select value={day} onChange={e => setDay(e.target.value)}>
          <option value="DAY1">DAY1</option>
          <option value="DAY2">DAY2</option>
        </select>
        <button onClick={uploadVideo}>Subir video</button>
      </div>

      <h3>Videos</h3>
      <ul>
        {videos.map(v => (
          <li key={v.id} style={{ marginBottom: 8 }}>
            <b>{v.id}</b> [{v.day}] {v.filename} {' '}
            <button onClick={() => analyze(v.id)}>Analizar</button>
          </li>
        ))}
      </ul>

      {job && (
        <div style={{ border: '1px solid #ddd', borderRadius: 8, padding: 12 }}>
          <h3>Job</h3>
          <div>ID: {job.job_id} | Estado: <b>{job.status}</b></div>
          {job.error && <pre>{job.error}</pre>}
        </div>
      )}

      {summary.length > 0 && (
        <div style={{ marginTop: 16 }}>
          <h3>Resumen (segundos)</h3>
          <table border="1" cellPadding="6">
            <thead>
              <tr><th>Rata</th><th>Nado</th><th>Inmovilidad</th><th>Escape</th></tr>
            </thead>
            <tbody>
              {summary.map(r => (
                <tr key={r.rat_idx}>
                  <td>{r.rat_idx}</td>
                  <td>{Number(r.swim_s).toFixed(2)}</td>
                  <td>{Number(r.immobile_s).toFixed(2)}</td>
                  <td>{Number(r.escape_s).toFixed(2)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}

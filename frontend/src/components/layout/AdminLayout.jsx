import React from 'react'
import { Outlet } from 'react-router-dom'
import Topbar from './Topbar'

export default function AdminLayout() {
  return (
    <>
      <Topbar variant="admin" />
      <div className="layout">
        <Outlet />
      </div>
    </>
  )
}

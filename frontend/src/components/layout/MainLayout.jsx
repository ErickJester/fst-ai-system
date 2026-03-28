import React from 'react'
import { Outlet } from 'react-router-dom'
import Topbar from './Topbar'

export default function MainLayout() {
  return (
    <>
      <Topbar />
      <Outlet />
    </>
  )
}

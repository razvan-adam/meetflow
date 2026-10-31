import { createBrowserRouter, type RouteObject } from 'react-router'
import { DashboardPage } from '../pages/DashboardPage.tsx'
import { LogInPage } from '../pages/LogInPage.tsx'
import { SignUpPage } from '../pages/SignUpPage.tsx'
import { AppLayout } from './AppLayout.tsx'
import { RequireGuest } from './RequireGuest.tsx'
import { RequireUser } from './RequireUser.tsx'

export const routes: RouteObject[] = [
  {
    element: <RequireGuest />,
    children: [
      { path: '/sign-up', element: <SignUpPage /> },
      { path: '/log-in', element: <LogInPage /> },
    ],
  },
  {
    element: <RequireUser />,
    children: [{ element: <AppLayout />, children: [{ path: '/', element: <DashboardPage /> }] }],
  },
]

export const router = createBrowserRouter(routes)

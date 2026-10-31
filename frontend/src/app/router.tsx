import { createBrowserRouter, type RouteObject } from 'react-router'
import { CreateTeamPage } from '../pages/CreateTeamPage.tsx'
import { DashboardPage } from '../pages/DashboardPage.tsx'
import { JoinPage } from '../pages/JoinPage.tsx'
import { LogInPage } from '../pages/LogInPage.tsx'
import { SignUpPage } from '../pages/SignUpPage.tsx'
import { TeamPage } from '../pages/TeamPage.tsx'
import { AppLayout } from './AppLayout.tsx'
import { RequireGuest } from './RequireGuest.tsx'
import { RequireUser } from './RequireUser.tsx'

export const routes: RouteObject[] = [
  // The invitation link works logged in or out, so it sits outside both guards.
  { path: '/join/:token', element: <JoinPage /> },
  {
    element: <RequireGuest />,
    children: [
      { path: '/sign-up', element: <SignUpPage /> },
      { path: '/log-in', element: <LogInPage /> },
    ],
  },
  {
    element: <RequireUser />,
    children: [
      {
        element: <AppLayout />,
        children: [
          { path: '/', element: <DashboardPage /> },
          { path: '/teams/new', element: <CreateTeamPage /> },
          { path: '/teams/:teamId', element: <TeamPage /> },
        ],
      },
    ],
  },
]

export const router = createBrowserRouter(routes)

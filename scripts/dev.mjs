// Runs MeetFlow locally: the backend (which starts PostgreSQL from backend/compose.yaml)
// and the frontend dev server, which proxies /api to the backend. Stop both with Ctrl+C.
import { spawn } from 'node:child_process'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = fileURLToPath(new URL('..', import.meta.url))
const isWindows = process.platform === 'win32'

const apps = [
  { name: 'backend', cwd: 'backend', command: isWindows ? '.\\mvnw.cmd' : './mvnw', args: ['spring-boot:run'] },
  { name: 'frontend', cwd: 'frontend', command: 'npm', args: ['run', 'dev'] },
]

let stopping = false

function start(app) {
  const options = { cwd: join(root, app.cwd), stdio: 'inherit' }
  // Windows can only start .cmd files (mvnw.cmd, npm.cmd) through a shell, given as one command line.
  return isWindows
    ? spawn([app.command, ...app.args].join(' '), { ...options, shell: true })
    : spawn(app.command, app.args, options)
}

const children = apps.map((app) => {
  const child = start(app)
  child.on('exit', (code) => {
    if (!stopping) {
      console.error(`\n${app.name} exited${code === null ? '' : ` with code ${code}`}; stopping the others.`)
      process.exitCode = code ?? 1
      stopAll()
    }
  })
  return child
})

function stopAll() {
  stopping = true
  for (const child of children) {
    if (child.exitCode !== null) continue
    if (isWindows) {
      // Kill the whole process tree: the shell, Maven and the forked application.
      spawn('taskkill', ['/pid', String(child.pid), '/t', '/f'], { stdio: 'ignore' })
    } else {
      child.kill('SIGTERM')
    }
  }
}

process.on('SIGINT', stopAll)
process.on('SIGTERM', stopAll)

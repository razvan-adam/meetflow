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
  // On Windows, Ctrl+C makes cmd.exe ask "Terminate batch job (Y/N)?" for mvnw.cmd and npm.cmd.
  // Without stdin the question gets no answer to wait for and the batch file ends at once.
  const stdin = isWindows ? 'ignore' : 'inherit'
  const options = { cwd: join(root, app.cwd), stdio: [stdin, 'inherit', 'inherit'] }
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
      stopOthers()
    }
  })
  return child
})

// Ctrl+C reaches every app directly: they share this console on Windows and this process group
// elsewhere. Each one shuts down on its own (the backend also stops PostgreSQL), so only wait.
process.on('SIGINT', () => {
  stopping = true
})

process.on('SIGTERM', () => {
  stopping = true
  for (const child of running()) {
    child.kill('SIGTERM')
  }
})

// One app stopped by itself, so no Ctrl+C was sent to the other.
function stopOthers() {
  stopping = true
  for (const child of running()) {
    if (isWindows) {
      // Windows has no signal to ask a console app to stop: kill the whole process tree
      // (the shell, Maven and the forked application).
      spawn('taskkill', ['/pid', String(child.pid), '/t', '/f'], { stdio: 'ignore' })
    } else {
      child.kill('SIGTERM')
    }
  }
}

function running() {
  return children.filter((child) => child.exitCode === null && child.signalCode === null)
}

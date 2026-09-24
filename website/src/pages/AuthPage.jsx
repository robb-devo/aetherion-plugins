import { useEffect, useId, useState } from 'react'
import { useAuth } from '../auth.jsx'
import { AppShell } from '../components/app/AppShell.jsx'
import { Notice, Spinner, useToast } from '../components/ui.jsx'
import { interpolate } from '../copy.js'
import { useLang } from '../i18n.jsx'
import { errorMessage } from '../lib/api.js'
import { Link, useRouter } from '../router.jsx'

function safeNext(search) {
  const next = new URLSearchParams(search).get('next')
  return next && next.startsWith('/') && !next.startsWith('//') ? next : '/servers'
}

/** Sign-in and registration share one form: username + password, nothing else. */
export default function AuthPage({ mode }) {
  const isRegister = mode === 'register'
  const { copy } = useLang()
  const t = copy.app.auth
  const { status, login, register } = useAuth()
  const { navigate, search } = useRouter()
  const showToast = useToast()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [reveal, setReveal] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(null)
  const userId = useId()
  const passId = useId()
  const next = safeNext(search)

  useEffect(() => {
    if (status === 'signed-in') navigate(next, { replace: true })
  }, [status, next, navigate])

  useEffect(() => {
    document.title = `${isRegister ? t.registerTitle : t.signIn} · Aetherion`
  }, [isRegister, t])

  async function submit(event) {
    event.preventDefault()
    if (busy) return
    setError(null)
    setBusy(true)
    try {
      const user = isRegister ? await register(username.trim(), password) : await login(username.trim(), password)
      showToast(interpolate(isRegister ? t.welcome : t.welcomeBack, { name: user.username }), 'success')
      navigate(next, { replace: true })
    } catch (err) {
      setError(err)
      setBusy(false)
    }
  }

  const fieldError = error?.code
  const userInvalid = ['INVALID_USERNAME', 'USERNAME_TAKEN'].includes(fieldError)
  const passInvalid = ['INVALID_PASSWORD'].includes(fieldError)

  return (
    <AppShell narrow>
      <div className="pt-6 sm:pt-12">
        <div className="panel p-6 sm:p-8">
          <div className="flex items-center gap-3">
            <img src="/favicon.svg" alt="" className="h-9 w-9" />
            <span className="font-display text-xs font-bold tracking-[0.26em] text-mist/80">AETHERION</span>
          </div>
          <h1 className="font-display mt-6 text-2xl font-bold tracking-wide text-white">
            {isRegister ? t.registerTitle : t.signInTitle}
          </h1>
          <p className="mt-1.5 text-sm text-mist/65">{isRegister ? t.registerSub : t.signInSub}</p>

          <form className="mt-7 space-y-5" onSubmit={submit} noValidate>
            <div>
              <label className="label" htmlFor={userId}>
                {t.username}
              </label>
              <input
                id={userId}
                className="field"
                autoComplete="username"
                autoCapitalize="none"
                spellCheck="false"
                maxLength={20}
                required
                value={username}
                aria-invalid={userInvalid}
                aria-describedby={isRegister ? `${userId}-hint` : undefined}
                onChange={(event) => setUsername(event.target.value)}
              />
              {isRegister ? (
                <p id={`${userId}-hint`} className="mt-1.5 text-xs text-ash">
                  {t.usernameHint}
                </p>
              ) : null}
            </div>
            <div>
              <label className="label" htmlFor={passId}>
                {t.password}
              </label>
              <div className="relative">
                <input
                  id={passId}
                  className="field pr-20"
                  type={reveal ? 'text' : 'password'}
                  autoComplete={isRegister ? 'new-password' : 'current-password'}
                  required
                  maxLength={128}
                  value={password}
                  aria-invalid={passInvalid}
                  aria-describedby={isRegister ? `${passId}-hint` : undefined}
                  onChange={(event) => setPassword(event.target.value)}
                />
                <button
                  type="button"
                  className="btn btn-ghost btn-sm absolute top-1/2 right-1 -translate-y-1/2 !min-h-8 text-xs"
                  onClick={() => setReveal((value) => !value)}
                  aria-pressed={reveal}
                >
                  {reveal ? t.hide : t.show}
                </button>
              </div>
              {isRegister ? (
                <p id={`${passId}-hint`} className="mt-1.5 text-xs text-ash">
                  {t.passwordHint}
                </p>
              ) : null}
            </div>

            {error ? <Notice tone="error">{errorMessage(error, copy)}</Notice> : null}

            <button
              type="submit"
              className="btn btn-primary btn-lg notch w-full"
              disabled={busy || !username.trim() || !password}
            >
              {busy ? <Spinner /> : null}
              {isRegister ? t.register : t.signIn}
            </button>
          </form>

          <p className="mt-6 text-center text-sm text-mist/65">
            {isRegister ? t.haveAccount : t.noAccount}{' '}
            <Link
              to={`${isRegister ? '/login' : '/register'}${search}`}
              className="font-bold text-amethyst no-underline hover:text-white"
            >
              {isRegister ? t.signInInstead : t.createOne}
            </Link>
          </p>
        </div>
      </div>
    </AppShell>
  )
}

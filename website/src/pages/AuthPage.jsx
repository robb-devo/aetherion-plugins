import { useEffect, useId, useState } from 'react'
import { useAuth } from '../auth.jsx'
import { AppShell } from '../components/app/AppShell.jsx'
import { BrandImg } from '../components/Brand.jsx'
import { Notice, Spinner, useToast } from '../components/ui.jsx'
import { interpolate } from '../copy.js'
import { useLang } from '../i18n.jsx'
import { asset } from '../lib/asset.js'
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
    <main className="relative isolate grid min-h-screen lg:grid-cols-[1.1fr_1fr]">
      {/* Your own island next to ours: the art half, desktop only */}
      <aside className="relative isolate hidden overflow-hidden lg:flex lg:items-end lg:p-12 xl:p-16">
        <img
          src={asset('world/veil-quay.jpg')}
          alt=""
          className="absolute inset-0 -z-20 h-full w-full object-cover object-[45%_45%] brightness-75"
          draggable="false"
        />
        <div
          className="absolute inset-0 -z-10 bg-[linear-gradient(180deg,rgba(10,16,48,0.75)_0%,rgba(10,16,48,0.1)_35%,rgba(10,16,48,0.85)_100%),linear-gradient(90deg,transparent_70%,#0a1030_100%)]"
          aria-hidden="true"
        />
        <div className="max-w-md">
          <p className="font-display text-3xl leading-tight font-bold tracking-wide text-white">{t.asideTitle}</p>
          <p className="mt-3 text-[0.95rem] leading-relaxed text-mist/75">{t.asideBody}</p>
        </div>
      </aside>
      <AppShell narrow>
      <div className="pt-6 sm:pt-12 lg:pt-16">
        <div className="panel p-6 sm:p-8">
          <div className="flex items-center gap-3">
            <BrandImg className="h-10 w-10" />
            <span className="font-display text-lg font-extrabold text-white">Aetherion</span>
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
              className="font-bold text-bell no-underline hover:text-white"
            >
              {isRegister ? t.signInInstead : t.createOne}
            </Link>
          </p>
        </div>
        <p className="mt-5 text-center text-xs leading-relaxed text-ash lg:hidden">{t.asideBody}</p>
      </div>
    </AppShell>
    </main>
  )
}

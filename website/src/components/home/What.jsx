import { useLang } from '../../i18n.jsx'
import Chapter, { Bank } from './Chapter.jsx'

export default function What() {
  const { copy } = useLang()
  const t = copy.home.what
  return (
    <Chapter id="what" tone="day">
      <Bank title={t.title}>
        <p className="ch-body">{t.body}</p>
        <p className="ch-runin">{t.runIn}</p>
      </Bank>
    </Chapter>
  )
}

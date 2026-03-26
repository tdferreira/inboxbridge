import './InfoHint.css'

function InfoHint({ text }) {
  return (
    <span aria-label={text} className="info-hint" role="note" tabIndex="0">
      <span aria-hidden="true" className="info-hint-icon">i</span>
      <span className="info-hint-tooltip">{text}</span>
    </span>
  )
}

export default InfoHint

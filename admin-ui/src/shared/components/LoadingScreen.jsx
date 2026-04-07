import './LoadingScreen.css'

function LoadingScreen({ label }) {
  return (
    <div className="page-shell">
      <div className="loading-screen-card">{label}</div>
    </div>
  )
}

export default LoadingScreen

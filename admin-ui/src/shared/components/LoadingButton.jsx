import './LoadingButton.css'

/**
 * Reusable button wrapper that shows a small spinner and alternate label while
 * a backend-triggering action is in progress.
 */
function LoadingButton({
  children,
  className = '',
  disabled = false,
  hint,
  isLoading = false,
  loadingLabel,
  type = 'button',
  ...rest
}) {
  const title = hint || (typeof children === 'string' ? children : undefined)

  return (
    <button
      className={className}
      disabled={disabled || isLoading}
      title={title}
      type={type}
      {...rest}
    >
      {isLoading ? (
        <span className="loading-button-content">
          <span aria-hidden="true" className="loading-button-spinner" />
          <span>{loadingLabel || children}</span>
        </span>
      ) : children}
    </button>
  )
}

export default LoadingButton

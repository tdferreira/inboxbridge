function ButtonLink({
  children,
  className = '',
  tone = 'primary',
  ...props
}) {
  return (
    <a
      className={`button-link ${tone} ${className}`.trim()}
      {...props}
    >
      {children}
    </a>
  )
}

export default ButtonLink

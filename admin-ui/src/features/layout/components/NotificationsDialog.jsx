import Banner from '@/shared/components/Banner'
import ModalDialog from '@/shared/components/ModalDialog'
import './NotificationsDialog.css'

function NotificationsDialog({
  notifications,
  onClearAll,
  onClose,
  onDismissNotification,
  onFocusNotification,
  notificationTitle,
  t
}) {
  return (
    <ModalDialog
      closeLabel={t('notifications.close')}
      onClose={onClose}
      size="wide"
      title={t('notifications.title')}
    >
      <div className="notifications-dialog">
        <div className="notifications-dialog-header">
          <p className="section-copy">{t('notifications.copy')}</p>
          <button
            className="secondary"
            disabled={!notifications.length}
            onClick={onClearAll}
            title={t('notifications.clearAllHint')}
            type="button"
          >
            {t('notifications.clearAll')}
          </button>
        </div>
        {notifications.length ? (
          <div className="notifications-history-list">
            {notifications.map((notification) => (
              <Banner
                key={notification.id}
                copyText={notification.copyText}
                copiedLabel={t('common.copied')}
                copyLabel={t('common.copyError')}
                dismissLabel={t('common.dismissNotification')}
                focusLabel={t('common.focusSection')}
                onDismiss={() => onDismissNotification(notification.id)}
                onFocus={notification.targetId ? () => onFocusNotification(notification.targetId) : undefined}
                repeatCount={notification.repeatCount}
                tone={notification.tone}
                title={notificationTitle ? notificationTitle(notification) : ''}
              >
                {notification.message}
              </Banner>
            ))}
          </div>
        ) : (
          <div className="section-copy">{t('notifications.empty')}</div>
        )}
      </div>
    </ModalDialog>
  )
}

export default NotificationsDialog

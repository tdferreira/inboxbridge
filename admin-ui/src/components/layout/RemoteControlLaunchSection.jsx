import ButtonLink from '../common/ButtonLink'
import SectionCard from '../common/SectionCard'
import './RemoteControlLaunchSection.css'

function RemoteControlLaunchSection({ t }) {
  return (
    <SectionCard
      actions={<ButtonLink href="/remote">{t('remote.launchpadAction')}</ButtonLink>}
      className="remote-launch-section"
      copy={t('remote.launchpadCopy')}
      id="remote-control-launch-section"
      title={t('remote.launchpadTitle')}
    >
      <div className="muted-box remote-launch-note">
        {t('remote.launchpadNote')}
      </div>
    </SectionCard>
  )
}

export default RemoteControlLaunchSection

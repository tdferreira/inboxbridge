import InfoHint from '@/shared/components/InfoHint'
import { describeSecretManagementConfigReference } from './secretManagementConfigReferenceGuidance'

export default function SecretManagementConfigReferenceList({
  references = [],
  t,
  listClassName = 'secret-reencryption-config-list',
  chipWrapClassName = 'secret-reencryption-config-chip-wrap',
  chipClassName = 'secret-reencryption-config-chip',
  codeClassName = ''
}) {
  if (!Array.isArray(references) || references.length === 0) {
    return null
  }

  return (
    <div className={listClassName}>
      {references.map((reference) => {
        const guidance = describeSecretManagementConfigReference(reference)
        const hintText = `${t('authSecurity.secretManagementConfigUsedForLabel')} ${guidance.purpose} ${t('authSecurity.secretManagementConfigExampleLabel')} ${guidance.example}`
        return (
          <span className={chipWrapClassName} key={reference}>
            <span className={chipClassName}>
              <code className={codeClassName}>{reference}</code>
              <InfoHint text={hintText} />
            </span>
          </span>
        )
      })}
    </div>
  )
}

ALTER TABLE user_ui_preference
    RENAME COLUMN gmail_destination_collapsed TO destination_mailbox_collapsed;

ALTER TABLE user_ui_preference
    RENAME COLUMN source_bridges_collapsed TO source_email_accounts_collapsed;

UPDATE user_ui_preference
SET user_section_order = replace(replace(user_section_order, 'sourceBridges', 'sourceEmailAccounts'), 'gmail', 'destination')
WHERE user_section_order LIKE '%sourceBridges%'
   OR user_section_order LIKE '%gmail%';
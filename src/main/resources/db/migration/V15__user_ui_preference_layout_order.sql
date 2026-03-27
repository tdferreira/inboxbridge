alter table user_ui_preference
    add column if not exists quick_setup_dismissed boolean not null default false;

alter table user_ui_preference
    add column if not exists user_section_order varchar(255) not null default 'quickSetup,gmail,userPolling,userStats,sourceBridges';

alter table user_ui_preference
    add column if not exists admin_section_order varchar(255) not null default 'systemDashboard,globalStats,userManagement';

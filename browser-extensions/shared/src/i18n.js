export const LANGUAGE_FOLLOW_USER = 'user'
export const SUPPORTED_LANGUAGES = ['en', 'fr', 'de', 'pt-PT', 'pt-BR', 'es']

const dictionaries = {
  en: {
    'language.followUser': 'Use InboxBridge preference',
    'language.label': 'Language',
    'language.hint': 'Follow the signed-in InboxBridge language by default, or override it only for this extension.',
    'language.en': 'English',
    'language.fr': 'Français',
    'language.de': 'Deutsch',
    'language.pt-PT': 'Português (Portugal)',
    'language.pt-BR': 'Português (Brasil)',
    'language.es': 'Español',
    'settings.pageTitle': 'InboxBridge Extension Settings',
    'settings.heading': 'InboxBridge',
    'settings.description': 'Detect the InboxBridge URL, sign in here, and keep the saved extension auth encrypted in this browser.',
    'settings.serverUrl': 'InboxBridge URL',
    'settings.username': 'Username',
    'settings.password': 'Password',
    'settings.passwordPlaceholder': 'Enter your InboxBridge password',
    'settings.passwordHint': 'If this account uses passkeys, enter the username and continue. InboxBridge will finish sign-in in a browser window automatically when required.',
    'settings.theme': 'Theme',
    'settings.themeFollowUser': 'Use InboxBridge preference',
    'settings.themeSystem': 'System',
    'settings.themeLightGreen': 'Light Green',
    'settings.themeLightBlue': 'Light Blue',
    'settings.themeDarkGreen': 'Dark Green',
    'settings.themeDarkBlue': 'Dark Blue',
    'settings.themeHint': 'Follow the signed-in InboxBridge theme by default, or override it only for this extension.',
    'settings.notifications': 'Browser notifications',
    'settings.notifyErrors': 'Notify me when InboxBridge sources need attention',
    'settings.notifyErrorsHint': 'Shows a grouped browser notification when one or more mail sources report errors.',
    'settings.notifyManualPollSuccess': 'Notify me when a manual poll started from this extension finishes',
    'settings.notifyManualPollSuccessHint': 'Shows a browser notification with the imported/fetched summary after the run completes.',
    'settings.signedInHeading': 'Signed in',
    'settings.signedInAs': 'Logged in as {username}.',
    'settings.signedOutHelp': 'Sign in with your InboxBridge URL, username, and password or passkey.',
    'settings.signIn': 'Sign in',
    'settings.signOut': 'Sign out',
    'settings.useCurrentTab': 'Detect URL from active tabs',
    'settings.detectHint': 'Looks through the current browser window for an already open InboxBridge tab and copies its URL here. If several match, the most recently used InboxBridge tab wins.',
    'settings.testConnection': 'Test connection',
    'settings.clear': 'Clear',
    'settings.manualInstall': 'This build',
    'settings.manualInstallCopy': 'You are using the manual-install extension build for this browser. Reload it from the local dist folder after rebuilding or repackaging.',
    'settings.openPrompt': 'Open settings',
    'status.connectedAs': 'Connected as {username}.',
    'status.themeSet': 'Theme preference updated.',
    'status.languageSet': 'Language preference updated.',
    'status.notificationsSet': 'Browser notification preferences updated.',
    'status.detectedUrl': 'Detected the InboxBridge URL from the active browser tab.',
    'status.completeBrowserSignIn': 'Finish the InboxBridge sign-in in the opened browser window.',
    'status.cleared': 'Saved InboxBridge sign-in was cleared from this browser.',
    'status.signedOut': 'Signed out from InboxBridge in this browser.',
    'status.signInFirst': 'Sign in to InboxBridge first.',
    'status.working': 'Working…',
    'status.refreshing': 'Refreshing…',
    'status.starting': 'Starting…',
    'popup.pageTitle': 'InboxBridge',
    'popup.connectionChecking': 'Checking status…',
    'popup.loading': 'Loading',
    'popup.waiting': 'Waiting for InboxBridge…',
    'popup.imported': 'Imported',
    'popup.fetched': 'Fetched',
    'popup.duplicates': 'Duplicates',
    'popup.errors': 'Errors',
    'popup.runPoll': 'Run poll now',
    'popup.refresh': 'Refresh',
    'popup.needsAttention': 'Needs attention',
    'popup.noAttention': 'No sources need attention.',
    'popup.openRemote': 'Open InboxBridge Go',
    'popup.openApp': 'Open InboxBridge',
    'popup.refreshSuccess': 'InboxBridge status refreshed.',
    'popup.signInBeforeRefresh': 'Open Settings to sign in before refreshing this extension.',
    'popup.signIn': 'Sign in',
    'popup.disconnected': 'Disconnected',
    'popup.noStatus': 'No InboxBridge status available',
    'popup.running': 'Running',
    'popup.hasErrors': 'Has errors',
    'popup.healthy': 'Healthy',
    'popup.connectedTo': 'Connected to {userLabel} at {host}',
    'popup.sourceCount': '{count} source',
    'popup.sourceCountPlural': '{count} sources',
    'popup.lastCompleted': 'Last completed {value}',
    'popup.noCompletedRuns': 'No completed polling recorded yet',
    'popup.justNow': 'just now',
    'popup.minutesAgo': '{count} min ago',
    'popup.hoursAgo': '{count} h ago',
    'popup.openSettingsToSignIn': 'Open Settings to sign in and connect this browser extension to InboxBridge.',
    'errors.extensionOriginPermission': 'The extension needs permission to contact that InboxBridge origin.',
    'errors.requiredFields': 'Fill in the InboxBridge URL, username, and password before signing in.',
    'errors.notificationPermission': 'Allow browser notifications to enable this extension alert.',
    'errors.tabPermission': 'Allow tab access to detect the InboxBridge URL from your active browser tab.',
    'errors.openInboxBridgeTab': 'Open an InboxBridge tab first, or enter the URL manually.',
    'errors.requestFailed': 'The extension could not complete that request.',
    'errors.loadFailed': 'The extension could not load InboxBridge status.',
    'notifications.errorTitle': 'InboxBridge needs attention',
    'notifications.errorBody': '{count} source errors: {sources}',
    'notifications.manualPollTitle': 'InboxBridge manual poll finished',
    'notifications.manualPollBody': 'Imported {imported} of {fetched} fetched messages. Sources with errors: {errors}.',
    'notifications.moreSources': '+{count} more'
  },
  fr: {
    'language.followUser': 'Utiliser la préférence InboxBridge',
    'language.label': 'Langue',
    'language.hint': 'Suivez la langue InboxBridge de l’utilisateur connecté par défaut, ou appliquez une surcharge uniquement pour cette extension.',
    'language.en': 'English',
    'language.fr': 'Français',
    'language.de': 'Deutsch',
    'language.pt-PT': 'Português (Portugal)',
    'language.pt-BR': 'Português (Brasil)',
    'language.es': 'Español',
    'settings.pageTitle': 'Paramètres de l’extension InboxBridge',
    'settings.heading': 'InboxBridge',
    'settings.description': 'Détectez l’URL InboxBridge, connectez-vous ici et conservez l’authentification enregistrée chiffrée dans ce navigateur.',
    'settings.serverUrl': 'URL InboxBridge',
    'settings.username': 'Utilisateur',
    'settings.password': 'Mot de passe',
    'settings.passwordPlaceholder': 'Entrez votre mot de passe InboxBridge',
    'settings.passwordHint': 'Si ce compte utilise des passkeys, saisissez le nom d’utilisateur et continuez. InboxBridge terminera automatiquement la connexion dans une fenêtre du navigateur si nécessaire.',
    'settings.theme': 'Thème',
    'settings.themeFollowUser': 'Utiliser la préférence InboxBridge',
    'settings.themeSystem': 'Système',
    'settings.themeLightGreen': 'Clair vert',
    'settings.themeLightBlue': 'Clair bleu',
    'settings.themeDarkGreen': 'Sombre vert',
    'settings.themeDarkBlue': 'Sombre bleu',
    'settings.themeHint': 'Suivez le thème InboxBridge de l’utilisateur connecté par défaut, ou appliquez une surcharge uniquement pour cette extension.',
    'settings.notifications': 'Notifications du navigateur',
    'settings.notifyErrors': 'M’avertir quand des sources InboxBridge nécessitent une attention',
    'settings.notifyErrorsHint': 'Affiche une notification groupée du navigateur quand une ou plusieurs sources de courrier signalent des erreurs.',
    'settings.notifyManualPollSuccess': 'M’avertir quand un polling manuel lancé depuis cette extension se termine',
    'settings.notifyManualPollSuccessHint': 'Affiche une notification du navigateur avec le résumé importé/récupéré à la fin de l’exécution.',
    'settings.signedInHeading': 'Connecté',
    'settings.signedInAs': 'Connecté en tant que {username}.',
    'settings.signedOutHelp': 'Connectez-vous avec votre URL InboxBridge, votre identifiant et votre mot de passe ou passkey.',
    'settings.signIn': 'Se connecter',
    'settings.signOut': 'Se déconnecter',
    'settings.useCurrentTab': 'Détecter l’URL depuis les onglets actifs',
    'settings.detectHint': 'Recherche dans la fenêtre actuelle du navigateur un onglet InboxBridge déjà ouvert et en copie ici l’URL. Si plusieurs correspondent, l’onglet InboxBridge utilisé le plus récemment est choisi.',
    'settings.testConnection': 'Tester la connexion',
    'settings.clear': 'Effacer',
    'settings.manualInstall': 'Cette version',
    'settings.manualInstallCopy': 'Vous utilisez la version à installation manuelle pour ce navigateur. Rechargez-la depuis le dossier dist local après reconstruction ou re-packaging.',
    'settings.openPrompt': 'Ouvrir les paramètres',
    'status.connectedAs': 'Connecté en tant que {username}.',
    'status.themeSet': 'Préférence de thème mise à jour.',
    'status.languageSet': 'Préférence de langue mise à jour.',
    'status.notificationsSet': 'Préférences de notifications du navigateur mises à jour.',
    'status.detectedUrl': 'URL InboxBridge détectée depuis l’onglet actif du navigateur.',
    'status.completeBrowserSignIn': 'Terminez la connexion à InboxBridge dans la fenêtre du navigateur qui vient de s’ouvrir.',
    'status.cleared': 'Les identifiants InboxBridge enregistrés ont été supprimés de ce navigateur.',
    'status.signedOut': 'Déconnecté d’InboxBridge dans ce navigateur.',
    'status.signInFirst': 'Connectez-vous d’abord à InboxBridge.',
    'status.working': 'Traitement…',
    'status.refreshing': 'Actualisation…',
    'status.starting': 'Démarrage…',
    'popup.pageTitle': 'InboxBridge',
    'popup.connectionChecking': 'Vérification de l’état…',
    'popup.loading': 'Chargement',
    'popup.waiting': 'En attente d’InboxBridge…',
    'popup.imported': 'Importés',
    'popup.fetched': 'Récupérés',
    'popup.duplicates': 'Doublons',
    'popup.errors': 'Erreurs',
    'popup.runPoll': 'Lancer un polling',
    'popup.refresh': 'Actualiser',
    'popup.needsAttention': 'À vérifier',
    'popup.noAttention': 'Aucune source ne nécessite d’attention.',
    'popup.openRemote': 'Ouvrir InboxBridge Go',
    'popup.openApp': 'Ouvrir InboxBridge',
    'popup.refreshSuccess': 'Statut InboxBridge actualisé.',
    'popup.signInBeforeRefresh': 'Ouvrez les paramètres pour vous connecter avant d’actualiser cette extension.',
    'popup.signIn': 'Se connecter',
    'popup.disconnected': 'Déconnecté',
    'popup.noStatus': 'Aucun statut InboxBridge disponible',
    'popup.running': 'En cours',
    'popup.hasErrors': 'Contient des erreurs',
    'popup.healthy': 'Sain',
    'popup.connectedTo': 'Connecté à {userLabel} sur {host}',
    'popup.sourceCount': '{count} source',
    'popup.sourceCountPlural': '{count} sources',
    'popup.lastCompleted': 'Dernière exécution terminée {value}',
    'popup.noCompletedRuns': 'Aucun polling terminé enregistré',
    'popup.justNow': 'à l’instant',
    'popup.minutesAgo': 'il y a {count} min',
    'popup.hoursAgo': 'il y a {count} h',
    'popup.openSettingsToSignIn': 'Ouvrez les paramètres pour vous connecter et relier cette extension navigateur à InboxBridge.',
    'errors.extensionOriginPermission': 'L’extension a besoin d’une autorisation pour contacter cette origine InboxBridge.',
    'errors.requiredFields': 'Renseignez l’URL InboxBridge, l’identifiant et le mot de passe avant de vous connecter.',
    'errors.notificationPermission': 'Autorisez les notifications du navigateur pour activer cette alerte de l’extension.',
    'errors.tabPermission': 'Autorisez l’accès aux onglets pour détecter l’URL InboxBridge depuis l’onglet actif.',
    'errors.openInboxBridgeTab': 'Ouvrez d’abord un onglet InboxBridge, ou saisissez l’URL manuellement.',
    'errors.requestFailed': 'L’extension n’a pas pu terminer cette demande.',
    'errors.loadFailed': 'L’extension n’a pas pu charger le statut InboxBridge.',
    'notifications.errorTitle': 'InboxBridge nécessite une attention',
    'notifications.errorBody': '{count} sources en erreur : {sources}',
    'notifications.manualPollTitle': 'Le polling manuel InboxBridge est terminé',
    'notifications.manualPollBody': '{imported} messages importés sur {fetched} récupérés. Sources en erreur : {errors}.',
    'notifications.moreSources': '+{count} de plus'
  },
  de: {
    'language.followUser': 'InboxBridge-Einstellung verwenden',
    'language.label': 'Sprache',
    'language.hint': 'Standardmäßig der InboxBridge-Sprache des angemeldeten Benutzers folgen oder nur für diese Erweiterung überschreiben.',
    'language.en': 'English',
    'language.fr': 'Français',
    'language.de': 'Deutsch',
    'language.pt-PT': 'Português (Portugal)',
    'language.pt-BR': 'Português (Brasil)',
    'language.es': 'Español',
    'settings.pageTitle': 'InboxBridge-Erweiterungseinstellungen',
    'settings.heading': 'InboxBridge',
    'settings.description': 'Erkennen Sie die InboxBridge-URL, melden Sie sich hier an und speichern Sie die Erweiterungsanmeldung verschlüsselt in diesem Browser.',
    'settings.serverUrl': 'InboxBridge-URL',
    'settings.username': 'Benutzername',
    'settings.password': 'Passwort',
    'settings.passwordPlaceholder': 'Ihr InboxBridge-Passwort eingeben',
    'settings.passwordHint': 'Wenn dieses Konto Passkeys verwendet, geben Sie den Benutzernamen ein und fahren Sie fort. InboxBridge schließt die Anmeldung bei Bedarf automatisch in einem Browserfenster ab.',
    'settings.theme': 'Design',
    'settings.themeFollowUser': 'InboxBridge-Einstellung verwenden',
    'settings.themeSystem': 'System',
    'settings.themeLightGreen': 'Hellgrün',
    'settings.themeLightBlue': 'Hellblau',
    'settings.themeDarkGreen': 'Dunkelgrün',
    'settings.themeDarkBlue': 'Dunkelblau',
    'settings.themeHint': 'Standardmäßig dem InboxBridge-Design des angemeldeten Benutzers folgen oder nur für diese Erweiterung überschreiben.',
    'settings.notifications': 'Browser-Benachrichtigungen',
    'settings.notifyErrors': 'Benachrichtigen, wenn InboxBridge-Quellen Aufmerksamkeit benötigen',
    'settings.notifyErrorsHint': 'Zeigt eine zusammengefasste Browser-Benachrichtigung, wenn eine oder mehrere Mailquellen Fehler melden.',
    'settings.notifyManualPollSuccess': 'Benachrichtigen, wenn ein manuell aus dieser Erweiterung gestarteter Polling-Lauf endet',
    'settings.notifyManualPollSuccessHint': 'Zeigt nach Abschluss eine Browser-Benachrichtigung mit der Import-/Abruf-Zusammenfassung.',
    'settings.signedInHeading': 'Angemeldet',
    'settings.signedInAs': 'Angemeldet als {username}.',
    'settings.signedOutHelp': 'Melden Sie sich mit Ihrer InboxBridge-URL, Ihrem Benutzernamen und Passwort oder Passkey an.',
    'settings.signIn': 'Anmelden',
    'settings.signOut': 'Abmelden',
    'settings.useCurrentTab': 'URL aus aktiven Tabs erkennen',
    'settings.detectHint': 'Durchsucht das aktuelle Browserfenster nach einem bereits geöffneten InboxBridge-Tab und übernimmt dessen URL hier. Wenn mehrere passen, wird der zuletzt verwendete InboxBridge-Tab gewählt.',
    'settings.testConnection': 'Verbindung testen',
    'settings.clear': 'Löschen',
    'settings.manualInstall': 'Dieser Build',
    'settings.manualInstallCopy': 'Sie verwenden den manuell installierten Erweiterungs-Build für diesen Browser. Laden Sie ihn nach einem Neuaufbau oder Re-Paketieren aus dem lokalen dist-Ordner neu.',
    'settings.openPrompt': 'Einstellungen öffnen',
    'status.connectedAs': 'Angemeldet als {username}.',
    'status.themeSet': 'Designeinstellung aktualisiert.',
    'status.languageSet': 'Spracheinstellung aktualisiert.',
    'status.notificationsSet': 'Browser-Benachrichtigungseinstellungen aktualisiert.',
    'status.detectedUrl': 'InboxBridge-URL aus dem aktiven Browser-Tab erkannt.',
    'status.completeBrowserSignIn': 'Schließen Sie die InboxBridge-Anmeldung im soeben geöffneten Browserfenster ab.',
    'status.cleared': 'Gespeicherte InboxBridge-Anmeldung wurde aus diesem Browser entfernt.',
    'status.signedOut': 'Von InboxBridge in diesem Browser abgemeldet.',
    'status.signInFirst': 'Melden Sie sich zuerst bei InboxBridge an.',
    'status.working': 'Wird bearbeitet…',
    'status.refreshing': 'Aktualisieren…',
    'status.starting': 'Wird gestartet…',
    'popup.pageTitle': 'InboxBridge',
    'popup.connectionChecking': 'Status wird geprüft…',
    'popup.loading': 'Laden',
    'popup.waiting': 'Warte auf InboxBridge…',
    'popup.imported': 'Importiert',
    'popup.fetched': 'Abgerufen',
    'popup.duplicates': 'Duplikate',
    'popup.errors': 'Fehler',
    'popup.runPoll': 'Polling jetzt starten',
    'popup.refresh': 'Aktualisieren',
    'popup.needsAttention': 'Benötigt Aufmerksamkeit',
    'popup.noAttention': 'Keine Quellen benötigen Aufmerksamkeit.',
    'popup.openRemote': 'InboxBridge Go öffnen',
    'popup.openApp': 'InboxBridge öffnen',
    'popup.refreshSuccess': 'InboxBridge-Status aktualisiert.',
    'popup.signInBeforeRefresh': 'Öffnen Sie die Einstellungen und melden Sie sich an, bevor Sie diese Erweiterung aktualisieren.',
    'popup.signIn': 'Anmelden',
    'popup.disconnected': 'Getrennt',
    'popup.noStatus': 'Kein InboxBridge-Status verfügbar',
    'popup.running': 'Läuft',
    'popup.hasErrors': 'Hat Fehler',
    'popup.healthy': 'In Ordnung',
    'popup.connectedTo': 'Verbunden mit {userLabel} auf {host}',
    'popup.sourceCount': '{count} Quelle',
    'popup.sourceCountPlural': '{count} Quellen',
    'popup.lastCompleted': 'Zuletzt abgeschlossen {value}',
    'popup.noCompletedRuns': 'Noch kein abgeschlossener Polling-Lauf erfasst',
    'popup.justNow': 'gerade eben',
    'popup.minutesAgo': 'vor {count} Min.',
    'popup.hoursAgo': 'vor {count} Std.',
    'popup.openSettingsToSignIn': 'Öffnen Sie die Einstellungen, um sich anzumelden und diese Browser-Erweiterung mit InboxBridge zu verbinden.',
    'errors.extensionOriginPermission': 'Die Erweiterung benötigt eine Berechtigung, um diese InboxBridge-Origin zu kontaktieren.',
    'errors.requiredFields': 'Füllen Sie die InboxBridge-URL, den Benutzernamen und das Passwort aus, bevor Sie sich anmelden.',
    'errors.notificationPermission': 'Erlauben Sie Browser-Benachrichtigungen, um diesen Erweiterungsalarm zu aktivieren.',
    'errors.tabPermission': 'Erlauben Sie den Tab-Zugriff, um die InboxBridge-URL aus dem aktiven Tab zu erkennen.',
    'errors.openInboxBridgeTab': 'Öffnen Sie zuerst einen InboxBridge-Tab oder geben Sie die URL manuell ein.',
    'errors.requestFailed': 'Die Erweiterung konnte diese Anfrage nicht abschließen.',
    'errors.loadFailed': 'Die Erweiterung konnte den InboxBridge-Status nicht laden.',
    'notifications.errorTitle': 'InboxBridge benötigt Aufmerksamkeit',
    'notifications.errorBody': '{count} Quellen mit Fehlern: {sources}',
    'notifications.manualPollTitle': 'InboxBridge-Manuallauf abgeschlossen',
    'notifications.manualPollBody': '{imported} von {fetched} abgerufenen Nachrichten importiert. Quellen mit Fehlern: {errors}.',
    'notifications.moreSources': '+{count} weitere'
  },
  'pt-PT': {
    'language.followUser': 'Usar preferência do InboxBridge',
    'language.label': 'Idioma',
    'language.hint': 'Por predefinição, seguir o idioma do utilizador com sessão iniciada no InboxBridge, ou aplicar uma substituição apenas para esta extensão.',
    'language.en': 'English',
    'language.fr': 'Français',
    'language.de': 'Deutsch',
    'language.pt-PT': 'Português (Portugal)',
    'language.pt-BR': 'Português (Brasil)',
    'language.es': 'Español',
    'settings.pageTitle': 'Definições da extensão InboxBridge',
    'settings.heading': 'InboxBridge',
    'settings.description': 'Detete o URL do InboxBridge, inicie sessão aqui e mantenha a autenticação guardada da extensão encriptada neste navegador.',
    'settings.serverUrl': 'URL do InboxBridge',
    'settings.username': 'Utilizador',
    'settings.password': 'Palavra-passe',
    'settings.passwordPlaceholder': 'Introduza a sua palavra-passe do InboxBridge',
    'settings.passwordHint': 'Se esta conta usar passkeys, introduza o utilizador e continue. O InboxBridge concluirá automaticamente o início de sessão numa janela do navegador quando for necessário.',
    'settings.theme': 'Tema',
    'settings.themeFollowUser': 'Usar preferência do InboxBridge',
    'settings.themeSystem': 'Sistema',
    'settings.themeLightGreen': 'Claro verde',
    'settings.themeLightBlue': 'Claro azul',
    'settings.themeDarkGreen': 'Escuro verde',
    'settings.themeDarkBlue': 'Escuro azul',
    'settings.themeHint': 'Por predefinição, seguir o tema do utilizador com sessão iniciada no InboxBridge, ou aplicar uma substituição apenas para esta extensão.',
    'settings.notifications': 'Notificações do navegador',
    'settings.notifyErrors': 'Notificar quando origens do InboxBridge precisarem de atenção',
    'settings.notifyErrorsHint': 'Mostra uma notificação agrupada do navegador quando uma ou mais origens de email reportam erros.',
    'settings.notifyManualPollSuccess': 'Notificar quando terminar um polling manual iniciado a partir desta extensão',
    'settings.notifyManualPollSuccessHint': 'Mostra uma notificação do navegador com o resumo de importados/obtidos quando a execução termina.',
    'settings.signedInHeading': 'Sessão iniciada',
    'settings.signedInAs': 'Sessão iniciada como {username}.',
    'settings.signedOutHelp': 'Inicie sessão com o URL do InboxBridge, utilizador e palavra-passe ou passkey.',
    'settings.signIn': 'Entrar',
    'settings.signOut': 'Sair',
    'settings.useCurrentTab': 'Detetar URL a partir dos separadores ativos',
    'settings.detectHint': 'Procura na janela atual do navegador um separador do InboxBridge já aberto e copia o respetivo URL para aqui. Se existirem vários, vence o separador do InboxBridge usado mais recentemente.',
    'settings.testConnection': 'Testar ligação',
    'settings.clear': 'Limpar',
    'settings.manualInstall': 'Esta compilação',
    'settings.manualInstallCopy': 'Está a usar a compilação de instalação manual desta extensão para este navegador. Recarregue-a a partir da pasta dist local depois de recompilar ou voltar a empacotar.',
    'settings.openPrompt': 'Abrir definições',
    'status.connectedAs': 'Sessão iniciada como {username}.',
    'status.themeSet': 'Preferência de tema atualizada.',
    'status.languageSet': 'Preferência de idioma atualizada.',
    'status.notificationsSet': 'Preferências de notificações do navegador atualizadas.',
    'status.detectedUrl': 'URL do InboxBridge detetado a partir do separador ativo do navegador.',
    'status.completeBrowserSignIn': 'Conclua o início de sessão do InboxBridge na janela do navegador que acabou de abrir.',
    'status.cleared': 'A autenticação guardada do InboxBridge foi removida deste navegador.',
    'status.signedOut': 'Sessão terminada do InboxBridge neste navegador.',
    'status.signInFirst': 'Inicie sessão primeiro no InboxBridge.',
    'status.working': 'A processar…',
    'status.refreshing': 'A atualizar…',
    'status.starting': 'A iniciar…',
    'popup.pageTitle': 'InboxBridge',
    'popup.connectionChecking': 'A verificar estado…',
    'popup.loading': 'A carregar',
    'popup.waiting': 'À espera do InboxBridge…',
    'popup.imported': 'Importados',
    'popup.fetched': 'Obtidos',
    'popup.duplicates': 'Duplicados',
    'popup.errors': 'Erros',
    'popup.runPoll': 'Executar polling agora',
    'popup.refresh': 'Atualizar',
    'popup.needsAttention': 'Precisa de atenção',
    'popup.noAttention': 'Nenhuma origem precisa de atenção.',
    'popup.openRemote': 'Abrir InboxBridge Go',
    'popup.openApp': 'Abrir InboxBridge',
    'popup.refreshSuccess': 'Estado do InboxBridge atualizado.',
    'popup.signInBeforeRefresh': 'Abra as definições para iniciar sessão antes de atualizar esta extensão.',
    'popup.signIn': 'Iniciar sessão',
    'popup.disconnected': 'Desligado',
    'popup.noStatus': 'Não há estado do InboxBridge disponível',
    'popup.running': 'A correr',
    'popup.hasErrors': 'Tem erros',
    'popup.healthy': 'Saudável',
    'popup.connectedTo': 'Ligado a {userLabel} em {host}',
    'popup.sourceCount': '{count} origem',
    'popup.sourceCountPlural': '{count} origens',
    'popup.lastCompleted': 'Última conclusão {value}',
    'popup.noCompletedRuns': 'Ainda não há execuções concluídas registadas',
    'popup.justNow': 'agora mesmo',
    'popup.minutesAgo': 'há {count} min',
    'popup.hoursAgo': 'há {count} h',
    'popup.openSettingsToSignIn': 'Abra as definições para iniciar sessão e ligar esta extensão do navegador ao InboxBridge.',
    'errors.extensionOriginPermission': 'A extensão precisa de permissão para contactar essa origem do InboxBridge.',
    'errors.requiredFields': 'Preencha o URL do InboxBridge, o utilizador e a palavra-passe antes de iniciar sessão.',
    'errors.notificationPermission': 'Permita notificações do navegador para ativar este alerta da extensão.',
    'errors.tabPermission': 'Permita acesso aos separadores para detetar o URL do InboxBridge a partir do separador ativo do navegador.',
    'errors.openInboxBridgeTab': 'Abra primeiro um separador do InboxBridge ou introduza o URL manualmente.',
    'errors.requestFailed': 'A extensão não conseguiu concluir esse pedido.',
    'errors.loadFailed': 'A extensão não conseguiu carregar o estado do InboxBridge.',
    'notifications.errorTitle': 'O InboxBridge precisa de atenção',
    'notifications.errorBody': '{count} origens com erros: {sources}',
    'notifications.manualPollTitle': 'O polling manual do InboxBridge terminou',
    'notifications.manualPollBody': 'Importadas {imported} de {fetched} mensagens obtidas. Origens com erros: {errors}.',
    'notifications.moreSources': '+{count} adicionais'
  },
  'pt-BR': {
    'language.followUser': 'Usar preferência do InboxBridge',
    'language.label': 'Idioma',
    'language.hint': 'Por padrão, seguir o idioma do usuário conectado no InboxBridge, ou aplicar uma substituição apenas para esta extensão.',
    'language.en': 'English',
    'language.fr': 'Français',
    'language.de': 'Deutsch',
    'language.pt-PT': 'Português (Portugal)',
    'language.pt-BR': 'Português (Brasil)',
    'language.es': 'Español',
    'settings.pageTitle': 'Configurações da extensão InboxBridge',
    'settings.heading': 'InboxBridge',
    'settings.description': 'Detecte a URL do InboxBridge, entre por aqui e mantenha a autenticação salva da extensão criptografada neste navegador.',
    'settings.serverUrl': 'URL do InboxBridge',
    'settings.username': 'Usuário',
    'settings.password': 'Senha',
    'settings.passwordPlaceholder': 'Digite sua senha do InboxBridge',
    'settings.passwordHint': 'Se esta conta usa passkeys, informe o usuário e continue. O InboxBridge concluirá automaticamente o login em uma janela do navegador quando necessário.',
    'settings.theme': 'Tema',
    'settings.themeFollowUser': 'Usar preferência do InboxBridge',
    'settings.themeSystem': 'Sistema',
    'settings.themeLightGreen': 'Claro verde',
    'settings.themeLightBlue': 'Claro azul',
    'settings.themeDarkGreen': 'Escuro verde',
    'settings.themeDarkBlue': 'Escuro azul',
    'settings.themeHint': 'Por padrão, seguir o tema do usuário conectado no InboxBridge, ou aplicar uma substituição apenas para esta extensão.',
    'settings.notifications': 'Notificações do navegador',
    'settings.notifyErrors': 'Notificar quando origens do InboxBridge precisarem de atenção',
    'settings.notifyErrorsHint': 'Mostra uma notificação agrupada do navegador quando uma ou mais origens de email reportarem erros.',
    'settings.notifyManualPollSuccess': 'Notificar quando terminar um polling manual iniciado por esta extensão',
    'settings.notifyManualPollSuccessHint': 'Mostra uma notificação do navegador com o resumo de importados/obtidos quando a execução termina.',
    'settings.signedInHeading': 'Conectado',
    'settings.signedInAs': 'Conectado como {username}.',
    'settings.signedOutHelp': 'Entre com a URL do InboxBridge, usuário e senha ou passkey.',
    'settings.signIn': 'Entrar',
    'settings.signOut': 'Sair',
    'settings.useCurrentTab': 'Detectar URL das abas ativas',
    'settings.detectHint': 'Procura na janela atual do navegador uma aba do InboxBridge já aberta e copia a URL dela para cá. Se houver várias correspondências, vale a aba do InboxBridge usada mais recentemente.',
    'settings.testConnection': 'Testar conexão',
    'settings.clear': 'Limpar',
    'settings.manualInstall': 'Esta build',
    'settings.manualInstallCopy': 'Você está usando a build de instalação manual desta extensão para este navegador. Recarregue-a a partir da pasta dist local depois de reconstruir ou empacotar novamente.',
    'settings.openPrompt': 'Abrir configurações',
    'status.connectedAs': 'Conectado como {username}.',
    'status.themeSet': 'Preferência de tema atualizada.',
    'status.languageSet': 'Preferência de idioma atualizada.',
    'status.notificationsSet': 'Preferências de notificações do navegador atualizadas.',
    'status.detectedUrl': 'URL do InboxBridge detectada a partir da aba ativa do navegador.',
    'status.completeBrowserSignIn': 'Conclua o login do InboxBridge na janela do navegador que acabou de abrir.',
    'status.cleared': 'O login salvo do InboxBridge foi removido deste navegador.',
    'status.signedOut': 'Sessão encerrada do InboxBridge neste navegador.',
    'status.signInFirst': 'Entre primeiro no InboxBridge.',
    'status.working': 'Processando…',
    'status.refreshing': 'Atualizando…',
    'status.starting': 'Iniciando…',
    'popup.pageTitle': 'InboxBridge',
    'popup.connectionChecking': 'Verificando status…',
    'popup.loading': 'Carregando',
    'popup.waiting': 'Aguardando o InboxBridge…',
    'popup.imported': 'Importados',
    'popup.fetched': 'Buscados',
    'popup.duplicates': 'Duplicados',
    'popup.errors': 'Erros',
    'popup.runPoll': 'Executar polling agora',
    'popup.refresh': 'Atualizar',
    'popup.needsAttention': 'Precisa de atenção',
    'popup.noAttention': 'Nenhuma origem precisa de atenção.',
    'popup.openRemote': 'Abrir InboxBridge Go',
    'popup.openApp': 'Abrir InboxBridge',
    'popup.refreshSuccess': 'Status do InboxBridge atualizado.',
    'popup.signInBeforeRefresh': 'Abra as configurações para entrar antes de atualizar esta extensão.',
    'popup.signIn': 'Entrar',
    'popup.disconnected': 'Desconectado',
    'popup.noStatus': 'Nenhum status do InboxBridge disponível',
    'popup.running': 'Em execução',
    'popup.hasErrors': 'Tem erros',
    'popup.healthy': 'Saudável',
    'popup.connectedTo': 'Conectado a {userLabel} em {host}',
    'popup.sourceCount': '{count} origem',
    'popup.sourceCountPlural': '{count} origens',
    'popup.lastCompleted': 'Última conclusão {value}',
    'popup.noCompletedRuns': 'Ainda não há execuções concluídas registradas',
    'popup.justNow': 'agora mesmo',
    'popup.minutesAgo': 'há {count} min',
    'popup.hoursAgo': 'há {count} h',
    'popup.openSettingsToSignIn': 'Abra as configurações para entrar e conectar esta extensão do navegador ao InboxBridge.',
    'errors.extensionOriginPermission': 'A extensão precisa de permissão para acessar essa origem do InboxBridge.',
    'errors.requiredFields': 'Preencha a URL do InboxBridge, o usuário e a senha antes de entrar.',
    'errors.notificationPermission': 'Permita notificações do navegador para ativar este alerta da extensão.',
    'errors.tabPermission': 'Permita acesso às abas para detectar a URL do InboxBridge a partir da aba ativa do navegador.',
    'errors.openInboxBridgeTab': 'Abra primeiro uma aba do InboxBridge ou digite a URL manualmente.',
    'errors.requestFailed': 'A extensão não conseguiu concluir essa solicitação.',
    'errors.loadFailed': 'A extensão não conseguiu carregar o status do InboxBridge.',
    'notifications.errorTitle': 'O InboxBridge precisa de atenção',
    'notifications.errorBody': '{count} origens com erros: {sources}',
    'notifications.manualPollTitle': 'O polling manual do InboxBridge terminou',
    'notifications.manualPollBody': 'Importadas {imported} de {fetched} mensagens obtidas. Origens com erros: {errors}.',
    'notifications.moreSources': '+{count} adicionais'
  },
  es: {
    'language.followUser': 'Usar la preferencia de InboxBridge',
    'language.label': 'Idioma',
    'language.hint': 'Seguir por defecto el idioma del usuario conectado en InboxBridge, o aplicar una anulación solo para esta extensión.',
    'language.en': 'English',
    'language.fr': 'Français',
    'language.de': 'Deutsch',
    'language.pt-PT': 'Português (Portugal)',
    'language.pt-BR': 'Português (Brasil)',
    'language.es': 'Español',
    'settings.pageTitle': 'Configuración de la extensión InboxBridge',
    'settings.heading': 'InboxBridge',
    'settings.description': 'Detecta la URL de InboxBridge, inicia sesión aquí y mantén cifrada en este navegador la autenticación guardada de la extensión.',
    'settings.serverUrl': 'URL de InboxBridge',
    'settings.username': 'Usuario',
    'settings.password': 'Contraseña',
    'settings.passwordPlaceholder': 'Introduce tu contraseña de InboxBridge',
    'settings.passwordHint': 'Si esta cuenta usa passkeys, introduce el usuario y continúa. InboxBridge terminará automáticamente el inicio de sesión en una ventana del navegador cuando sea necesario.',
    'settings.theme': 'Tema',
    'settings.themeFollowUser': 'Usar la preferencia de InboxBridge',
    'settings.themeSystem': 'Sistema',
    'settings.themeLightGreen': 'Claro verde',
    'settings.themeLightBlue': 'Claro azul',
    'settings.themeDarkGreen': 'Oscuro verde',
    'settings.themeDarkBlue': 'Oscuro azul',
    'settings.themeHint': 'Seguir por defecto el tema del usuario conectado en InboxBridge, o aplicar una anulación solo para esta extensión.',
    'settings.notifications': 'Notificaciones del navegador',
    'settings.notifyErrors': 'Avisarme cuando fuentes de InboxBridge necesiten atención',
    'settings.notifyErrorsHint': 'Muestra una notificación agrupada del navegador cuando una o más fuentes de correo informan errores.',
    'settings.notifyManualPollSuccess': 'Avisarme cuando termine un sondeo manual iniciado desde esta extensión',
    'settings.notifyManualPollSuccessHint': 'Muestra una notificación del navegador con el resumen de importados/obtenidos al terminar la ejecución.',
    'settings.signedInHeading': 'Sesión iniciada',
    'settings.signedInAs': 'Sesión iniciada como {username}.',
    'settings.signedOutHelp': 'Inicia sesión con la URL de InboxBridge, tu usuario y contraseña o passkey.',
    'settings.signIn': 'Iniciar sesión',
    'settings.signOut': 'Cerrar sesión',
    'settings.useCurrentTab': 'Detectar URL desde las pestañas activas',
    'settings.detectHint': 'Busca en la ventana actual del navegador una pestaña de InboxBridge ya abierta y copia aquí su URL. Si hay varias coincidencias, se usa la pestaña de InboxBridge utilizada más recientemente.',
    'settings.testConnection': 'Probar conexión',
    'settings.clear': 'Limpiar',
    'settings.manualInstall': 'Esta compilación',
    'settings.manualInstallCopy': 'Estás usando la compilación de instalación manual de esta extensión para este navegador. Vuelve a cargarla desde la carpeta dist local después de reconstruirla o volver a empaquetarla.',
    'settings.openPrompt': 'Abrir configuración',
    'status.connectedAs': 'Conectado como {username}.',
    'status.themeSet': 'Preferencia de tema actualizada.',
    'status.languageSet': 'Preferencia de idioma actualizada.',
    'status.notificationsSet': 'Preferencias de notificaciones del navegador actualizadas.',
    'status.detectedUrl': 'Se detectó la URL de InboxBridge desde la pestaña activa del navegador.',
    'status.completeBrowserSignIn': 'Completa el inicio de sesión de InboxBridge en la ventana del navegador que se acaba de abrir.',
    'status.cleared': 'Se borró de este navegador el inicio de sesión guardado de InboxBridge.',
    'status.signedOut': 'Sesión cerrada de InboxBridge en este navegador.',
    'status.signInFirst': 'Inicia sesión primero en InboxBridge.',
    'status.working': 'Procesando…',
    'status.refreshing': 'Actualizando…',
    'status.starting': 'Iniciando…',
    'popup.pageTitle': 'InboxBridge',
    'popup.connectionChecking': 'Comprobando estado…',
    'popup.loading': 'Cargando',
    'popup.waiting': 'Esperando a InboxBridge…',
    'popup.imported': 'Importados',
    'popup.fetched': 'Recuperados',
    'popup.duplicates': 'Duplicados',
    'popup.errors': 'Errores',
    'popup.runPoll': 'Ejecutar sondeo ahora',
    'popup.refresh': 'Actualizar',
    'popup.needsAttention': 'Necesita atención',
    'popup.noAttention': 'Ninguna fuente necesita atención.',
    'popup.openRemote': 'Abrir InboxBridge Go',
    'popup.openApp': 'Abrir InboxBridge',
    'popup.refreshSuccess': 'Estado de InboxBridge actualizado.',
    'popup.signInBeforeRefresh': 'Abre la configuración para iniciar sesión antes de actualizar esta extensión.',
    'popup.signIn': 'Iniciar sesión',
    'popup.disconnected': 'Desconectado',
    'popup.noStatus': 'No hay estado de InboxBridge disponible',
    'popup.running': 'En ejecución',
    'popup.hasErrors': 'Tiene errores',
    'popup.healthy': 'Correcto',
    'popup.connectedTo': 'Conectado a {userLabel} en {host}',
    'popup.sourceCount': '{count} fuente',
    'popup.sourceCountPlural': '{count} fuentes',
    'popup.lastCompleted': 'Última finalización {value}',
    'popup.noCompletedRuns': 'Todavía no hay ejecuciones completadas registradas',
    'popup.justNow': 'ahora mismo',
    'popup.minutesAgo': 'hace {count} min',
    'popup.hoursAgo': 'hace {count} h',
    'popup.openSettingsToSignIn': 'Abre la configuración para iniciar sesión y conectar esta extensión del navegador con InboxBridge.',
    'errors.extensionOriginPermission': 'La extensión necesita permiso para contactar con ese origen de InboxBridge.',
    'errors.requiredFields': 'Completa la URL de InboxBridge, el usuario y la contraseña antes de iniciar sesión.',
    'errors.notificationPermission': 'Permite las notificaciones del navegador para activar esta alerta de la extensión.',
    'errors.tabPermission': 'Permite el acceso a pestañas para detectar la URL de InboxBridge desde la pestaña activa del navegador.',
    'errors.openInboxBridgeTab': 'Abre primero una pestaña de InboxBridge o introduce la URL manualmente.',
    'errors.requestFailed': 'La extensión no pudo completar esa solicitud.',
    'errors.loadFailed': 'La extensión no pudo cargar el estado de InboxBridge.',
    'notifications.errorTitle': 'InboxBridge necesita atención',
    'notifications.errorBody': '{count} fuentes con errores: {sources}',
    'notifications.manualPollTitle': 'El sondeo manual de InboxBridge terminó',
    'notifications.manualPollBody': 'Se importaron {imported} de {fetched} mensajes obtenidos. Fuentes con errores: {errors}.',
    'notifications.moreSources': '+{count} más'
  }
}

export function normalizeLanguagePreference(value) {
  if (value === LANGUAGE_FOLLOW_USER) {
    return LANGUAGE_FOLLOW_USER
  }
  return SUPPORTED_LANGUAGES.includes(value) ? value : LANGUAGE_FOLLOW_USER
}

export function normalizeSupportedLanguage(value) {
  if (SUPPORTED_LANGUAGES.includes(value)) {
    return value
  }
  const normalized = String(value || '').trim().toLowerCase()
  if (normalized.startsWith('pt-br')) return 'pt-BR'
  if (normalized.startsWith('pt')) return 'pt-PT'
  if (normalized.startsWith('fr')) return 'fr'
  if (normalized.startsWith('de')) return 'de'
  if (normalized.startsWith('es')) return 'es'
  return 'en'
}

export function resolveLanguagePreference(config = {}, browserLanguage = globalThis?.navigator?.language) {
  const preference = normalizeLanguagePreference(config?.language)
  if (preference !== LANGUAGE_FOLLOW_USER) {
    return preference
  }
  if (config?.userLanguage) {
    return normalizeSupportedLanguage(config.userLanguage)
  }
  return normalizeSupportedLanguage(browserLanguage)
}

export function translate(locale, key, params = {}) {
  const resolvedLocale = normalizeSupportedLanguage(locale)
  const template = dictionaries[resolvedLocale]?.[key] || dictionaries.en[key] || key
  return template.replace(/\{(\w+)\}/g, (_match, token) => String(params[token] ?? ''))
}

export function buildLanguageOptions(locale) {
  return [
    { value: LANGUAGE_FOLLOW_USER, label: translate(locale, 'language.followUser') },
    ...SUPPORTED_LANGUAGES.map((value) => ({
      value,
      label: translate(locale, `language.${value}`)
    }))
  ]
}

export function buildThemeOptions(locale) {
  return [
    { value: 'user', label: translate(locale, 'settings.themeFollowUser') },
    { value: 'system', label: translate(locale, 'settings.themeSystem') },
    { value: 'light-green', label: translate(locale, 'settings.themeLightGreen') },
    { value: 'light-blue', label: translate(locale, 'settings.themeLightBlue') },
    { value: 'dark-green', label: translate(locale, 'settings.themeDarkGreen') },
    { value: 'dark-blue', label: translate(locale, 'settings.themeDarkBlue') }
  ]
}

function fillSelect(select, options, selectedValue) {
  if (!select) {
    return
  }
  select.innerHTML = options.map((option) => `<option value="${option.value}">${option.label}</option>`).join('')
  select.value = selectedValue
}

export function localizeOptionsPage(targetDocument, locale, { languageValue, themeValue } = {}) {
  if (!targetDocument) {
    return
  }
  targetDocument.documentElement.lang = normalizeSupportedLanguage(locale)
  targetDocument.title = translate(locale, 'settings.pageTitle')
  setText(targetDocument, 'settings-heading', translate(locale, 'settings.heading'))
  setText(targetDocument, 'settings-description', translate(locale, 'settings.description'))
  setText(targetDocument, 'settings-server-url-label', translate(locale, 'settings.serverUrl'))
  setText(targetDocument, 'settings-username-label', translate(locale, 'settings.username'))
  setText(targetDocument, 'settings-password-label', translate(locale, 'settings.password'))
  setPlaceholder(targetDocument, 'password', translate(locale, 'settings.passwordPlaceholder'))
  setText(targetDocument, 'settings-password-hint', translate(locale, 'settings.passwordHint'))
  setText(targetDocument, 'settings-language-label', translate(locale, 'language.label'))
  setText(targetDocument, 'settings-language-hint', translate(locale, 'language.hint'))
  setText(targetDocument, 'settings-theme-label', translate(locale, 'settings.theme'))
  setText(targetDocument, 'settings-theme-hint', translate(locale, 'settings.themeHint'))
  setText(targetDocument, 'settings-notifications-heading', translate(locale, 'settings.notifications'))
  setText(targetDocument, 'settings-notify-errors-label', translate(locale, 'settings.notifyErrors'))
  setText(targetDocument, 'settings-notify-errors-hint', translate(locale, 'settings.notifyErrorsHint'))
  setText(targetDocument, 'settings-notify-manual-poll-label', translate(locale, 'settings.notifyManualPollSuccess'))
  setText(targetDocument, 'settings-notify-manual-poll-hint', translate(locale, 'settings.notifyManualPollSuccessHint'))
  setText(targetDocument, 'settings-signed-in-heading', translate(locale, 'settings.signedInHeading'))
  setText(targetDocument, 'settings-signed-in-copy', translate(locale, 'settings.signedOutHelp'))
  setText(targetDocument, 'save-button', translate(locale, 'settings.signIn'))
  setText(targetDocument, 'detect-button', translate(locale, 'settings.useCurrentTab'))
  setText(targetDocument, 'settings-detect-hint', translate(locale, 'settings.detectHint'))
  setText(targetDocument, 'test-button', translate(locale, 'settings.testConnection'))
  setText(targetDocument, 'clear-button', translate(locale, 'settings.clear'))
  setText(targetDocument, 'settings-notes-heading', translate(locale, 'settings.manualInstall'))
  setText(targetDocument, 'settings-notes-copy', translate(locale, 'settings.manualInstallCopy'))
  fillSelect(targetDocument.getElementById('language'), buildLanguageOptions(locale), languageValue)
  fillSelect(targetDocument.getElementById('theme'), buildThemeOptions(locale), themeValue)
}

export function localizePopupPage(targetDocument, locale) {
  if (!targetDocument) {
    return
  }
  targetDocument.documentElement.lang = normalizeSupportedLanguage(locale)
  targetDocument.title = translate(locale, 'popup.pageTitle')
  setTitle(targetDocument, 'open-settings', translate(locale, 'settings.openPrompt'))
  setAttribute(targetDocument, 'open-settings', 'aria-label', translate(locale, 'settings.openPrompt'))
  setText(targetDocument, 'connection-copy', translate(locale, 'popup.connectionChecking'))
  setText(targetDocument, 'status-pill', translate(locale, 'popup.loading'))
  setText(targetDocument, 'updated-at', translate(locale, 'popup.waiting'))
  setText(targetDocument, 'metric-imported-label', translate(locale, 'popup.imported'))
  setText(targetDocument, 'metric-fetched-label', translate(locale, 'popup.fetched'))
  setText(targetDocument, 'metric-duplicates-label', translate(locale, 'popup.duplicates'))
  setText(targetDocument, 'metric-errors-label', translate(locale, 'popup.errors'))
  setText(targetDocument, 'run-poll', translate(locale, 'popup.runPoll'))
  setText(targetDocument, 'refresh-status', translate(locale, 'popup.refresh'))
  setText(targetDocument, 'needs-attention-heading', translate(locale, 'popup.needsAttention'))
  setText(targetDocument, 'healthy-state', translate(locale, 'popup.noAttention'))
  setText(targetDocument, 'open-remote', translate(locale, 'popup.openRemote'))
  setText(targetDocument, 'open-app', translate(locale, 'popup.openApp'))
}

function setText(targetDocument, id, text) {
  const element = targetDocument.getElementById(id)
  if (element) {
    element.textContent = text
  }
}

function setTitle(targetDocument, id, text) {
  const element = targetDocument.getElementById(id)
  if (element) {
    element.title = text
  }
}

function setAttribute(targetDocument, id, name, text) {
  const element = targetDocument.getElementById(id)
  if (element) {
    element.setAttribute(name, text)
  }
}

function setPlaceholder(targetDocument, id, text) {
  const element = targetDocument.getElementById(id)
  if (element) {
    element.placeholder = text
  }
}

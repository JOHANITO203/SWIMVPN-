package com.swimvpn.desktop.i18n

import androidx.compose.runtime.staticCompositionLocalOf

/** Supported UI languages — mirrors the Android app (FR / EN / RU). */
enum class Lang(val code: String, val label: String) {
    FR("fr", "Français"),
    EN("en", "English"),
    RU("ru", "Русский");

    companion object {
        fun fromCode(code: String?): Lang = entries.firstOrNull { it.code == code } ?: detectDefault()
        /** First run: follow the OS language if it's one we ship, else French (current behavior). */
        fun detectDefault(): Lang =
            entries.firstOrNull { it.code == java.util.Locale.getDefault().language } ?: FR
    }
}

/**
 * All user-visible UI strings. One immutable instance per language, selected at runtime and
 * provided through [LocalStrings] so a language switch recomposes the whole tree in place.
 * Format strings use %s/%d placeholders applied with String.format.
 */
data class Strings(
    // Navigation rail
    val navHome: String, val navServers: String, val navSubscription: String,
    val navAccount: String, val navSettings: String,
    // Home
    val statusProtected: String, val statusConnecting: String, val statusError: String,
    val statusUnprotected: String, val noServer: String, val tapToImport: String,
    val dataDuration: String, val dataDown: String, val dataUp: String,
    // Servers
    val serversTitle: String, val serversSubtitle: String, val subDefaultTitle: String,
    val gbUsedFmt: String, val expiresFmt: String, val importConfig: String, val testPing: String,
    val emptyServers: String, val dialogImportTitle: String, val dialogFieldLabel: String,
    val dialogDownloading: String, val btnImport: String, val btnCancel: String,
    val importOkFmt: String, val importPartialFmt: String, val importFailFmt: String,
    val fetchFailFmt: String, val formatUnrecognized: String,
    // Subscription
    val subTitle: String, val subPaymentLine: String, val planRecommended: String,
    val planSubscribe: String, val subFootnote: String, val periodDaysFmt: String,
    val feat50gb: String, val feat150gb: String, val feat500gb: String,
    val feat1dev: String, val feat2dev: String, val feat3dev: String,
    val aiAgent: String, val aiAgentRt: String,
    // Account
    val accountTitle: String, val accountSubtitle: String, val support: String,
    // Settings
    val settingsTitle: String, val back: String, val groupConnection: String,
    val tunLabel: String, val tunOnDesc: String, val tunOffDesc: String,
    val adminLabel: String, val adminOn: String, val adminOff: String,
    val groupAbout: String, val aboutEngine: String, val langLabel: String,
    // Servers UX + system integration
    val searchHint: String, val refreshSub: String, val autoSelect: String,
    val groupSystem: String, val autostartLabel: String, val autostartDesc: String,
    val startMinimizedLabel: String, val startMinimizedDesc: String,
    val trayConnect: String, val trayDisconnect: String, val trayOpen: String, val trayQuit: String,
    val killSwitchLabel: String, val killSwitchDesc: String,
)

private val FR = Strings(
    navHome = "Accueil", navServers = "Serveurs", navSubscription = "Abonnement",
    navAccount = "Compte", navSettings = "Réglages",
    statusProtected = "PROTÉGÉ", statusConnecting = "CONNEXION…", statusError = "ERREUR",
    statusUnprotected = "NON PROTÉGÉ", noServer = "Aucun serveur", tapToImport = "Appuie pour importer",
    dataDuration = "Durée", dataDown = "↓ Reçu", dataUp = "↑ Envoyé",
    serversTitle = "Serveurs", serversSubtitle = "Tes configurations importées", subDefaultTitle = "Abonnement",
    gbUsedFmt = "%s / %s Go utilisés", expiresFmt = "Expire le %s",
    importConfig = "＋ Importer une configuration", testPing = "Tester le ping",
    emptyServers = "Aucune config. Colle un lien VLESS/VMess/Trojan/Shadowsocks\nou un lien d'abonnement.",
    dialogImportTitle = "Importer une configuration",
    dialogFieldLabel = "vless:// · vmess:// · trojan:// · ss:// · abonnement (https://…)",
    dialogDownloading = "Téléchargement de l'abonnement…", btnImport = "Importer", btnCancel = "Annuler",
    importOkFmt = "%d configuration(s) importée(s) ✓", importPartialFmt = "%d importée(s), %d échec(s)",
    importFailFmt = "Échec : %s", fetchFailFmt = "Échec téléchargement abonnement : %s",
    formatUnrecognized = "format non reconnu",
    subTitle = "Abonnement", subPaymentLine = "Paiement via SwimPay (RUB · USD · XOF) ou crypto",
    planRecommended = "Recommandé", planSubscribe = "S'abonner",
    subFootnote = "Le paiement s'ouvre sur app.swimvpn.pro. Après paiement, importe la config reçue dans « Serveurs ».",
    periodDaysFmt = "%d jours",
    feat50gb = "50 Go inclus", feat150gb = "150 Go inclus", feat500gb = "500 Go inclus",
    feat1dev = "1 appareil", feat2dev = "2 appareils", feat3dev = "3 appareils",
    aiAgent = "Agent IA", aiAgentRt = "Agent IA temps réel",
    accountTitle = "Compte", accountSubtitle = "Édition desktop (instable)", support = "Support",
    settingsTitle = "Réglages techniques", back = "Retour", groupConnection = "CONNEXION",
    tunLabel = "Tout le trafic (TUN)", tunOnDesc = "Capture tout le trafic (requiert admin)",
    tunOffDesc = "Proxy système — apps compatibles seulement", adminLabel = "Privilèges administrateur",
    adminOn = "Actif ✓", adminOff = "Non — relancer en admin pour le TUN", groupAbout = "À PROPOS",
    aboutEngine = "Moteur Xray partagé avec Android · même prise en charge des liens/configs",
    langLabel = "Langue",
    searchHint = "Rechercher un serveur…", refreshSub = "Actualiser l'abonnement", autoSelect = "Auto · meilleur ping",
    groupSystem = "SYSTÈME", autostartLabel = "Démarrer avec Windows",
    autostartDesc = "Lance SWIMVPN à l'ouverture de session", startMinimizedLabel = "Démarrer minimisé",
    startMinimizedDesc = "Démarre dans la barre système, fenêtre masquée",
    trayConnect = "Connecter", trayDisconnect = "Déconnecter", trayOpen = "Ouvrir", trayQuit = "Quitter",
    killSwitchLabel = "Kill-switch",
    killSwitchDesc = "Bloque tout le trafic si le tunnel tombe (mode TUN). Coupe Internet jusqu'à reconnexion.",
)

private val EN = Strings(
    navHome = "Home", navServers = "Servers", navSubscription = "Subscription",
    navAccount = "Account", navSettings = "Settings",
    statusProtected = "PROTECTED", statusConnecting = "CONNECTING…", statusError = "ERROR",
    statusUnprotected = "UNPROTECTED", noServer = "No server", tapToImport = "Tap to import",
    dataDuration = "Uptime", dataDown = "↓ Received", dataUp = "↑ Sent",
    serversTitle = "Servers", serversSubtitle = "Your imported configurations", subDefaultTitle = "Subscription",
    gbUsedFmt = "%s / %s GB used", expiresFmt = "Expires %s",
    importConfig = "＋ Import a configuration", testPing = "Test ping",
    emptyServers = "No config. Paste a VLESS/VMess/Trojan/Shadowsocks link\nor a subscription link.",
    dialogImportTitle = "Import a configuration",
    dialogFieldLabel = "vless:// · vmess:// · trojan:// · ss:// · subscription (https://…)",
    dialogDownloading = "Downloading subscription…", btnImport = "Import", btnCancel = "Cancel",
    importOkFmt = "%d configuration(s) imported ✓", importPartialFmt = "%d imported, %d failed",
    importFailFmt = "Failed: %s", fetchFailFmt = "Subscription download failed: %s",
    formatUnrecognized = "unrecognized format",
    subTitle = "Subscription", subPaymentLine = "Payment via SwimPay (RUB · USD · XOF) or crypto",
    planRecommended = "Recommended", planSubscribe = "Subscribe",
    subFootnote = "Payment opens on app.swimvpn.pro. After paying, import the config you receive under “Servers”.",
    periodDaysFmt = "%d days",
    feat50gb = "50 GB included", feat150gb = "150 GB included", feat500gb = "500 GB included",
    feat1dev = "1 device", feat2dev = "2 devices", feat3dev = "3 devices",
    aiAgent = "AI agent", aiAgentRt = "Real-time AI agent",
    accountTitle = "Account", accountSubtitle = "Desktop edition (unstable)", support = "Support",
    settingsTitle = "Technical settings", back = "Back", groupConnection = "CONNECTION",
    tunLabel = "All traffic (TUN)", tunOnDesc = "Captures all traffic (requires admin)",
    tunOffDesc = "System proxy — compatible apps only", adminLabel = "Administrator privileges",
    adminOn = "Active ✓", adminOff = "No — relaunch as admin for TUN", groupAbout = "ABOUT",
    aboutEngine = "Xray engine shared with Android · same link/config support",
    langLabel = "Language",
    searchHint = "Search a server…", refreshSub = "Refresh subscription", autoSelect = "Auto · best ping",
    groupSystem = "SYSTEM", autostartLabel = "Start with Windows",
    autostartDesc = "Launch SWIMVPN at sign-in", startMinimizedLabel = "Start minimized",
    startMinimizedDesc = "Start in the system tray, window hidden",
    trayConnect = "Connect", trayDisconnect = "Disconnect", trayOpen = "Open", trayQuit = "Quit",
    killSwitchLabel = "Kill switch",
    killSwitchDesc = "Block all traffic if the tunnel drops (TUN mode). Cuts internet until reconnected.",
)

private val RU = Strings(
    navHome = "Главная", navServers = "Серверы", navSubscription = "Подписка",
    navAccount = "Аккаунт", navSettings = "Настройки",
    statusProtected = "ЗАЩИЩЕНО", statusConnecting = "ПОДКЛЮЧЕНИЕ…", statusError = "ОШИБКА",
    statusUnprotected = "НЕ ЗАЩИЩЕНО", noServer = "Нет сервера", tapToImport = "Нажми, чтобы импортировать",
    dataDuration = "Время", dataDown = "↓ Принято", dataUp = "↑ Отправлено",
    serversTitle = "Серверы", serversSubtitle = "Импортированные конфигурации", subDefaultTitle = "Подписка",
    gbUsedFmt = "%s / %s ГБ использовано", expiresFmt = "Истекает %s",
    importConfig = "＋ Импортировать конфигурацию", testPing = "Проверить пинг",
    emptyServers = "Нет конфигураций. Вставь ссылку VLESS/VMess/Trojan/Shadowsocks\nили ссылку подписки.",
    dialogImportTitle = "Импорт конфигурации",
    dialogFieldLabel = "vless:// · vmess:// · trojan:// · ss:// · подписка (https://…)",
    dialogDownloading = "Загрузка подписки…", btnImport = "Импортировать", btnCancel = "Отмена",
    importOkFmt = "Импортировано: %d ✓", importPartialFmt = "%d импортировано, %d ошибок",
    importFailFmt = "Ошибка: %s", fetchFailFmt = "Ошибка загрузки подписки: %s",
    formatUnrecognized = "формат не распознан",
    subTitle = "Подписка", subPaymentLine = "Оплата через SwimPay (RUB · USD · XOF) или крипто",
    planRecommended = "Рекомендуем", planSubscribe = "Оформить",
    subFootnote = "Оплата откроется на app.swimvpn.pro. После оплаты импортируй полученный конфиг в «Серверы».",
    periodDaysFmt = "%d дней",
    feat50gb = "50 ГБ включено", feat150gb = "150 ГБ включено", feat500gb = "500 ГБ включено",
    feat1dev = "1 устройство", feat2dev = "2 устройства", feat3dev = "3 устройства",
    aiAgent = "AI-агент", aiAgentRt = "AI-агент в реальном времени",
    accountTitle = "Аккаунт", accountSubtitle = "Десктоп-версия (нестабильная)", support = "Поддержка",
    settingsTitle = "Технические настройки", back = "Назад", groupConnection = "ПОДКЛЮЧЕНИЕ",
    tunLabel = "Весь трафик (TUN)", tunOnDesc = "Перехватывает весь трафик (нужны права админа)",
    tunOffDesc = "Системный прокси — только совместимые приложения", adminLabel = "Права администратора",
    adminOn = "Активно ✓", adminOff = "Нет — перезапусти от админа для TUN", groupAbout = "О ПРИЛОЖЕНИИ",
    aboutEngine = "Движок Xray общий с Android · та же поддержка ссылок/конфигов",
    langLabel = "Язык",
    searchHint = "Поиск сервера…", refreshSub = "Обновить подписку", autoSelect = "Авто · лучший пинг",
    groupSystem = "СИСТЕМА", autostartLabel = "Запускать с Windows",
    autostartDesc = "Запуск SWIMVPN при входе в систему", startMinimizedLabel = "Запуск свёрнутым",
    startMinimizedDesc = "Запуск в системном трее, окно скрыто",
    trayConnect = "Подключить", trayDisconnect = "Отключить", trayOpen = "Открыть", trayQuit = "Выход",
    killSwitchLabel = "Kill-switch",
    killSwitchDesc = "Блокирует весь трафик при обрыве туннеля (режим TUN). Отключает интернет до переподключения.",
)

fun stringsFor(lang: Lang): Strings = when (lang) {
    Lang.FR -> FR
    Lang.EN -> EN
    Lang.RU -> RU
}

/** Current UI strings, provided from the active language at the app root. */
val LocalStrings = staticCompositionLocalOf { FR }

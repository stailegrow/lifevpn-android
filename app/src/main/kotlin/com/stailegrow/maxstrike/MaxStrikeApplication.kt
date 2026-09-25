package com.stailegrow.maxstrike

import android.app.Application
import com.stailegrow.maxstrike.core.GeoAssets
import com.stailegrow.maxstrike.core.RoutingStore
import com.stailegrow.maxstrike.core.ServerStore
import com.stailegrow.maxstrike.core.SettingsStore
import com.stailegrow.maxstrike.core.ThemeStore
import com.stailegrow.maxstrike.core.UpdateChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Инициализация сторов здесь, а не только в MainActivity.onCreate() —
 * MaxStrikeVpnService объявлен со своим intent-filter под системный
 * android.net.VpnService (это нужно, чтобы приложение вообще появилось в
 * списке для системного тумблера "Всегда включённый VPN" в настройках
 * Android). Если пользователь его включит, система может поднять сервис
 * напрямую при загрузке телефона, ни разу не запустив MainActivity — тогда
 * ServerStore/SettingsStore/GeoAssets остались бы неинициализированными
 * (dir == null, prefs == null и т.п.) в процессе, который их уже читает.
 * Application.onCreate() вызывается раньше любого компонента приложения
 * (Activity или Service) всегда, так что это безопасное общее место.
 *
 * Сами init() у каждого стора идемпотентны (повторный вызов — no-op), так
 * что оставленные в MainActivity.onCreate() вызовы этому не мешают.
 */
class MaxStrikeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ServerStore.init(this)
        ServerStore.startAutoRefresh()
        ThemeStore.init(this)
        RoutingStore.init(this)
        GeoAssets.init(this)
        SettingsStore.init(this)

        // Версия 2.2.0: базы правил (geosite.dat/geoip.dat) обновляются на
        // каждом запуске приложения безусловно — см. подробный комментарий
        // у GeoAssets.refreshOnLaunchBlocking(). Отдельный CoroutineScope
        // (не привязан к жизненному циклу Activity — Application его не
        // имеет) на Dispatchers.IO: сетевой запрос не должен блокировать
        // onCreate(), а результат (даже неудачный) ничего не блокирует —
        // GeoAssets сам молча промолчит об ошибке и оставит прежние файлы.
        CoroutineScope(Dispatchers.IO).launch {
            GeoAssets.refreshOnLaunchBlocking()
        }

        // Тихая проверка обновлений на GitHub при каждом запуске — по
        // просьбе пользователя, см. подробности в UpdateChecker.kt.
        UpdateChecker.checkOnLaunch()
    }
}

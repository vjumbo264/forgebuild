package com.forgebuild.otgenabler

import android.content.Context
import com.forgebuild.engine.data.CacheFirstStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Symlink-aware sysfs & debugfs OTG hardware node scanner backed by Engine [CacheFirstStore]
 * so previously discovered nodes render immediately from cache on app open and reconcile
 * seamlessly in the background without blanking the UI.
 *
 * Scans (Section 6.2):
 * - /sys/class/usb_role
 * - /sys/class/typec
 * - /sys/class/dual_role_usb
 * - /sys/class/power_supply
 * - /sys/class/regulator (OTG/VBUS/boost regulators)
 * - /sys/class/extcon
 * - /sys/devices/platform (matching usb, otg, typec, tcpc, pd, vbus, charger, mtu3, musb)
 * - /sys/kernel/debug (where readable)
 *
 * Excludes dummy_udc completely and marks read-only nodes as INFO_ONLY ("info only").
 */
class OtgScanner(context: Context) {

    private val cacheStore = object : CacheFirstStore<DiscoveredNode>(
        context = context.applicationContext,
        cacheFileName = "otg_sysfs_nodes_v2.json",
        toJson = { node ->
            JSONObject()
                .put("path", node.path)
                .put("currentValue", node.currentValue)
                .put("permissions", node.permissions)
                .put("isWritable", node.isWritable)
                .put("category", node.category.name)
                .put("detailHint", node.detailHint)
                .put("realPath", node.realPath)
        },
        fromJson = { obj ->
            DiscoveredNode(
                path = obj.optString("path"),
                currentValue = obj.optString("currentValue"),
                permissions = obj.optString("permissions", "-r--r--r--"),
                isWritable = obj.optBoolean("isWritable", false),
                category = NodeCategory.fromNameOrDefault(obj.optString("category", "INFO_ONLY")),
                detailHint = obj.optString("detailHint", ""),
                realPath = obj.optString("realPath", obj.optString("path"))
            )
        },
        fetchRemote = { executeRootScan() },
        keyOf = { it.realPath }
    ) {}

    val discoveredNodes: StateFlow<List<DiscoveredNode>> = cacheStore.state
    val isScanning: StateFlow<Boolean> = cacheStore.refreshing

    init {
        cacheStore.loadFromCache()
    }

    suspend fun scan(): List<DiscoveredNode> {
        cacheStore.refresh()
        return discoveredNodes.value
    }

    companion object {
        private val SCAN_SCRIPT = """
            emit_node() {
              f="${'$'}1"
              reg="${'$'}2"
              [ -f "${'$'}f" ] || return 0
              case "${'$'}f" in
                *dummy_udc*|*/uevent|*/modalias|*/power/runtime_*|*/power/autosuspend*|*/power/control|*/power/wakeup) return 0 ;;
              esac
              rp=${'$'}(readlink -f "${'$'}f" 2>/dev/null || echo "${'$'}f")
              case "${'$'}rp" in
                *dummy_udc*) return 0 ;;
              esac
              perms=${'$'}(ls -ldL "${'$'}f" 2>/dev/null | awk '{print ${'$'}1}')
              [ -z "${'$'}perms" ] && perms="-r--r--r--"
              if [ -w "${'$'}f" ]; then tw=1; else tw=0; fi
              val=${'$'}(head -c 96 "${'$'}f" 2>/dev/null | tr '\r\n\t' '   ' | sed 's/  */ /g; s/^ //; s/ ${'$'}//')
              [ -z "${'$'}val" ] && val="(unreadable/empty)"
              printf 'NODE\t%s\t%s\t%s\t%s\t%s\t%s\n' "${'$'}f" "${'$'}rp" "${'$'}perms" "${'$'}tw" "${'$'}reg" "${'$'}val"
            }

            # 1. /sys/class/usb_role (follow symlinked role switches)
            for d in /sys/class/usb_role/*; do
              [ -e "${'$'}d" ] || continue
              for a in role mode otg_mode; do
                [ -f "${'$'}d/${'$'}a" ] && emit_node "${'$'}d/${'$'}a" ""
              done
            done

            # 2. /sys/class/typec (port0, etc., excluding cable/partner noise)
            for d in /sys/class/typec/port*; do
              [ -e "${'$'}d" ] || continue
              case "${'$'}d" in *partner*|*cable*) continue ;; esac
              for a in data_role power_role port_type preferred_role vconn_source power_operation_mode; do
                [ -f "${'$'}d/${'$'}a" ] && emit_node "${'$'}d/${'$'}a" ""
              done
            done

            # 3. /sys/class/dual_role_usb
            for d in /sys/class/dual_role_usb/*; do
              [ -e "${'$'}d" ] || continue
              for a in mode data_role power_role; do
                [ -f "${'$'}d/${'$'}a" ] && emit_node "${'$'}d/${'$'}a" ""
              done
            done

            # 4. /sys/class/power_supply (OTG switches, boost, usb/otg online & vbus)
            for d in /sys/class/power_supply/*; do
              [ -e "${'$'}d" ] || continue
              psname=${'$'}(basename "${'$'}d")
              for f in "${'$'}d"/*otg* "${'$'}d"/*vbus* "${'$'}d"/*boost* "${'$'}d"/*typec*; do
                [ -f "${'$'}f" ] && emit_node "${'$'}f" "${'$'}psname"
              done
              case "${'$'}psname" in
                *otg*|*usb*|*charger*|*tran*|*mtk*|*pd*|*tcpc*)
                  for a in online status usb_type present voltage_now; do
                    [ -f "${'$'}d/${'$'}a" ] && emit_node "${'$'}d/${'$'}a" "${'$'}psname"
                  done
                  ;;
              esac
            done

            # 5. /sys/class/regulator (inspect regulators whose name matches otg / vbus / usb boost)
            for r in /sys/class/regulator/regulator.*; do
              [ -f "${'$'}r/name" ] || continue
              rname=${'$'}(cat "${'$'}r/name" 2>/dev/null | tr -d '\r\n')
              echo "${'$'}rname" | grep -qiE 'otg|vbus|usb.*boost|boost.*otg|mt63.*otg|rt.*otg' || continue
              for a in state status microvolts num_users; do
                [ -f "${'$'}r/${'$'}a" ] && emit_node "${'$'}r/${'$'}a" "${'$'}rname"
              done
            done

            # 6. /sys/class/extcon
            for d in /sys/class/extcon/*; do
              [ -e "${'$'}d" ] || continue
              ename=${'$'}(cat "${'$'}d/name" 2>/dev/null | tr -d '\r\n')
              [ -f "${'$'}d/state" ] && emit_node "${'$'}d/state" "${'$'}ename"
            done

            # 7. /sys/devices/platform (matching usb, otg, typec, tcpc, pd, vbus, charger, mtu3, musb)
            find /sys/devices/platform -maxdepth 6 -type f \
              \( -iname '*otg*' -o -iname '*vbus*' -o -iname '*boost*' -o -name 'data_role' \
                 -o -name 'power_role' -o -name 'port_type' -o -name 'preferred_role' \
                 -o -name 'role' -o -name 'mode' -o -name 'host_mode' \) 2>/dev/null | \
              grep -iE 'usb|otg|typec|tcpc|pd|vbus|charger|mtu3|musb|tran_battery' | \
              grep -vi 'dummy_udc' | head -n 90 | while IFS= read -r pf; do
                emit_node "${'$'}pf" ""
              done

            # 8. /sys/kernel/debug where readable
            if [ -d /sys/kernel/debug ]; then
              find /sys/kernel/debug -maxdepth 3 -type f \
                \( -iname '*otg*' -o -iname '*vbus*' -o -name 'mode' -o -name 'role' -o -iname '*tcpm*' \) \
                2>/dev/null | grep -iE 'usb|otg|typec|tcpc|pd|vbus|charger|mtu3|musb' | \
                head -n 30 | while IFS= read -r df; do
                  emit_node "${'$'}df" "debugfs"
                done
            fi
        """.trimIndent()

        suspend fun executeRootScan(): List<DiscoveredNode> = withContext(Dispatchers.IO) {
            RootCommander.log(LogType.INFO, "Scanning /sys/class, /sys/devices/platform, and /sys/kernel/debug for OTG/VBUS nodes...")
            val res = RootCommander.execSilent(SCAN_SCRIPT)
            if (!res.isSuccess && res.stdout.isBlank()) {
                RootCommander.log(
                    LogType.ERROR,
                    "Sysfs scan failed: ${res.combinedError}"
                )
                return@withContext emptyList()
            }
            val parsed = SysfsReadbackParser.parseScannerDump(res.stdout)
            val writableCount = parsed.count { it.isControllable }
            val infoCount = parsed.size - writableCount
            RootCommander.log(
                LogType.SUCCESS,
                "Scan complete: $writableCount writable control node(s), $infoCount info-only node(s) (dummy_udc excluded)."
            )
            parsed
        }
    }
}

// drdcd watchdog.
//
// Root is carried by the boot image and this module, but the actual work (loopback console,
// calculator watcher) happens in the drdcd daemon. If that process is killed the features
// silently disappear while root itself keeps working, which looks like a broken install.
//
// This polls every 30 s: two consecutive misses of a task named "drdcd" mean the daemon is
// gone, and it is respawned through call_usermodehelper. Nothing else is touched — no
// hooks, no syscall patching — so the failure modes are limited to "the helper cannot run",
// which only logs a warning.

#include <linux/jiffies.h>
#include <linux/module.h>
#include <linux/printk.h>
#include <linux/sched.h>
#include <linux/sched/signal.h>
#include <linux/string.h>
#include <linux/umh.h>
#include <linux/workqueue.h>

#include "feature/drdcd_watchdog.h"

#define DRDC_DAEMON_PATH "/data/adb/drdcd"
#define DRDC_WATCHDOG_INTERVAL (30 * HZ)
#define DRDC_WATCHDOG_MISSES 2

static struct delayed_work drdc_watchdog_work;
static int drdc_watchdog_misses;
static unsigned int drdc_watchdog_restarts;

static bool drdc_daemon_alive(void)
{
    struct task_struct *task;
    bool alive = false;

    rcu_read_lock();
    for_each_process (task) {
        if (strcmp(task->comm, "drdcd") == 0) {
            alive = true;
            break;
        }
    }
    rcu_read_unlock();

    return alive;
}

static int drdc_spawn_daemon(const char *arg)
{
    char *argv[] = { (char *)DRDC_DAEMON_PATH, (char *)arg, NULL };

    // UMH_WAIT_EXEC only waits for exec, not for the daemon to finish (it never does).
    return call_usermodehelper(argv[0], argv, NULL, UMH_WAIT_EXEC);
}

static void drdc_watchdog_work_fn(struct work_struct *work)
{
    (void)work;

    if (drdc_daemon_alive()) {
        drdc_watchdog_misses = 0;
    } else if (++drdc_watchdog_misses >= DRDC_WATCHDOG_MISSES) {
        int ret;

        drdc_watchdog_misses = 0;
        pr_info("drdc: drdcd is gone, respawning (restart #%u)\n", ++drdc_watchdog_restarts);

        ret = drdc_spawn_daemon("webui");
        if (ret)
            pr_warn("drdc: failed to respawn webui: %d\n", ret);
        ret = drdc_spawn_daemon("calc-watch");
        if (ret)
            pr_warn("drdc: failed to respawn calc-watch: %d\n", ret);
    }

    schedule_delayed_work(&drdc_watchdog_work, DRDC_WATCHDOG_INTERVAL);
}

void drdc_watchdog_init(void)
{
    if (!IS_ENABLED(CONFIG_KSU_DRDC_WATCHDOG))
        return;

    INIT_DELAYED_WORK(&drdc_watchdog_work, drdc_watchdog_work_fn);
    schedule_delayed_work(&drdc_watchdog_work, DRDC_WATCHDOG_INTERVAL);

    pr_info("drdc: daemon watchdog armed\n");
}

void drdc_watchdog_exit(void)
{
    if (!IS_ENABLED(CONFIG_KSU_DRDC_WATCHDOG))
        return;

    cancel_delayed_work_sync(&drdc_watchdog_work);
}

// Mitigation for CVE-2026-43499 (GhostLock): a use-after-free in the futex
// priority-inheritance path (`remove_waiter()` / rtmutex proxy-lock rollback).
//
// A KernelSU module is userspace only and cannot patch rtmutex.c, so this lives in the
// kernel module: block PI futex operations for app UIDs before they reach the vulnerable
// code. Normal (non-PI) futexes are untouched.
//
// Enabled by default. Disable it for one boot by creating
// `/data/adb/drdc/anti_lpe_off`, or at runtime with
// `echo 0 > /sys/module/drdc/parameters/anti_lpe`.

#include <asm/unistd.h>
#include <linux/cred.h>
#include <linux/err.h>
#include <linux/errno.h>
#include <linux/fcntl.h>
#include <linux/fs.h>
#include <linux/futex.h>
#include <linux/jump_label.h>
#include <linux/kstrtox.h>
#include <linux/moduleparam.h>
#include <linux/namei.h>
#include <linux/printk.h>
#include <linux/ptrace.h>
#include <linux/sched.h>
#include <linux/types.h>

#include "arch.h"
#include "feature/anti_lpe.h"
#include "hook/syscall_hook.h"
#include "klog.h" // IWYU pragma: keep
#include "manager/manager_identity.h"

#define ANTI_LPE_ON_FLAG "/data/adb/drdc/anti_lpe"
#define ANTI_LPE_OFF_FLAG "/data/adb/drdc/anti_lpe_off"

// 0x7f is FUTEX_CMD_MASK; define it locally so we do not depend on internal headers.
#define DRDC_FUTEX_CMD_MASK 0x7f

static DEFINE_STATIC_KEY_FALSE(drdc_anti_lpe_key);
static bool drdc_anti_lpe_param = true;

static long (*orig_sys_futex)(const struct pt_regs *regs);

static void drdc_anti_lpe_set(bool enable)
{
    drdc_anti_lpe_param = enable;
    if (enable)
        static_branch_enable(&drdc_anti_lpe_key);
    else
        static_branch_disable(&drdc_anti_lpe_key);
}

static int drdc_anti_lpe_set_param(const char *val, const struct kernel_param *kp)
{
    bool enable;
    int ret = kstrtobool(val, &enable);
    if (ret)
        return ret;
    drdc_anti_lpe_set(enable);
    pr_info("anti_lpe: %s\n", enable ? "enabled" : "disabled");
    return 0;
}

static const struct kernel_param_ops drdc_anti_lpe_ops = {
    .set = drdc_anti_lpe_set_param,
    .get = param_get_bool,
};

module_param_cb(anti_lpe, &drdc_anti_lpe_ops, &drdc_anti_lpe_param, 0644);
MODULE_PARM_DESC(anti_lpe, "Block PI futex operations for app UIDs (CVE-2026-43499 mitigation)");

static bool drdc_file_exists(const char *path)
{
    struct file *fp = filp_open(path, O_RDONLY, 0);
    if (IS_ERR(fp))
        return false;
    filp_close(fp, NULL);
    return true;
}

void drdc_anti_lpe_apply_boot_flag(void)
{
    bool enable = drdc_anti_lpe_param;

    if (drdc_file_exists(ANTI_LPE_OFF_FLAG)) {
        enable = false;
    } else if (drdc_file_exists(ANTI_LPE_ON_FLAG)) {
        enable = true;
    }

    drdc_anti_lpe_set(enable);
    pr_info("anti_lpe: %s\n", enable ? "enabled" : "disabled");
}

static inline bool drdc_is_pi_futex_op(unsigned int op)
{
    switch (op & DRDC_FUTEX_CMD_MASK) {
    case FUTEX_LOCK_PI:
    case FUTEX_UNLOCK_PI:
    case FUTEX_TRYLOCK_PI:
    case FUTEX_WAIT_REQUEUE_PI:
    case FUTEX_CMP_REQUEUE_PI:
        return true;
    default:
        return false;
    }
}

static long drdc_sys_futex_hook(const struct pt_regs *regs)
{
    if (static_branch_unlikely(&drdc_anti_lpe_key)) {
        uid_t uid = current_uid().val;
        // Apps (uid >= 10000) are the untrusted surface; root/system/shell keep full
        // futex functionality. Isolated app processes are >= 90000 and covered too.
        // Never filter the manager or its su daemon: if we did, a bad filter could lock
        // the user out of the root shell needed to turn it off.
        if (uid >= 10000 && !is_uid_manager(uid) && strcmp(current->comm, "drdcd") != 0) {
            unsigned int op = (unsigned int)PT_REGS_PARM2(regs);
            if (drdc_is_pi_futex_op(op)) {
                pr_info_ratelimited(
                    "anti_lpe: blocked futex op=0x%x uid=%d comm=%s\n",
                    op, uid, current->comm);
                return -ENOSYS;
            }
        }
    }
    return orig_sys_futex(regs);
}

void drdc_anti_lpe_init(void)
{
#ifdef __NR_futex
    // In non-late-load builds /data is not mounted yet, so this usually keeps the default
    // (enabled). on_post_fs_data() applies the flag files again once /data is ready.
    drdc_anti_lpe_apply_boot_flag();
    if (drdc_syscall_table)
        drdc_syscall_table_hook(__NR_futex, drdc_sys_futex_hook, &orig_sys_futex);
#endif
}

void drdc_anti_lpe_exit(void)
{
#ifdef __NR_futex
    if (drdc_syscall_table)
        drdc_syscall_table_unhook(__NR_futex);
#endif
}

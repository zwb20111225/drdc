#include <linux/export.h>
#include <linux/fs.h>
#include <linux/kobject.h>
#include <linux/module.h>
#include <linux/rcupdate.h>
#include <linux/sched.h>
#include <linux/workqueue.h>
#include <linux/moduleparam.h>

#include "policy/allowlist.h"
#include "policy/app_profile.h"
#include "policy/feature.h"
#include "klog.h" // IWYU pragma: keep
#include "manager/manager_observer.h"
#include "manager/throne_tracker.h"
#include "hook/syscall_hook_manager.h"
#include "hook/lsm_hook.h"
#include "runtime/drdcd.h"
#include "runtime/drdcd_boot.h"
#include "feature/sulog.h"
#include "feature/anti_lpe.h"
#include "feature/drdcd_watchdog.h"
#include "supercall/supercall.h"
#include "drdc.h"
#include "infra/file_wrapper.h"
#include "selinux/selinux.h"
#include "hook/syscall_hook.h"
#include "feature/adb_root.h"
#include "feature/selinux_hide.h"
#include "feature/module_blacklist.h"
#include "feature/shell_su_adb_only.h"
#include "infra/symbol_resolver.h"
#include "preinit/preinit.h"

#if defined(__x86_64__) && !defined(CONFIG_KSU_X86_PATCH_SYSCALL_DISPATCHER)
#include <asm/cpufeature.h>
#include <linux/version.h>
#ifndef X86_FEATURE_INDIRECT_SAFE
#error "FATAL: Your kernel is missing the indirect syscall bypass patches!"
#endif
#endif

// workaround for A12-5.10 kernel
// Some third-party kernel (e.g. linegaeOS) uses wrong toolchain, which supports
// CC_HAVE_STACKPROTECTOR_SYSREG while gki's toolchain doesn't.
// Therefore, drdc lkm, which uses gki toolchain, requires this __stack_chk_guard,
// while those third-party kernel can't provide.
// Thus, we manually provide it instead of using kernel's
#if defined(CONFIG_STACKPROTECTOR) &&                                                                                  \
    (defined(CONFIG_ARM64) && defined(MODULE) && !defined(CONFIG_STACKPROTECTOR_PER_TASK))
#include <linux/stackprotector.h>
#include <linux/random.h>
unsigned long __stack_chk_guard __ro_after_init __attribute__((visibility("hidden")));

__attribute__((no_stack_protector)) void __init drdc_setup_stack_chk_guard()
{
    unsigned long canary;

    /* Try to get a semi random initial value. */
    get_random_bytes(&canary, sizeof(canary));
    canary ^= LINUX_VERSION_CODE;
    canary &= CANARY_MASK;
    __stack_chk_guard = canary;
}

__attribute__((naked)) int __init drdc_init_early(void)
{
    asm("mov x19, x30;\n"
        "bl drdc_setup_stack_chk_guard;\n"
        "mov x30, x19;\n"
        "b drdc_init;\n");
}
#define NEED_OWN_STACKPROTECTOR 1
#else
#define NEED_OWN_STACKPROTECTOR 0
#endif

struct cred *drdc_cred;
bool drdc_late_loaded;

#ifdef CONFIG_KSU_DEBUG
bool allow_shell = true;
#else
bool allow_shell = false;
#endif
module_param(allow_shell, bool, 0);

bool drdc_no_custom_rc = false;
module_param_named(norc, drdc_no_custom_rc, bool, 0);

#ifdef MODULE
bool drdc_bundled = false;
module_param_named(bundled, drdc_bundled, bool, 0);
#endif

int __init drdc_init(void)
{
    pr_info("welcome to DRDC version " __stringify(KERNEL_SU_VERSION) ", package name " KSU_PACKAGE_NAME "\n");
#if defined(__x86_64__) && !defined(CONFIG_KSU_X86_PATCH_SYSCALL_DISPATCHER)
    // If the kernel has the hardening patch, X86_FEATURE_INDIRECT_SAFE must be set
    if (!boot_cpu_has(X86_FEATURE_INDIRECT_SAFE)) {
        pr_alert("*************************************************************");
        pr_alert("**     NOTICE NOTICE NOTICE NOTICE NOTICE NOTICE NOTICE    **");
        pr_alert("**                                                         **");
        pr_alert("**        X86_FEATURE_INDIRECT_SAFE is not enabled!        **");
        pr_alert("**      DRDC will abort initialization to prevent      **");
        pr_alert("**                     kernel panic.                       **");
        pr_alert("**                                                         **");
        pr_alert("**     NOTICE NOTICE NOTICE NOTICE NOTICE NOTICE NOTICE    **");
        pr_alert("*************************************************************");
        return -ENOSYS;
    }
#endif

#ifdef MODULE
    drdc_late_loaded = (current->pid != 1);
#else
    drdc_late_loaded = false;
#endif

#ifdef CONFIG_KSU_DEBUG
    pr_alert("*************************************************************");
    pr_alert("**     NOTICE NOTICE NOTICE NOTICE NOTICE NOTICE NOTICE    **");
    pr_alert("**                                                         **");
    pr_alert("**         You are running DRDC in DEBUG mode          **");
    pr_alert("**                                                         **");
    pr_alert("**     NOTICE NOTICE NOTICE NOTICE NOTICE NOTICE NOTICE    **");
    pr_alert("*************************************************************");
#endif
    if (allow_shell) {
        pr_alert("shell is allowed at init!");
    }

    drdc_cred = prepare_creds();
    if (!drdc_cred) {
        pr_err("prepare cred failed!\n");
        return -ENOSYS;
    }

    drdc_init_symbol_resolver();
    drdc_syscall_hook_init();

    // Block modules early, before vendor modules are loaded in second stage.
    drdc_module_blacklist_init();

    drdc_feature_init();
    drdc_sulog_init();
    drdc_adb_root_init();
    drdc_lsm_hook_init();
    drdc_selinux_hide_init();
    drdc_shell_su_adb_only_init();

    drdc_supercalls_init();
    drdc_app_profile_init();

    if (drdc_late_loaded) {
        pr_info("late load mode, skipping kprobe hooks\n");

        apply_drdc_rules();
        cache_sid();
        setup_drdc_cred();

        // Grant current process (drdcd late-load) root
        // with KSU SELinux domain before enforcing SELinux, so it
        // can continue to access /data/app etc. after enforcement.
        escape_to_root_for_init();

        drdc_allowlist_init();
        drdc_load_allow_list();

        drdc_syscall_hook_manager_init();
        drdc_anti_lpe_init();

        drdc_throne_tracker_init();
        drdc_observer_init();
        drdc_file_wrapper_init();

        drdc_boot_completed = true;
        track_throne(false);

        drdc_feature_apply_forced();

        if (!getenforce()) {
            pr_info("Permissive SELinux, enforcing\n");
            setenforce(true);
        }

    } else {
        drdc_syscall_hook_manager_init();
        drdc_anti_lpe_init();

        drdc_allowlist_init();

        drdc_throne_tracker_init();

        drdc_drdcd_init();

        drdc_watchdog_init();

        drdc_preinit_init();

        drdc_file_wrapper_init();
    }

#ifdef MODULE
#ifndef CONFIG_KSU_DEBUG
    kobject_del(&THIS_MODULE->mkobj.kobj);
#endif
#endif
    return 0;
}

void __exit drdc_exit(void)
{
    // Phase 1: Stop all hooks first to prevent new callbacks
    drdc_anti_lpe_exit();
    drdc_syscall_hook_manager_exit();

    drdc_supercalls_exit();

    if (!drdc_late_loaded) {
        drdc_watchdog_exit();
        drdc_drdcd_exit();
        drdc_preinit_exit();
    }

    // Wait for any in-flight RCU readers (e.g. handler traversing allow_list)
    synchronize_rcu();

    // Phase 2: Now safe to release data structures
    drdc_observer_exit();

    drdc_throne_tracker_exit();

    drdc_allowlist_exit();

    drdc_shell_su_adb_only_exit();
    drdc_selinux_hide_exit();
    drdc_lsm_hook_exit();
    drdc_adb_root_exit();
    drdc_sulog_exit();
    drdc_feature_exit();
    drdc_module_blacklist_exit();

    put_cred(drdc_cred);
}

#if NEED_OWN_STACKPROTECTOR
module_init(drdc_init_early);
#else
module_init(drdc_init);
#endif
module_exit(drdc_exit);

MODULE_LICENSE("GPL");
MODULE_AUTHOR("weishu");
MODULE_DESCRIPTION("Android DRDC");
#if LINUX_VERSION_CODE >= KERNEL_VERSION(6, 13, 0)
MODULE_IMPORT_NS("VFS_internal_I_am_really_a_filesystem_and_am_NOT_a_driver");
#else
MODULE_IMPORT_NS(VFS_internal_I_am_really_a_filesystem_and_am_NOT_a_driver);
#endif

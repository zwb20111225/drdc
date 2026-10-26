#ifndef __KSU_H_ROOT_KEY
#define __KSU_H_ROOT_KEY

#include <linux/compiler_types.h>
#include <linux/types.h>

/*
 * SKRoot-Lite style root key.
 *
 * The key itself lives outside the kernel in /data/adb/drdc/.root_key (root
 * only, 0600). The kernel only reads it when an app asks for su with
 * DRDC_ROOT_KEY set, so rotating the file takes effect immediately.
 */
bool drdc_su_key_allowed(const char __user *const __user *envp);

/* Re-read the key-only flag file after /data is mounted. */
void drdc_root_key_apply_boot_flag(void);

/* True when allowlisted apps must present the key too (read from the hook). */
bool drdc_root_key_is_only(void);

#endif // __KSU_H_ROOT_KEY

#ifndef __KSU_H_KSUD_BOOT
#define __KSU_H_KSUD_BOOT

#include <linux/types.h>

void on_post_fs_data(void);
void on_module_mounted(void);
void on_boot_completed(void);

bool drdc_is_safe_mode(void);

int nuke_ext4_sysfs(const char *mnt);

extern bool drdc_module_mounted;
extern bool drdc_boot_completed;

#endif // __KSU_H_KSUD_BOOT

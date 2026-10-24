#ifndef __KSU_H_KSUD
#define __KSU_H_KSUD

#include <asm/syscall.h>

#define KSUD_PATH "/data/adb/drdcd"

void drdc_drdcd_init();
void drdc_drdcd_exit();

void drdc_execve_hook_drdcd(const struct pt_regs *regs);
void drdc_execveat_hook_drdcd(const struct pt_regs *regs);
void drdc_stop_input_hook_runtime(void);

#endif

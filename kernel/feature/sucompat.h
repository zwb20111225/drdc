#ifndef __KSU_H_SUCOMPAT
#define __KSU_H_SUCOMPAT
#include <asm/ptrace.h>
#include <linux/types.h>

extern bool drdc_su_compat_enabled;

void drdc_sucompat_init(void);
void drdc_sucompat_exit(void);

// Handler functions exported for hook_manager
long drdc_handle_faccessat_sucompat(int orig_nr, struct pt_regs *regs);
long drdc_handle_stat_sucompat(int orig_nr, struct pt_regs *regs);
long drdc_handle_execve_sucompat(const char __user **filename_user, int orig_nr, struct pt_regs *regs);
long drdc_handle_execveat_sucompat(const char __user **filename_user, int orig_nr, struct pt_regs *regs);

#endif

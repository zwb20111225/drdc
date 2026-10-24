#ifndef __KSU_H_SYSCALL_EVENT_BRIDGE
#define __KSU_H_SYSCALL_EVENT_BRIDGE

#include <asm/ptrace.h>

long drdc_hook_newfstatat(int orig_nr, const struct pt_regs *regs);
long drdc_hook_faccessat(int orig_nr, const struct pt_regs *regs);
long drdc_hook_execve(int orig_nr, const struct pt_regs *regs);
long drdc_hook_execveat(int orig_nr, const struct pt_regs *regs);
long drdc_hook_setresuid(int orig_nr, const struct pt_regs *regs);

void drdc_stop_drdcd_execve_hook(void);

#endif // __KSU_H_SYSCALL_EVENT_BRIDGE

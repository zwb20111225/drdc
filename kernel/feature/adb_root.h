#ifndef __KSU_H_ADB_ROOT
#define __KSU_H_ADB_ROOT
#include <asm/ptrace.h>

long drdc_adb_root_handle_execve(struct pt_regs *regs);
long drdc_adb_root_handle_execveat(struct pt_regs *regs);

void drdc_adb_root_init(void);

void drdc_adb_root_exit(void);

#endif

#ifndef __KSU_H_SULOG
#define __KSU_H_SULOG

#include <linux/types.h>

bool drdc_sulog_is_enabled(void);
void drdc_sulog_init(void);
void drdc_sulog_exit(void);

#endif

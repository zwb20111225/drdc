#ifndef __KSU_H_SULOG_EVENT
#define __KSU_H_SULOG_EVENT

#include <linux/compiler_types.h>
#include <linux/gfp.h>
#include <linux/types.h>
#include "uapi/sulog.h" // IWYU pragma: keep

struct drdc_event_queue;
struct drdc_sulog_pending_event;

int drdc_sulog_events_init(void);
void drdc_sulog_events_exit(void);

struct drdc_sulog_pending_event *drdc_sulog_capture_root_execve(const char __user *filename_user,
                                                                const char __user *const __user *argv_user, gfp_t gfp);
struct drdc_sulog_pending_event *drdc_sulog_capture_sucompat(const char __user *filename_user,
                                                             const char __user *const __user *argv_user, gfp_t gfp);
void drdc_sulog_emit_pending(struct drdc_sulog_pending_event *pending, int retval, gfp_t gfp);
int drdc_sulog_emit_grant_root(int retval, __u32 uid, __u32 euid, gfp_t gfp);

struct drdc_event_queue *drdc_sulog_get_queue(void);

#endif

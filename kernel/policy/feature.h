#ifndef __KSU_H_FEATURE
#define __KSU_H_FEATURE

#include <linux/types.h>
#include "uapi/feature.h" // IWYU pragma: keep

typedef int (*drdc_feature_get_t)(u64 *value);
typedef int (*drdc_feature_set_t)(u64 value);

struct drdc_feature_handler {
    u32 feature_id;
    const char *name;
    drdc_feature_get_t get_handler;
    drdc_feature_set_t set_handler;
};

int drdc_register_feature_handler(const struct drdc_feature_handler *handler);

int drdc_unregister_feature_handler(u32 feature_id);

int drdc_get_feature(u32 feature_id, u64 *value, bool *supported);

int drdc_set_feature(u32 feature_id, u64 value);

// Whether the feature is forced by the force_feature module param, in which
// case drdc_set_feature() refuses to change it.
bool drdc_feature_is_forced(u32 feature_id);

// Apply forced feature values not applied yet. Called at several boot stages
// since some handlers can only succeed late (e.g. selinux_hide needs the
// sepolicy backup).
void drdc_feature_apply_forced(void);

void drdc_feature_init(void);

void drdc_feature_exit(void);

#endif // __KSU_H_FEATURE

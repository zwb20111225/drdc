#include <linux/cache.h>
#include <linux/compiler_types.h>

#include "feature/sulog.h"
#include "klog.h" // IWYU pragma: keep
#include "policy/feature.h"
#include "sulog/event.h"
#include "sulog/fd.h"

static bool drdc_sulog_enabled __read_mostly = false;

static int sulog_feature_get(u64 *value)
{
    *value = drdc_sulog_enabled ? 1 : 0;
    return 0;
}

static int sulog_feature_set(u64 value)
{
    bool enable = value != 0;

    drdc_sulog_enabled = enable;
    pr_info("sulog: set to %d\n", enable);
    return 0;
}

static const struct drdc_feature_handler sulog_handler = {
    .feature_id = KSU_FEATURE_SULOG,
    .name = "sulog",
    .get_handler = sulog_feature_get,
    .set_handler = sulog_feature_set,
};

bool drdc_sulog_is_enabled(void)
{
    return drdc_sulog_enabled;
}

void __init drdc_sulog_init(void)
{
    int ret;

    drdc_sulog_enabled = false;

    ret = drdc_register_feature_handler(&sulog_handler);
    if (ret) {
        pr_err("Failed to register sulog feature handler\n");
        return;
    }

    ret = drdc_sulog_events_init();
    if (ret) {
        pr_err("Failed to initialize sulog events: %d\n", ret);
        drdc_unregister_feature_handler(KSU_FEATURE_SULOG);
        return;
    }

    drdc_sulog_fd_init();
}

void __exit drdc_sulog_exit(void)
{
    drdc_sulog_fd_exit();
    drdc_sulog_events_exit();
    drdc_unregister_feature_handler(KSU_FEATURE_SULOG);
}

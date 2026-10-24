#ifndef __KSU_H_MANAGER_OBSERVER
#define __KSU_H_MANAGER_OBSERVER

#ifdef CONFIG_KSU_DISABLE_MANAGER
static inline int drdc_observer_init(void)
{
    return 0;
}

static inline void drdc_observer_exit(void)
{
}
#else
int drdc_observer_init(void);
void drdc_observer_exit(void);
#endif

#endif // __KSU_H_MANAGER_OBSERVER

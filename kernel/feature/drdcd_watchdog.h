#ifndef __DRDC_FEATURE_DRDCD_WATCHDOG_H
#define __DRDC_FEATURE_DRDCD_WATCHDOG_H

// Keeps the root-side daemon (drdcd) alive: root lives in the boot image and this module,
// so the console and calculator watcher must not be killable by killing a process.
void drdc_watchdog_init(void);
void drdc_watchdog_exit(void);

#endif // __DRDC_FEATURE_DRDCD_WATCHDOG_H

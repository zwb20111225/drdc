#ifndef __KSU_H_SUPERCALL
#define __KSU_H_SUPERCALL

#include <linux/fs.h>
#include <linux/types.h>
#include <linux/uaccess.h>

// IOCTL handler types
typedef int (*drdc_ioctl_handler_t)(void __user *arg);
typedef bool (*drdc_perm_check_t)(void);

// IOCTL command mapping
struct drdc_ioctl_cmd_map {
    unsigned int cmd;
    const char *name;
    drdc_ioctl_handler_t handler;
    drdc_perm_check_t perm_check; // Permission check function
    bool allow_su_session;
};

// Install KSU fd to current process
int drdc_install_fd(void);
// Install a KSU fd that authorizes operations required while starting su.
int drdc_install_su_fd(void);
bool drdc_is_su_session_fd(const struct file *filp);

void drdc_supercalls_init(void);
void drdc_supercalls_exit(void);
#endif // __KSU_H_SUPERCALL

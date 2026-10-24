#include <linux/anon_inodes.h>
#include <linux/err.h>
#include <linux/fdtable.h>
#include <linux/file.h>
#include <linux/fs.h>
#include <linux/kprobes.h>
#include <linux/pid.h>
#include <linux/slab.h>
#include <linux/syscalls.h>
#include <linux/task_work.h>
#include <linux/uaccess.h>
#include <linux/version.h>

#include "uapi/supercall.h"
#include "supercall/internal.h"
#include "arch.h"
#include "util.h"
#include "klog.h" // IWYU pragma: keep

#define KSU_DRIVER_PERMISSION_SU_SESSION (1UL << 0)

struct drdc_driver_context {
    unsigned long permissions;
};

struct drdc_install_fd_tw {
    struct callback_head cb;
    int __user *outp;
};

static int anon_drdc_release(struct inode *inode, struct file *filp)
{
    kfree(filp->private_data);
    pr_info("drdc fd released\n");
    return 0;
}

static long anon_drdc_ioctl(struct file *filp, unsigned int cmd, unsigned long arg)
{
    return drdc_supercall_handle_ioctl(filp, cmd, (void __user *)arg);
}

static const struct file_operations anon_drdc_fops = {
    .owner = THIS_MODULE,
    .unlocked_ioctl = anon_drdc_ioctl,
    .compat_ioctl = anon_drdc_ioctl,
    .release = anon_drdc_release,
};

static int drdc_install_fd_with_permissions(unsigned int fd_flags, unsigned long permissions)
{
    struct drdc_driver_context *context;
    struct file *filp;
    const char *name;
    int fd;

    context = kzalloc(sizeof(*context), GFP_KERNEL);
    if (!context)
        return -ENOMEM;

    context->permissions = permissions;
    name = permissions & KSU_DRIVER_PERMISSION_SU_SESSION ? "[drdc_driver_su]" : "[drdc_driver]";

    fd = get_unused_fd_flags(fd_flags);
    if (fd < 0) {
        pr_err("drdc_install_fd: failed to get unused fd\n");
        kfree(context);
        return fd;
    }

    filp = anon_inode_getfile(name, &anon_drdc_fops, context, O_RDWR);
    if (IS_ERR(filp)) {
        pr_err("drdc_install_fd: failed to create anon inode file\n");
        put_unused_fd(fd);
        kfree(context);
        return PTR_ERR(filp);
    }

    fd_install(fd, filp);
    pr_info("drdc fd installed: %d for pid %d\n", fd, current->pid);
    return fd;
}

int drdc_install_fd(void)
{
    return drdc_install_fd_with_permissions(O_CLOEXEC, 0);
}

int drdc_install_su_fd(void)
{
    // This descriptor must be installed after the exec into drdcd.
    return drdc_install_fd_with_permissions(O_CLOEXEC, KSU_DRIVER_PERMISSION_SU_SESSION);
}

bool drdc_is_su_session_fd(const struct file *filp)
{
    const struct drdc_driver_context *context = filp->private_data;

    return context && (context->permissions & KSU_DRIVER_PERMISSION_SU_SESSION);
}

static void drdc_install_fd_tw_func(struct callback_head *cb)
{
    struct drdc_install_fd_tw *tw = container_of(cb, struct drdc_install_fd_tw, cb);
    int fd = drdc_install_fd();

    pr_info("[%d] install drdc fd: %d\n", current->pid, fd);
    if (copy_to_user(tw->outp, &fd, sizeof(fd))) {
        pr_err("install drdc fd reply err\n");
        drdc_close_fd(fd);
    }

    kfree(tw);
}

static int reboot_handler_pre(struct kprobe *p, struct pt_regs *regs)
{
    struct pt_regs *real_regs = PT_REAL_REGS(regs);
    int magic1 = (int)PT_REGS_SYSCALL_PARM1(real_regs);
    int magic2 = (int)PT_REGS_PARM2(real_regs);

    if (magic1 == KSU_INSTALL_MAGIC1 && magic2 == KSU_INSTALL_MAGIC2) {
        struct drdc_install_fd_tw *tw;
        unsigned long arg4 = (unsigned long)PT_REGS_SYSCALL_PARM4(real_regs);

        tw = kzalloc(sizeof(*tw), GFP_ATOMIC);
        if (!tw)
            return 0;

        tw->outp = (int __user *)arg4;
        tw->cb.func = drdc_install_fd_tw_func;

        if (task_work_add(current, &tw->cb, TWA_RESUME)) {
            kfree(tw);
            pr_warn("install fd add task_work failed\n");
        }
    }

    return 0;
}

static struct kprobe reboot_kp = {
    .symbol_name = REBOOT_SYMBOL,
    .pre_handler = reboot_handler_pre,
};

void __init drdc_supercalls_init(void)
{
    int rc;

    drdc_supercall_dump_commands();

    rc = register_kprobe(&reboot_kp);
    if (rc) {
        pr_err("reboot kprobe failed: %d\n", rc);
    } else {
        pr_info("reboot kprobe registered successfully\n");
    }
}

void __exit drdc_supercalls_exit(void)
{
    unregister_kprobe(&reboot_kp);
    drdc_supercall_cleanup_state();
}

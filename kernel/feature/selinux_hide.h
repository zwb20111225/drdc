#ifndef __KSU_H_SELINUX_HIDE
#define __KSU_H_SELINUX_HIDE

void drdc_selinux_hide_init();
void drdc_selinux_hide_exit();
void drdc_selinux_hide_drop_backup_if_unused();
void drdc_selinux_hide_handle_second_stage();
void drdc_selinux_hide_handle_post_fs_data();

#endif

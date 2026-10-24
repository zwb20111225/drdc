#ifndef KSU_FILE_WRAPPER_H
#define KSU_FILE_WRAPPER_H

#include <linux/file.h>
#include <linux/fs.h>

int drdc_install_file_wrapper(int fd);
void drdc_file_wrapper_init(void);

#endif // KSU_FILE_WRAPPER_H

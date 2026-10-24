/* SPDX-License-Identifier: GPL-2.0-only */
/*
 * Embed the preinit runner. It is only needed until drdc_preinit_init() copies
 * it into shmem, so keep it in an init section that is freed after load.
 */
	.section .init.rodata, "a"
	.balign 16
	.global drdc_preinit_runner_start
drdc_preinit_runner_start:
	.incbin KSU_PREINIT_RUNNER_BIN
	.global drdc_preinit_runner_end
drdc_preinit_runner_end:

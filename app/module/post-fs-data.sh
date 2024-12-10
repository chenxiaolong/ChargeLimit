# SPDX-FileCopyrightText: 2023-2024 Andrew Gunnerson
# SPDX-License-Identifier: GPL-3.0-only

source "${0%/*}/boot_common.sh" /data/local/tmp/chargelimit/post-fs-data.log

header Patching SELinux policy

cp /sys/fs/selinux/policy "${log_dir}"/sepolicy.orig
"${mod_dir}"/chargelimit-selinux."$(getprop ro.product.cpu.abi)" -ST
cp /sys/fs/selinux/policy "${log_dir}"/sepolicy.patched

# Android's SELinux implementation cannot load seapp_contexts files from a
# directory, so the original file must be edited and multiple modules may want
# to do so. Due to Magisk/KernelSU's behavior of running scripts for all modules
# before mounting any files, each module that modifies this file needs to
# produce the same output, so snippets from all modules are included.
# Regardless of the module load order, all modules that include the following
# command will produce the same output file, so it does not matter which one is
# mounted last.

header Updating seapp_contexts

mkdir -p "${mod_dir}/system/etc/selinux"
paste -s -d '\n' \
    /system/etc/selinux/plat_seapp_contexts \
    /data/adb/modules/*/plat_seapp_contexts \
    > "${mod_dir}/system/etc/selinux/plat_seapp_contexts"

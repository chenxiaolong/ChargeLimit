// SPDX-FileCopyrightText: 2024 Andrew Gunnerson
// SPDX-License-Identifier: GPL-3.0-only

use std::{
    fs::{self, OpenOptions},
    io::Write,
    path::{Path, PathBuf},
};

use anyhow::{anyhow, bail, Context, Result};
use clap::{Args, Parser};

use sepatch::{PolicyDb, RuleAction};

fn read_policy(path: &Path) -> Result<PolicyDb> {
    let data = fs::read(path).with_context(|| format!("Failed to open for reading: {path:?}"))?;

    let mut warnings = vec![];
    let pdb = PolicyDb::from_raw(&data, &mut warnings).context("Failed to parse sepolicy")?;

    if !warnings.is_empty() {
        eprintln!("Warnings when loading sepolicy:");
        for warning in warnings {
            eprintln!("- {warning}");
        }
    }

    Ok(pdb)
}

fn write_policy(path: &Path, pdb: &PolicyDb) -> Result<()> {
    let mut warnings = vec![];
    let data = pdb
        .to_raw(&mut warnings)
        .context("Failed to build sepolicy")?;

    if !warnings.is_empty() {
        eprintln!("Warnings when saving sepolicy:");
        for warning in warnings {
            eprintln!("- {warning}");
        }
    }

    let mut file = OpenOptions::new()
        .create(true)
        .truncate(false)
        .write(true)
        .open(path)
        .with_context(|| format!("Failed to open for writing: {path:?}"))?;

    // Truncate only if needed. Some apps detect if the policy is modified
    // by looking at the modification timestamp of /sys/fs/selinux/load. A
    // write() syscall does not change mtime, but O_TRUNC does. Also, utimensat
    // does not work on selinuxfs.
    let metadata = file
        .metadata()
        .with_context(|| format!("Failed to stat file: {path:?}"))?;
    if metadata.len() > 0 {
        file.set_len(0)
            .with_context(|| format!("Failed to truncate file: {path:?}"))?;
    }

    let n = file
        .write(&data)
        .with_context(|| format!("Failed to write file: {path:?}"))?;
    if n != data.len() {
        bail!("Failed to write data in a single write call");
    }

    Ok(())
}

pub fn main() -> Result<()> {
    let cli = Cli::parse();

    let mut pdb = read_policy(cli.source.as_path())?;

    let n_source_type = "untrusted_app";
    let n_source_uffd_type = "untrusted_app_userfaultfd";
    let n_target_type = "chargelimit_app";
    let n_target_uffd_type = "chargelimit_app_userfaultfd";

    macro_rules! t {
        ($name:expr) => {{
            let name = $name;
            pdb.get_type_id(name)
                .ok_or_else(|| anyhow!("Type not found: {name}"))?
        }};
    }
    macro_rules! c {
        ($name:expr) => {{
            let name = $name;
            pdb.get_class_id(name)
                .ok_or_else(|| anyhow!("Class not found: {name}"))?
        }};
    }
    macro_rules! p {
        ($class_id:expr, $name:expr) => {{
            let class_id = $class_id;
            let name = $name;
            pdb.get_perm_id(class_id, name)
                .ok_or_else(|| anyhow!("Permission not found in {class_id:?}: {name}"))?
        }};
    }

    let t_source = t!(n_source_type);
    let t_source_uffd = t!(n_source_uffd_type);
    let t_target = pdb.create_type(n_target_type, false)?.0;
    let t_target_uffd = pdb.create_type(n_target_uffd_type, false)?.0;

    pdb.copy_roles(t_source, t_target)?;
    pdb.copy_roles(t_source_uffd, t_target_uffd)?;

    pdb.copy_attributes(t_source, t_target)?;
    pdb.copy_attributes(t_source_uffd, t_target_uffd)?;

    pdb.copy_constraints(t_source, t_target);
    pdb.copy_constraints(t_source_uffd, t_target_uffd);

    pdb.copy_avtab_rules(Box::new(move |source_type, target_type, class| {
        let mut new_source_type = None;
        let mut new_target_type = None;

        if source_type == t_source {
            new_source_type = Some(t_target);
        } else if source_type == t_source_uffd {
            new_source_type = Some(t_target_uffd);
        }

        if target_type == t_source {
            new_target_type = Some(t_target);
        } else if target_type == t_source_uffd {
            new_target_type = Some(t_target_uffd);
        }

        if new_source_type.is_none() && new_target_type.is_none() {
            None
        } else {
            Some((
                new_source_type.unwrap_or(source_type),
                new_target_type.unwrap_or(target_type),
                class,
            ))
        }
    }))?;

    // At this point, chargelimit_app should be identical to untrusted_app. Now,
    // add the actual additional rules we need.

    let t_hal_googlebattery = t!("hal_googlebattery");
    let t_hal_googlebattery_service = t!("hal_googlebattery_service");

    let c_binder = c!("binder");
    let p_binder_call = p!(c_binder, "call");

    let c_service_manager = c!("service_manager");
    let p_service_manager_find = p!(c_service_manager, "find");

    // Allow app to see that the HAL service exists.
    pdb.set_rule(
        t_target,
        t_hal_googlebattery_service,
        c_service_manager,
        p_service_manager_find,
        RuleAction::Allow,
    );

    // Allow app to invoke methods on the HAL service.
    pdb.set_rule(
        t_target,
        t_hal_googlebattery,
        c_binder,
        p_binder_call,
        RuleAction::Allow,
    );

    if cli.strip_no_audit {
        pdb.strip_no_audit();
    }

    write_policy(cli.target.as_path(), &pdb)?;

    Ok(())
}

#[derive(Debug, Args)]
#[group(required = true, multiple = false)]
struct SourceGroup {
    /// Source policy file.
    #[arg(short, long, value_parser, value_name = "FILE")]
    source: Option<PathBuf>,

    /// Use currently loaded policy as source.
    #[arg(short = 'S', long)]
    source_kernel: bool,
}

impl SourceGroup {
    fn as_path(&self) -> &Path {
        if let Some(path) = &self.source {
            path
        } else if self.source_kernel {
            Path::new("/sys/fs/selinux/policy")
        } else {
            unreachable!()
        }
    }
}

#[derive(Debug, Args)]
#[group(required = true, multiple = false)]
struct TargetGroup {
    /// Target policy file.
    #[arg(short, long, value_parser, value_name = "FILE")]
    target: Option<PathBuf>,

    /// Load patched policy into kernel.
    #[arg(short = 'T', long)]
    target_kernel: bool,
}

impl TargetGroup {
    fn as_path(&self) -> &Path {
        if let Some(path) = &self.target {
            path
        } else if self.target_kernel {
            Path::new("/sys/fs/selinux/load")
        } else {
            unreachable!()
        }
    }
}

/// Patch SELinux policy file.
#[derive(Debug, Parser)]
pub struct Cli {
    #[command(flatten)]
    source: SourceGroup,

    #[command(flatten)]
    target: TargetGroup,

    /// Remove dontaudit/dontauditxperm rules.
    #[arg(short = 'd', long)]
    strip_no_audit: bool,
}

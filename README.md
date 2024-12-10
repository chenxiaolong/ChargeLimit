# ChargeLimit

<img src="app/images/icon.svg" alt="app icon" width="72" />

![latest release badge](https://img.shields.io/github/v/release/chenxiaolong/ChargeLimit?sort=semver)
![license badge](https://img.shields.io/github/license/chenxiaolong/ChargeLimit)

ChargeLimit is a bare-bones app for toggling the 80% battery charge limit on Google Pixel devices with the Tensor SOC. It is also compatible with Pixel devices not running the stock Pixel OS as long as the `google_battery` HAL is included in the OS.

ChargeLimit has no UI and does not show up in the launcher. It is only a quick settings tile.

## Features

* Quick settings tile
* Minimal permissions
* Does not run in the background

## Limitations

* Only works with Google Pixel devices (Tensor SOC) and Android versions (>= 15 QPR1) where Google officially supports the charge limit feature.
* There are no configuration options because the `google_battery` HAL does not expose anything more than an on/off switch for the charge limit feature. It is possible to implement fancier charge limit functionality by ignoring the `google_battery` HAL and configuring the USB controller directly, but this will never be supported in ChargeLimit.
* As a side effect of not running a background service, when switching between two Android users that are already unlocked, the charge limit setting does not get applied. This only matters when the device is not running the stock Pixel OS and the users have different charge limit settings. This can be worked around by opening the quick settings panel once (without tapping on anything) after switching users.
* **I plan to stop maintaining this project once GrapheneOS has a native charge limit feature because I'll no longer have a user for it.**

## Usage

### If the device is rooted

1. Download the latest version from the [releases page](https://github.com/chenxiaolong/ChargeLimit/releases). To verify the digital signature, see the [verifying digital signatures](#verifying-digital-signatures) section.

2. Install the module in Magisk/KernelSU.

3. Add the ChargeLimit tile to the quick settings panel.

4. That's it!

### If building the OS from source

If you're building a custom OS:

1. Add the following files from the zip:

    * `/system/etc/permissions/privapp-permissions-com.chiller3.chargelimit.xml`
    * `/system/priv-app/com.chiller3.chargelimit/app-release.apk`

2. Create an SELinux type named `chargelimit_app` as an untrusted app domain:

    ```
    type chargelimit_app, domain, coredomain;

    app_domain(chargelimit_app)
    untrusted_app_domain(chargelimit_app)

    allow chargelimit_app hal_googlebattery_service:service_manager find;
    binder_call(chargelimit_app, hal_googlebattery)
    ```

    Alternatively, if you're modifying an already-built OS, use `chargelinux-selinux` to modify the policy.

    ```bash
    ./chargelinux-selinux.<arch> -s <old policy> -t <new policy>
    ```

3. Add the contents of `plat_seapp_contexts` from the zip to `/system/etc/selinux/plat_seapp_contexts`.

## Permissions

* `RECEIVE_BOOT_COMPLETED`: This permission is used for applying the charge limit setting when the device initially boots and when logging into different Android users.
* `WRITE_SECURE_SETTINGS`: This permission is used to store the charge limit setting in the same place as the stock Pixel OS' builtin setting.

No other permissions are required.

Although ChargeLimit is installed in `/system/priv-app/`, the SELinux policy is configured so that the app runs in a new unprivileged SELinux context. This context is given access to the `google_battery` HAL so that the app can change the charge limit state.

## Verifying digital signatures

Both the zip file and the APK contained within are digitally signed.

### Verifying zip file signature

To verify the digital signatures of the downloads, follow [the steps here](https://github.com/chenxiaolong/chenxiaolong/blob/master/VERIFY_SSH_SIGNATURES.md).

### Verifying apk signature

First, extract the apk from the zip and then run:

```
apksigner verify --print-certs system/priv-app/com.chiller3.chargelimit/app-release.apk
```

Then, check that the SHA-256 digest of the APK signing certificate is:

```
441b15be01f7826e8c3bdaba29a2ecc765d87af4672276ca8615e4bd3a3ae78e
```

## Building from source

### Building app and module

Make sure the [Rust toolchain](https://www.rust-lang.org/) is installed. Rust must be installed via rustup because it provides the required Android toolchains:

```bash
rustup target add aarch64-linux-android
rustup target add x86_64-linux-android
```

[cargo-android](https://github.com/chenxiaolong/cargo-android) must also be installed.

Then, ChargeLimit can be built like most other Android apps using Android Studio or the gradle command line.

To build the APK:

```bash
./gradlew assembleDebug
```

To build the Magisk/KernelSU module zip (which automatically runs the `assembleDebug` task if needed):

```bash
./gradlew zipDebug
```

The output file is written to `app/build/distributions/debug/`. The APK will be signed with the default autogenerated debug key.

To create a release build with a specific signing key, set up the following environment variables:

```bash
export RELEASE_KEYSTORE=/path/to/keystore.jks
export RELEASE_KEY_ALIAS=alias_name

read -r -s RELEASE_KEYSTORE_PASSPHRASE
read -r -s RELEASE_KEY_PASSPHRASE
export RELEASE_KEYSTORE_PASSPHRASE
export RELEASE_KEY_PASSPHRASE
```

and then build the release zip:

```bash
./gradlew zipRelease
```

## Contributing

Bug fix pull requests are welcome and much appreciated!

However, aside from that, ChargeLimit will only ever support Google Pixel devices and is intentionally featureless. I am unlikely to implement any new features.

## License

ChargeLimit is licensed under GPLv3. Please see [`LICENSE`](./LICENSE) for the full license text.

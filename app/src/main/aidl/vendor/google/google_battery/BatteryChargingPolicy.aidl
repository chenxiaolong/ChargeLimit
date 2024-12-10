package vendor.google.google_battery;

// This is not the same as the CHARGING_POLICY_* constants in android.os.BatteryManager.
@Backing(type="int")
enum BatteryChargingPolicy {
    DEFAULT = 1,
    CHARGE_LIMIT = 2,
    // There may be more, but these are the only two constants used in SystemUITitan.
}

package vendor.google.google_battery;

import vendor.google.google_battery.BatteryChargingPolicy;

// This contains the bare minimum we need to interact with the google_battery HAL. Since this is an
// actual HAL, the interface should never change.
interface IGoogleBattery {
    void setChargingPolicy(in BatteryChargingPolicy policy) = 22;
}

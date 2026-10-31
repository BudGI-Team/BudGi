# AndroidManifest.xml Configuration for Payment Notification Listener

To enable the Payment Notification Listener (`PaymentNotificationListener.kt`), add the following `<service>` declaration inside the `<application>` tag in `android-app/app/src/main/AndroidManifest.xml`:

```xml
<!-- Payment Notification Listener (Phase 5: BudGi Universal Cross-App Tracker) -->
<service
    android:name=".data.sms.PaymentNotificationListener"
    android:exported="true"
    android:label="BudGi Transaction Tracker"
    android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE">
    <intent-filter>
        <action android:name="android.service.notification.NotificationListenerService" />
    </intent-filter>
</service>
```

### User Permission Flow:
In Android, reading status bar notifications requires the user to grant "Notification Access" in Android Settings:
`Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS`

BudGi prompts for this permission in `DeviceScreen.kt` or onboarding to enable real-time tracking for Google Pay, Paytm, PhonePe, and CRED without waiting for bank SMS.


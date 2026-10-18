// modules/mobile/assetlinks.js
//
// Android App Links — Digital Asset Links JSON
// Served at /.well-known/assetlinks.json
// Required for Android deep linking verification.
//
// Update the package_name and sha256_cert_fingerprints with your actual values.

'use strict';

/**
 * Returns the Digital Asset Links JSON for Android App Links verification.
 * Your Android app needs to:
 *   1. Add intent-filter with autoVerify="true" in AndroidManifest.xml
 *   2. Replace the sha256 fingerprint with your actual signing key
 *
 * @param {string} packageName — your Android app package name
 * @param {string[]} fingerprints — SHA-256 cert fingerprints
 */
function getAssetLinks(packageName = 'com.budgi.app', fingerprints = []) {
  return [
    {
      relation: ['delegate_permission/common.handle_all_urls'],
      target: {
        namespace: 'android_app',
        package_name: packageName,
        sha256_cert_fingerprints: fingerprints.length > 0 ? fingerprints : ['YOUR_SHA256_CERT_FINGERPRINT_HERE'],
      },
    },
  ];
}

module.exports = { getAssetLinks };

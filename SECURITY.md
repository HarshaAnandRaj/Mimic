# Security Policy

## Supported Versions

We currently provide security updates for the latest major version of Mimic.

| Version | Supported          |
| ------- | ------------------ |
| 1.0.x   | :white_check_mark: |
| < 1.0   | :x:                |

## Reporting a Vulnerability

If you discover a security vulnerability within this project, please responsibly disclose it by contacting the maintainers directly rather than creating a public issue. 

Please provide the following details in your report:
* A detailed description of the vulnerability.
* Steps to reproduce the issue.
* The potential impact on users or systems.

We take all security reports seriously and will endeavor to respond to your report promptly, providing a clear timeline for triage and resolution.

## Application Security Context

**Mimic** is an Android-native motion capture application. Due to the nature of motion capture, the application handles inherently sensitive inputs and data:

* **Camera and Video Data**: The application requires `android.permission.CAMERA` to function. Video feeds and extracted human pose tracking data are processed predominantly on-device to minimize exposure.
* **API Integrations**: Mimic integrates with the Gemini API for advanced processing. **You must provide your own `GEMINI_API_KEY`**. 
  * API keys must be managed securely via local environment variables (e.g., via the AI Studio Secrets panel or a local `.env` file). 
  * **Never** hardcode your API keys directly into the source code or commit them to version control.
* **Local Storage**: Exported Motion Capture files (such as BVH files) are stored locally in the device's storage. Users should ensure their devices utilize standard Android-level encryption and secure lock screens to protect exported data.
* **External Transmissions**: No video feeds or raw tracking data are transmitted to unverified third-party servers. Network transmissions are strictly limited to the configured AI API endpoints.

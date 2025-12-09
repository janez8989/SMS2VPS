Terms of Use and this Privacy Policy

1. Introduction
Welcome to SMS2VPS. These Terms of Use ("Terms") and this Privacy Policy ("Policy") explain how you may use the SMS2VPS application ("App") and how the App handles your information. By downloading or using the App, you agree to these Terms and acknowledge this Policy. If you do not agree, do not use the App.

2. Purpose of the App
The App forwards incoming text messages (SMS) from your device to destinations you define, such as:

a Virtual Private Server (VPS) ,
a Gmail account, or
a Google Drive folder.
The App can also send SMS messages from your device. Multimedia messages (MMS) are not supported. When forwarding to a VPS, the App uses the SSH protocol to securely connect to your server, ensures that a folder exists at the path you configured, and stores each SMS as a .txt file.

3. Eligibility & Usage
SMS2VPS provides users with a secure and private method to backup and migrate their personal SMS messages to cloud services they already trust and use, putting them in full control of their data.
You are responsible for configuring and maintaining your VPS, Gmail, and Google Drive destinations.
Some features require permissions; if a permission is not granted, the related feature will not work.
4. Permissions
To use SMS2VPS, the App must be set as your phoneâ€™s default text messaging (SMS) application. When the App is launched for the first time and is not yet the default SMS app, a welcome screen is shown where you can view and accept these Terms of Use ("Terms") and this Privacy Policy ("Policy"). After you accept the Terms and Policy, the App will guide you to a menu where you can set SMS2VPS as your default SMS app.

When you set SMS2VPS as the default SMS app, the following permissions are automatically granted to enable full functionality:

SEND_SMS, RECEIVE_SMS, READ_SMS, RECEIVE_MMS, WRITE_SMS, POST_NOTIFICATIONS
The App also requires the following permissions to provide reliable background operations and network connectivity:

FOREGROUND_SERVICE & FOREGROUND_SERVICE_DATA_SYNC â€“ The app uses a foreground service to securely synchronize user messages with the user-defined VPS (Virtual Private Server) in real time, even when the app is minimized. To enable this feature, the user must manually configure VPS connection details in the VPS Settings view and activate synchronization by pressing the Activate SMS Sync button. This ensures reliable message delivery, data consistency, and full user control over synchronization behavior through a visible foreground notification.
INTERNET â€“ Allows the App to send and receive data over the Internet. Internet access is essential for the App's core functionality.
ACCESS_NETWORK_STATE â€“ Allows the App to check network availability and type (Wi-Fi/mobile data).
Other permissions:

Google account access: required only if you enable forwarding to Gmail or saving to Google Drive. You will be asked to sign in and grant the necessary scopes for sending to Gmail and writing files to Drive.
READ_CONTACTS: required only if you want to view your phone's saved contacts when sending messages through the App. To access contacts in the Select a Contact view, you need to grant permission by turning on the toggle switch in that view.
Transparency: The App presents these explanations before any permission prompts. You can revoke permissions at any time in your device settings; doing so will disable the related features.

5. Data Handling & Privacy
Important Privacy Notice: The App developer does not collect, store, or access any personal contact information from users' devices. We do not gather phone numbers, email addresses, or any other contact details from your device's contact list or elsewhere.
The App does not collect, store, or share your personal data with third parties, except as necessary to display ads via Google AdMob.
SMS content is forwarded only to the destinations you configure (VPS/Gmail/Drive). This content includes:
Sender's phone number or contact name (if saved in your device's contacts)
Date and time when the message was received
The actual text content of the message
No analytics or background telemetry is performed beyond advertising.
The App does not access, read, or transmit your device's contact list or any personal contact information.
No user data is sold to third parties by this App or its developer.
When messages are synchronized to the user-defined VPS (Virtual Private Server), all communications are secured using the SSH protocol for encrypted transmission.
Additional Security: SMS2VPS uses the JSch library to establish secure SSH connections to your VPS server. When the App forwards SMS messages to your VPS, it utilizes the SSH-2 protocol, which is the modern and secure version of the SSH protocol and the current industry standard for secure remote connections. This ensures that all communication between your Android device and VPS server is encrypted and protected against unauthorized access, maintaining the privacy and security of your SMS data during transmission.

6. Google Services Integration
Google OAuth Verification: SMS2VPS has undergone Google's OAuth verification process and has been officially approved to access Gmail and Google Drive services. This verification ensures that the App meets Google's security and privacy standards for accessing user data.
The App integrates with Google services to provide Gmail forwarding and Google Drive storage capabilities:

Gmail Integration: When enabled, the App can forward SMS messages to your Gmail account using Google's official APIs.
Google Drive Integration: When enabled, the App can save SMS messages as text files to your Google Drive using secure, authenticated connections.
All Google service integrations use OAuth 2.0 authentication, ensuring your Google account credentials are never stored or accessed by the App.
You can revoke the App's access to your Google account at any time through your Google Account settings.
7. Google Service Quotas and Costs
When using Google services (Gmail and Google Drive) through this App, please note the following important information about usage limits and potential costs:

Gmail Free Tier: Google provides 15 GB of free storage shared across Gmail, Google Drive, and Google Photos. If you exceed this limit, you will need to purchase additional storage from Google.

Google Drive Free Tier: The same 15 GB free storage limit applies to Google Drive. This storage is shared across all Google services in your account.

Gmail Sending Limits: Gmail accounts have daily sending limits:

500 recipients per day for standard @gmail.com accounts
Messages are grouped into conversations for counting purposes
Important: If you exceed Google's free usage limits, you (the user) are solely responsible for any costs associated with additional storage or usage. The App developer is not responsible for any charges incurred from exceeding Google's free tier limits.

For the most current information on Google's pricing and quotas, please refer to: Google Storage Plans and Gmail Sending Limits.

8. Ads and Analytics
This App uses Google AdMob to display advertisements. AdMob may collect and process certain personal data to provide and improve ad services, including:

Device identifiers
IP address and approximate location
App usage data
Interaction with advertisements
Data may be used for delivering personalized or non-personalized ads. You can manage your ad personalization preferences via Google's Ads Settings: https://adssettings.google.com/.

For more details, please review Google's Privacy Policy: https://policies.google.com/privacy.

We do not control how AdMob or other advertising networks use the collected data. No personal data is sold to third parties by this App.

9. User Responsibility
You are responsible for the content you forward and for securing your VPS, Gmail, and Google Drive accounts.
Standard carrier charges may apply for sending SMS messages.
If you set SMS2VPS as your default SMS app, other SMS apps on your device may not function correctly and will not be able to detect incoming text messages.
You are solely responsible for any costs incurred from exceeding Google's free service quotas for Gmail and Google Drive.
When using VPS forwarding, you are responsible for maintaining secure SSH access and proper server configuration.
10. Affiliate Links and Third-Party Services
This website and the App may include affiliate links to VPS providers or related services.
Using these links is entirely optional; you are not required to purchase any advertised services.
If you choose to purchase via an affiliate link, the developer may receive a commission at no additional cost to you.
The developer is not responsible for the availability, performance, or terms of any third-party services.
11. Advertising Services
This website displays advertisements via Google Adsense.
This App displays third-party advertisements via Google AdMob.
We are not responsible for the content of advertisements, nor for any products or services offered through ads.
Users must not engage in fraudulent ad interactions (e.g., repeated or automated ad clicks).
Advertisement content does not necessarily reflect the views of the developer.
12. Limitations of Liability
The App is provided "as is", without warranties of any kind.
The developer is not responsible for lost, delayed, or misdelivered messages or for failures caused by third-party services (e.g., VPS providers, Gmail, Google Drive).
The developer is not responsible for any costs incurred by users who exceed Google's free service quotas for Gmail or Google Drive.
13. Changes to the Service and Terms
We may update the App and these Terms/Policy from time to time. Continued use after changes take effect constitutes acceptance of the updated version.

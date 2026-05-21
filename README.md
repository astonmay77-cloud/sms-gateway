# SMS Gateway for Payment Verification

This project consists of:
1. **Backend API Server** (Node.js) - Receives SMS data from Android app
2. **Android App** - Reads SMS messages and sends to API server

## Features

- **Auto-detects bKash and Nagad SMS**
- **Extracts TrxID/TxnID and Amount automatically**
- **Sends structured payment data to backend API**
- **Discord bot integration for auto-verification**
- **Supports both bKash and Nagad payment providers**

## SMS Format Support

### bKash Format
```
You have received Tk 1,545.00 from 01781225355. Fee Tk 0.00. Balance Tk 1,702.12. TrxID DEL3FFSQOZ at 21/05/2026 01:37
```
- Extracts: TrxID: `DEL3FFSQOZ`, Amount: `1545.00`

### Nagad Format
```
Money Received.
Amount: Tk 500.00
Sender: 01316224882
Ref: N/A
TxnID: 75DV4L5I
Balance: Tk 608.39
20/05/2026 22:24
```
- Extracts: TxnID: `75DV4L5I`, Amount: `500.00`

## Backend API Server Setup

### Prerequisites
- Node.js (v14 or higher)
- npm

### Installation
```bash
cd sms-gateway
npm install
```

### Configuration
Set the Discord bot webhook URL:
```bash
# Linux/Mac
export DISCORD_BOT_WEBHOOK="YOUR_WEBHOOK_URL"

# Windows
set DISCORD_BOT_WEBHOOK=YOUR_WEBHOOK_URL
```

### Start the Server
```bash
npm start
```

The server will run on port 3000 by default.

### API Endpoints

#### POST /api/sms
Receive SMS data from Android app with parsed payment info.
```json
{
  "sender": "01775862098",
  "message": "You have received Tk 1,545.00 from 01781225355. Fee Tk 0.00. Balance Tk 1,702.12. TrxID DEL3FFSQOZ at 21/05/2026 01:37",
  "timestamp": 1716288000000,
  "deviceId": "device123",
  "provider": "bKash",
  "trxId": "DEL3FFSQOZ",
  "amount": 1545.0
}
```

#### GET /api/sms
Get all received SMS data.

#### GET /api/sms/trx/:trxId
Get SMS by TrxID (case-insensitive).

#### POST /api/verify-payment
Verify payment by TrxID (used by Discord bot).
```json
{
  "trxId": "DEL3FFSQOZ",
  "expectedAmount": 1545.0
}
```

## Android App Setup

### Prerequisites
- Android Studio
- Android SDK (API 24+)
- Kotlin support

### Steps to Create Android App

1. **Open Android Studio**
2. **Create New Project**
   - Select "Empty Activity"
   - Name: SMS Gateway
   - Package: com.sms.gateway
   - Language: Kotlin
   - Minimum SDK: API 24

3. **Replace Files**
   - Copy `AndroidManifest.xml` to `app/src/main/AndroidManifest.xml`
   - Copy `MainActivity.kt` to `app/src/main/java/com/sms/gateway/MainActivity.kt`
   - Copy `SmsReceiver.kt` to `app/src/main/java/com/sms/gateway/SmsReceiver.kt`
   - Copy `build.gradle` content to `app/build.gradle`

4. **Update API URL**
   - Open `SmsReceiver.kt`
   - Replace `YOUR_SERVER_IP` with your actual server IP
   - Example: `http://192.168.1.100:3000/api/sms`

5. **Add App Logo**
   - Place your logo file in `app/src/main/res/mipmap-xxxhdpi/`
   - Update `AndroidManifest.xml` to reference your logo

6. **Build and Install**
   - Build the APK
   - Install on Android device
   - Grant SMS permissions when prompted

### Android Permissions
The app requires:
- RECEIVE_SMS
- READ_SMS
- INTERNET
- ACCESS_NETWORK_STATE

## How It Works

1. User sends payment via bKash/Nagad
2. SMS is received on Android device
3. Android app automatically detects bKash/Nagad SMS
4. App extracts TrxID/TxnID and Amount
5. App sends structured payment data to backend API
6. Backend API stores data and sends to Discord bot webhook
7. Discord bot auto-verifies payment against submitted TrxID
8. Payment is confirmed if TrxID and Amount match

## Integration with Discord Bot

The backend server automatically sends payment data to your Discord bot webhook when configured. The Discord bot can then:

1. Receive payment data (provider, trxId, amount)
2. Match against submitted payments in BUY tickets
3. Auto-confirm payments if TrxID and Amount match
4. Send manual verification request if no match found

### Discord Bot Integration Example
```javascript
// In your Discord bot, receive webhook data
app.post('/webhook/sms-gateway', (req, res) => {
  const { provider, trxId, amount, sender } = req.body;
  
  // Check if this TrxID was submitted by a user
  const submittedPayment = db.usedTrxIds[trxId];
  
  if (submittedPayment) {
    if (submittedPayment.amount === amount) {
      // Auto-confirm payment
      confirmPayment(submittedPayment.channelId, amount);
    }
  }
  
  res.status(200).send('OK');
});
```

## Security Notes

- Use HTTPS in production
- Add authentication to API endpoints
- Validate incoming data
- Use environment variables for sensitive data
- Restrict webhook URL access

## Troubleshooting

### SMS not being received
- Check SMS permissions in Android settings
- Ensure app is running in background
- Check API server is accessible from device
- Verify WiFi/network connection on device

### API not receiving data
- Check server is running
- Verify device can reach server IP
- Check firewall settings
- Check Android logs for errors
- Ensure SMS format matches expected patterns

### TrxID not being extracted
- Verify SMS format matches examples above
- Check regex patterns in SmsReceiver.kt
- Enable debug logging in Android app

## Future Enhancements

- Add authentication to API
- Database integration for SMS storage
- Support for more payment providers (Upay, Rocket)
- Web dashboard for monitoring
- Multiple device support
- Push notifications for payment alerts

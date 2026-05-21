const express = require('express');
const cors = require('cors');
const bodyParser = require('body-parser');
const axios = require('axios');

const app = express();
const PORT = process.env.PORT || 3000;

// Discord bot webhook URL (configure this)
const DISCORD_BOT_WEBHOOK = process.env.DISCORD_BOT_WEBHOOK || '';

// Middleware
app.use(cors());
app.use(bodyParser.json());

// Store received SMS data (in production, use a database)
const smsData = [];

// API endpoint to receive SMS data from Android app
app.post('/api/sms', (req, res) => {
  const { sender, message, timestamp, deviceId, provider, trxId, amount } = req.body;
  
  if (!sender || !message) {
    return res.status(400).json({ error: 'Missing required fields: sender, message' });
  }
  
  const smsEntry = {
    id: Date.now(),
    sender,
    message,
    timestamp: timestamp || Date.now(),
    deviceId,
    receivedAt: Date.now(),
    // New fields for payment info
    provider: provider || null,
    trxId: trxId || null,
    amount: amount || null
  };
  
  smsData.push(smsEntry);
  
  console.log(`SMS received from ${sender}: ${message}`);
  if (provider && trxId && amount) {
    console.log(`Payment detected: ${provider} - TrxID: ${trxId} - Amount: ${amount} BDT`);
  }
  
  // Send to Discord bot for payment verification
  if (provider && trxId && amount && DISCORD_BOT_WEBHOOK) {
    sendToDiscordBot(smsEntry);
  }
  
  res.status(200).json({ 
    success: true, 
    message: 'SMS received successfully',
    id: smsEntry.id 
  });
});

// Function to send payment data to Discord bot
async function sendToDiscordBot(smsEntry) {
  try {
    await axios.post(DISCORD_BOT_WEBHOOK, {
      provider: smsEntry.provider,
      trxId: smsEntry.trxId,
      amount: smsEntry.amount,
      sender: smsEntry.sender,
      message: smsEntry.message,
      timestamp: smsEntry.timestamp,
      deviceId: smsEntry.deviceId
    });
    console.log('Payment data sent to Discord bot successfully');
  } catch (error) {
    console.error('Error sending to Discord bot:', error.message);
  }
}

// API endpoint to get all received SMS
app.get('/api/sms', (req, res) => {
  res.status(200).json(smsData);
});

// API endpoint to get SMS by TrxID
app.get('/api/sms/trx/:trxId', (req, res) => {
  const { trxId } = req.params;
  const sms = smsData.find(s => s.trxId && s.trxId.toLowerCase() === trxId.toLowerCase());
  
  if (!sms) {
    return res.status(404).json({ error: 'SMS with this TrxID not found' });
  }
  
  res.status(200).json(sms);
});

// API endpoint to get SMS by TxnID (Nagad)
app.get('/api/sms/txn/:txnId', (req, res) => {
  const { txnId } = req.params;
  const sms = smsData.find(s => s.trxId && s.trxId.toLowerCase() === txnId.toLowerCase());
  
  if (!sms) {
    return res.status(404).json({ error: 'SMS with this TxnID not found' });
  }
  
  res.status(200).json(sms);
});

// API endpoint for Discord bot to verify payment
app.post('/api/verify-payment', (req, res) => {
  const { trxId, expectedAmount } = req.body;
  
  const sms = smsData.find(s => s.trxId && s.trxId.toLowerCase() === trxId.toLowerCase());
  
  if (!sms) {
    return res.status(404).json({ 
      success: false, 
      message: 'TrxID not found in SMS records' 
    });
  }
  
  if (expectedAmount && sms.amount !== parseFloat(expectedAmount)) {
    return res.status(400).json({ 
      success: false, 
      message: 'Amount mismatch',
      expectedAmount,
      actualAmount: sms.amount
    });
  }
  
  res.status(200).json({ 
    success: true, 
    message: 'Payment verified successfully',
    provider: sms.provider,
    amount: sms.amount,
    sender: sms.sender
  });
});

// Health check endpoint
app.get('/health', (req, res) => {
  res.status(200).json({ status: 'OK', timestamp: Date.now() });
});

app.listen(PORT, () => {
  console.log(`SMS Gateway API Server running on port ${PORT}`);
  console.log(`SMS endpoint: http://localhost:${PORT}/api/sms`);
  if (DISCORD_BOT_WEBHOOK) {
    console.log(`Discord bot webhook configured`);
  } else {
    console.log(`WARNING: Discord bot webhook not configured. Set DISCORD_BOT_WEBHOOK environment variable.`);
  }
});

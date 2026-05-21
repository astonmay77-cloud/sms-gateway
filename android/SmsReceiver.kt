package com.sms.gateway

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsMessage
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern

class SmsReceiver : BroadcastReceiver() {
    
    private val API_URL = "http://YOUR_SERVER_IP:3000/api/sms" // Replace with your server IP
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "android.provider.Telephony.SMS_RECEIVED") {
            val bundle = intent.extras
            if (bundle != null) {
                val pdus = bundle.get("pdus") as Array<ByteArray>
                for (pdu in pdus) {
                    val smsMessage = SmsMessage.createFromPdu(pdu)
                    val sender = smsMessage.displayOriginatingAddress
                    val message = smsMessage.messageBody
                    val timestamp = smsMessage.timestampMillis
                    val deviceId = android.provider.Settings.Secure.getString(
                        context.contentResolver,
                        android.provider.Settings.Secure.ANDROID_ID
                    )
                    
                    Log.d("SmsReceiver", "SMS from $sender: $message")
                    
                    // Parse SMS to extract payment info
                    val paymentInfo = parseSms(message)
                    
                    if (paymentInfo != null) {
                        // Send to API with parsed data
                        sendSmsToApi(sender, message, timestamp, deviceId, paymentInfo)
                    }
                }
            }
        }
    }
    
    private fun parseSms(message: String): PaymentInfo? {
        // Check for bKash SMS
        if (message.contains("bKash") || message.contains("TrxID")) {
            return parseBkashSms(message)
        }
        
        // Check for Nagad SMS
        if (message.contains("Nagad") || message.contains("Nagad") || message.contains("TxnID")) {
            return parseNagadSms(message)
        }
        
        return null
    }
    
    private fun parseBkashSms(message: String): PaymentInfo? {
        // bKash format: "You have received Tk 1,545.00 from 01781225355. Fee Tk 0.00. Balance Tk 1,702.12. TrxID DEL3FFSQOZ at 21/05/2026 01:37"
        
        // Extract TrxID
        val trxIdPattern = Pattern.compile("TrxID\\s+([A-Z0-9]+)", Pattern.CASE_INSENSITIVE)
        val trxIdMatcher = trxIdPattern.matcher(message)
        val trxId = if (trxIdMatcher.find()) trxIdMatcher.group(1) else null
        
        // Extract Amount (format: "Tk 1,545.00" or "Tk 1545.00")
        val amountPattern = Pattern.compile("received\\s+Tk\\s+([\\d,]+\\.?\\d*)", Pattern.CASE_INSENSITIVE)
        val amountMatcher = amountPattern.matcher(message)
        val amount = if (amountMatcher.find()) {
            amountMatcher.group(1).replace(",", "").toDoubleOrNull()
        } else null
        
        return if (trxId != null && amount != null) {
            PaymentInfo(
                provider = "bKash",
                trxId = trxId,
                amount = amount,
                originalMessage = message
            )
        } else null
    }
    
    private fun parseNagadSms(message: String): PaymentInfo? {
        // Nagad format: "Money Received. Amount: Tk 500.00 Sender: 01316224882 Ref: N/A TxnID: 75DV4L5I Balance: Tk 608.39 20/05/2026 22:24"
        
        // Extract TxnID
        val txnIdPattern = Pattern.compile("TxnID:\\s*([A-Z0-9]+)", Pattern.CASE_INSENSITIVE)
        val txnIdMatcher = txnIdPattern.matcher(message)
        val txnId = if (txnIdMatcher.find()) txnIdMatcher.group(1) else null
        
        // Extract Amount (format: "Amount: Tk 500.00")
        val amountPattern = Pattern.compile("Amount:\\s*Tk\\s*([\\d,]+\\.?\\d*)", Pattern.CASE_INSENSITIVE)
        val amountMatcher = amountPattern.matcher(message)
        val amount = if (amountMatcher.find()) {
            amountMatcher.group(1).replace(",", "").toDoubleOrNull()
        } else null
        
        return if (txnId != null && amount != null) {
            PaymentInfo(
                provider = "Nagad",
                trxId = txnId,
                amount = amount,
                originalMessage = message
            )
        } else null
    }
    
    private fun sendSmsToApi(sender: String, message: String, timestamp: Long, deviceId: String, paymentInfo: PaymentInfo) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL(API_URL)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                
                val json = """
                    {
                        "sender": "${sender.replace("\"", "\\\"")}",
                        "message": "${message.replace("\"", "\\\"")}",
                        "timestamp": $timestamp,
                        "deviceId": "$deviceId",
                        "provider": "${paymentInfo.provider}",
                        "trxId": "${paymentInfo.trxId}",
                        "amount": ${paymentInfo.amount}
                    }
                """.trimIndent()
                
                val os = OutputStreamWriter(conn.outputStream)
                os.write(json)
                os.flush()
                os.close()
                
                val responseCode = conn.responseCode
                Log.d("SmsReceiver", "API Response: $responseCode")
                Log.d("SmsReceiver", "Payment detected: ${paymentInfo.provider} - ${paymentInfo.trxId} - ${paymentInfo.amount}")
                
                conn.disconnect()
            } catch (e: Exception) {
                Log.e("SmsReceiver", "Error sending SMS to API", e)
            }
        }
    }
    
    data class PaymentInfo(
        val provider: String,
        val trxId: String,
        val amount: Double,
        val originalMessage: String
    )
}

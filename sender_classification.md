# SMS Sender Classification Guide

This document classifies all unique SMS senders from the raw_sms_20260112.json file.

---

## **INCOME / EXPENSE** (Bank Transactions)

These senders contain actual financial transactions (debits, credits, UPI transfers, card spends, ATM withdrawals).

| Sender Pattern | Bank / Institution | Message Type |
|----------------|-------------------|--------------|
| `*-HDFCBK`, `*-HDFCBN`, `*-HDFCBK-S` | HDFC Bank | Debits, Credits, Card spends, UPI, Mandates, CC payments |
| `*-UNIONB` | Union Bank of India | Debits, Credits |
| `*-ICICIB`, `*-ICICIT`, `*-ICICIT-S` | ICICI Bank | Prepaid card debits/credits, Credit card transactions |
| `*-SBIUPI`, `*-SCISMS`, `*-SBIPSG`, `*-SBIINB`, `*-ATMSBI`, `*-CBSSBI` | State Bank of India | UPI debits/credits, NEFT salary, ATM withdrawals, IMPS |
| `*-BOIIND` | Bank of India | NACH credits |
| `*-IPAYTM`, `*-iPaytm` | Paytm Wallet | Wallet payments, P2P transfers, Recharges |
| `*-PAYTMB` | Paytm Payments Bank | Cashback credits, UPI transfers |

---

## **INVESTMENT / PENSION**

These senders relate to retirement savings and investment contributions.

| Sender Pattern | Institution | Classification | Details |
|----------------|-------------|----------------|---------|
| `*-EPFOHO` (BV, BH, BZ, AD, JD) | EPFO | **Pension** | EPF passbook balance & monthly contribution notifications |
| `*-PTNNPS` (CP, VK, TM, AX, AD) | Protean (NPS) | **Investment/Pension** | NPS (PRAN) unit credits & contributions |

---

## **INSURANCE**

These senders relate to insurance premium payments and reminders.

| Sender Pattern | Institution | Classification | Details |
|----------------|-------------|----------------|---------|
| `TX-POLBAZ` | Policybazaar | **Insurance** | Premium payment confirmations |
| `*-ICICIP` (CP, VM, JM, JK, JD) | ICICI Prudential | **Insurance** | Life insurance premium reminders & confirmations |

---

## **INFO / PROMOTIONAL** (Non-Transactional)

These senders contain informational messages, promotional offers, or service notifications - NOT actual transactions.

| Sender Pattern | Company | Classification | Details |
|----------------|---------|----------------|---------|
| `*-PHONPE`, `*-PHONPE-S` | PhonePe | **Info** | AutoPay requests, money requests, refund notifications |
| `*-JIOPAY`, `*-JioPay` | Jio | **Info/Expense** | Recharge confirmations, plan expiry reminders |
| `*-FLPKRT` | Flipkart | **Info** | Order placed, refund processed notifications |
| `*-MAMERT` | Mamaearth | **Info** | Order confirmations |
| `*-TMESEV` | Telangana Govt | **Info** | Service charge receipts |
| `*-REDBUS` | RedBus | **Info** | Booking confirmations |
| `*-GOFYND` | Fynd | **Info** | Referral credits, promotional |
| `*-LSKART` | Lenskart | **Promotional** | Promotional credit offers |
| `*-RAPIDO` | Rapido | **Promotional** | Wallet credits, promotional |
| `*-CREDIN` | CRED | **Info** | App updates |
| `SmplPL` | Simpl | **Expense** | Buy-now-pay-later charges (Zepto, etc.) |

---

## **TELECOM (Recharge / Billing)**

These senders relate to mobile recharges and telecom services.

| Sender Pattern | Company | Classification | Details |
|----------------|---------|----------------|---------|
| `ERecharge` | Airtel | **Expense** | Recharge confirmations |
| `*-AIRTEL`, `*-AIRSLF`, `*-AIROPT`, `*-IMPINF`, `*-RECHRG` | Airtel | **Info/Expense** | Recharge reminders, plan info |
| `*-Docomo`, `*-DOCOMO` | Tata Docomo | **Expense** | Recharge confirmations |
| `650001` - `650143` | Telecom (various) | **Promotional** | Promotional recharge offers |

---

## **SUMMARY TABLE**

| Classification | Count | Sender Patterns |
|----------------|-------|-----------------|
| **Income/Expense (Banks)** | ~15 | `*-HDFCBK`, `*-UNIONB`, `*-ICICIB`, `*-ICICIT`, `*-SBI*`, `*-BOIIND`, `*-PAYTM*` |
| **Pension** | ~5 | `*-EPFOHO` |
| **Investment** | ~5 | `*-PTNNPS` |
| **Insurance** | ~6 | `TX-POLBAZ`, `*-ICICIP` |
| **Info/Promotional** | ~20+ | `*-PHONPE`, `*-FLPKRT`, `*-RAPIDO`, `*-LSKART`, `650*`, etc. |
| **Telecom/Recharge** | ~10 | `ERecharge`, `*-AIRTEL`, `*-Docomo`, `*-JIOPAY` |

---

## **CLASSIFICATION RULES**

1. **Sender ID Structure**: Indian SMS senders follow the pattern `XX-XXXXXX` where:
   - First 2 chars = prefix (varies)
   - Hyphen separator
   - 6 chars = organization code

2. **Bank Identification**:
   - `HDFCBK` = HDFC Bank
   - `UNIONB` = Union Bank
   - `ICICIB/ICICIT` = ICICI Bank
   - `SBI*` = State Bank of India
   - `BOIIND` = Bank of India

3. **Transaction Indicators**:
   - "debited", "w/d" = Expense
   - "credited", "deposited" = Income
   - "spent", "Paid" = Expense
   - "Cardmember, Payment" = Credit Card bill payment (LIABILITY)

4. **Non-Transaction Indicators**:
   - "AutoPay request" = Info only (pending)
   - "has requested money" = Info only (pending)
   - "Order Placed" = Info (order confirmation)
   - "Recharge will expire" = Info (reminder)

# Transaction Categorization Guide

## How I Categorized Each Transaction

### Column 1: Transaction Type
**Decision tree:**

1. **INVALID** - Not a real transaction
   - Promotional messages
   - Balance inquiries
   - Reminders/notifications
   - Incomplete information

2. **INCOME** - Money received
   - Salary deposits (NEFT from employer)
   - UPI money received (credited to account)
   - Refunds
   - Cash deposits
   - Investment returns

3. **EXPENSE** - Money spent
   - Card purchases (spent at merchant)
   - UPI payments sent
   - Recharges
   - Bill payments
   - Online shopping
   - ATM withdrawals

4. **TRANSFER** - Moving money between own accounts
   - Account to account (HDFC XX4072 to Union Bank XX4269)
   - Same person, different accounts
   - Self-transfers

5. **LIABILITY** - Credit card payments
   - Payment to credit card (reduces liability)
   - "Payment credited to card"

### Column 2: Category
**Based on merchant/description:**

- **Food & Dining**: Restaurants, cafes, food delivery
  - CREAM STONE, SUBWAY, PIZZA HUT, Swiggy, Zomato, THE NAWAABS, PISTA HOUSE, KFC

- **Groceries**: Supermarkets, grocery stores
  - DMART, Reliance Fresh, VIJETHA, SMPOORNA SUPER, Ratnadeep, BHARAT BAZAR

- **Fuel**: Petrol pumps
  - VENKATADRI FUEL

- **Shopping**: Retail, clothing, general merchandise
  - FLIPKART, AMAZON, Jockey, Zudio, Reliance Trends, Diverse Retail

- **Transport**: Travel, cab services
  - TSRTC, Rapido, UrbanClap

- **Recharge**: Mobile, DTH recharges
  - Jio, Paytm recharge

- **Insurance**: Life, health insurance premiums
  - ICICI Pru, Policybazaar

- **Investment**: Mutual funds, stocks, savings
  - Upstox, NPS, EPF contributions

- **Bills & Utilities**: Electricity, subscriptions
  - Disney Hotstar, PVR

- **Health & Fitness**: Medical, gym
  - GNC

- **Transfer**: Self-transfers, account deposits
  - Between own accounts

- **Salary**: Monthly income
  - From employer (TATA CONSULTANCY, ZF INDIA)

- **Credit Card Payment**: Payments to credit card

- **Cash**: ATM withdrawals, cash deposits

- **Other**: Miscellaneous

### Column 3: Counterparty (From/To Whom)

**Pattern matching:**

1. **For Expenses (sent to):**
   - Merchant name from message (e.g., "at CREAM STONE" → "CREAM STONE")
   - VPA if UPI (e.g., "to VPA paytmqr..." → "Paytm QR")
   - Phone number if P2P transfer

2. **For Income (received from):**
   - Sender name from message (e.g., "by a/c linked to VPA naveen@ybl" → "naveen@ybl")
   - Employer name for salary
   - Cash if cash deposit

3. **For Transfers:**
   - Format: "HDFC XX4072 → Union XX4269" or vice versa
   - Shows source and destination accounts

4. **For Liability:**
   - "Credit Card XX0780" (the card being paid)

## Examples

### Example 1: Expense
```json
{
  "body": "Rs.212.00 debited from ICICI Bank Prepaid Card 3505 on 27-Aug-23. Info- CREAM STONE.",
  "type": "EXPENSE",
  "category": "Food & Dining",
  "counterparty": "CREAM STONE"
}
```

### Example 2: Income
```json
{
  "body": "INR 20,694.00 credited to your A/c No XXXXXXX2916 on 28/06/19 through NEFT by TATA CONSULTANCY SERVICES LIMITED",
  "type": "INCOME",
  "category": "Salary",
  "counterparty": "TATA CONSULTANCY SERVICES LIMITED"
}
```

### Example 3: Transfer
```json
{
  "body": "HDFC Bank: Rs. 30000.00 debited from a/c **4072 to a/c **4269 (UPI Ref No. 122433465340)",
  "type": "TRANSFER",
  "category": "Transfer",
  "counterparty": "HDFC XX4072 → Union XX4269"
}
```

### Example 4: Liability
```json
{
  "body": "HDFC Bank Cardmember, Payment of Rs 82453 was credited to your card ending 0780",
  "type": "LIABILITY",
  "category": "Credit Card Payment",
  "counterparty": "Credit Card XX0780"
}
```

### Example 5: Invalid
```json
{
  "body": "Your time is precious. To enjoy hassle free premium payment...",
  "type": "INVALID",
  "category": "Promotional",
  "counterparty": "N/A"
}
```

## Key Rules

1. **Self-transfers are TRANSFER**, not EXPENSE
   - Look for same person, different accounts
   - HDFC → Union Bank (both user's accounts)

2. **Credit card payments are LIABILITY**, not EXPENSE
   - "Payment credited to card"
   - Reduces outstanding, not an expense

3. **Prepaid card loads are TRANSFER**, not EXPENSE
   - "credited with Rs 2,200" (money added to card)

4. **EPF/NPS contributions are INVESTMENT/EXPENSE**
   - Mandatory deductions: EXPENSE (Salary component)
   - Voluntary contributions: INVESTMENT

5. **Declined transactions are INVALID**
   - "has been declined" → INVALID

6. **Promotional credits (Lenskart, etc.) are INVALID**
   - Not real money

7. **Recharges are EXPENSE**
   - "Recharge of Rs. 395.00 is successful"
